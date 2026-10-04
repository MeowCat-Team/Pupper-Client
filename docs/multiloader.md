# Fabric and NeoForge

Pupper Client targets Minecraft 26.2 with Java 25. Architectury API supplies the loader-independent platform and event APIs. Client features, screens, music services, rendering, settings, translations and Minecraft mixins live in one `common` project; both distributable JARs include that implementation.

## Project layout

| Project | Responsibility |
| --- | --- |
| `common/` | `cn.pupperclient` feature code, shared assets and mixins, classTweaker source, and standalone verification programs. |
| `fabric/` | Fabric client entrypoint, Fabric metadata, ViaFabricPlus protocol provider, and Fabric development dependencies. |
| `neoforge/` | NeoForge client entrypoint, NeoForge metadata and access transformer, and NeoForge development dependencies. |
| `gradle/libraries.gradle.kts` | Shared Java libraries and native-file-dialog dependencies. |
| `scripts/verify_multiloader.py` | Inspection of the actual packaged Fabric and NeoForge JARs. |

Changes to a feature belong in `common/src/main/java`, and changes to client assets belong in `common/src/main/resources/assets/pupper`. Loader projects contain integration code rather than copies of feature classes. The root `build` checks that the two artifacts contain byte-identical shared classes and assets. This keeps feature implementations synchronized; it does not make a third-party Fabric mod available on NeoForge.

Chat command interception uses Architectury's `ClientChatEvent.SEND`, and the target HUD uses `PlayerEvent.ATTACK_ENTITY`. Version lookup, optional-mod detection and development-mode detection use Architectury's `Platform` API. Existing Minecraft mixins remain shared.

Shared mixins compile against Mixin 0.17.3, the annotation API supported by both loader runtimes. Fabric's newer API changes `Redirect.at` and `slice` into arrays, which NeoForge's current MixinExtras cannot read. This constraint applies only to common compilation; neither loader's runtime is replaced. The HUD event runs immediately before the shared `Hud.extractEffects` method because NeoForge splits the original hotbar/decorations dispatcher into separate layers. `verifyMixinCompatibility` checks the compiled annotations, both actual runtime implementations and the HUD anchor/order.

## Protocol support

`ProtocolAccess` is the common source of protocol information. The Fabric entrypoint installs `FabricProtocolProvider`, which delegates to the existing ViaFabricPlus API. Its selected target remains visible while disconnected, target changes update the protocol HUD immediately, and the existing 1.8-specific animation gates retain their previous target-version condition. Before ViaFabricPlus initializes, or if it has no selected target, the provider reports Minecraft's actual native version.

