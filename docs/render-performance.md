# Rendering performance

## Dropped items

The original optimizer only limited decorative copies within a stack during submission. It did not reduce model extraction for a single dropped block or the terrain/light queries used to build each shadow.

The item optimization setting now also reuses static ground block models, including their bounding boxes. The cache is limited to 256 model identifiers, isolated by world, validated against the current baked model object and cleared on resource reload. Only exact vanilla cuboid models without tint, foil, animation or non-default stack components qualify. Conditional, custom and dynamic models use vanilla extraction. Each entity still keeps its own position, animation, light, stack count and seed.

The separate item-shadow setting skips shadow extraction beyond 8 blocks when visible item count reaches the threshold (64 by default), or beyond 4 blocks at twice the threshold. The preceding extracted frame supplies density, so activation is delayed by one frame and does not rescan all loaded entities. World changes and disabling the setting reset the budget. Item models remain visible, and other entity shadows retain vanilla behavior. Client entity ticking and server TPS are unchanged.

`verifyItemRendering` is included in `check`. It checks density/distance boundaries, reset and thread isolation, plus the 26.2 bytecode contracts used by the new mixins and static-model cache. These checks do not apply Fabric mixins in a live game or measure item FPS.

## Skia and glass

The scene is captured once per frame that needs glass. With both glass switches off, the renderer does not copy the framebuffer. Each visible rounded panel samples that shared scene with a normalized nine-sample stencil and small edge refraction. No fullscreen backdrop filter is applied. Text shadows use faint ordinary glyph draws; animated opacity layers use local bounds; cover-art blur filters have a bounded cache.

`verifyGlassGpu` exercises real hidden OpenGL framebuffers, including valid scene RGB with alpha zero, scene-only sampling, clipping/transforms, alpha layers, disabled effects and GL state restoration. The old backdrop pass doubles RGB for an alpha-zero scene; the regression explicitly reproduces this before checking the replacement.

`benchmarkGlassGpu` measures four modes at 2560×1540 using the bundled font: content, tint, legacy per-panel blur, and the new glass. Each mode renders 12 representative HUD panels with or without a 600×400 menu, warms up 30 frames, then measures 120 frames. CPU figures exclude query-result waiting; GPU figures use `GL_TIME_ELAPSED`. This is an isolated renderer benchmark, not a game FPS prediction. World rendering, entity load, resource packs, display scaling and other mods still require a same-scene in-game comparison.

Local run on 2026-09-27, Intel Arc Graphics, driver 32.0.101.8331 (median milliseconds; separate CPU/GPU measurements must not be added):

| Sample | Legacy CPU | New CPU | Legacy GPU | New GPU |
| --- | ---: | ---: | ---: | ---: |
| HUD | 2.686 | 0.796 | 0.699 | 0.267 |
| HUD and menu | 3.516 | 1.204 | 0.833 | 0.305 |

The comparison isolates the panel algorithms. It excludes the previous fullscreen menu blur and per-line text blur, and does not claim an end-to-end FPS gain from these figures.
