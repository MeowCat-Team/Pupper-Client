# Material Glass UI

Pupper's custom GUI and HUD share the current Material dynamic palette, with light/dark themes, rounded surfaces and restrained glass edges. Vanilla and third-party screens retain their own renderers.

## Defaults and settings

- GUI background opacity: **45%**, adjustable in Settings → Mod Menu → Interface background opacity.
- HUD background opacity: **45%**, adjustable independently in Settings → HUD settings.
- Values follow the existing opacity convention: 0% is transparent; 100% is opaque. Text, icons and active controls keep their own readable colors.
- Existing saved settings take precedence over new defaults.
- GUI blur uses the Mod Menu blur switch and intensity. HUD blur uses its separate setting. The HUD editor keeps the world sharp.

## Shared rendering

`ui/theme/MaterialTokens` holds the defaults and 28/20/12 GUI corner radii. `MaterialTheme.panel` draws a single tint gradient at the configured opacity, with a quiet shadow and upper glass rim. `card` draws a lighter nested tonal layer; `surface` applies the GUI opacity to individual surfaces. Avoid applying surface opacity to an entire canvas layer containing text.

`SimpleSoarGui` renders the configurable backdrop blur once before GUI content. `SoarGui` scales its fixed layout to fit small windows and applies the same inverse transform to mouse input. `Page` keeps its search field fixed while child pages clip and scroll their lists.

The theme covers navigation, home, mods, settings, profiles, cosmetics, music, resource-pack conversion, HUD editing and shared controls. HUD dimensions stay in Minecraft GUI units and use their existing token scale.

## Verification

```powershell
.\gradlew.bat build previewHudTheme previewMaterialTheme --offline --console=plain
```

`previewMaterialTheme` renders actual shared surface functions in light/dark offscreen specimens and verifies panel alpha and canvas state balance. Output: `build/reports/ui/material-preview.png`. `previewHudTheme` produces `build/reports/hud/theme-preview.png`. These are design specimens, not screenshots of a running client.

Check the running client for world compositing, small-window pointer alignment, list scrolling, focus/selection, music controls and blur on/off at multiple GUI scales.
