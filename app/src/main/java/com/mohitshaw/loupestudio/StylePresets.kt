package com.mohitshaw.loupestudio

/** One entry in the "Browse Styles" grid. `base` is applied at 100% intensity. */
data class StylePreset(val name: String, val category: String, val base: Adjustments)

/** One rule for the free-text "Describe it yourself" box. Multiple rules can match and stack. */
data class KeywordRule(val keywords: List<String>, val add: Adjustments)

object StylePresets {

    private fun a(
        exposure: Float = 0f, contrast: Float = 0f, highlights: Float = 0f, shadows: Float = 0f,
        saturation: Float = 0f, temperature: Float = 0f, sharpen: Float = 0f, vignette: Float = 0f
    ) = Adjustments(exposure, contrast, highlights, shadows, saturation, temperature, sharpen, vignette)

    val styles: List<StylePreset> = listOf(
        // Cinematic
        StylePreset("Teal & Orange", "Cinematic", a(contrast = 22f, saturation = 10f, temperature = -6f, vignette = 16f, sharpen = 10f)),
        StylePreset("Blockbuster Blue", "Cinematic", a(contrast = 18f, temperature = -18f, vignette = 14f)),
        StylePreset("Film Noir", "Cinematic", a(saturation = -100f, contrast = 32f, vignette = 28f)),
        StylePreset("Neo-Western", "Cinematic", a(contrast = 14f, saturation = -10f, temperature = 14f, vignette = 12f)),
        StylePreset("Sci-Fi Chrome", "Cinematic", a(contrast = 20f, saturation = -6f, temperature = -20f, sharpen = 16f)),
        StylePreset("War Drama", "Cinematic", a(saturation = -40f, contrast = 20f, shadows = -10f, vignette = 20f)),
        // Film stocks
        StylePreset("Kodak Portra", "Film", a(exposure = 4f, contrast = 6f, saturation = 8f, temperature = 12f)),
        StylePreset("Fuji Velvia", "Film", a(contrast = 22f, saturation = 34f, vignette = 12f)),
        StylePreset("Kodachrome", "Film", a(contrast = 16f, saturation = 20f, temperature = 6f)),
        StylePreset("Ilford HP5 Mono", "Film", a(saturation = -100f, contrast = 18f, sharpen = 14f)),
        StylePreset("Polaroid 600", "Film", a(contrast = -10f, saturation = -14f, temperature = 10f, vignette = 18f)),
        StylePreset("Cinestill 800T", "Film", a(temperature = -16f, contrast = 10f, vignette = 14f)),
        StylePreset("Lomography", "Film", a(saturation = 26f, contrast = 16f, vignette = 24f, temperature = 8f)),
        // Nature
        StylePreset("Lush Forest", "Nature", a(saturation = 18f, contrast = 10f, temperature = -4f)),
        StylePreset("Desert Gold", "Nature", a(temperature = 24f, contrast = 12f, saturation = 10f)),
        StylePreset("Ocean Breeze", "Nature", a(temperature = -14f, saturation = 14f, contrast = 6f)),
        StylePreset("Mountain Mist", "Nature", a(saturation = -12f, contrast = -6f, highlights = -8f)),
        StylePreset("Autumn Glow", "Nature", a(temperature = 26f, saturation = 16f, contrast = 8f)),
        StylePreset("Spring Bloom", "Nature", a(saturation = 20f, exposure = 8f, temperature = 4f)),
        // Urban
        StylePreset("Midnight City", "Urban", a(contrast = 24f, saturation = -8f, temperature = -14f, vignette = 20f)),
        StylePreset("Rainy Streets", "Urban", a(saturation = -18f, contrast = 8f, highlights = -14f)),
        StylePreset("Neon Nights", "Urban", a(contrast = 24f, saturation = 26f, temperature = -22f, vignette = 18f)),
        StylePreset("Concrete Jungle", "Urban", a(saturation = -22f, contrast = 16f)),
        // Portrait
        StylePreset("Soft Portrait", "Portrait", a(exposure = 8f, contrast = -6f, saturation = 6f, shadows = 10f)),
        StylePreset("Studio Glam", "Portrait", a(contrast = 14f, saturation = 10f, sharpen = 14f)),
        StylePreset("Editorial Matte", "Portrait", a(contrast = -8f, saturation = -10f, shadows = 14f)),
        StylePreset("Natural Skin", "Portrait", a(exposure = 6f, contrast = 4f, saturation = 4f, shadows = 6f)),
        // Mood
        StylePreset("Melancholy", "Mood", a(saturation = -30f, contrast = 10f, shadows = -10f, vignette = 20f)),
        StylePreset("Euphoria", "Mood", a(exposure = 10f, saturation = 22f, contrast = 10f)),
        StylePreset("Nostalgia", "Mood", a(saturation = -16f, temperature = 12f, contrast = -6f, vignette = 14f)),
        StylePreset("Tension", "Mood", a(contrast = 30f, saturation = -20f, vignette = 26f)),
        StylePreset("Serenity", "Mood", a(exposure = 6f, saturation = -6f, contrast = -6f)),
        // Black & White
        StylePreset("Classic Mono", "B&W", a(saturation = -100f, contrast = 14f)),
        StylePreset("High-Contrast Mono", "B&W", a(saturation = -100f, contrast = 38f, sharpen = 16f)),
        StylePreset("Soft Mono", "B&W", a(saturation = -100f, contrast = -8f, highlights = -10f)),
        // Retro decades
        StylePreset("70s Faded", "Retro", a(saturation = -14f, temperature = 14f, contrast = -10f, vignette = 16f)),
        StylePreset("80s VHS", "Retro", a(saturation = 20f, contrast = 8f, temperature = -10f)),
        StylePreset("90s Disposable", "Retro", a(contrast = 6f, saturation = 12f, temperature = 8f, vignette = 10f)),
        StylePreset("Y2K Flash", "Retro", a(exposure = 16f, contrast = 12f, saturation = 14f)),
        // Light / time of day
        StylePreset("Golden Hour", "Light", a(temperature = 26f, exposure = 8f, vignette = 10f)),
        StylePreset("Blue Hour", "Light", a(temperature = -24f, contrast = 10f, shadows = 8f)),
        StylePreset("Overcast Soft", "Light", a(contrast = -10f, saturation = -6f, highlights = -6f)),
        StylePreset("Harsh Noon", "Light", a(contrast = 22f, highlights = -16f, saturation = 6f)),
        // Creative
        StylePreset("Cyberpunk Neon", "Creative", a(contrast = 24f, saturation = 22f, temperature = -24f, vignette = 18f)),
        StylePreset("Dreamcore", "Creative", a(exposure = 12f, saturation = -10f, contrast = -8f)),
        StylePreset("Infrared", "Creative", a(temperature = -40f, saturation = 24f, contrast = 14f)),
        StylePreset("Sepia Antique", "Creative", a(saturation = -60f, temperature = 30f, contrast = 8f)),
        StylePreset("Solar Flare", "Creative", a(exposure = 14f, contrast = 16f, temperature = 20f, highlights = -10f))
    )

