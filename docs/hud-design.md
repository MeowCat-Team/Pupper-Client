# HUD design system

The HUD uses Material 3 Expressive principles adapted to Minecraft GUI units. It is a custom Skia implementation, not an Android component port or a claim of official certification.

## Rendering contract

- `HUDTokens`: shared spacing, shapes, text sizes and cached native fonts. Use these instead of allocating a font for each label each frame. Fonts have the same lifetime as the renderer.
- `HUDColors`: translucent surface roles, opaque paired foregrounds and contrast correction where attainable. At low opacity, retain the Material role's hue when black/white adjustment cannot meet the target on both bright and dark backdrops. Never use the raw HCT seed as a component background.
- `HUDText`: original filled glyphs and font weight, with a faint offset black shadow below text on translucent surfaces. The shadow reuses normal glyph rendering, with no per-line image blur or offscreen filter. It never strokes glyphs. Cached native paint is released with the renderer.
- `HUDDesign`: the single surface renderer and palette cache. A theme change invalidates the cache through the new `ColorPalette` snapshot.
- `HUDMotion`: elapsed-time effects and a critically damped spatial spring. Changing a target preserves velocity. `reducedMotion()` snaps to the final state and disables music artwork movement.
- `SimpleHUDMod`: compact metric, accent-container icon badge, optional subordinate unit, and bounded text.
- `SimpleListHUDMod`: fixed line boxes and a title role for scoreboard-style content.
- `AnimatedListHUDMod`: stable entry identity, shared layout, bounded text and interruptible entry/exit movement for modules and potion effects.

Use `colors().surface()` with `text()` / `secondaryText()`, or `accentContainer()` with `onAccentContainer()`. `accent()` indicates active state/progress. `danger()` indicates low health or an expiring effect; retain a number, icon or label so color is not the only cue. Progress tracks use `track()`.

Background opacity defaults to 45% and can be adjusted from 0% to 100% in HUD settings. The default is shared with GUI surfaces through `MaterialTokens`. Only the base and raised surfaces become translucent; text, badges and progress remain opaque. Foreground roles are corrected against dark and bright sRGB composites where the target is attainable, otherwise retaining the Material role's color. Below 70%, `HUDText` adds a faint black shadow (at most 14% alpha, no image blur, 0.6-unit downward offset); it fades out as the surface becomes more opaque. Solid accent badges do not need a shadow. This preserves the original font shapes and does not guarantee a fixed contrast ratio over arbitrary world pixels at low opacity. Rounded backdrop blur is enabled by default and can be disabled independently. Text is not faded during list transitions; the row is revealed by clipping and movement instead.

## Overlay priority

Skia HUD rendering currently happens at the end of the frame. While the vanilla player list is visible, HUD event dispatch is suppressed so Skia cannot cover the Tab list. The decision reads `PlayerTabOverlay.visible` through a registered accessor, not the physical Tab key. HUD rendering also respects F1; the HUD editor explicitly keeps its preview visible. Custom Skia screens still render independently. This is visibility arbitration, not a change to Minecraft's GPU render order.

## Components and compatibility

- Saved `design.simple`, `design.classic` and `design.materialyou` keys still load. They share the same color and typography contract. Material Glass is the new-install default. Existing style and opacity selections remain selected.
- Module/potion lists keep their filter, alignment and background keys. Background enabled groups rows on one surface; disabled gives rows individual readable surfaces. Alignment controls entry direction; list ordering stays stable as values change.
- Target HUD retains its health text/bar option and always shows a numeric value. Its background toggle is retired. Zero health stays zero. The attack callback is registered once, not once per enable.
- Music keeps simple/normal/cover layouts, lyrics and the artwork-motion toggle. Cover uses a larger contained image. Unbounded particles, glowing text and moving full-card artwork are retired. Removed setting keys are safely ignored by the existing config loader.
- Dynamic Island becomes a status card with at most three current notifications, truncation and paired raised surfaces. Repeated toggles replace a notification for the same module. Auto-again feedback remains supported.
- The custom Boss HUD uses Skia, the shared theme and the saved position/scale, retaining progress and segment counts. The explicit vanilla-position option still uses Minecraft's original presentation; custom is the new-install default.
- The scoreboard uses plain component text with theme colors; server formatting colors are intentionally replaced. Siblings are no longer duplicated and the list follows the usual 15-row limit.
- The Bedwars table measures its row count before drawing and respects the configured maximum. It now participates in HUD scaling.
- HUD and menu glass sample one shared scene snapshot taken before custom HUD content. Their enable settings remain independent; only rounded panels sample the scene. Disabling both effects skips the snapshot entirely.

## Verification

Run with Java 25:

```powershell
.\gradlew.bat build --offline --console=plain
.\gradlew.bat previewHudTheme --offline --console=plain
```

`check` includes `verifyHudDesign`, a standalone verification source set that needs no JUnit dependency. It covers 13,608 seed/theme/surface/opacity combinations, with black, white, grass and sky backdrops (including nested surfaces). At 70–100% opacity it checks composited text contrast >= 4.5:1 and icon/progress contrast >= 3:1. Across 0–100% it checks the configured surface alpha, opaque foreground roles and paired badge contrast. It also checks 30/60/144/240 FPS motion equivalence, interruption and reduced motion. The default 45% translucent panels are not claimed to meet a fixed text contrast ratio over every world pixel.

`previewHudTheme` uses the bundled native Skia renderer and real Chinese/icon fonts. It checks narrow-width, exact-fit and surrogate-pair truncation, plus a glyph raster regression: opaque text exactly matches ordinary filled text, glass text retains filled glyph colors, and pixels outside the original glyph remain within the faint shadow's alpha limit. It writes `build/reports/hud/theme-preview.png`. This is a theme/layout specimen, **not** a live Minecraft screenshot or an integration test of every component.

In-game acceptance remains necessary for GPU compositing, mixin injection and actual world placement: check snow/sky and dark caves, both themes, GUI scale changes, long Chinese/player/music text, multiple bosses/effects, enable-disable transitions, target death, empty lists, and reduced motion. Existing saved positions may need rearranging because cards have more readable spacing.

## References

- [Material 3 Expressive research](https://design.google/library/expressive-material-design-google-research)
- [Material at Google I/O 2026](https://io.google/2026/explore/technical-session-30)
- [Material color roles and typography](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Motion schemes](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)
- [Text contrast guidance](https://developer.android.com/guide/topics/ui/accessibility/apps)
