package com.mohit.loupestudio

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Real on-device AI super-resolution using ESRGAN (TensorFlow Lite).
 *
 * The model works on small fixed-size tiles (e.g. 50x50 -> 200x200), so the image is processed
 * tile by tile. Tiles overlap and only their centre is kept, which avoids visible seams.
 * If the model cannot be loaded or run, an exception is thrown (no silent fake fallback).
 */
class AiEnhancer(private val context: Context) {

    companion object {
        // Must match the asset name exactly (Android assets are case-sensitive)
        private const val MODEL_FILENAME = "ESRGAN.tflite"

        // ESRGAN is heavy on a phone CPU; this keeps runtime and memory reasonable (4x -> up to 2560px)
        private const val MAX_INPUT_DIMENSION = 640

        // Pixels of context around each tile that are discarded to hide tile borders
        private const val TILE_OVERLAP = 8
    }

    suspend fun enhanceImage(
        input: Bitmap,
        onProgress: (Float, String) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        onProgress(0.02f, "Preparing image...")

        val working = if (max(input.width, input.height) > MAX_INPUT_DIMENSION) {
            val ratio = MAX_INPUT_DIMENSION.toFloat() / max(input.width, input.height)
            Bitmap.createScaledBitmap(
                input,
                (input.width * ratio).roundToInt().coerceAtLeast(8),
                (input.height * ratio).roundToInt().coerceAtLeast(8),
                true
            )
        } else {
            input.copy(Bitmap.Config.ARGB_8888, false)
        }

        onProgress(0.04f, "Loading AI model...")
        val model = loadModel()
        runEsrgan(working, model, onProgress)
    }

    private fun loadModel(): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILENAME)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }

    private suspend fun runEsrgan(
        source: Bitmap,
        modelBuffer: MappedByteBuffer,
        onProgress: (Float, String) -> Unit
    ): Bitmap {
        val options = Interpreter.Options().apply {
            setNumThreads(max(2, Runtime.getRuntime().availableProcessors().coerceAtMost(6)))
            setUseXNNPACK(true)
        }

        Interpreter(modelBuffer, options).use { interpreter ->
            val inTensor = interpreter.getInputTensor(0)
            val outTensor = interpreter.getOutputTensor(0)
            check(inTensor.dataType() == DataType.FLOAT32) { "Unsupported model type: ${inTensor.dataType()}" }

            val inShape = inTensor.shape()   // [1, H, W, 3]
            val outShape = outTensor.shape() // [1, H*s, W*s, 3]
            val tileH = inShape[1]
            val tileW = inShape[2]
            val outTileH = outShape[1]
            val outTileW = outShape[2]
            val scale = outTileH / tileH
            check(scale >= 1 && tileH > 2 * TILE_OVERLAP && tileW > 2 * TILE_OVERLAP) { "Unexpected model shape" }

            val srcW = source.width
            val srcH = source.height
            val outW = srcW * scale
            val outH = srcH * scale

            val srcPixels = IntArray(srcW * srcH)
            source.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)
            val outPixels = IntArray(outW * outH)

            val strideX = tileW - 2 * TILE_OVERLAP
            val strideY = tileH - 2 * TILE_OVERLAP
            val tilesX = (srcW + strideX - 1) / strideX
            val tilesY = (srcH + strideY - 1) / strideY
            val totalTiles = tilesX * tilesY

            val inBuffer = ByteBuffer.allocateDirect(tileH * tileW * 3 * 4).order(ByteOrder.nativeOrder())
            val outBuffer = ByteBuffer.allocateDirect(outTileH * outTileW * 3 * 4).order(ByteOrder.nativeOrder())
            val outFloats = FloatArray(outTileH * outTileW * 3)

            var done = 0
            for (ty in 0 until tilesY) {
                for (tx in 0 until tilesX) {
                    ensureActive()

                    // Interior region (the part of the source this tile is responsible for)
                    val ix = tx * strideX
                    val iy = ty * strideY
                    val iw = min(strideX, srcW - ix)
                    val ih = min(strideY, srcH - iy)

                    // Fill the input tile, clamping coordinates at the image border
                    inBuffer.rewind()
                    for (y in 0 until tileH) {
                        val sy = (iy - TILE_OVERLAP + y).coerceIn(0, srcH - 1)
                        for (x in 0 until tileW) {
                            val sx = (ix - TILE_OVERLAP + x).coerceIn(0, srcW - 1)
                            val px = srcPixels[sy * srcW + sx]
                            inBuffer.putFloat(((px shr 16) and 0xFF).toFloat())
                            inBuffer.putFloat(((px shr 8) and 0xFF).toFloat())
                            inBuffer.putFloat((px and 0xFF).toFloat())
                        }
                    }

                    inBuffer.rewind()
                    outBuffer.rewind()
                    interpreter.run(inBuffer, outBuffer)
                    outBuffer.rewind()
                    outBuffer.asFloatBuffer().get(outFloats)

                    // Copy only the interior of the upscaled tile into the final image
                    val margin = TILE_OVERLAP * scale
                    for (oy in 0 until ih * scale) {
                        val dstRow = (iy * scale + oy) * outW + ix * scale
                        val srcRow = (margin + oy) * outTileW + margin
                        for (ox in 0 until iw * scale) {
                            val o = (srcRow + ox) * 3
                            val r = outFloats[o].roundToInt().coerceIn(0, 255)
                            val g = outFloats[o + 1].roundToInt().coerceIn(0, 255)
                            val b = outFloats[o + 2].roundToInt().coerceIn(0, 255)
                            outPixels[dstRow + ox] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }

                    done++
                    onProgress(
                        0.05f + 0.93f * done / totalTiles,
                        "AI upscaling ${scale}x ($done/$totalTiles)..."
                    )
                }
            }

            val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            result.setPixels(outPixels, 0, outW, 0, 0, outW, outH)
            onProgress(1f, "Done")
            return result
        }
    }
}
