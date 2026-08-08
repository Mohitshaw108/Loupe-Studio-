package com.loupestudio.editor

import androidx.compose.ui.graphics.Color

/**
 * Mirrors the web build's `A` (tone) + `C` (color) state objects.
 * Sliders stay on the same human-facing scale as the original
 * (roughly -100..100), and get remapped to shader units inside
 * PhotoRenderer / the GLSL uniforms — see fragment_tone.glsl.
 */
data class Adjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val sharpen: Float = 0f,
    val vignette: Float = 0f,
    // 0f..1f normalized RGB, defaults match the web build's #27384B / #E08A3C
    val splitShadow: Triple<Float, Float, Float> = Triple(0.153f, 0.220f, 0.294f),
    val splitHigh: Triple<Float, Float, Float> = Triple(0.878f, 0.541f, 0.235f),
    val splitAmount: Float = 0f,
    val mixR: Float = 0f,
    val mixG: Float = 0f,
    val mixB: Float = 0f
) {
    /** Used by the "Browse Styles" intensity slider (20%-150% in the original). */
    fun scaledBy(intensity: Float): Adjustments = copy(
        exposure = exposure * intensity,
        contrast = contrast * intensity,
        highlights = highlights * intensity,
        shadows = shadows * intensity,
        saturation = saturation * intensity,
        temperature = temperature * intensity,
        sharpen = sharpen * intensity,
        vignette = vignette * intensity
    )

    operator fun plus(o: Adjustments): Adjustments = copy(
        exposure = exposure + o.exposure,
        contrast = contrast + o.contrast,
        highlights = highlights + o.highlights,
        shadows = shadows + o.shadows,
        saturation = saturation + o.saturation,
        temperature = temperature + o.temperature,
        sharpen = sharpen + o.sharpen,
        vignette = vignette + o.vignette
    )

    val hasAnyEdit: Boolean
        get() = exposure != 0f || contrast != 0f || highlights != 0f || shadows != 0f ||
            saturation != 0f || temperature != 0f || sharpen != 0f || vignette != 0f ||
            splitAmount != 0f || mixR != 0f || mixG != 0f || mixB != 0f

    companion object {
        val NEUTRAL = Adjustments()

        fun fromComposeColor(c: Color): Triple<Float, Float, Float> = Triple(c.red, c.green, c.blue)
    }
}
