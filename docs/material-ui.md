# Material Glass UI

Pupper Client's custom GUI and HUD share the current Material dynamic palette, with light/dark themes, rounded surfaces and restrained glass edges. Vanilla and third-party screens retain their own renderers.

## Defaults and settings

- GUI background opacity: **45%**, adjustable in Settings → Mod Menu → Interface background opacity.
- HUD background opacity: **45%**, adjustable independently in Settings → HUD settings.
- Values follow the existing opacity convention: 0% is transparent; 100% is opaque. Text, icons and active controls keep their own readable colors.
- Existing saved settings take precedence over new defaults.
- GUI glass uses the Mod Menu blur switch and intensity. HUD glass uses its separate setting. The scene outside the panels stays untouched.

## Shared rendering

`ui/theme/MaterialTokens` holds the defaults and 28/20/12 GUI corner radii. `MaterialTheme.glassPanel` samples the scene behind a root window, with local softening and a small refraction at the rounded edges. `panel` adds a single tint gradient at the configured opacity, a shadow and a continuous specular rim. `card` draws a lighter nested tonal layer without repeatedly sampling the scene. `surface` applies the GUI opacity to individual surfaces. Text stays separate from the glass effect.

`SimplePupperClientGui` does not apply a fullscreen post-process. `PupperClientGui` scales its fixed layout to fit small windows and applies the same inverse transform to mouse input. `Page` keeps its search field fixed while child pages clip and scroll their lists.

Glass captures the scene once before custom content, then uses a normalized nine-sample stencil only inside visible panels. Disabling GUI and HUD glass skips the snapshot. Entry/exit alpha layers are bounded to their window or HUD, and text shadows no longer create image-blur passes per line. Small nested cards use a simple outline instead of allocating a gradient shader per control.

The old backdrop layer could reuse a scene with valid RGB and zero alpha as translucent content, adding it to the scene a second time. The new glass explicitly treats the scene as opaque. The GPU regression reproduces the old saturation and verifies the replacement preserves constant RGB, including through animated alpha layers.

The theme covers navigation, home, mods, settings, profiles, cosmetics, music, resource-pack conversion, HUD editing and shared controls. HUD dimensions stay in Minecraft GUI units and use their existing token scale.

## Verification

```powershell
.\gradlew.bat build previewHudTheme previewMaterialTheme --offline --console=plain
.\gradlew.bat verifyGlassGpu --offline --console=plain
.\gradlew.bat benchmarkGlassGpu --offline --console=plain
```

`previewMaterialTheme` uses the same control drawing functions as the actual settings components, at their real sizes and states. It also verifies panel alpha and canvas state balance. Output: `build/reports/ui/material-preview.png`. `previewHudTheme` produces `build/reports/hud/theme-preview.png`. These are component specimens, not screenshots of a running client or a proposed settings-page layout.

`verifyGlassGpu` uses a hidden GLFW OpenGL context to exercise the actual Minecraft/Skia framebuffer boundary with known RGB values, including a framebuffer with zero alpha. It checks that glass does not raise brightness, that pixels outside the panel are untouched, and that GL state is restored. This requires a working GPU driver and supplements, rather than replaces, in-game verification.

Check the running client for world compositing, small-window pointer alignment, list scrolling, focus/selection, music controls and blur on/off at multiple GUI scales.

`benchmarkGlassGpu` compares content only, tint only, the old per-panel blur and the new shared-scene glass at 2560×1540 with the bundled font. It reports CPU submission and GPU elapsed time after warmup; these are isolated UI timings, not Minecraft FPS. See `render-performance.md` for scope and item rendering changes.
