package com.mohit.loupestudio

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.max
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object PhotoRenderer {

    private const val AGSL_SHADER = """
        uniform shader inputImage;
        uniform float2 resolution;
        uniform float exposure;
        uniform float contrast;
        uniform float saturation;
        uniform float warmth;
        uniform float sharpness;
        uniform float vignette;
        uniform int loupeEnabled;
        uniform float2 loupeCenter;
        uniform float loupeRadius;

        float3 applyColorGrading(float3 col, float2 uv) {
            // 1. Exposure (EV stop approximation)
            col *= exp2(exposure);

            // 2. Contrast
            col = (col - 0.5) * contrast + 0.5;

            // 3. Warmth (Orange/Blue tint balance)
            col.r += warmth * 0.10;
            col.g += warmth * 0.02;
            col.b -= warmth * 0.10;

            // 4. Saturation
            float luma = dot(col, float3(0.299, 0.587, 0.114));
            col = mix(float3(luma), col, saturation);

            // 5. Vignette
            if (vignette > 0.001) {
                float2 centered = uv - float2(0.5, 0.5);
                float dist = length(centered) * 1.414;
                float vig = smoothstep(1.0, 0.35, dist * vignette);
                col *= mix(1.0, vig, vignette);
            }

            return clamp(col, 0.0, 1.0);
        }

        half4 main(float2 coord) {
            float2 res = max(resolution, float2(1.0, 1.0));
            float2 sampleCoord = coord;
            float distToLoupe = distance(coord, loupeCenter);
            bool insideLoupe = (loupeEnabled == 1) && (distToLoupe < loupeRadius);

            // Optical Loupe 2.5x Zoom with subtle spherical lens distortion
            if (insideLoupe) {
                float2 delta = coord - loupeCenter;
                float normDist = distToLoupe / max(loupeRadius, 1.0);
                float lensCurve = mix(0.38, 0.45, normDist * normDist);
                sampleCoord = loupeCenter + delta * lensCurve;
            }

            sampleCoord = clamp(sampleCoord, float2(0.0, 0.0), res - float2(1.0, 1.0));
            float3 centerCol = float3(inputImage.eval(sampleCoord).rgb);

            // 5-Tap GPU Convolution Sharpening
            if (sharpness > 0.01) {
                float3 n = float3(inputImage.eval(clamp(sampleCoord + float2(0.0, -1.0), float2(0.0), res)).rgb);
                float3 s = float3(inputImage.eval(clamp(sampleCoord + float2(0.0, 1.0), float2(0.0), res)).rgb);
                float3 e = float3(inputImage.eval(clamp(sampleCoord + float2(1.0, 0.0), float2(0.0), res)).rgb);
                float3 w = float3(inputImage.eval(clamp(sampleCoord + float2(-1.0, 0.0), float2(0.0), res)).rgb);
                float3 blur = (n + s + e + w) * 0.25;
                centerCol = clamp(centerCol + (centerCol - blur) * (sharpness * 1.6), 0.0, 1.0);
            }

            float2 uv = sampleCoord / res;
            float3 finalCol = applyColorGrading(centerCol, uv);

            // Render optical glass rim for Loupe
            if (loupeEnabled == 1) {
                float rim = abs(distToLoupe - loupeRadius);
                if (rim < 3.5) {
                    finalCol = mix(finalCol, float3(0.95, 0.98, 1.0), 0.85);
                } else if (rim < 6.0) {
                    finalCol *= 0.45;
                }
            }

            return half4(half3(finalCol), 1.0);
        }
    """

    fun render(
        source: Bitmap,
        adjustments: Adjustments,
        loupeEnabled: Boolean = false,
        loupeCenterX: Float = 0.5f,
        loupeCenterY: Float = 0.5f
    ): Bitmap {
        val width = source.width
        val height = source.height

        return try {
            val runtimeShader = RuntimeShader(AGSL_SHADER)
            val bitmapShader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

            runtimeShader.setInputShader("inputImage", bitmapShader)
            runtimeShader.setFloatUniform("resolution", width.toFloat(), height.toFloat())
            runtimeShader.setFloatUniform("exposure", adjustments.exposure)
            runtimeShader.setFloatUniform("contrast", adjustments.contrast)
            runtimeShader.setFloatUniform("saturation", adjustments.saturation)
            runtimeShader.setFloatUniform("warmth", adjustments.warmth)
            runtimeShader.setFloatUniform("sharpness", adjustments.sharpness)
            runtimeShader.setFloatUniform("vignette", adjustments.vignette)
            runtimeShader.setIntUniform("loupeEnabled", if (loupeEnabled) 1 else 0)
            runtimeShader.setFloatUniform(
                "loupeCenter",
                loupeCenterX * width.toFloat(),
                loupeCenterY * height.toFloat()
            )
            runtimeShader.setFloatUniform("loupeRadius", min(width, height) * 0.22f)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = runtimeShader
            }

            // Hardware-accelerated GPU offscreen rendering via Picture
            val picture = Picture()
            val recordingCanvas = picture.beginRecording(width, height)
            recordingCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            picture.endRecording()

            val hwBitmap = Bitmap.createBitmap(picture, width, height, Bitmap.Config.HARDWARE)
            val outBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
            hwBitmap.recycle()
            outBitmap
        } catch (e: Exception) {
            // Safe fallback if a device driver does not support offscreen Hardware Picture
            renderFallback(source, adjustments)
        }
    }

    private fun renderFallback(source: Bitmap, adj: Adjustments): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val cm = ColorMatrix().apply {
            setSaturation(adj.saturation)
        }
        val scale = adj.contrast * Math.pow(2.0, adj.exposure.toDouble()).toFloat()
        val translate = (-0.5f * adj.contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate + adj.warmth * 25f,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate - adj.warmth * 25f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(contrastMatrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }
}
