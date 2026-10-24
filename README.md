# Pupper Client

A better, faster Minecraft client.

## Features
- Optimized rendering and performance
- Custom HUD and UI
- Multi-version support via ViaFabricPlus (Fabric)
- In-game account switching

## Requirements
- Minecraft 26.2 on Java 25
- Fabric Loader 0.19.5+ or NeoForge 26.2.0.88+
- Architectury API 21.1.11+
- Fabric only: Fabric API and ViaFabricPlus

NeoForge uses Minecraft's native protocol; ViaFabricPlus translation is Fabric-only.

## Install
Put the matching JAR from [Modrinth](https://modrinth.com/mod/pupper-client) into `mods`, together with
Architectury API (and, on Fabric, Fabric API and ViaFabricPlus), then launch that loader's profile.

## Develop
Shared code and assets live in `common`; the loader modules hold only entrypoints and integrations.
The default branch is `architectury/26.2`, and `ver/<minecraft_version>` branches remain supported.

    ./gradlew build              # both distributions, staged in build/libs
    ./gradlew :fabric:runClient
    ./gradlew :neoforge:runClient

Maintainers publish by bumping `mod_version` and pushing to `architectury/<minecraft_version>`.
Open an issue before larger contributions.

## License
Pupper Client is licensed under the GNU General Public License, version 3 or later
(GPL-3.0-or-later) — see [LICENSE](LICENSE). Portions derived from Soar Client (MIT)
are attributed in [NOTICE](NOTICE); third-party components are listed in
[THIRD-PARTY.md](THIRD-PARTY.md).
