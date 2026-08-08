package com.loupestudio.editor.gl

/**
 * Every shader here is a direct port of the per-pixel math in the web
 * build's `applyToneAndColor` / `applyUnsharp` / `boxBlur` (loupe-studio-2.html).
 * The math is unchanged; what moves is *where* it runs — once per pixel,
 * per frame, on the GPU, instead of a JS loop over a Uint8ClampedArray on
 * the main thread (which is why the original needed a downsampled "live"
 * preview and a separate full-res "commit" pass on pointerup).
 */
object Shaders {

    const val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """

    /** Pass 1: exposure, temperature, contrast, highlights/shadows, saturation,
     *  channel mix, split toning, vignette. One texture sample in, one out. */
    const val FRAGMENT_TONE = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform vec2 uResolution;

        uniform float uExposure;      // -100..100
        uniform float uContrast;      // -100..100
        uniform float uHighlights;    // -100..100
        uniform float uShadows;       // -100..100
        uniform float uSaturation;    // -100..100
        uniform float uTemperature;   // -100..100
        uniform float uVignette;      // -100..100

        uniform vec3 uSplitShadow;    // normalized 0..1 rgb
        uniform vec3 uSplitHigh;      // normalized 0..1 rgb
        uniform float uSplitAmount;   // 0..100
        uniform float uMixR;          // -100..100
        uniform float uMixG;
        uniform float uMixB;

        void main() {
            vec3 c = texture2D(uTexture, vTexCoord).rgb;

            float exposure = uExposure / 100.0 * (80.0 / 255.0);
            float temp = uTemperature / 100.0 * (40.0 / 255.0);
            c.r += exposure; c.g += exposure; c.b += exposure;
            c.r += temp; c.b -= temp;

            float contrastAmt = uContrast * 2.55;
            float contrastFactor = (259.0 * (contrastAmt + 255.0)) / (255.0 * (259.0 - contrastAmt));
            c = contrastFactor * (c - 0.5) + 0.5;

            float lum = dot(c, vec3(0.299, 0.587, 0.114));
            float highlights = uHighlights / 100.0;
            if (highlights != 0.0) {
                float hiMask = max(0.0, (lum - 0.5) / 0.498);
                float f = 1.0 - highlights * hiMask * 0.6;
                c *= f;
            }
            float shadows = uShadows / 100.0;
            if (shadows != 0.0) {
                float shMask = max(0.0, (0.5 - lum) / 0.5);
                float add = shadows * shMask * (60.0 / 255.0);
                c += add;
            }

            float gray = dot(c, vec3(0.299, 0.587, 0.114));
            float satFactor = 1.0 + uSaturation / 100.0;
            c = gray + (c - gray) * satFactor;

            c.r += (uMixR / 100.0) * (60.0 / 255.0);
            c.g += (uMixG / 100.0) * (60.0 / 255.0);
            c.b += (uMixB / 100.0) * (60.0 / 255.0);

            float splitAmt = uSplitAmount / 100.0;
            if (splitAmt > 0.0) {
                float lum2 = dot(c, vec3(0.299, 0.587, 0.114));
                float wShadow = (1.0 - lum2) * splitAmt;
                float wHigh = lum2 * splitAmt;
                c += (uSplitShadow - 0.5) * wShadow + (uSplitHigh - 0.5) * wHigh;
            }

            float vig = uVignette / 100.0;
            if (vig != 0.0) {
                vec2 px = vTexCoord * uResolution;
                vec2 center = uResolution * 0.5;
                float maxDist = length(center);
                float dist = length(px - center) / maxDist;
                float v = 1.0 - vig * dist * dist * 1.2;
                c *= v;
            }

            gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
        }
    """

    /** Passes 2 & 3: separable 5-tap box blur (radius 2), same as the JS boxBlur(data,w,h,2). */
    const val FRAGMENT_BOX_BLUR = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform vec2 uTexelStep; // (1/w, 0) for horizontal pass, (0, 1/h) for vertical pass

        void main() {
            vec4 sum = vec4(0.0);
            for (int k = -2; k <= 2; k++) {
                sum += texture2D(uTexture, vTexCoord + uTexelStep * float(k));
            }
            gl_FragColor = sum / 5.0;
        }
    """

    /** Pass 4: unsharp mask composite — original + (original - blurred) * amount. */
    const val FRAGMENT_SHARPEN_COMPOSITE = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uSharp;   // toned image (pass 1 output)
        uniform sampler2D uBlurred; // blurred version of the same
        uniform float uAmount;      // sharpen/100 * 1.6

        void main() {
            vec3 sharp = texture2D(uSharp, vTexCoord).rgb;
            vec3 blurred = texture2D(uBlurred, vTexCoord).rgb;
            vec3 out_ = sharp + (sharp - blurred) * uAmount;
            gl_FragColor = vec4(clamp(out_, 0.0, 1.0), 1.0);
        }
    """

    /** Plain textured quad — used to blit an FBO to the visible surface. */
    const val FRAGMENT_COPY = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """
}
