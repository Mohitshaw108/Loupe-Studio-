package com.loupestudio.editor.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.loupestudio.editor.Adjustments

class PhotoGLSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val renderer = PhotoRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        // Only redraw when something actually changed (new image, slider move, export
        // request) — not a fixed 60fps loop. Cheap on battery, still feels live because
        // every adjustment call is followed by requestRender().
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setBitmap(bmp: Bitmap) {
        renderer.loadBitmap(bmp)
        requestRender()
    }

    fun setAdjustments(adjustments: Adjustments) {
        renderer.adjustments = adjustments
        requestRender()
    }

    fun exportBitmap(callback: (Bitmap?) -> Unit) {
        renderer.requestExport(callback)
        requestRender()
    }
}