Fabric keeps ViaFabricPlus as a required dependency. Its protocol selection, packet translation and compatibility fixes remain supplied by ViaFabricPlus. The upstream project explicitly supports Fabric only and does not plan a Forge or NeoForge port. [ViaFabricPlus documentation](https://github.com/ViaVersion/ViaFabricPlus)

NeoForge reports the name and protocol number from `SharedConstants.getCurrentVersion()`. This is an actual native protocol implementation, with no built-in cross-version translator. A NeoForge build therefore does not expose a false protocol selector or claim that translated 1.8 animation compatibility is available. The module, settings and other shared feature code are still present.

ViaForge is archived, and its actual repository branches do not provide a Minecraft 26.2 implementation. Adding an old ViaForge artifact would not provide a supported equivalent to ViaFabricPlus. ViaLoader is also archived; its maintainers recommend using the current ViaVersion platform API directly for new integrations. [ViaForge status](https://github.com/ViaVersion/ViaForge), [ViaLoader status](https://github.com/ViaVersion/ViaLoader)

An external ViaProxy instance can translate connections from a 26.2 client to supported servers. Configure it separately and connect to the address it provides. This works outside the loader, but does not add ViaFabricPlus's client-side movement, interaction or rendering fixes. Pupper Client continues to report its native protocol through that connection; it cannot infer the proxy's backend target or automatically enable 1.8-specific fixes. [ViaProxy documentation](https://github.com/ViaVersion/ViaProxy)

## Build and verification

Install a JDK 25 and Python 3.11 or newer, then use the repository's Gradle wrapper. On Windows:

```powershell
.\gradlew.bat build --console=plain
```

On Linux or macOS:

```sh
./gradlew build --console=plain -Ppython_executable=python3
```

The root build compiles both loaders, runs the shared checks and loader-specific checks, stages the distributable JARs, and verifies their contents. With version `9.0.0-alpha.6`, the staged files are:

```text
build/libs/Pupper Client-Fabric-9.0.0-alpha.6+mc26.2.jar
build/libs/Pupper Client-NeoForge-9.0.0-alpha.6+mc26.2.jar
```

Install the artifact for the loader you use. `common` and intermediate development JARs are not installable client distributions. Minecraft 26.2 uses official unobfuscated names; these builds use Architectury Loom's no-remap plugin.

You can inspect the staged JARs again without accessing the network:

```powershell
python scripts/verify_multiloader.py
```

The verifier checks matching shared classes, assets and mixin configurations; real client entrypoints; matching version metadata; equivalent classTweaker/access-transformer permissions; nested library declarations; and matching native-file-dialog resources. It also rejects smoke-test classes or mixin files in distributable and nested JARs, and smoke-test mod or mixin declarations in distribution metadata. It accepts `--fabric-jar` and `--neoforge-jar` for explicit artifact paths, and `--fabric-sources` with `--neoforge-sources` to compare the shared contents of source JARs. It fails if files differ or required packaging metadata is missing.

The shared `verifyProtocol` checks cover native protocol reporting and selected-target changes. Fabric's protocol checks exercise the actual ViaFabricPlus API with a controlled selected-target fixture, including initialization, disconnected state and target changes. These checks do not claim to replace playing on servers with both loaders.

## Development clients and existing data

Run the loaders separately:

```powershell
.\gradlew.bat :fabric:runClient --console=plain
.\gradlew.bat :neoforge:runClient --console=plain
```

The root `runClient` command remains a compatibility alias for the Fabric client. Fabric uses `run/fabric/`, and NeoForge uses `run/neoforge/`. Each has its own Minecraft options, logs, resource packs and `pupper/` configuration directory. This prevents one loader's development launch from rewriting the other's configuration.

The previous root `run/` data remains in place. To reuse it, close the clients and copy the existing `run/pupper/` folder into the chosen loader's `pupper/` folder. That includes client configuration, music, profiles, cached data, backgrounds, capes and the first-launch marker. Copy `options.txt`, `servers.dat`, `resourcepacks/` or `shaderpacks/` separately if you want those settings and files in the new instance. Keep the originals until the new instance has been checked.

Use loader-specific versions for extra mods in each instance. A Fabric `mods/` folder cannot be copied wholesale into the NeoForge instance. New logs are in `run/fabric/logs/` and `run/neoforge/logs/`.

## Startup smoke checks

The separate smoke verification source set runs the actual loader and Minecraft client with a test mod. Run one loader at a time on a machine with a graphics device:

```powershell
.\gradlew.bat :fabric:runSmokeClient --console=plain
.\gradlew.bat :neoforge:runSmokeClient --console=plain
```

These runs use `fabric/build/smoke/` and `neoforge/build/smoke/`, rather than either development instance or the original `run/`. The test mod waits for at least 60 client ticks and 60 completed render frames, checks manager initialization and identity, validates the active protocol provider and mod metadata, and checks that the production mixins are merged into their targets. A successful check writes `pupper-smoke-result.json` in that loader's smoke directory, prints `PUPPER_SMOKE_SUCCESS`, and closes the client. Each run clears its old result first; a timeout or failed assertion must not be treated as a pass.

Smoke classes, resources and mixins belong only to the smoke verification source set. Normal `runClient`, `jar` and `shadowJar` tasks do not load or package the test mod. Startup checks validate these specific loading and rendering contracts; they do not establish that every gameplay feature or server interaction has been exercised.

After building both distributions and preparing the smoke runs above, test the packaged JARs separately:

```powershell
python scripts/run_packaged_smoke.py fabric
python scripts/run_packaged_smoke.py neoforge
```

Use `--java <path-to-java>` if Java 25 is not on `PATH`. These checks reuse the local loader/game classpath, remove loose common/platform classes and all external copies of the bundled libraries, and load the actual staged JAR through the loader's normal nested-JAR support. They use isolated `build/packaged-smoke/<loader>/` directories. Acceptance additionally requires the actual client class to originate from the selected artifact and successful Native File Dialog and Unix socket JNI initialization. The launcher does not download dependencies or publish artifacts; the game and its mods retain their normal service requests.

On Windows x64 with Java 25 and Intel Arc graphics, both packaged distributions passed the 60-tick/render-frame, 53-mixin, class-origin and JNI checks. Common checks, GPU UI/startup checks and the full dual-loader build also passed. Server gameplay, shader packs and other operating systems still require their own verification.

Architectury's NeoForge development transformer can retain worker threads after the game window closes. The smoke harness ends its development process only after the launcher thread returns and normal Minecraft/FML cleanup completes. It leaves packaged runs and Fabric's natural shutdown unchanged. Ordinary NeoForge development runs may need Ctrl+C after closing the window until the upstream runtime fixes that thread lifecycle; the packaged NeoForge distribution exits normally.

## Optional development mods

The following NeoForge coordinates were verified against the projects' official Modrinth version records on 2026-10-04. They are compatible development choices, not additional required Pupper Client dependencies. The version catalog pins the selected development set.

| Mod | Existing Fabric version | NeoForge 26.2 coordinate | Source |
| --- | --- | --- | --- |
| Sodium | 0.9.2 | `maven.modrinth:sodium:mc26.2-0.9.2-neoforge` | [Official version](https://modrinth.com/mod/sodium/version/DmnNKsfS) |
| Iris | 1.11.4 | `maven.modrinth:iris:1.11.4+26.2-neoforge` | [Official version](https://modrinth.com/mod/iris/version/k55HdONq) |
| Lithium | 0.25.3 | `maven.modrinth:lithium:mc26.2-0.25.3-neoforge` | [Official version](https://modrinth.com/mod/lithium/version/J9CowDXK) |
| In-Game Account Switcher | 9.0.7 | `maven.modrinth:in-game-account-switcher:9.0.7+26.2-neoforge` | [Matching version](https://modrinth.com/mod/in-game-account-switcher/version/16f7YGrC) |
| ImmediatelyFast | 1.16.2 | `maven.modrinth:immediatelyfast:1.16.2+26.2-neoforge` | [Matching version](https://modrinth.com/mod/immediatelyfast/version/E76T0Qen) |
| EntityCulling | 1.10.5 | `maven.modrinth:entityculling:6vCuV21u` (1.11.2) | [Official version](https://modrinth.com/mod/entityculling/version/6vCuV21u) |

Iris 1.11.4 explicitly requires the Sodium 0.9.2 NeoForge build listed above. Sodium's optional fast item writer remains shared, and the ordinary `VertexConsumer` path is available when Sodium is absent. [Iris dependency declaration](https://api.modrinth.com/v2/version/k55HdONq)

The seven Sodium API and bridge classes referenced by the shared item writer were compared in the official 0.9.2 Fabric and NeoForge artifacts after validating their published SHA-512 digests; their class bytes are identical. NeoForge puts the actual Sodium implementation in its nested `net.caffeinemc.sodium-neoforge-0.9.2+mc26.2-mod.jar`. This permits compiling the shared writer against the existing Fabric API while loading the corresponding implementation through NeoForge's JarJar metadata. It is an API compatibility check; in-game rendering still needs verification on each loader. [Fabric Sodium artifact](https://api.modrinth.com/v2/version/xJZxADzI), [NeoForge Sodium artifact](https://api.modrinth.com/v2/version/DmnNKsfS)

IAS also provides a newer NeoForge 9.0.8 build (`maven.modrinth:in-game-account-switcher:9.0.8+26.2-neoforge`); matching Fabric 9.0.8 is available if both development sets are deliberately upgraded. [NeoForge 9.0.8](https://modrinth.com/mod/in-game-account-switcher/version/zXbn0dcL), [Fabric 9.0.8](https://modrinth.com/mod/in-game-account-switcher/version/GseYYTDa)

There is no official NeoForge 26.2 EntityCulling 1.10.5 record. NeoForge 1.11.2 and Fabric 1.11.2 (`RWjup6Jf`) are available if matching third-party versions are needed. Pinning the version IDs distinguishes loader-specific files with the same version label. [Fabric EntityCulling 1.11.2](https://modrinth.com/mod/entityculling/version/RWjup6Jf)

Mod Menu and Placeholder API have no official NeoForge 26.2 builds in their version records. They remain Fabric development dependencies. Pupper Client's own mod menu, music player and translations are common features and do not depend on either external mod. [Mod Menu versions](https://api.modrinth.com/v2/project/modmenu/version?game_versions=%5B%2226.2%22%5D&loaders=%5B%22neoforge%22%5D), [Placeholder API versions](https://api.modrinth.com/v2/project/placeholder-api/version?game_versions=%5B%2226.2%22%5D&loaders=%5B%22neoforge%22%5D)

## Packaged libraries and rendering

Both loader artifacts carry the same shared Java libraries for caching, fonts and images, music decoding, networking and native integration. Fabric declares nested JARs in `fabric.mod.json`; NeoForge declares them in JarJar metadata. Runtime-only development mods are supplied by their loader and are not bundled into the Pupper Client distribution.

The shared dependency list includes Skija's Java implementation and Windows native wrapper, Tritonus for MP3 SPI, and junixsocket's JNI companion. Loom's non-transitive inclusion configuration does not automatically package those runtime companions. Minecraft's existing JNA, Gson and SLF4J libraries are reused rather than overridden.

Native File Dialog includes Windows x64, Linux x64, macOS x64 and macOS ARM64 resources on both loaders. NeoForge packaging retains their resource paths so LWJGL can locate the native library. The artifact verifier checks that both distributions contain matching native resource bytes.

The existing Skija dependency is `skija-windows-x64`, so this change retains the existing Windows x64 Skia asset-generation support. Packaging several Native File Dialog platforms does not by itself add Skia support for other operating systems. Skia continues to generate CPU assets; shared UI and glass rendering use Blaze3D rather than a Skia OpenGL or Vulkan context.

## Publication

The primary development and GitHub default branch is `architectury/26.2`, renamed in place from `refactor/architectury-26.2`. CI builds `architectury/**` branches. Publishing is allowed only from the exact `architectury/<minecraft_version>` or historical `ver/<minecraft_version>` branch, plus `main` and `master`; other game versions, nested feature branches and refactor branches cannot publish.

The branch rename and CI adaptation retain `mod_version=9.0.0-alpha.6`. This push only builds because the version is unchanged; it does not republish or replace the existing Fabric-only alpha.6 artifacts.

Select a new release version before publishing the new pair of loader artifacts; the existing Fabric-only release is immutable. See [release instructions](releases.md) for the normal GitHub Release and Modrinth workflow.
