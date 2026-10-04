"""Build metadata and retryable publishing; no network calls in prepare/verify."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import tomllib
from urllib.parse import quote
from urllib.request import Request, urlopen
import zipfile


PROPERTIES = Path("gradle.properties")
NOTES = Path("build/release/notes.md")


def properties(text):
    result = {}
    for line in text.splitlines():
        if line.strip() and not line.lstrip().startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def metadata(props):
    mod, game = props["mod_version"], props["minecraft_version"]
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", mod):
        raise ValueError("mod_version must be a semantic version, optionally alpha/beta/rc")
    if not re.fullmatch(r"[0-9A-Za-z][0-9A-Za-z.-]*", game):
        raise ValueError("Invalid minecraft_version")
    archive = props["archives_base_name"]
    if not re.fullmatch(r"[0-9A-Za-z _.-]+", archive):
        raise ValueError("Invalid archives_base_name")
    project = props["modrinth_project_id"]
    if not re.fullmatch(r"[0-9A-Za-z_-]+", project):
        raise ValueError("Invalid modrinth_project_id")
    legacy = "enabled_platforms" not in props
    platforms = ["fabric"] if legacy else [item.strip() for item in props["enabled_platforms"].split(",")]
    if not platforms or len(platforms) != len(set(platforms)) or any(item not in ("fabric", "neoforge") for item in platforms):
        raise ValueError("enabled_platforms must list unique supported loaders: fabric,neoforge")
    version = f"{mod}+mc{game}"
    kind = "beta" if "beta" in mod.lower() else "alpha" if "-" in mod else "release"
    info = {"mod": mod, "game": game, "version": version, "tag": f"v{version}",
            "title": f"Pupper Client {mod} for Minecraft {game}", "kind": kind,
            "project": project, "archive": archive, "legacy": legacy, "platforms": platforms}
    info["jar"] = artifacts(info)[0]["jar"]
    return info


def artifacts(info):
    """One shared game version/tag, with separate immutable files and Modrinth versions."""
    if info["legacy"]:
        return [info | {"loader": "fabric", "modrinth_version": info["version"],
                        "jar": str(Path("build/libs") / f"{info['archive']}-{info['version']}.jar")}]
    names = {"fabric": "Fabric", "neoforge": "NeoForge"}
    return [info | {"loader": loader, "modrinth_version": f"{info['version']}-{loader}",
                    "jar": str(Path("build/libs") / f"{info['archive']}-{names[loader]}-{info['version']}.jar")}
            for loader in info["platforms"]]


def should_publish(event, branch, current, previous, manual=True):
    if event not in ("push", "workflow_dispatch"):
        return False
    changed = previous is None or any(current[key] != previous.get(key) for key in ("mod_version", "minecraft_version"))
    requested = manual if event == "workflow_dispatch" else changed
    if not requested:
        return False
    if branch not in ("main", "master", f"ver/{current['minecraft_version']}", f"architectury/{current['minecraft_version']}"):
        if event == "workflow_dispatch":
            raise ValueError("Publishing requires main/master, ver/<minecraft_version> or architectury/<minecraft_version>; check branch and gradle.properties")
        return False
    return True


def command(*args, optional=False, input_text=None):
    result = subprocess.run(args, input=input_text, capture_output=True, text=True, encoding="utf-8")
    if result.returncode and not optional:
        raise RuntimeError(f"{args[0]} failed: {result.stderr.strip()}")
    return result


def prepare():
    props = properties(PROPERTIES.read_text(encoding="utf-8"))
    info = metadata(props)
    event_path = os.getenv("GITHUB_EVENT_PATH")
    payload = json.loads(Path(event_path).read_text(encoding="utf-8")) if event_path else {}
    previous = None
    before = payload.get("before", "")
    if before and before != "0" * 40:
        if not re.fullmatch(r"[0-9a-f]{40}", before):
            raise ValueError("Invalid push base commit")
        result = command("git", "show", f"{before}:gradle.properties", optional=True)
        if result.returncode:
            raise RuntimeError("Cannot read the push base; fetch full history or retry with workflow_dispatch")
        previous = properties(result.stdout)
    manual = str(payload.get("inputs", {}).get("publish", "true")).lower() == "true"
    publish = should_publish(os.getenv("GITHUB_EVENT_NAME", "local"), os.getenv("GITHUB_REF_NAME", ""), props, previous, manual)
    previous_tag = command("git", "describe", "--tags", "--abbrev=0", "--match", f"v*+mc{info['game']}",
                           "--exclude", info["tag"], optional=True)
    revision = f"{previous_tag.stdout.strip()}..HEAD" if previous_tag.returncode == 0 else "HEAD"
    changes = command("git", "log", "--max-count=100", "--format=- %s (%h)", revision).stdout.strip()
    NOTES.parent.mkdir(parents=True, exist_ok=True)
    loaders = f"Fabric Loader {props['loader_version']}+" if info["legacy"] else "Fabric / NeoForge · Architectury API"
    NOTES.write_text(f"# {info['title']}\n\nMinecraft {info['game']} · {loaders} · Java 25\n\n"
                     f"## Changes\n\n{changes or '- Maintenance update.'}\n", encoding="utf-8")
    jars = "\n".join(artifact["jar"] for artifact in artifacts(info))
    output = os.getenv("GITHUB_OUTPUT")
    if output:
        with Path(output).open("a", encoding="utf-8") as stream:
            stream.write(f"publish={str(publish).lower()}\nversion={info['version']}\njar={info['jar']}\n"
                         f"jars<<PUPPER_ARTIFACTS\n{jars}\nPUPPER_ARTIFACTS\n")
    print(f"{info['version']}; publish={str(publish).lower()}; artifacts={jars.replace(chr(10), ', ')}")


def retry(run_id):
    """Reuse a successful build while executing the current, repaired release scripts."""
    if not re.fullmatch(r"[0-9]+", run_id):
        raise ValueError("retry_run_id must be a numeric Actions run ID")
    repo = os.environ["GITHUB_REPOSITORY"]
    run = github(repo, f"actions/runs/{run_id}")
    if (run.get("event") not in ("push", "workflow_dispatch") or run.get("status") != "completed"
            or run.get("path", "").split("@")[0] != ".github/workflows/build.yml"
            or run.get("head_repository", {}).get("full_name", "").lower() != repo.lower()
            or run.get("head_branch") != os.environ["GITHUB_REF_NAME"]):
        raise ValueError("Retry requires a completed Build and Release run from this repository and branch, not a pull request")
    sha = run["head_sha"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid original build commit")
    command("git", "merge-base", "--is-ancestor", sha, "HEAD")
    props = properties(command("git", "show", f"{sha}:gradle.properties").stdout)
    info = metadata(props)
    should_publish("workflow_dispatch", run["head_branch"], props, None)
    jobs = github(repo, f"actions/runs/{run_id}/jobs?filter=latest&per_page=100")["jobs"]
    if not any(job["name"] == "build" and job["conclusion"] == "success" for job in jobs):
        raise ValueError("The original build job must have passed")
    artifact_name = f"pupper-client-{sha}"
    artifacts = github(repo, f"actions/runs/{run_id}/artifacts?per_page=100")["artifacts"]
    matches = [artifact for artifact in artifacts if artifact["name"] == artifact_name and not artifact["expired"]]
    if len(matches) != 1:
        raise ValueError("The original verified build artifact is missing or expired")
    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as stream:
        stream.write(f"run_id={run_id}\nsha={sha}\nversion={info['version']}\n")
    print(f"Retrying {info['version']} from verified run {run_id}, commit {sha}")


def hashes(path):
    result = {}
    for algorithm in ("sha256", "sha512"):
        with path.open("rb") as stream:
            result[algorithm] = hashlib.file_digest(stream, algorithm).hexdigest()
    return result


def verify(info):
    path = Path(info["jar"])
    with zipfile.ZipFile(path) as jar:
        loader = info.get("loader", "fabric")
        if loader == "fabric":
            mod = json.loads(jar.read("fabric.mod.json"))
            valid = (mod.get("id") == "pupper" and mod.get("version") == info["version"]
                     and mod.get("depends", {}).get("minecraft") == info["game"])
        else:
            descriptor = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8"))
            mods = [mod for mod in descriptor.get("mods", []) if mod.get("modId") == "pupper"]
            game_dependencies = [dependency for dependency in descriptor.get("dependencies", {}).get("pupper", [])
                                 if dependency.get("modId") == "minecraft"]
            valid = (len(mods) == 1 and mods[0].get("version") == info["version"] and len(game_dependencies) == 1
                     and game_dependencies[0].get("versionRange") == f"[{info['game']}]")
        if not valid:
            raise ValueError(f"{loader} JAR metadata does not match the version being released")
        if "cn/pupperclient/PupperClient.class" not in jar.namelist():
            raise ValueError("Release artifact lacks compiled client classes")
    return hashes(path)


def verify_artifacts(info):
    return [(artifact, verify(artifact)) for artifact in artifacts(info)]


def github(repo, endpoint, data=None, missing=False):
    args = ["gh", "api", f"repos/{repo}/{endpoint}"]
    if data is not None:
        args += ["--method", "PATCH" if endpoint.startswith("releases/") else "POST", "--input", "-"]
    result = command(*args, optional=True, input_text=json.dumps(data) if data is not None else None)
    if result.returncode:
        if missing and "HTTP 404" in result.stderr:
            return None
        raise RuntimeError(f"GitHub request failed: {result.stderr.strip()}")
    return json.loads(result.stdout)


def find_release(repo, info):
    release = github(repo, f"releases/tags/{quote(info['tag'], safe='')}", missing=True)
    if release is not None:
        return release
    # The tag endpoint only promises published releases. Find a previous failed
    # run's draft through the authenticated, paginated list instead.
    page = 1
    while True:
        releases = github(repo, f"releases?per_page=100&page={page}")
        matches = [release for release in releases if release["tag_name"] == info["tag"]]
        if len(matches) > 1:
            raise ValueError("Multiple GitHub releases use this tag; resolve the conflict before retrying")
        if matches:
            return matches[0]
        if len(releases) < 100:
            return None
        page += 1


def versions(info):
    # A conflicting version number must not be hidden by loader/game filters.
    # New builds use unique loader suffixes; changelogs are not needed for hashes.
    request = Request(f"https://api.modrinth.com/v2/project/{quote(info['project'])}/version?include_changelog=false",
                      headers={"User-Agent": "Pupper-Client-release/1.0 (github.com/MeowCat-Team/Pupper-Client)",
                               "Authorization": os.environ["MODRINTH_TOKEN"]})
    with urlopen(request, timeout=30) as response:
        return json.load(response)


def needs_modrinth_upload(published, info, digest):
    number = info.get("modrinth_version", info["version"])
    matches = [version for version in published if version["version_number"] == number]
    if not matches:
        return True
    if (len(matches) != 1 or matches[0].get("version_type") != info["kind"]
            or set(matches[0].get("loaders", [])) != {info.get("loader", "fabric")}
            or info["game"] not in matches[0].get("game_versions", [])):
        raise ValueError("Modrinth version conflict; bump mod_version rather than overwriting a release")
    if not any(file.get("primary") and file.get("hashes", {}).get("sha512") == digest["sha512"] for file in matches[0]["files"]):
        raise ValueError("Modrinth already has different bytes for this version; bump mod_version")
    return False


def check_tag(repo, info, sha):
    tag = github(repo, f"git/ref/tags/{quote(info['tag'], safe='')}", missing=True)
    if tag:
        obj = tag["object"]
        for _ in range(8):
            if obj["type"] != "tag":
                break
            obj = github(repo, f"git/tags/{obj['sha']}")["object"]
        if obj["type"] != "commit" or obj["sha"] != sha:
            raise ValueError("Release tag points to another commit; bump mod_version")
    return tag is not None


def asset_name(info):
    # GitHub normalizes spaces to dots. Use the canonical name explicitly on upload.
    return Path(info["jar"]).name.replace(" ", ".")


def has_asset(repo, release, info, digest):
    names = {Path(info["jar"]).name, asset_name(info)}
    assets = [asset for asset in release.get("assets", []) if asset["name"] in names]
    if not assets:
        return False
    for asset in assets:
        if asset.get("digest"):
            matches = asset["digest"] == f"sha256:{digest['sha256']}"
        else:
            # Older GitHub assets lack the digest field; compare the downloaded bytes.
            with tempfile.TemporaryDirectory() as temporary:
                command("gh", "release", "download", info["tag"], "--repo", repo, "--pattern", asset["name"], "--dir", temporary)
                with (Path(temporary) / asset["name"]).open("rb") as stream:
                    matches = hashlib.file_digest(stream, "sha256").hexdigest() == digest["sha256"]
        if not matches:
            raise ValueError("GitHub already has different bytes for this version; bump mod_version")
    return True


def upload_asset(repo, info):
    with tempfile.TemporaryDirectory() as temporary:
        path = Path(temporary) / asset_name(info)
        shutil.copyfile(info["jar"], path)
        command("gh", "release", "upload", info["tag"], str(path), "--repo", repo)


def publish(info):
    if not os.getenv("GH_TOKEN") or not os.getenv("MODRINTH_TOKEN"):
        raise ValueError("Set repository Secret MODRINTH_TOKEN (CREATE_VERSION scope); GitHub supplies GITHUB_TOKEN")
    repo, sha = os.environ["GITHUB_REPOSITORY"], os.getenv("RELEASE_SHA") or os.environ["GITHUB_SHA"]
    if not re.fullmatch(r"[0-9A-Za-z_.-]+/[0-9A-Za-z_.-]+", repo) or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid repository or commit")
    if command("git", "rev-parse", "HEAD").stdout.strip() != sha:
        raise ValueError("Checkout does not match the verified workflow commit")
    verified = verify_artifacts(info)
    notes = NOTES.read_text(encoding="utf-8")
    tag_exists = check_tag(repo, info, sha)
    published = versions(info)
    plan = [(artifact, digest, needs_modrinth_upload(published, artifact, digest)) for artifact, digest in verified]
    release = find_release(repo, info)
    assets_present = [release is not None and has_asset(repo, release, artifact, digest) for artifact, digest, _ in plan]
    if release is not None and not tag_exists and release.get("target_commitish") != sha:
        raise ValueError("Existing draft targets another commit; bump mod_version")
    if not tag_exists:
        github(repo, "git/refs", {"ref": f"refs/tags/{info['tag']}", "sha": sha})
    if release is None:
        release = github(repo, "releases", {"tag_name": info["tag"], "target_commitish": sha,
                         "name": info["title"], "body": notes, "draft": True, "prerelease": info["kind"] != "release"})
    elif release.get("body"):
        notes = release["body"]
        NOTES.write_text(notes, encoding="utf-8")
    for (artifact, digest, _), asset_exists in zip(plan, assets_present):
        if not asset_exists:
            upload_asset(repo, artifact)
            if not has_asset(repo, github(repo, f"releases/{release['id']}"), artifact, digest):
                raise RuntimeError("GitHub upload could not be verified; rerun this failed release job")
    for artifact, digest, upload in plan:
        if not upload:
            continue
        environment = os.environ.copy()
        environment["CHANGELOG_FILE"] = str(NOTES.resolve())
        environment["RELEASE_ARTIFACT_FILE"] = str(Path(artifact["jar"]).resolve())
        # The verified artifact is downloaded from the build job. Never rebuild it here.
        task = "modrinth" if info["legacy"] else f":{artifact['loader']}:modrinth"
        excluded = "jar" if info["legacy"] else f":{artifact['loader']}:shadowJar"
        subprocess.run(["./gradlew", task, "-x", excluded, "--no-daemon", "--console=plain"], env=environment, check=True)
        if needs_modrinth_upload(versions(artifact), artifact, digest):
            raise RuntimeError("Modrinth upload could not be verified; rerun this failed release job")
    if release["draft"]:
        github(repo, f"releases/{release['id']}", {"draft": False, "prerelease": info["kind"] != "release",
                                               "make_latest": "false" if info["kind"] != "release" else "legacy"})
    summary = f"Published {info['version']} ({', '.join(info['platforms'])}) to GitHub Release and Modrinth."
    print(summary)
    if os.getenv("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as stream:
            stream.write(f"{summary}\n\n- [GitHub Release](https://github.com/{repo}/releases/tag/{info['tag']})\n"
                         f"- [Modrinth](https://modrinth.com/mod/{info['project']})\n")


if __name__ == "__main__":
    try:
        action = sys.argv[1]
        if action == "prepare":
            prepare()
        elif action == "retry":
            retry(sys.argv[2])
        elif action in ("verify", "publish"):
            info = metadata(properties(PROPERTIES.read_text(encoding="utf-8")))
            if action == "publish":
                publish(info)
            else:
                for artifact, _ in verify_artifacts(info):
                    print(f"Verified {artifact['loader']} release artifact: {artifact['jar']}")
        else:
            raise ValueError("Usage: release.py prepare|verify|publish|retry RUN_ID")
    except (KeyError, IndexError, ValueError, OSError, RuntimeError, subprocess.CalledProcessError, zipfile.BadZipFile) as failure:
        print(f"Release error: {failure}", file=sys.stderr)
        sys.exit(1)
