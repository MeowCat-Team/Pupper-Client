# Pupper Client AI Agent Guide

## Architecture Overview
- **Mod Structure**: Architectury API client mod with one `common` implementation and thin `fabric` / `neoforge` modules; singleton `PupperClient` manages specialized managers (ModManager, EventBus, etc.)
- **Event System**: Custom `EventBus` using reflection for `@EventListener` methods and `EventListener<T>` fields
- **Mods**: Categorized features (HUD, player, render, misc) in `management/mod/impl/`
- **Rendering**: Blaze3D UI and shaders, with Skia generating CPU assets; no raw GL/Vulkan or Skia GPU contexts
- **Integration**: Shared `ProtocolAccess`; Fabric delegates to ViaFabricPlus, NeoForge reports native Minecraft protocol. WebSocket supplies real-time features.

## Key Workflows
- **Build**: `./gradlew build` (Architectury Loom no-remap, Java 25, Minecraft 26.2); stages the final Fabric and NeoForge JARs in `build/libs/`
- **Run Client**: `./gradlew :fabric:runClient` / `./gradlew :neoforge:runClient` (separate `run/fabric` and `run/neoforge` directories); root `runClient` is a Fabric compatibility alias
- **First Launch**: Creates `pupper.ok` config file and shows terms screen
- **Mod Initialization**: `ModManager.init()` registers all mods and settings

## Project Conventions
- **Package**: `cn.pupperclient` with subpackages by feature (animation, event, gui, management, mixin, shader, skia, ui, utils)
- **Managers**: Singleton pattern for core systems (e.g., `EventBus.getInstance()`)
- **Events**: Extend `Event` class, post via `EventBus.getInstance().post(event)`
- **Mixins**: Located in `mixin/mixins/` with accessors in `mixin/interfaces/`
- **Dependencies**: Managed via `gradle/libs.versions.toml` version catalog
- **Shared Sources**: All features in `common/src/main/java`; do not copy implementations between loader modules
- **Resources**: Assets in `common/src/main/resources/assets/pupper/`; platform descriptors are in their respective modules
- **Permissions**: `common/src/main/resources/pupper.classtweaker` is the source of truth; Loom converts it to NeoForge Access Transformers
- **Verification**: Common checks run under `:common:check`; `scripts/verify_multiloader.py` checks both final artifacts for identical shared code/assets, metadata, permissions and native libraries

## Integration Points
- **ViaFabricPlus**: `ProtocolAccess` consumes a `FabricProtocolProvider`; keep ViaFabricPlus imports inside `fabric`. NeoForge has no equivalent native ViaFabricPlus distribution; do not pretend to support translated protocols.
- **WebSocket**: Real-time communication in `management/websocket/`
- **Hypixel**: Server-specific features in `management/hypixel/`
- **Music**: NetEase/Audius catalog, downloads, playlists and audio playback in shared `management/music/`
- **UI**: Custom GUI screens in `gui/`, HUD mods in `management/mod/impl/hud/`

## Examples
- Add mod: Extend `Mod` class, register in `ModManager.initHudMods()`
- Handle event: Annotate method `@EventListener` or use `EventListener<TickEvent>`
- Access Minecraft: Use mixins like `MixinMinecraftClient` for client modifications
- Add setting: Create `Setting` subclass, add via `ModManager.addSetting()`
