#!/usr/bin/env python3
"""Verify the actual distributable Fabric/NeoForge jars without network access."""

from __future__ import annotations

import argparse
import io
import json
from pathlib import Path
import struct
import sys
import tomllib
import zipfile


class VerificationError(ValueError):
    pass


PLATFORM_PACKAGES = ("cn/pupperclient/fabric/", "cn/pupperclient/neoforge/")
NESTING_METADATA = "META-INF/architectury-loom-nesting-metadata.json"
SMOKE_PACKAGE = "cn/pupperclient/smoke/"
SMOKE_MIXINS = "pupper-smoke.mixins.json"
REQUIRED_LIBRARIES = (
    "caffeine", "types", "skija-shared", "skija-windows-x64", "junixsocket-common", "junixsocket-native-common",
    "Java-WebSocket", "mp3agic", "mp3spi", "jaudiotagger", "jlayer",
    "tritonus-share", "MCPing", "Reflect", "lwjgl-nfd",
)
REQUIRED_LIBRARY_CLASSES = {
    "skija-shared": ("io/github/humbleui/skija/Font.class", "io/github/humbleui/skija/Surface.class"),
    "tritonus-share": ("org/tritonus/share/sampled/file/TAudioFileReader.class",
                      "org/tritonus/share/sampled/convert/TAsynchronousFilteredAudioInputStream.class"),
    "junixsocket-native-common": ("org/newsclub/lib/junixsocket/common/NarMetadata.class",),
}
REQUIRED_LIBRARY_NATIVES = {
    "junixsocket-native-common": (
        ("lib/amd64-Windows10-clang/jni/", ".dll"),
        ("lib/amd64-Linux-clang/jni/", ".so"),
        ("lib/x86_64-MacOSX-clang/jni/", ".dylib"),
        ("lib/aarch64-MacOSX-clang/jni/", ".dylib"),
    ),
}
NFD_NATIVE_PATHS = (
    "windows/x64/org/lwjgl/nfd/lwjgl_nfd.dll",
    "linux/x64/org/lwjgl/nfd/liblwjgl_nfd.so",
    "macos/x64/org/lwjgl/nfd/liblwjgl_nfd.dylib",
    "macos/arm64/org/lwjgl/nfd/liblwjgl_nfd.dylib",
)


def check(condition: bool, message: str) -> None:
    if not condition:
        raise VerificationError(message)


def entries(source: Path | bytes, label: str) -> dict[str, bytes]:
    try:
        with zipfile.ZipFile(io.BytesIO(source) if isinstance(source, bytes) else source) as jar:
            files = [entry for entry in jar.infolist() if not entry.is_dir()]
            names = [entry.filename for entry in files]
            check(len(names) == len(set(names)), f"{label}: duplicate ZIP entries")
            return {entry.filename: jar.read(entry) for entry in files}
    except (OSError, zipfile.BadZipFile, RuntimeError) as error:
        raise VerificationError(f"{label}: cannot read JAR: {error}") from error


def require(files: dict[str, bytes], name: str, label: str) -> bytes:
    check(name in files, f"{label}: missing {name}")
    return files[name]


def no_smoke_entries(files: dict[str, bytes], label: str) -> None:
    forbidden = sorted(name for name in files
                       if name.startswith(SMOKE_PACKAGE) or name.rsplit("/", 1)[-1] == SMOKE_MIXINS)
    check(not forbidden, f"{label}: development smoke entries in distributable JAR: {forbidden[:8]}")


def shared_code(name: str) -> bool:
    return name.startswith("cn/pupperclient/") and not name.startswith(PLATFORM_PACKAGES)


def same_entries(left: dict[str, bytes], right: dict[str, bytes], label: str) -> int:
    missing = sorted(left.keys() - right.keys())
    extra = sorted(right.keys() - left.keys())
    check(not missing and not extra,
          f"{label}: inventories differ; missing in NeoForge={missing[:8]}, extra={extra[:8]}")
    differences = [name for name in sorted(left) if left[name] != right[name]]
    check(not differences, f"{label}: different bytes in {differences[:8]}")
    return len(left)


