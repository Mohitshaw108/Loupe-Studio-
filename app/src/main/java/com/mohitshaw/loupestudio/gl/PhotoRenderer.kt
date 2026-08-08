package com.mohitshaw.loupestudio.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import com.mohitshaw.loupestudio.Adjustments
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renders one photo through the tone/color + sharpen pipeline entirely on
 * the GPU, at the image's native resolution, every frame. There is no
 * "preview resolution vs full-res commit" split like the web build has —
 * the shader is cheap enough that full-res live editing is the normal case.
 */
class PhotoRenderer : GLSurfaceView.Renderer {

    // Public, mutated from the UI thread; only read on the GL thread inside onDrawFrame.
    @Volatile var adjustments: Adjustments = Adjustments.NEUTRAL
    @Volatile private var pendingBitmap: Bitmap? = null
    @Volatile var surfaceWidth = 0
        private set
    @Volatile var surfaceHeight = 0
        private set

    private var imageWidth = 0
    private var imageHeight = 0

    private var toneProgram = 0
    private var blurProgram = 0
    private var sharpenProgram = 0
    private var copyProgram = 0

    private var sourceTexture = 0
    private var toneFbo = FboTarget()
    private var blurFboA = FboTarget()
    private var blurFboB = FboTarget()

    private var quadVertices: FloatBuffer
    private var pendingExport: ((Bitmap?) -> Unit)? = null

