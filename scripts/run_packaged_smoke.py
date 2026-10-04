#!/usr/bin/env python3
"""Run the existing smoke mod against a built distribution using cached dependencies.

Prepare :<loader>:runSmokeClient once before using this tool. The generated Loom
classpath supplies Minecraft, the loader and development mods; release libraries
and client classes must come from the final distribution and its nested JARs.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tomllib

from verify_multiloader import REQUIRED_LIBRARIES, VerificationError, check, entries, no_smoke_entries


def classpath_from_loom(path: Path) -> list[Path]:
    lines = [line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    check(len(lines) == 2 and lines[0] in ("-classpath", "-cp", "--class-path"),
          f"Unexpected Loom classpath argument file: {path}")
    # Loom quotes space characters within its sole classpath argument. Windows
    # paths cannot contain literal double quotes; do not shlex away backslashes.
    return [Path(value).resolve() for value in lines[1].replace('"', '').split(os.pathsep) if value]


def artifact_jar(name: str, artifact: str) -> bool:
    return name.startswith(artifact + "-") and name[len(artifact) + 1:][:1].isdigit() and name.endswith(".jar")


def release_classpath(original: list[Path], root: Path, loader: str, jar: Path) -> tuple[list[Path], list[Path]]:
    loose = {(root / loader / "build/classes/java/main").resolve(),
             (root / loader / "build/resources/main").resolve(),
             (root / loader / "out/production/classes").resolve(),
             (root / loader / "out/production/resources").resolve()}
    common = (root / "common").resolve()
    kept, removed = [], []
    for path in original:
        development = path in loose or path.is_relative_to(common)
        transformer = artifact_jar(path.name, "architectury-transformer")
        bundled = any(artifact_jar(path.name, artifact) for artifact in REQUIRED_LIBRARIES)
        # Soundlibs' JLayer POM declares JUnit as compile; its runtime code does
        # not reference it. Leaving it out makes the packaged test realistic.
        incidental = artifact_jar(path.name, "junit")
        (removed if development or transformer or bundled or incidental else kept).append(path)
    check(bool(removed), "No development classes/libraries removed; incorrect input classpath")
    for path in kept:
        check(path.exists(), f"Missing loader/runtime classpath entry: {path}")
    kept.append(jar.resolve())
    return list(dict.fromkeys(kept)), removed


def packaged_launch_config(original: str, smoke_paths: list[Path], loader: str) -> str:
    lines = [line for line in original.splitlines() if not line.strip().startswith("fabric.classPathGroups=")]
    if loader == "fabric":
        index = lines.index("commonProperties") + 1
        lines.insert(index, "\tfabric.classPathGroups=" + os.pathsep.join(str(path) for path in smoke_paths))
    return "\n".join(lines) + "\n"


def java_argument(value: str) -> str:
    check("\n" not in value and "\r" not in value and "\x00" not in value, "Invalid Java argument")
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def metadata_version(files: dict[str, bytes], loader: str) -> str:
    no_smoke_entries(files, "Packaged client")
    if loader == "fabric":
        metadata = json.loads(files["fabric.mod.json"])
        check(metadata.get("id") == "pupper", "Expected the Fabric Pupper Client distribution")
        check("META-INF/neoforge.mods.toml" not in files, "Fabric artifact contains NeoForge metadata")
        return metadata["version"]
    metadata = tomllib.loads(files["META-INF/neoforge.mods.toml"].decode("utf-8"))
    mods = [mod for mod in metadata["mods"] if mod.get("modId") == "pupper"]
    check(len(mods) == 1, "Expected the NeoForge Pupper Client distribution")
    check("fabric.mod.json" not in files, "NeoForge artifact contains Fabric metadata")
    return mods[0]["version"]


def prepare(root: Path, loader: str, jar: Path) -> tuple[Path, Path, dict[str, str], dict]:
    jar = jar.resolve()
    version = metadata_version(entries(jar, "Packaged client"), loader)
    original = classpath_from_loom(root / loader / "build/loom-cache/argFiles/runSmokeClient")
    classpath, removed = release_classpath(original, root, loader, jar)
    smoke_paths = [(root / loader / "build/classes/java/smokeVerification").resolve(),
                   (root / loader / "build/resources/smokeVerification").resolve()]
    for path in smoke_paths:
        check(path in classpath and path.is_dir(), f"Smoke mod output missing from classpath: {path}")
    # Retain StartupSmoke's isolation contract and never reuse the ordinary dev run.
    work = root / "build/packaged-smoke" / loader
    game = work / "build/smoke"
    game.mkdir(parents=True, exist_ok=True)
    config = work / "launch.cfg"
    config.write_text(packaged_launch_config(
        (root / f".gradle/loom-cache/projects/{loader}/launch.cfg").read_text(encoding="utf-8"), smoke_paths, loader),
        encoding="utf-8")
    vm_args = ["-Xms512M", "-Xmx4G", "-XX:HeapBaseMinAddress=34g",
               "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"]
    if loader == "neoforge":
        universal = next((path for path in classpath if path.name == "forge-universal.jar"), None)
        check(universal is not None, "Missing NeoForge universal JAR in Loom classpath")
        template = json.loads((universal.parent / "forge-config.json").read_text(encoding="utf-8"))["runs"]["client"]
        default_args = set(vm_args)
        vm_args.extend(argument for argument in template.get("jvmArgs", []) if argument not in default_args)
        main = template["main"]
    else:
        main = "net.fabricmc.loader.impl.launch.knot.KnotClient"
        if sys.platform == "darwin":
            vm_args.append("-XstartOnFirstThread")
    vm_args.extend([f"-Dfabric.dli.config={config}", "-Dfabric.dli.env=client", f"-Dfabric.dli.main={main}",
                    "-Dpupper.smoke=true", f"-Dpupper.smoke.loader={loader}",
                    f"-Dpupper.smoke.expectedVersion={version}", "-Dpupper.smoke.packaged=true",
                    f"-Dpupper.smoke.expectedArtifact={jar}"])
    arguments = vm_args + ["-classpath", os.pathsep.join(str(path) for path in classpath),
                           "net.fabricmc.devlaunchinjector.Main", "--username", "PupperPackagedSmoke",
                           "--width", "854", "--height", "480"]
    argfile = work / "java.args"
    argfile.write_text("\n".join(java_argument(argument) for argument in arguments) + "\n", encoding="utf-8")
    environment = os.environ.copy()
    environment.pop("MOD_CLASSES", None)
    if loader == "neoforge":
        environment["MOD_CLASSES"] = os.pathsep.join("pupper_smoke%%" + str(path) for path in smoke_paths)
    audit = {"loader": loader, "version": version, "jar": str(jar),
             "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(), "working_directory": str(game),
             "classpath": [str(path) for path in classpath], "removed": [str(path) for path in removed],
             "main": main, "smoke_mod_paths": [str(path) for path in smoke_paths]}
    (work / "launch-audit.json").write_text(json.dumps(audit, indent=2) + "\n", encoding="utf-8")
    return argfile, game, environment, audit


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("loader", choices=("fabric", "neoforge"))
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--java", default=str(Path(os.environ["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in os.environ else "java")
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--prepare-only", action="store_true", help="Write isolated launch inputs without starting Java")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    props = dict(line.split("=", 1) for line in (root / "gradle.properties").read_text().splitlines()
                 if "=" in line and not line.lstrip().startswith("#"))
    version = props["mod_version"].strip() + "+mc" + props["minecraft_version"].strip()
    artifact = "Fabric" if args.loader == "fabric" else "NeoForge"
    jar = args.jar or root / f"build/libs/Pupper Client-{artifact}-{version}.jar"
    try:
        argfile, game, environment, audit = prepare(root, args.loader, jar)
        print(f"Prepared {args.loader} packaged smoke: removed {len(audit['removed'])} development/library entries")
        print(f"Artifact: {audit['jar']}\nSHA-256: {audit['sha256']}\nAudit: {argfile.parent / 'launch-audit.json'}")
        if args.prepare_only:
            return 0
        check(shutil.which(args.java) is not None, f"Java executable not found: {args.java}")
        result = game / "pupper-smoke-result.json"
        result.unlink(missing_ok=True)
        console = argfile.parent / "console.log"
        with console.open("wb") as output:
            process = subprocess.Popen([args.java, "@" + str(argfile)], cwd=game, env=environment,
                                       stdout=output, stderr=subprocess.STDOUT)
            try:
                code = process.wait(timeout=args.timeout)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
                raise VerificationError(f"Packaged smoke timed out; inspect {console}")
        check(code == 0, f"Packaged smoke exited {code}; inspect {console}")
        check(result.is_file(), f"No successful packaged smoke result; inspect {console}")
        report = json.loads(result.read_text(encoding="utf-8"))
        check(report.get("loader") == args.loader and report.get("version") == audit["version"], "Wrong smoke result metadata")
        check(report.get("ticks", 0) >= 60 and report.get("frames", 0) >= 60, "Incomplete smoke result")
        check(report.get("packaged") is True, "Smoke result did not verify packaged class origins and native loading")
        check(Path(report.get("artifact", "")).resolve() == jar.resolve(), "Smoke result used a different artifact")
        check(report.get("nfdInitialized") is True and report.get("unixSocketSupported") is True,
              "Packaged smoke did not confirm both native libraries loaded")
        print(json.dumps(report, indent=2, ensure_ascii=False))
        return 0
    except (VerificationError, OSError, KeyError, ValueError, TypeError, StopIteration) as error:
        print(f"Packaged smoke failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