def class_annotations(data: bytes) -> list[tuple[str, dict[str, object]]]:
    """Read class-level annotations; no javap or Java runtime is required."""
    stream = io.BytesIO(data)

    def read(size: int) -> bytes:
        value = stream.read(size)
        check(len(value) == size, "NeoForge entrypoint: truncated class file")
        return value

    def u1() -> int:
        return read(1)[0]

    def u2() -> int:
        return struct.unpack(">H", read(2))[0]

    def u4() -> int:
        return struct.unpack(">I", read(4))[0]

    check(read(4) == b"\xca\xfe\xba\xbe", "NeoForge entrypoint: invalid class file")
    read(4)  # minor/major version
    pool: dict[int, object] = {}
    count = u2()
    index = 1
    while index < count:
        tag = u1()
        if tag == 1:
            pool[index] = read(u2()).decode("utf-8", errors="replace")
        elif tag in (3, 4):
            pool[index] = read(4)
        elif tag in (5, 6):
            read(8)
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = u2()
        elif tag in (9, 10, 11, 12, 17, 18):
            read(4)
        elif tag == 15:
            read(3)
        else:
            raise VerificationError(f"NeoForge entrypoint: unknown constant pool tag {tag}")
        index += 1

    def annotation() -> tuple[str, dict[str, object]]:
        annotation_type = str(pool[u2()])
        values = {}
        for _ in range(u2()):
            name = str(pool[u2()])
            values[name] = element()
        return annotation_type, values

    def element() -> object:
        tag = chr(u1())
        if tag in "BCDFIJSZs":
            return pool[u2()]
        if tag == "e":
            return (pool[u2()], pool[u2()])
        if tag == "c":
            return pool[u2()]
        if tag == "@":
            return annotation()
        if tag == "[":
            return [element() for _ in range(u2())]
        raise VerificationError(f"NeoForge entrypoint: unknown annotation tag {tag}")

    def skip_attributes() -> None:
        for _ in range(u2()):
            u2()
            read(u4())

    read(6)  # access flags, this class, super class
    read(2 * u2())  # interfaces
    for _ in range(2):  # fields and methods
        for _ in range(u2()):
            read(6)
            skip_attributes()
    result = []
    for _ in range(u2()):
        name = pool[u2()]
        length = u4()
        end = stream.tell() + length
        if name in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
            result.extend(annotation() for _ in range(u2()))
            check(stream.tell() == end, "NeoForge entrypoint: invalid annotation length")
        else:
            read(length)
    return result


def access_rules(source: str) -> set[tuple[str, str, str]]:
    lines = [line.split("#", 1)[0].strip() for line in source.splitlines()]
    lines = [line for line in lines if line]
    check(lines and lines[0] in ("classTweaker v1 official", "accessWidener v2 official"),
          "classTweaker: expected official namespace")
    result = set()
    for line in lines[1:]:
        words = line.split()
        check(len(words) >= 3 and words[0] == "accessible", f"classTweaker: unsupported rule {line}")
        kind, owner = words[1], words[2].replace("/", ".")
        if kind == "class" and len(words) == 3:
            result.add((kind, owner, ""))
        elif kind == "field" and len(words) == 5:
            result.add((kind, owner, words[3]))
        elif kind == "method" and len(words) == 5:
            result.add((kind, owner, words[3] + words[4]))
        else:
            raise VerificationError(f"classTweaker: malformed rule {line}")
    check(bool(result), "classTweaker: no access rules")
    return result


def transformer_rules(source: str) -> set[tuple[str, str, str]]:
    result = set()
    for line in source.splitlines():
        words = line.split("#", 1)[0].split()
        if not words:
            continue
        check(len(words) in (2, 3) and words[0] == "public", f"NeoForge AT: unexpected rule {line}")
        owner = words[1].replace("/", ".")
        member = words[2] if len(words) == 3 else ""
        kind = "method" if "(" in member else "field" if member else "class"
        result.add((kind, owner, member))
    return result


def declared_mixins(metadata: dict, platform: str) -> set[str]:
    result = set()
    for mixin in metadata.get("mixins", []):
        name = mixin if isinstance(mixin, str) else mixin.get("config")
        check(isinstance(name, str), f"{platform}: malformed mixin declaration")
        result.add(name)
    return result


def nested_libraries(files: dict[str, bytes], metadata: dict, platform: str) -> dict[str, dict[str, bytes]]:
    if platform == "Fabric":
        paths = [item["file"] for item in metadata.get("jars", [])]
    else:
        jij = json.loads(require(files, "META-INF/jarjar/metadata.json", platform))
        records = jij.get("jars", [])
        identifiers = [(item["identifier"]["group"], item["identifier"]["artifact"]) for item in records]
        check(len(identifiers) == len(set(identifiers)), "NeoForge: duplicate JarJar library identifiers")
        paths = [item["path"] for item in records]
    check(bool(paths), f"{platform}: no packaged libraries")
    check(len(paths) == len(set(paths)), f"{platform}: duplicate nested library declarations")
    actual = {name for name in files if name.startswith("META-INF/jars/") and name.endswith(".jar")}
    check(actual == set(paths), f"{platform}: nested JARs and metadata disagree")
    result = {}
    for path in paths:
        name = Path(path).name
        check(name not in result, f"{platform}: duplicate nested filename {name}")
        result[name] = entries(require(files, path, platform), f"{platform} library {name}")
        no_smoke_entries(result[name], f"{platform} library {name}")
    return result


