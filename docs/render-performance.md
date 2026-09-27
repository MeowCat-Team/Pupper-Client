# Rendering performance

## Dropped items

The original optimizer only limited decorative copies within a stack during submission. It did not reduce model extraction for a single dropped block or the terrain/light queries used to build each shadow.

The item optimization setting reuses static ground item models, including their bounding boxes. The cache is limited to 256 model identifiers, isolated by world, validated against the current baked model object and cleared on resource reload. Exact vanilla cuboid models without tint qualify, including generated tool models. Their only stack-dependent input is foil selection, cached separately as none, standard or special. Enchantment/name/damage components therefore do not exclude otherwise static geometry; glint and atlas animations remain active. Conditional, custom and world-tinted models use vanilla extraction. Each entity still keeps its own position, animation, light, stack count and seed.

Ground-item main-pass submission acquires a vertex consumer once per consecutive material run. Runs preserve quad order and stop at every render-type change; render types that cannot consolidate geometry keep individual submissions. With Sodium 0.9.2 or newer, supported consumers receive up to 64 quads per native vertex push, using at most 9 KiB of stack scratch space. Packed normals are reused within the same pose. Sodium still owns format conversion and upload, including Iris extended vertices. Unsupported consumers and installations without Sodium retain `putBakedQuad`; outlines and foil passes retain their original paths. This does not remove entities or reduce their geometry.

The separate item-shadow setting skips shadow extraction beyond 8 blocks when visible item count reaches the threshold (64 by default), or beyond 4 blocks at twice the threshold. The preceding extracted frame supplies density, so activation is delayed by one frame and does not rescan all loaded entities. World changes and disabling the setting reset the budget. Item models remain visible, and other entity shadows retain vanilla behavior. Client entity ticking and server TPS are unchanged.

`verifyItemRendering` and `verifyItemVertices` are included in `check`. They check density/distance boundaries, reset and thread isolation, the 26.2 hook/cache/buffer contracts, and 7,872 packed vertices against Sodium's actual encoder. Vertex cases cover rotated, mirrored and nonuniformly scaled poses, per-vertex color/light, emission, normal-cache overflow/reset and batch boundaries. Fifteen Iris conversion cases compare individual quads with batches, including item/entity IDs, tangents and mid-UV. These checks do not measure item FPS.

A separate local Fabric pre-launch smoke check loaded the configured runtime mods and confirmed application of the item submission/cache mixins, Sodium's quad interface and widened pose-normal access. It exited before entering Minecraft; no game window or world was used for that successful check. This verifies hook loading, not in-game rendering or resource-pack behavior.

A 30-second JFR sample of the running development client on 2026-09-27 contained 1,770 render-thread samples. The main item submission path appeared in 893 samples, Sodium quad encoding in 598, vertex-consumer acquisition in 260, and item model bounding-box calculation in 112. These inclusive counts overlap and are not wall-clock percentages. GC pauses totaled about 92 ms. The server thread was largely paused during this capture, so it is evidence for the render bottleneck, not a server-tick benchmark. Raw recordings remain local because they can contain process/environment metadata.

`benchmarkItemVertices` compares the installed Sodium encoder with the batch encoder on 512 items × 64 quads, including native staging copies. It alternates execution order, warms up 15 rounds and measures 31 rounds. Local median CPU time was 3.698 ms versus 2.375 ms (35.8% lower). This excludes GPU rendering, model resolution and consumer acquisition; it is not an end-to-end FPS claim. Test the rebuilt client after restarting, in the same scene with the same resource/shader packs and item optimization enabled.

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
