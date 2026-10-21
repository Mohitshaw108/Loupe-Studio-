package com.mohit.loupestudio

data class Adjustments(
    val exposure: Float = 0.0f,     // -1.0f to 1.0f
    val contrast: Float = 1.0f,     // 0.5f to 2.0f
    val saturation: Float = 1.0f,   // 0.0f to 2.0f
    val warmth: Float = 0.0f,       // -1.0f to 1.0f
    val sharpness: Float = 0.0f,    // 0.0f to 2.0f
    val vignette: Float = 0.0f      // 0.0f to 1.0f
)

data class StylePreset(
    val name: String,
    val adjustments: Adjustments
)

object StylePresets {
    val all = listOf(
        StylePreset("Original", Adjustments()),
        StylePreset(
            "AI Crisp",
            Adjustments(exposure = 0.08f, contrast = 1.18f, saturation = 1.15f, sharpness = 0.85f)
        ),
        StylePreset(
            "Vivid Pop",
            Adjustments(exposure = 0.12f, contrast = 1.25f, saturation = 1.45f, warmth = 0.10f, sharpness = 0.4f)
        ),
        StylePreset(
            "Cinema",
            Adjustments(exposure = -0.05f, contrast = 1.30f, saturation = 0.90f, warmth = -0.20f, vignette = 0.45f)
        ),
        StylePreset(
            "Golden Hour",
            Adjustments(exposure = 0.10f, contrast = 1.12f, saturation = 1.25f, warmth = 0.45f, vignette = 0.25f)
        ),
        StylePreset(
            "B&W Noir",
            Adjustments(exposure = 0.05f, contrast = 1.55f, saturation = 0.0f, sharpness = 0.70f, vignette = 0.55f)
        )
    )
}
