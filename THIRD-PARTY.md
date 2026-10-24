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
| Material Color Utilities | `common/src/main/java/cn/pupperclient/libraries/material3/` | Copyright 2021-2024 Google LLC | Apache-2.0 | Small changes in `Material3.java` |

These directories are excluded from the project license header check
(`spotlessCheck`) on purpose: their files carry the upstream notices, and those
notices must not be replaced.

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

| Library | Coordinates | License |
| --- | --- | --- |
| Caffeine | `com.github.ben-manes.caffeine:caffeine:3.1.8` | Apache-2.0 |
| HumbleUI types | `io.github.humbleui:types:0.2.0` | Apache-2.0 |
| Skija | `io.github.humbleui:skija-shared:0.143.12`, `skija-windows-x64`, `skija-linux-x64` | Apache-2.0 |
| junixsocket | `com.kohlschutter.junixsocket:junixsocket-common:2.10.1`, `junixsocket-native-common` | Apache-2.0 |
| Java-WebSocket | `org.java-websocket:Java-WebSocket:1.6.0` | MIT |
| mp3agic | `com.mpatric:mp3agic:0.9.1` | MIT |
| mp3spi | `com.googlecode.soundlibs:mp3spi:1.9.5.4` | LGPL-2.1 |
| tritonus-share | `com.googlecode.soundlibs:tritonus-share:0.3.7.4` | LGPL-2.1 |
| jaudiotagger | `org:jaudiotagger:2.0.3` | LGPL-2.1 |
| jlayer | `com.googlecode.soundlibs:jlayer:1.0.1.4` | LGPL-2.1 |
| MCPing | `net.lenni0451:MCPing:1.4.2` | MIT |
| Reflect | `net.lenni0451:Reflect:1.4.0` | MIT |
| LWJGL NFD | `org.lwjgl:lwjgl-nfd:3.4.1` (+ `natives-*` classifiers) | BSD-3-Clause |

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
| ResourcePackConverter (`libraries/resourcepack/`, 25 files) | No copyright or license header in any file and its origin could not be established, so it could not be redistributed lawfully. Removed with the `Convert` button it powered. |
| Google Sans (`assets/pupper/fonts/GoogleSans-Regular.ttf`) | Proprietary Google font, licensed only for Google's own products; not redistributable. Replaced by Open Sans Medium. |
| Meteor Client `Color.java` / `ICopyable.java` and the unused `shader/` package | GPL-3.0-only code reachable only from dead code. Keeping it would have forced the whole JAR to GPL-3.0-only instead of GPL-3.0-or-later. |
