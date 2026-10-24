# Third-party components

Pupper Client is distributed under the GNU General Public License, version 3 or
later (GPL-3.0-or-later) — see [LICENSE](LICENSE). It contains the components
listed below, each of which remains under its own license; copyright in every
component stays with its authors. Code inherited from Soar Client (MIT) is
attributed in [NOTICE](NOTICE).

## Source code kept in this repository

| Component | Location | Copyright | License | Modified |
| --- | --- | --- | --- | --- |
| libFLAC (Java port) | `common/src/main/java/cn/pupperclient/libraries/flac/` | Copyright (C) 2000-2003 Josh Coalson; Java port by kc7bfi | LGPL-2.0-or-later | No — original GNU header kept in every file |
| DiscordIPC | `common/src/main/java/cn/pupperclient/libraries/discordipc/` | Copyright 2017 John Grosh (john.a.grosh@gmail.com) | Apache-2.0 | No |
| Material Color Utilities | `common/src/main/java/cn/pupperclient/libraries/material3/` (except `Material3.java`) | Copyright 2021-2024 Google LLC | Apache-2.0 | No |

These directories are excluded from the project license header check
(`spotlessCheck`) on purpose: their files carry the upstream notices, and those
notices must not be replaced. The one exception is
`libraries/material3/Material3.java`: it is a first-party adapter written against
the library's public API (it calls the project's own `ImageUtils` and adds
helpers that upstream does not have), so it carries the project license header
rather than Google's.

## Bundled fonts

| Font | File | Copyright | License |
| --- | --- | --- | --- |
| Inter + Noto Sans CJK SC + WFM Sans SC | `assets/pupper/fonts/Inter-Regular-CJKsc.ttf`, `Inter-Medium-CJKsc.ttf` | Copyright 2020 The Inter Project Authors; © 2014-2020 Adobe; Copyright © 2018-2019 Cyano Hao | SIL OFL 1.1 |
| Open Sans | `assets/pupper/fonts/OpenSans-Medium.ttf` | Copyright 2020 The Open Sans Project Authors | SIL OFL 1.1 |
| Material Symbols Rounded | `assets/pupper/fonts/MaterialSymbolsRounded.ttf`, `MaterialSymbolsRounded_Fill.ttf` | Copyright 2020-2023 Google LLC | Apache-2.0 |

The full SIL OFL 1.1 text that these fonts require is shipped next to them in
`assets/pupper/fonts/OFL.txt`.

## Jar-in-jar dependencies

These libraries are shaded into the release JARs by Loom's jar-in-jar support
(see `gradle/libraries.gradle.kts`). None of them is modified.

Versions come from `gradle/libs.versions.toml`; the coordinates below are the
stable part of each entry.

The Skija rows list the platform modules that are packaged, not the full upstream
set: a native module is only shipped for a platform the client supports, and the
two must stay in sync. `scripts/verify_multiloader.py` fails when the platforms
named here and the modules inside the JAR disagree, so this table cannot drift
from the artifact again.

| Library | Coordinates | License |
| --- | --- | --- |
| Caffeine | `com.github.ben-manes.caffeine:caffeine` | Apache-2.0 |
| HumbleUI types | `io.github.humbleui:types` | Apache-2.0 |
| Skija | `io.github.humbleui:skija-shared`, `skija-windows-x64`, `skija-linux-x64` — one native module per supported desktop platform, selected at runtime | Apache-2.0 |
| junixsocket | `com.kohlschutter.junixsocket:junixsocket-common`, `junixsocket-native-common` | Apache-2.0 |
| Java-WebSocket | `org.java-websocket:Java-WebSocket` | MIT |
| mp3agic | `com.mpatric:mp3agic` | MIT |
| mp3spi | `com.googlecode.soundlibs:mp3spi` | LGPL-2.1 |
| tritonus-share | `com.googlecode.soundlibs:tritonus-share` | LGPL-2.1 |
| jaudiotagger | `org:jaudiotagger` | LGPL-2.1 |
| jlayer | `com.googlecode.soundlibs:jlayer` | LGPL-2.1 |
| MCPing | `net.lenni0451:MCPing` | MIT |
| Reflect | `net.lenni0451:Reflect` | MIT |
| LWJGL NFD | `org.lwjgl:lwjgl-nfd` (+ `natives-*` classifiers) | BSD-3-Clause |

## Build and runtime dependencies that are not redistributed

Minecraft, Fabric Loader, Fabric API, NeoForge, Architectury API, ViaFabricPlus,
Mod Menu, Sodium, Lithium, Iris, ImmediatelyFast, EntityCulling, In-Game Account
Switcher and Placeholder API are compile/runtime dependencies only. They are not
shaded into the published JARs and stay under their own licenses.

## LGPL source availability

The LGPL components are used unmodified, and their complete source is available
from this repository (`common/src/main/java/cn/pupperclient/libraries/`) and
from each project's upstream release. Recipients may replace any of them with a
modified version; the build loads them as ordinary classes, so a recompiled
replacement can be supplied by rebuilding from this source tree.

## License texts

| Text | Location in the repository | Location in release JARs |
| --- | --- | --- |
| GNU GPL 3.0 | `LICENSE` | `LICENSE` |
| Project copyright and Soar Client MIT notice | `NOTICE` | `NOTICE` |
| This inventory | `THIRD-PARTY.md` | `THIRD-PARTY.md` |
| SIL OFL 1.1 | `common/src/main/resources/assets/pupper/fonts/OFL.txt` | `assets/pupper/fonts/OFL.txt` |
| Apache-2.0 | `common/src/main/resources/META-INF/licenses/Apache-2.0.txt` | `META-INF/licenses/Apache-2.0.txt` |
| LGPL-2.0 | `common/src/main/resources/META-INF/licenses/LGPL-2.0.txt` | `META-INF/licenses/LGPL-2.0.txt` |
| LGPL-2.1 | `common/src/main/resources/META-INF/licenses/LGPL-2.1.txt` | `META-INF/licenses/LGPL-2.1.txt` |

## Components removed from this tree

| Component | Reason |
| --- | --- |
| ResourcePackConverter (`libraries/resourcepack/`) | No copyright or license header in any file and its origin could not be established, so it could not be redistributed lawfully. Removed with the `Convert` button it powered. |
| Google Sans (`assets/pupper/fonts/GoogleSans-Regular.ttf`) | Proprietary Google font, licensed only for Google's own products; not redistributable. Replaced by Open Sans Medium. |
| Meteor Client `Color.java` / `ICopyable.java` and the unused `shader/` package | GPL-3.0-only code reachable only from dead code. Keeping it would have forced the whole JAR to GPL-3.0-only instead of GPL-3.0-or-later. |
