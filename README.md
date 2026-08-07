# Loupe Studio — Android (Kotlin, GPU-accelerated)

Ported from `loupe-studio-2.html` (a single-file web canvas editor). This
covers the **live tone/color pipeline + style presets** — the part that was
actually CPU-bound in the original — rewritten to run on the GPU.

## What's ported (1:1 math, verified against the JS)
- `applyToneAndColor` → `fragment_tone.glsl` (exposure, contrast, highlights,
  shadows, saturation, temperature, channel mix, split toning, vignette)
- `boxBlur` + unsharp compositing → `fragment_box_blur.glsl` +
  `fragment_sharpen_composite.glsl` (same separable radius-2 box blur, same
  `amt = sharpen/100*1.6` formula)
- `AI_STYLES` (48 presets) → `StylePresets.kt`
- `AI_KEYWORDS` free-text matcher → `StylePresets.matchPrompt()`
- Intensity slider (20%–150%) → `Adjustments.scaledBy()`
- Undo per gesture → `EditorViewModel` undo/redo stacks (simpler than the
  web version's raster snapshots, since adjustments here are non-destructive
  numbers, not baked pixels, until export)

## What's intentionally not ported yet
Crop/rotate, draw/retouch with masking, text/watermark stamping, collage
builder, and the live histogram canvas. None of them are the performance
bottleneck the GPU rewrite targets; each is a separate, independent
subsystem in the original and can be added the same way — a Compose overlay
on top of `PhotoGLSurfaceView`, writing into its own FBO pass. Happy to do
any of these next.

## Why this is faster than the web version
The original does the entire pipeline as a JS loop over a
`Uint8ClampedArray`, once per pixel, on the CPU main thread — and it *knows*
this is slow: `renderLive()` runs on a **downsampled preview canvas** while
you drag a slider, then `commitFullRes()` reruns everything at full
resolution once you release. That two-tier hack is the site's whole live-
preview performance strategy.

On Android, the identical math runs as a GLSL fragment shader:
- One thread per pixel, in parallel, on the GPU, not a JS `for` loop.
- Runs at **full image resolution every frame** — no downsampled/full-res
  split needed; `RENDERMODE_WHEN_DIRTY` only redraws on an actual change.
- The unsharp mask's separable box blur is two GPU passes instead of two
  full CPU passes over every pixel (horizontal, then vertical).
- Sliders bind straight to shader uniforms (`glUniform1f`), so a drag just
  changes what the next GPU frame reads — no array reallocation or `putImageData`.

## Architecture
```
MainActivity (image picker, MediaStore export)
  └─ EditorScreen (Compose)
       ├─ PhotoGLSurfaceView (AndroidView) → PhotoRenderer (GLSurfaceView.Renderer)
       │     Pass 1: tone/color  → FBO
       │     Pass 2/3: separable blur (only if sharpen > 0) → FBO
       │     Pass 4: sharpen composite → FBO
       │     Blit: letterboxed draw to screen
       │     Export: glReadPixels on the tone/final FBO → Bitmap
       └─ EditorViewModel (StateFlow<Adjustments>, undo/redo, style/prompt logic)
```

## Building
Standard Android Studio project layout (Gradle Kotlin DSL). Needs a root
`settings.gradle.kts` / `build.gradle.kts` and `gradle/libs.versions.toml`
or equivalent plugin versions wired up — not included here since those are
project-wide, not specific to this feature.

Minimum practical requirement: **minSdk 26** (chosen for MediaStore/
scoped-storage ergonomics on export, not for GL — GLES 2.0 + FBOs work fine
back to API 11).
