package com.mohitshaw.loupestudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Wraps the ESRGAN TFLite model (https://tfhub.dev/captain-pool/esrgan-tf2/1,
 * converted to TFLite by the TensorFlow team). The model's input is FIXED at
 * 50x50 px and always outputs 200x200 px (a fixed 4x scale factor) — it can't
 * take an arbitrary-size image directly, so this class tiles the source
 * image into 50x50 blocks, runs each tile through the model, and stitches
 * the 200x200 results back into one 4x image.
 *
 * Model file expected at: app/src/main/assets/ESRGAN.tflite
 */
class ImageUpscaler(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = loadModelFile(context, "ESRGAN.tflite")
        val options = Interpreter.Options().apply { setNumThreads(4) }
        interpreter = Interpreter(model, options)
    }

    private fun loadModelFile(context: Context, assetName: String): ByteBuffer {
        val afd = context.assets.openFd(assetName)
        FileChannel::class // no-op, keeps import used clearly
        val stream = afd.createInputStream()
        val channel = stream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }

    /**
     * Runs 4x AI upscaling on [src]. Blocking — call from a background thread/coroutine.
     * [onProgress] reports 0f..1f across tiles, useful for a progress bar since a large
     * photo can be 200+ tiles.
     */
    fun upscale4x(src: Bitmap, onProgress: ((Float) -> Unit)? = null): Bitmap {
        val tile = 50
        val scale = 4
        val srcW = src.width
        val srcH = src.height
        val tilesX = (srcW + tile - 1) / tile
        val tilesY = (srcH + tile - 1) / tile
        val outBitmap = Bitmap.createBitmap(srcW * scale, srcH * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)

        val inputBuffer = ByteBuffer.allocateDirect(4 * tile * tile * 3).order(ByteOrder.nativeOrder())
        val outputBuffer = ByteBuffer.allocateDirect(4 * (tile * scale) * (tile * scale) * 3).order(ByteOrder.nativeOrder())

        val totalTiles = tilesX * tilesY
        var done = 0

        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                val tileBitmap = extractTileClamped(src, tx * tile, ty * tile, tile, tile)

                inputBuffer.rewind()
                val pixels = IntArray(tile * tile)
                tileBitmap.getPixels(pixels, 0, tile, 0, 0, tile, tile)
                for (p in pixels) {
                    inputBuffer.putFloat(((p shr 16) and 0xFF).toFloat())
                    inputBuffer.putFloat(((p shr 8) and 0xFF).toFloat())
                    inputBuffer.putFloat((p and 0xFF).toFloat())
                }
                inputBuffer.rewind()
                outputBuffer.rewind()

                interpreter.run(inputBuffer, outputBuffer)

                val outTile = tile * scale
                val outPixels = IntArray(outTile * outTile)
                outputBuffer.rewind()
                for (i in outPixels.indices) {
                    val r = outputBuffer.float.coerceIn(0f, 255f).toInt()
                    val g = outputBuffer.float.coerceIn(0f, 255f).toInt()
                    val b = outputBuffer.float.coerceIn(0f, 255f).toInt()
                    outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                val resultTile = Bitmap.createBitmap(outTile, outTile, Bitmap.Config.ARGB_8888)
                resultTile.setPixels(outPixels, 0, outTile, 0, 0, outTile, outTile)
                canvas.drawBitmap(resultTile, (tx * outTile).toFloat(), (ty * outTile).toFloat(), null)
                resultTile.recycle()
                tileBitmap.recycle()

                done++
                onProgress?.invoke(done.toFloat() / totalTiles)
            }
        }
        return outBitmap
    }

    /** Crops a tile*tile region starting at (x,y); if it runs past the source edges,
     *  clamps by replicating edge pixels instead of leaving black/transparent, so tile
     *  seams at the right/bottom border don't introduce a visible dark edge. */
    private fun extractTileClamped(src: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (x + w <= src.width && y + h <= src.height) {
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, x, y, w, h)
            out.setPixels(pixels, 0, w, 0, 0, w, h)
            return out
        }
        val srcPixels = IntArray(w * h)
        for (row in 0 until h) {
            val sy = (y + row).coerceAtMost(src.height - 1)
            for (col in 0 until w) {
                val sx = (x + col).coerceAtMost(src.width - 1)
                srcPixels[row * w + col] = src.getPixel(sx, sy)
            }
        }
        out.setPixels(srcPixels, 0, w, 0, 0, w, h)
        return out
    }

    fun close() = interpreter.close()
}
