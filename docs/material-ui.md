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

The theme covers navigation, home, mods, settings, profiles, cosmetics, the standalone music player, resource-pack conversion, HUD editing and shared controls. HUD dimensions stay in Minecraft GUI units and use their existing token scale.

## Music player

The standalone player uses a cover column, a Library/Search/Liked view and a bottom transport bar. It replaces the mod-menu music page; the Home music shortcut opens the standalone screen, as does the configurable Music Player keybind (M by default). Search is submitted with Enter or Search and supports pagination; local lists filter immediately. Download and heart actions use 48-unit hit targets, Material state layers and the current dynamic palette. Both English and Chinese labels, statuses, errors and tooltips come from language resources. Esc closes the standalone screen.

The player and `.music` commands share the NetEase API origin used by `.login`. Downloading from search preserves the title and artists if song details are unavailable, checks the returned MP3/FLAC format, publishes only complete files, and refreshes the library. Provider metadata lives in `music/.pupper-music.json` and takes precedence over embedded tags. Opening or refreshing the music view repairs old `music_ID` files when the API is available; active files keep their filename while their displayed title is repaired.

Guest likes are saved on the device. Logged-in likes use the account's `/like` and `/likelist` endpoints and remain isolated from guest favorites and other accounts. Synchronization refreshes the account's liked list. Public search and download remain available without a login when the provider permits the requested song and quality.

**Lyrics:** the header button switches the right pane between the library and lyrics. LRC lines follow the decoded audio clock, preserve blank/instrumental intervals and display provider translations when timestamps match. Scroll browses other lines, returning to playback after five seconds. Untimed lyrics remain a browsable text list. A shared asynchronous cache serves the player and Music Info HUD: sibling `.lrc` and embedded MP3/FLAC lyrics take precedence, followed by cached NetEase `/lyric` results and the network. Network failures and songs without lyrics have separate translated states. Refresh retries failed requests. Newly configured non-simple Music Info HUDs show lyrics by default; existing saved preferences still apply.

**Windows media controls:** Windows 10/11 SMTC uses Java 25's final FFM API and the actual GLFW HWND. No helper executable, JNI DLL or new JNA dependency is required. A single platform/MTA daemon publishes title, artist, album, cached cover, playback state and timeline every 250 ms. System Play, Pause, Stop, Previous and Next events dispatch to the game thread. Seeking is not advertised because these decoders do not yet support arbitrary seek. Closing the client disables SMTC, unregisters callbacks and releases COM references. Other hosts skip SMTC; native-access denial/API failure leaves ordinary playback available and logs one diagnostic.

The Gradle client run enables `--enable-native-access=ALL-UNNAMED`. Add the same JVM option in a launcher to explicitly enable FFM native access; this uses the finalized API and needs no preview flag.

## Verification

```powershell
.\gradlew.bat build previewHudTheme previewMaterialTheme --offline --console=plain
.\gradlew.bat verifyGlassGpu --offline --console=plain
.\gradlew.bat benchmarkGlassGpu --offline --console=plain
.\gradlew.bat verifyMusicService previewMusicPlayer --offline --console=plain
.\gradlew.bat verifyWindowsSmtc --offline --console=plain
```

`previewMaterialTheme` uses the same control drawing functions as the actual settings components, at their real sizes and states. It also verifies panel alpha and canvas state balance. Output: `build/reports/ui/material-preview.png`. `previewHudTheme` produces `build/reports/hud/theme-preview.png`. These are component specimens, not screenshots of a running client or a proposed settings-page layout.

`verifyMusicService` uses a local HTTP server and temporary files to cover both search schemas, metadata outages, legacy name repair, MP3/FLAC extensions, partial-file cleanup and account-specific favorite persistence. Generated two-second silence fixtures exercise the real MP3/FLAC decoders, pause/resume, switching, mute and shutdown against an instrumented audio line without opening audio hardware. It runs with `check` and does not download public audio or modify a live account. `previewMusicPlayer` renders the production music painters with fixture tracks in light/English and dark/Chinese layouts under `build/reports/music/`; it verifies balanced canvas state, and is an offscreen specimen rather than an in-game interaction test.

Music checks also cover LRC precision, multiple timestamps, translation matching, async request deduplication, stale-request isolation and local/offline lyric persistence. `verifyWindowsSmtc` creates a hidden top-level test HWND on Windows, checks actual WinRT metadata and timeline round trips, opens a cached image through the thumbnail stream, and exercises native-to-Java button upcalls. It does not play audio, and skips on other hosts. These checks supplement in-game/media-panel interaction testing.

`verifyGlassGpu` uses a hidden GLFW OpenGL context to exercise the actual Minecraft/Skia framebuffer boundary with known RGB values, including a framebuffer with zero alpha. It checks that glass does not raise brightness, that pixels outside the panel are untouched, and that GL state is restored. This requires a working GPU driver and supplements, rather than replaces, in-game verification.

Check the running client for world compositing, small-window pointer alignment, list scrolling, focus/selection, music controls and blur on/off at multiple GUI scales.

`benchmarkGlassGpu` compares content only, tint only, the old per-panel blur and the new shared-scene glass at 2560×1540 with the bundled font. It reports CPU submission and GPU elapsed time after warmup; these are isolated UI timings, not Minecraft FPS. See `render-performance.md` for scope and item rendering changes.