    val categories: List<String> = listOf("All") + styles.map { it.category }.distinct()

    val keywordRules: List<KeywordRule> = listOf(
        KeywordRule(listOf("cyberpunk", "neon"), a(contrast = 22f, temperature = -24f, vignette = 14f, saturation = 16f)),
        KeywordRule(listOf("vintage", "nostalgic", "old film", "film"), a(contrast = -8f, saturation = -16f, temperature = 8f)),
        KeywordRule(listOf("noir", "moody", "dark", "shadow"), a(saturation = -70f, contrast = 26f, vignette = 24f)),
        KeywordRule(listOf("bright", "clean", "airy", "crisp"), a(exposure = 14f, contrast = 6f, highlights = -10f, saturation = 6f)),
        KeywordRule(listOf("golden hour", "sunset", "warm"), a(temperature = 28f, exposure = 6f, vignette = 10f)),
        KeywordRule(listOf("cold", "arctic", "winter", "blue"), a(temperature = -30f, contrast = 8f, saturation = -4f)),
        KeywordRule(listOf("dreamy", "pastel", "soft"), a(exposure = 8f, saturation = -8f)),
        KeywordRule(listOf("black and white", "monochrome", "mono", "bw"), a(saturation = -100f)),
        KeywordRule(listOf("cinematic"), a(contrast = 16f, vignette = 10f)),
        KeywordRule(listOf("portrait", "skin", "glam"), a(contrast = 10f, saturation = 8f, shadows = 8f)),
        KeywordRule(listOf("landscape", "nature", "forest"), a(saturation = 14f, contrast = 8f)),
        KeywordRule(listOf("street", "urban", "city"), a(contrast = 14f, saturation = -6f, vignette = 12f)),
        KeywordRule(listOf("ocean", "beach", "sea"), a(temperature = -12f, saturation = 12f)),
        KeywordRule(listOf("desert", "sand"), a(temperature = 22f, contrast = 10f, saturation = 8f)),
        KeywordRule(listOf("rain", "moody weather", "storm"), a(saturation = -16f, contrast = 10f, highlights = -12f)),
        KeywordRule(listOf("80s", "retro"), a(saturation = 18f, contrast = 8f, temperature = -8f)),
        KeywordRule(listOf("70s"), a(saturation = -12f, temperature = 14f, contrast = -8f, vignette = 14f)),
        KeywordRule(listOf("90s"), a(contrast = 6f, saturation = 10f, temperature = 6f, vignette = 8f)),
        KeywordRule(listOf("high contrast", "dramatic"), a(contrast = 32f, shadows = -14f, vignette = 18f)),
        KeywordRule(listOf("low contrast", "flat"), a(contrast = -16f)),
        KeywordRule(listOf("sharp", "crisp detail", "hd"), a(sharpen = 40f)),
        KeywordRule(listOf("glow", "ethereal", "angelic"), a(exposure = 12f, highlights = -16f, saturation = -6f)),
        KeywordRule(listOf("sepia", "antique"), a(saturation = -60f, temperature = 30f, contrast = 8f)),
        KeywordRule(listOf("infrared"), a(temperature = -40f, saturation = 24f, contrast = 14f)),
        KeywordRule(listOf("pastel sky"), a(exposure = 10f, saturation = -10f, temperature = 6f)),
        KeywordRule(listOf("gritty", "rugged"), a(contrast = 24f, saturation = -20f, sharpen = 20f)),
        KeywordRule(listOf("vibrant", "punchy", "pop"), a(saturation = 34f, contrast = 14f, sharpen = 10f)),
        KeywordRule(listOf("faded", "washed out"), a(contrast = -14f, saturation = -20f, shadows = 10f))
    )

    private val genericEnhance = a(exposure = 6f, contrast = 10f, saturation = 6f, vignette = 8f, temperature = 4f, sharpen = 10f)

    /** Matches free text against [keywordRules] and sums all hits, same as the web build. */
    fun matchPrompt(promptRaw: String): Pair<Adjustments, Int> {
        val prompt = promptRaw.lowercase().trim()
        if (prompt.isEmpty()) return Adjustments.NEUTRAL to 0
        var acc = Adjustments.NEUTRAL
        var matched = 0
        for (rule in keywordRules) {
            if (rule.keywords.any { prompt.contains(it) }) {
                matched++
                acc += rule.add
            }
        }
        return if (matched == 0) genericEnhance to 0 else acc to matched
    }
}
