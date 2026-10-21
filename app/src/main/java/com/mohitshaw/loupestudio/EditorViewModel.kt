package com.mohit.loupestudio

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUiState(
    val sourceBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val adjustments: Adjustments = Adjustments(),
    val activePreset: String = "Original",
    val loupeEnabled: Boolean = false,
    val loupeX: Float = 0.5f,
    val loupeY: Float = 0.5f,
    val showOriginal: Boolean = false,
    val isAiRunning: Boolean = false,
    val aiProgress: Float = 0f,
    val statusMessage: String = "Ready",
    val resolutionLabel: String = ""
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val aiEnhancer = AiEnhancer(application.applicationContext)
    private var renderJob: Job? = null

    init {
        val sample = createSampleStudioBitmap(800, 1000)
        _uiState.value = _uiState.value.copy(
            sourceBitmap = sample,
            previewBitmap = sample,
            resolutionLabel = "${sample.width} × ${sample.height} px",
            statusMessage = "Sample loaded • Pick a photo or tap AI Upscale 4x"
        )
    }

    fun loadFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val maxDim = maxOf(info.size.width, info.size.height)
                    if (maxDim > 1600) {
                        val scale = 1600f / maxDim
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt(),
                            (info.size.height * scale).toInt()
                        )
                    }
                }
                _uiState.value = _uiState.value.copy(
                    sourceBitmap = decoded,
                    adjustments = Adjustments(),
                    activePreset = "Original",
                    resolutionLabel = "${decoded.width} × ${decoded.height} px",
                    statusMessage = "Photo loaded"
                )
                triggerRender()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Failed to load image: ${e.localizedMessage}"
                )
            }
        }
    }

    fun updateAdjustments(newAdjustments: Adjustments, presetName: String = "Custom") {
        _uiState.value = _uiState.value.copy(
            adjustments = newAdjustments,
            activePreset = presetName
        )
        triggerRender()
    }

    fun applyPreset(preset: StylePreset) {
        updateAdjustments(preset.adjustments, preset.name)
    }

    fun toggleLoupe() {
        val next = !_uiState.value.loupeEnabled
        _uiState.value = _uiState.value.copy(
            loupeEnabled = next,
            statusMessage = if (next) "Loupe Active: Drag finger on photo to inspect" else "Loupe Off"
        )
        triggerRender()
    }

    fun updateLoupePosition(normX: Float, normY: Float) {
        if (!_uiState.value.loupeEnabled) return
        _uiState.value = _uiState.value.copy(
            loupeX = normX.coerceIn(0.05f, 0.95f),
            loupeY = normY.coerceIn(0.05f, 0.95f)
        )
        triggerRender()
    }

    fun setShowOriginal(show: Boolean) {
        _uiState.value = _uiState.value.copy(showOriginal = show)
    }

    fun runAiEnhancer() {
        val currentSource = _uiState.value.sourceBitmap ?: return
        if (_uiState.value.isAiRunning) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAiRunning = true,
                aiProgress = 0.05f,
                statusMessage = "Starting AI Super-Resolution..."
            )
            try {
                val enhanced = aiEnhancer.enhanceImage(currentSource) { progress, msg ->
                    _uiState.value = _uiState.value.copy(
                        aiProgress = progress,
                        statusMessage = msg
                    )
                }
                _uiState.value = _uiState.value.copy(
                    sourceBitmap = enhanced,
                    isAiRunning = false,
                    aiProgress = 1f,
                    resolutionLabel = "${enhanced.width} × ${enhanced.height} px (AI 4x)",
                    statusMessage = "AI Super-Resolution Complete (${enhanced.width}×${enhanced.height})"
                )
                triggerRender()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isAiRunning = false,
                    statusMessage = "AI Enhance error: ${e.localizedMessage ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    fun saveToGallery() {
        val state = _uiState.value
        val src = state.sourceBitmap ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val finalBitmap = PhotoRenderer.render(
                    source = src,
                    adjustments = state.adjustments,
                    loupeEnabled = false
                )

                val context = getApplication<Application>().applicationContext
                val filename = "LoupeStudio_${System.currentTimeMillis()}.jpg"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/LoupeStudio"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 96, outStream)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            statusMessage = "Saved HD photo to Pictures/LoupeStudio!"
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Save failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun triggerRender() {
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            val state = _uiState.value
            val src = state.sourceBitmap ?: return@launch
            val rendered = PhotoRenderer.render(
                source = src,
                adjustments = state.adjustments,
                loupeEnabled = state.loupeEnabled,
                loupeCenterX = state.loupeX,
                loupeCenterY = state.loupeY
            )
            _uiState.value = _uiState.value.copy(previewBitmap = rendered)
        }
    }

    /**
     * Generates a rich, detailed test artwork on startup so the user can test
     * the AGSL shaders, Loupe magnifier, and AI 4x upscaler right away.
     */
    private fun createSampleStudioBitmap(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Sunset sky gradient
        paint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(
                Color.rgb(18, 24, 58),
                Color.rgb(88, 42, 94),
                Color.rgb(215, 92, 72),
                Color.rgb(248, 178, 92)
            ),
            floatArrayOf(0f, 0.42f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Glowing sun with fine concentric rings (great for testing Loupe & AI sharpening)
        val sunX = width * 0.5f
        val sunY = height * 0.48f
        paint.shader = RadialGradient(
            sunX, sunY, width * 0.28f,
            intArrayOf(Color.rgb(255, 248, 210), Color.rgb(255, 140, 66), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sunX, sunY, width * 0.28f, paint)

        // Fine geometric grid & detail lines for testing Loupe magnification
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        paint.color = Color.argb(90, 255, 255, 255)
        for (r in 20..180 step 16) {
            canvas.drawCircle(sunX, sunY, r.toFloat(), paint)
        }

        // Foreground mountain silhouettes
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(28, 22, 46)
        val path = android.graphics.Path().apply {
            moveTo(0f, height.toFloat())
            lineTo(0f, height * 0.68f)
            lineTo(width * 0.28f, height * 0.54f)
            lineTo(width * 0.55f, height * 0.66f)
            lineTo(width * 0.82f, height * 0.50f)
            lineTo(width.toFloat(), height * 0.62f)
            lineTo(width.toFloat(), height.toFloat())
            close()
        }
        canvas.drawPath(path, paint)

        // Crisp test typography
        paint.color = Color.WHITE
        paint.textSize = 34f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("LOUPE STUDIO • AGSL + AI", sunX, height * 0.86f, paint)
        paint.textSize = 20f
        paint.color = Color.rgb(210, 220, 240)
        canvas.drawText("Drag with Loupe ON or tap AI Upscale 4x", sunX, height * 0.91f, paint)

        return bmp
    }
}
