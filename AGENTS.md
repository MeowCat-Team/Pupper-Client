# Pupper Client AI Agent Guide

## Architecture Overview
- **Mod Structure**: Fabric mod with singleton `PupperClient` managing specialized managers (ModManager, EventBus, etc.)
- **Event System**: Custom `EventBus` using reflection for `@EventListener` methods and `EventListener<T>` fields
- **Mods**: Categorized features (HUD, player, render, misc) in `management/mod/impl/`
- **Rendering**: Skia-based UI in `skia/` package, custom shaders in `shader/`
- **Integration**: ViaFabricPlus for version switching, WebSocket for real-time features

## Key Workflows
- **Build**: `./gradlew build` (Loom plugin, Java 25, Minecraft 26.2)
- **Run Client**: `./gradlew runClient` (outputs to `run/` directory)
- **First Launch**: Creates `pupper.ok` config file and shows terms screen
- **Mod Initialization**: `ModManager.init()` registers all mods and settings

## Project Conventions
- **Package**: `cn.pupperclient` with subpackages by feature (animation, event, gui, management, mixin, shader, skia, ui, utils)
- **Managers**: Singleton pattern for core systems (e.g., `EventBus.getInstance()`)
- **Events**: Extend `Event` class, post via `EventBus.getInstance().post(event)`
- **Mixins**: Located in `mixin/mixins/` with accessors in `mixin/interfaces/`
- **Dependencies**: Managed via `gradle/libs.versions.toml` version catalog
- **Resources**: Assets in `src/main/resources/assets/pupper/`, configs processed by Gradle

## Integration Points
- **ViaFabricPlus**: Version protocol management via `PupperClient.getProtocolVersion()`
- **WebSocket**: Real-time communication in `management/websocket/`
- **Hypixel**: Server-specific features in `management/hypixel/`
- **Music**: MP3 playback with JLayer in `management/music/`
- **UI**: Custom GUI screens in `gui/`, HUD mods in `management/mod/impl/hud/`

## Examples
- Add mod: Extend `Mod` class, register in `ModManager.initHudMods()`
- Handle event: Annotate method `@EventListener` or use `EventListener<TickEvent>`
- Access Minecraft: Use mixins like `MixinMinecraftClient` for client modifications
- Add setting: Create `Setting` subclass, add via `ModManager.addSetting()`