    init {
        val verts = floatArrayOf(
            // x, y,      u, v
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f
        )
        quadVertices = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(verts); position(0) }
    }

    fun loadBitmap(bmp: Bitmap) {
        pendingBitmap = bmp
    }

    /** Reads back the current full-res, fully-processed frame as a Bitmap. Callback fires on GL thread. */
    fun requestExport(callback: (Bitmap?) -> Unit) {
        pendingExport = callback
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        toneProgram = GlUtil.buildProgram(Shaders.VERTEX_SHADER, Shaders.FRAGMENT_TONE)
        blurProgram = GlUtil.buildProgram(Shaders.VERTEX_SHADER, Shaders.FRAGMENT_BOX_BLUR)
        sharpenProgram = GlUtil.buildProgram(Shaders.VERTEX_SHADER, Shaders.FRAGMENT_SHARPEN_COMPOSITE)
        copyProgram = GlUtil.buildProgram(Shaders.VERTEX_SHADER, Shaders.FRAGMENT_COPY)
        GLES20.glClearColor(0.035f, 0.039f, 0.051f, 1f) // matches --bg: #090A0D
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingBitmap?.let { bmp ->
            uploadTexture(bmp)
            imageWidth = bmp.width
            imageHeight = bmp.height
            ensureFbos(imageWidth, imageHeight)
            pendingBitmap = null
        }
        if (sourceTexture == 0 || imageWidth == 0) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }

        // Pass 1: tone/color -> toneFbo, at full image resolution.
        drawTonePass(sourceTexture, toneFbo, imageWidth, imageHeight)

        var finalTexture = toneFbo.texture
        val amount = adjustments.sharpen / 100f * 1.6f
        if (adjustments.sharpen > 0f) {
            drawBlurPass(toneFbo.texture, blurFboA, imageWidth, imageHeight, 1f / imageWidth, 0f)
            drawBlurPass(blurFboA.texture, blurFboB, imageWidth, imageHeight, 0f, 1f / imageHeight)
            drawSharpenComposite(toneFbo.texture, blurFboB.texture, toneFbo, imageWidth, imageHeight, amount)
            finalTexture = toneFbo.texture
        }

        // Blit the final full-res result to the visible surface, letterboxed to fit.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        drawLetterboxed(finalTexture, imageWidth, imageHeight)

        pendingExport?.let { cb ->
            pendingExport = null
            cb(readFboAsBitmap(toneFbo, imageWidth, imageHeight))
        }
    }

    // ---------- pass helpers ----------

    private fun drawTonePass(srcTex: Int, dst: FboTarget, w: Int, h: Int) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, dst.framebuffer)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glUseProgram(toneProgram)
        bindQuad(toneProgram)
        bindTexture(toneProgram, "uTexture", srcTex, 0)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(toneProgram, "uResolution"), w.toFloat(), h.toFloat())
        val a = adjustments
        GLES20.glUniform1f(GLES20.glGetUniformLocation(toneProgram, "uExposure"), a.exposure)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(toneProgram, "uContrast"), a.contrast)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(toneProgram, "uHighlights"), a.highlights)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(toneProgram, "uShadows"), a.shadows)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(toneProgram, "uSaturation"), a.saturation)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(toneProgram, "uTemperature"), a.temperature)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(toneProgram, "uVignette"), a.vignette)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(toneProgram, "uSplitShadow"), a.splitShadow.first, a.splitShadow.second, a.splitShadow.third)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(toneProgram, "uSplitHigh"), a.splitHigh.first, a.splitHigh.second, a.splitHigh.third)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(toneProgram, "uSplitAmount"), a.splitAmount)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(toneProgram, "uMixR"), a.mixR)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(toneProgram, "uMixG"), a.mixG)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(toneProgram, "uMixB"), a.mixB)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawBlurPass(srcTex: Int, dst: FboTarget, w: Int, h: Int, stepX: Float, stepY: Float) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, dst.framebuffer)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glUseProgram(blurProgram)
        bindQuad(blurProgram)
        bindTexture(blurProgram, "uTexture", srcTex, 0)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(blurProgram, "uTexelStep"), stepX, stepY)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawSharpenComposite(sharpTex: Int, blurredTex: Int, dst: FboTarget, w: Int, h: Int, amount: Float) {
        // Composite into blurFboA as scratch, then copy back into dst's texture via a draw
        // (kept simple: we draw into blurFboA then treat it as the new "toneFbo" contents).
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFboA.framebuffer)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glUseProgram(sharpenProgram)
        bindQuad(sharpenProgram)
        bindTexture(sharpenProgram, "uSharp", sharpTex, 0)
        bindTexture(sharpenProgram, "uBlurred", blurredTex, 1)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(sharpenProgram, "uAmount"), amount)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        // Copy the composited result back into dst (toneFbo) so callers can treat toneFbo as final.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, dst.framebuffer)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glUseProgram(copyProgram)
        bindQuad(copyProgram)
        bindTexture(copyProgram, "uTexture", blurFboA.texture, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawLetterboxed(tex: Int, imgW: Int, imgH: Int) {
        val surfaceAspect = surfaceWidth.toFloat() / surfaceHeight
        val imageAspect = imgW.toFloat() / imgH
        var vx = 1f; var vy = 1f
        if (imageAspect > surfaceAspect) vy = surfaceAspect / imageAspect else vx = imageAspect / surfaceAspect
        val verts = floatArrayOf(
            -vx, -vy, 0f, 1f,
            vx, -vy, 1f, 1f,
            -vx, vy, 0f, 0f,
            vx, vy, 1f, 0f
        )
        val buf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts); buf.position(0)
        GLES20.glUseProgram(copyProgram)
        val posLoc = GLES20.glGetAttribLocation(copyProgram, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(copyProgram, "aTexCoord")
        buf.position(0)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, buf)
        buf.position(2)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 16, buf)
        bindTexture(copyProgram, "uTexture", tex, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun bindQuad(program: Int) {
        val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        quadVertices.position(0)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, quadVertices)
        quadVertices.position(2)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 16, quadVertices)
    }

    private fun bindTexture(program: Int, uniform: String, tex: Int, unit: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, uniform), unit)
    }

    private fun uploadTexture(bmp: Bitmap) {
        if (sourceTexture == 0) sourceTexture = GlUtil.createTexture()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTexture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
    }

    private fun ensureFbos(w: Int, h: Int) {
        toneFbo.ensure(w, h)
        blurFboA.ensure(w, h)
        blurFboB.ensure(w, h)
    }

    private fun readFboAsBitmap(fbo: FboTarget, w: Int, h: Int): Bitmap {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo.framebuffer)
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buf)
        // glReadPixels is bottom-up; flip vertically to match Bitmap's top-down convention.
        val matrix = android.graphics.Matrix().apply { preScale(1f, -1f) }
        return Bitmap.createBitmap(bmp, 0, 0, w, h, matrix, true)
    }
}

/** A framebuffer + backing texture pair, (re)allocated when the image size changes. */
private class FboTarget {
    var framebuffer = 0
        private set
    var texture = 0
        private set
    private var w = 0
    private var h = 0

    fun ensure(width: Int, height: Int) {
        if (framebuffer != 0 && w == width && h == height) return
        release()
        w = width; h = height
        texture = GlUtil.createTexture(width, height)
        val fbos = IntArray(1)
        GLES20.glGenFramebuffers(1, fbos, 0)
        framebuffer = fbos[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0)
    }

    private fun release() {
        if (framebuffer != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
        framebuffer = 0; texture = 0
    }
}
