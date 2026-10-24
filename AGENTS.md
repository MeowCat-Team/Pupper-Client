# Pupper Client AI Agent Guide

You are working in a codebase you did not design. Read before you write.

An Architectury API client mod: one shared `common` implementation plus thin
`fabric` and `neoforge` modules. User-visible behaviour, assets and mixins live
in `common`; loader modules hold only platform glue.

## Absolute

1. Do not commit, push, tag, or open a PR unless asked in this turn.
2. Do not edit these rules, the CI configuration, or the test selection to make a
   check pass. If a check fails, fix the code or report that you cannot.
3. Do not claim a change works without running it. Never invent command output.
4. Do not silence a warning or an error with a suppression pragma, a catch-all,
   or a cast. Fix the cause, or say why it cannot be fixed.
5. Do not leave scratch work behind: temporary tests, debug binaries, examples,
   commented-out code, placeholders that would ship. Private plans, notes and
   verification reports belong under `.local/` only — see Local Documentation
   Policy below.
6. Do not commit secrets, credentials, or tokens. Do not log them.
7. Do not write facts that expire into documentation: version numbers, line
   numbers, file counts, dates. Point at the source instead — Java, Minecraft and
   loader versions come from `gradle.properties`, dependency versions from
   `gradle/libs.versions.toml`.

## Before you edit

- Read the file you are changing and one neighbour. Match them, not your habits.
- State the files you intend to touch and why. More than five files, or more
  than one module: stop and ask.
- When the documentation and the code disagree, the code is right. Say which
  document is stale.
- If the request is ambiguous, ask one question rather than guessing and
  building the wrong thing.
- A change is finished for both loaders or not at all. `common` code must
  compile and pass the checks on the Fabric and NeoForge paths before you call
  it done.

## While you edit

- Change what the task needs. No drive-by reformatting, renaming, import
  sorting, or comment rewriting.
- Delete what you replace. No fallback path, compatibility shim, dead flag, or
  unused config key kept "just in case".
- Do not add an abstraction before there is a third call site.
- Do not add a dependency without saying what it replaces, what it costs at
  build and runtime, and why the standard library or an existing dependency is
  not enough. New versions go in `gradle/libs.versions.toml`; never pin one in
  a module build file.
- Do not copy an implementation into a loader module, and do not add a second
  code path around the shared managers.
- Unverified is not done. If part of the change cannot be checked, name that
  part.

## Before you report

- Run the project's checks (see Verify with, below). Report the real result,
  including failures.
- List the files you actually changed, using real paths.
- State partial work as partial. Do not present an unfinished task as complete.
- If a rule here conflicts with the request, name the conflict and ask. Do not
  choose silently.

## Commits

- Conventional Commits: `type(scope): summary` (`feat`, `fix`, `perf`,
  `refactor`, `test`, `docs`, `chore`). Lowercase, imperative, one theme per
  commit, 72 characters or fewer, no trailing period.
- Describe what changed and why. Nothing else — no phase, step, ticket, or
  planning numbers in the subject or the body, and no identifiers such as
  `P0-A`, `P1-D`, `阶段3`, `Phase 2` or `步骤4`. Those codes exist only in
  `.local/` plans.
- Never `git add -A` when the tree spans unrelated changes.
- Never commit anything under `.local/`; never use `git add -f` on it.

## Project-specific

- **Verify with**:
  - `./gradlew build` — compiles both loaders, runs `:common:check`, stages the
    final Fabric and NeoForge JARs in `build/libs/`.
  - `./gradlew :common:check` — the headless verification suite alone (HUD
    contrast and motion, Skia interop and item rendering, startup rendering,
    music service, protocol, mixin compatibility).
  - `python -m unittest discover -s scripts -p 'test_*.py' -v`, and the same
    under `.github/scripts` — the tooling tests CI runs as `python3`.
  - `python scripts/verify_multiloader.py` after a build — compares the two
    staged JARs for identical shared code, assets, metadata, permissions and
    native libraries.
  - `./gradlew :fabric:runClient` or `:neoforge:runClient` — anything visual or
    interactive; do not claim it works from a headless check alone.
- **Ownership boundaries**: shared features, resources and mixins in `common`;
  entrypoints, platform wiring and Fabric-only integrations in the loader
  modules; build logic in the root `build.gradle.kts` and `gradle/*.gradle.kts`,
  with per-module build files limited to module-specific wiring.