def native_resources(files: dict[str, bytes], libraries: dict[str, dict[str, bytes]]) -> dict[str, bytes]:
    result = {}
    for container in [files, *libraries.values()]:
        for name, data in container.items():
            if "/org/lwjgl/nfd/" in name and name.endswith((".dll", ".so", ".dylib", ".sha1")):
                check(name not in result or result[name] == data, f"NFD: conflicting native resource {name}")
                result[name] = data
    return result


def verify_sources(fabric: Path, neoforge: Path) -> dict[str, int]:
    left, right = entries(fabric, "Fabric sources"), entries(neoforge, "NeoForge sources")
    platform_metadata = {"META-INF/MANIFEST.MF", "fabric.mod.json", "META-INF/neoforge.mods.toml"}
    common = lambda items: {name: data for name, data in items.items()
                            if not name.startswith(PLATFORM_PACKAGES) and name not in platform_metadata}
    count = same_entries(common(left), common(right), "shared sources/resources")
    check(count > 0, "source jars: no shared sources or resources")
    return {"source_entries": count}


def verify(fabric: Path, neoforge: Path, classtweaker: Path, *, version: str | None = None,
           required_libraries: tuple[str, ...] = REQUIRED_LIBRARIES) -> dict[str, object]:
    left, right = entries(fabric, "Fabric"), entries(neoforge, "NeoForge")
    no_smoke_entries(left, "Fabric")
    no_smoke_entries(right, "NeoForge")
    fabric_meta = json.loads(require(left, "fabric.mod.json", "Fabric"))
    neo_meta = tomllib.loads(require(right, "META-INF/neoforge.mods.toml", "NeoForge").decode("utf-8"))
    check(fabric_meta.get("id") == "pupper", "Fabric: incorrect mod id")
    check(fabric_meta.get("environment") == "client", "Fabric: must be client-only")
    mods = [mod for mod in neo_meta.get("mods", []) if mod.get("modId") == "pupper"]
    check(len(mods) == 1, "NeoForge: expected one pupper mod declaration")
    check(not any(mod.get("modId") == "pupper_smoke" for mod in neo_meta.get("mods", [])),
          "NeoForge: development smoke mod declared in distributable JAR")
    check(fabric_meta.get("version") == mods[0].get("version"), "loader metadata: different versions")
    if version:
        check(fabric_meta.get("version") == version, f"loader metadata: expected version {version}")
    clients = fabric_meta.get("entrypoints", {}).get("client", [])
    check(bool(clients), "Fabric: missing client entrypoint")
    for entry in clients:
        name = entry if isinstance(entry, str) else entry.get("value")
        check(isinstance(name, str), "Fabric: malformed client entrypoint")
        class_file = name.split("::", 1)[0].replace(".", "/") + ".class"
        require(left, class_file, "Fabric client entrypoint")
    neo_entrypoints = []
    for name, data in right.items():
        if name.startswith("cn/pupperclient/neoforge/") and name.endswith(".class"):
            if any(kind == "Lnet/neoforged/fml/common/Mod;" and values.get("value") == "pupper"
                   for kind, values in class_annotations(data)):
                neo_entrypoints.append(name)
    check(bool(neo_entrypoints), "NeoForge: missing real @Mod(\"pupper\") entrypoint")
    classes = same_entries({n: d for n, d in left.items() if shared_code(n) and n.endswith(".class")},
                           {n: d for n, d in right.items() if shared_code(n) and n.endswith(".class")}, "shared classes")
    require(left, "cn/pupperclient/PupperClient.class", "common client")
    assets = same_entries({n: d for n, d in left.items() if n.startswith("assets/")},
                          {n: d for n, d in right.items() if n.startswith("assets/")}, "shared assets")
    check(classes > 0 and assets > 0, "JARs: missing common classes/assets")
    fabric_mixins, neo_mixins = declared_mixins(fabric_meta, "Fabric"), declared_mixins(neo_meta, "NeoForge")
    check(SMOKE_MIXINS not in fabric_mixins | neo_mixins,
          "metadata: development smoke mixins declared in distributable JAR")
    common_mixins = fabric_mixins & neo_mixins
    check("pupper.mixins.json" in common_mixins, "metadata: common mixins not registered on both loaders")
    for name in common_mixins:
        check(require(left, name, "Fabric mixins") == require(right, name, "NeoForge mixins"),
              f"shared mixins: different bytes in {name}")
        config = json.loads(left[name])
        package = config.get("package", "").replace(".", "/")
        for mixin in config.get("mixins", []) + config.get("client", []):
            path = package + "/" + mixin.replace(".", "/") + ".class"
            require(left, path, "Fabric mixin class")
            require(right, path, "NeoForge mixin class")
    source = classtweaker.read_bytes()
    tweaker_name = fabric_meta.get("accessWidener")
    check(isinstance(tweaker_name, str), "Fabric: missing classTweaker declaration")
    check(require(left, tweaker_name, "Fabric classTweaker") == source, "Fabric: classTweaker differs from common source")
    expected = access_rules(source.decode("utf-8"))
    transformers = transformer_rules(require(right, "META-INF/accesstransformer.cfg", "NeoForge AT").decode("utf-8"))
    check(transformers == expected,
          f"NeoForge AT: access rules differ; missing={sorted(expected - transformers)}, extra={sorted(transformers - expected)}")
    libs_left = nested_libraries(left, fabric_meta, "Fabric")
    libs_right = nested_libraries(right, neo_meta, "NeoForge")
    for platform, libraries in (("Fabric", libs_left), ("NeoForge", libs_right)):
        for artifact in required_libraries:
            matching = [files for name, files in libraries.items()
                        if name.startswith(artifact + "-") and name[len(artifact) + 1:][:1].isdigit()
                        and "-natives-" not in name]
            check(bool(matching),
                  f"{platform}: missing packaged library {artifact}")
            for class_file in REQUIRED_LIBRARY_CLASSES.get(artifact, ()):
                check(any(class_file in files for files in matching),
                      f"{platform}: library {artifact} missing required runtime class {class_file}")
            for prefix, suffix in REQUIRED_LIBRARY_NATIVES.get(artifact, ()):
                check(any(name.startswith(prefix) and name.endswith(suffix)
                          for files in matching for name in files),
                      f"{platform}: library {artifact} missing required native {prefix}*{suffix}")
        check(any("org/lwjgl/util/nfd/NativeFileDialog.class" in files for files in libraries.values()),
              f"{platform}: missing NFD Java bindings")
    ordinary = lambda libraries: {name: data for name, data in libraries.items() if "-natives-" not in name}
    paired_left, paired_right = ordinary(libs_left), ordinary(libs_right)
    check(paired_left.keys() == paired_right.keys(), "packaged libraries: different non-native inventories")
    for name in paired_left:
        strip = lambda files: {n: d for n, d in files.items() if n not in ("fabric.mod.json", NESTING_METADATA)}
        same_entries(strip(paired_left[name]), strip(paired_right[name]), f"library {name}")
    native_left, native_right = native_resources(left, libs_left), native_resources(right, libs_right)
    for name in NFD_NATIVE_PATHS:
        require(native_left, name, "Fabric NFD native")
        require(native_right, name, "NeoForge NFD native")
    natives = same_entries(native_left, native_right, "NFD native resources")
    return {"version": fabric_meta["version"], "shared_classes": classes, "shared_assets": assets,
            "common_mixins": len(common_mixins), "access_rules": len(expected),
            "shared_libraries": len(paired_left), "native_resources": natives,
            "neoforge_entrypoints": neo_entrypoints}


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    props = dict(line.split("=", 1) for line in (root / "gradle.properties").read_text().splitlines()
                 if "=" in line and not line.lstrip().startswith("#"))
    version = props["mod_version"].strip() + "+mc" + props["minecraft_version"].strip()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fabric-jar", type=Path, default=root / f"build/libs/Pupper Client-Fabric-{version}.jar")
    parser.add_argument("--neoforge-jar", type=Path, default=root / f"build/libs/Pupper Client-NeoForge-{version}.jar")
    parser.add_argument("--classtweaker", type=Path, default=root / "common/src/main/resources/pupper.classtweaker")
    parser.add_argument("--version", default=version)
    parser.add_argument("--fabric-sources", type=Path)
    parser.add_argument("--neoforge-sources", type=Path)
    args = parser.parse_args()
    try:
        check(bool(args.fabric_sources) == bool(args.neoforge_sources), "provide both loader source jars")
        result = verify(args.fabric_jar, args.neoforge_jar, args.classtweaker, version=args.version)
        if args.fabric_sources:
            result.update(verify_sources(args.fabric_sources, args.neoforge_sources))
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except (VerificationError, OSError, ValueError, KeyError, TypeError) as error:
        print(f"Multiloader verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
