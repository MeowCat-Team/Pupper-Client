# Pupper Client

A Better, Faster Minecraft Client

## Features
- Optimized performance and gameplay experience
- Custom HUD and UI elements
- ViaFabricPlus integration for multi-version support on Fabric
- In-game account switching capability
- Enhanced rendering and visual effects

## Installation

1. Ensure you have Minecraft 26.2, Java 25 and either Fabric Loader 0.19.5+ or NeoForge 26.2.0.88+ installed.
2. Download the latest version of Pupper Client from the [Modrinth](https://modrinth.com/mod/pupper-client) page.
3. Install Architectury API 21.1.11+ and place the Pupper Client JAR for your loader into `mods`. Fabric also requires Fabric API and ViaFabricPlus.
4. Launch Minecraft with the matching loader profile.

## Requirements
- **Minecraft**: Version 26.2
- **Fabric Loader**: Version 0.19.5 or higher
- **NeoForge**: Version 26.2.0.88 or higher (alternative to Fabric)
- **Architectury API**: Version 21.1.11 or higher
- **Fabric required mods**: Fabric API and ViaFabricPlus

UI, music, HUDs, settings, rendering and gameplay features share the same `common` implementation. ViaFabricPlus's protocol translation remains a Fabric integration; NeoForge uses Minecraft's native protocol. See [multiloader development and compatibility](docs/multiloader.md).

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

## Contributing

The primary development and GitHub default branch is `architectury/26.2`.

Build both distributions with `./gradlew build`. Run Fabric with `./gradlew :fabric:runClient` (or the `runClient` alias), and NeoForge with `./gradlew :neoforge:runClient`. Shared code and assets live in `common`; loader modules contain only their entrypoints and integrations.

Maintainers can bump `mod_version` and push to the exact `architectury/<minecraft_version>` branch to publish both loader builds automatically to GitHub Release and Modrinth. The historical matching `ver/<minecraft_version>` branches remain supported. See [release setup and retries](docs/releases.md).

Contributions are welcome! Please fork the repository and submit a pull request with your changes. 

For major changes, please open an issue first to discuss what you would like to change.