- **Do not touch**: `build/`, `.gradle/`, `run/`, `logs/` and `.local/`
  (generated or local-only); the vendored sources in
  `common/src/main/java/cn/pupperclient/libraries/` (`flac`, `discordipc`,
  `material3`) whose upstream licenses demand their headers stay intact — see
  `THIRD-PARTY.md`; the licensing inventory (`LICENSE`, `NOTICE`,
  `THIRD-PARTY.md`) unless the dependency set actually changes; CI workflow
  and release scripts.
- **Conventions live in**: this file, `.editorconfig` for formatting, and
  `gradle/libs.versions.toml` for dependency versions.

## Architecture Overview

- **Mod Structure**: Architectury API client mod with one `common` implementation
  and thin `fabric` / `neoforge` modules; singleton `PupperClient` manages
  specialized managers (ModManager, EventBus, etc.)
- **Event System**: Custom `EventBus` using reflection for `@EventListener`
  methods and `EventListener<T>` fields
- **Mods**: Categorized features (HUD, player, render, misc) in
  `management/mod/impl/`
- **Rendering**: Blaze3D UI and shaders, with Skia generating CPU assets; no raw
  GL/Vulkan or Skia GPU contexts
- **Integration**: Shared `ProtocolAccess`; Fabric delegates to ViaFabricPlus,
  NeoForge reports native Minecraft protocol. WebSocket supplies real-time
  features.

## Key Workflows

- **Build**: `./gradlew build` (Architectury Loom no-remap); stages the final
  Fabric and NeoForge JARs in `build/libs/`
- **Run Client**: `./gradlew :fabric:runClient` / `./gradlew :neoforge:runClient`
  (separate `run/fabric` and `run/neoforge` directories); root `runClient` is a
  Fabric compatibility alias
- **First Launch**: Creates `pupper.ok` config file and shows terms screen
- **Mod Initialization**: `ModManager.init()` registers all mods and settings

## Project Conventions

- **Mandatory Local Documentation Policy**: Never create or add implementation
  plans, architecture/design documents, research notes, internal verification
  reports, or other private working documents in a Git-visible directory. Store
  them only under `.local/` (for example `.local/docs/`), which must remain
  ignored by Git. Before writing any such document, run `git check-ignore` on
  its intended path and confirm that it is ignored. Never use `git add -f` to
  commit these files. Existing tracked internal documents must be moved into
  `.local/` and removed from the tracked tree. `AGENTS.md` contains agent
  instructions, not working plans; public user documentation belongs in the
  tracked tree only when explicitly requested by the user.
- **Settings Simplicity**: Preserve the existing product design and choose
  sensible defaults. Expose only common, meaningful user choices; combine
  related technical parameters into clear presets, avoid duplicate controls, and
  keep connection/maintenance configuration behind a separate optional entry.
- **Package**: `cn.pupperclient` with subpackages by feature (animation, event,
  gui, management, mixin, shader, skia, ui, utils)
- **Managers**: Singleton pattern for core systems (e.g., `EventBus.getInstance()`)
- **Events**: Extend `Event` class, post via `EventBus.getInstance().post(event)`
- **Mixins**: Located in `mixin/mixins/` with accessors in `mixin/interfaces/`
- **Dependencies**: Managed via `gradle/libs.versions.toml` version catalog
- **Shared Sources**: All features in `common/src/main/java`; do not copy
  implementations between loader modules
- **Resources**: Assets in `common/src/main/resources/assets/pupper/`; platform
  descriptors are in their respective modules
- **Permissions**: `common/src/main/resources/pupper.classtweaker` is the source
  of truth; Loom converts it to NeoForge Access Transformers
- **Verification**: Common checks run under `:common:check`;
  `scripts/verify_multiloader.py` checks both final artifacts for identical
  shared code/assets, metadata, permissions and native libraries

## Integration Points

- **ViaFabricPlus**: `ProtocolAccess` consumes a `FabricProtocolProvider`; keep
  ViaFabricPlus imports inside `fabric`. NeoForge has no equivalent native
  ViaFabricPlus distribution; do not pretend to support translated protocols.
- **WebSocket**: Real-time communication in `management/websocket/`
- **Hypixel**: Server-specific features in `management/hypixel/`
- **Music**: NetEase/Audius catalog, downloads, playlists and audio playback in
  shared `management/music/`
- **UI**: Custom GUI screens in `gui/`, HUD mods in `management/mod/impl/hud/`

## Examples

- Add mod: Extend `Mod` class, register in `ModManager.initHudMods()`
- Handle event: Annotate method `@EventListener` or use `EventListener<TickEvent>`
- Access Minecraft: Use mixins like `MixinMinecraftClient` for client modifications
- Add setting: Create `Setting` subclass, add via `ModManager.addSetting()`
