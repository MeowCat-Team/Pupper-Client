# Releases

The primary development and GitHub default branch is `architectury/26.2`. The `Build and Release` workflow builds and checks pushes and pull requests targeting `architectury/**`, `main`, `master`, `ver/**` and `refactor/**`. Pull requests and refactor branches never publish. A push that changes `mod_version` or `minecraft_version` in `gradle.properties` automatically publishes after the build passes. Publishing is limited to the exact `architectury/<minecraft_version>` or `ver/<minecraft_version>` branch, plus the historical `main` and `master` branches. Other Minecraft versions and nested feature branches only build.

The migration branch was renamed in place from `refactor/architectury-26.2` to `architectury/26.2`. This rename and its CI adaptation keep `mod_version=9.0.0-alpha.6`, so their push only builds and does not publish. The existing Fabric-only alpha.6 release remains immutable; select a new version before releasing the two loader artifacts.

## Setup

Add the repository Actions Secret `MODRINTH_TOKEN`, using a Modrinth personal access token with the `CREATE_VERSION` scope and access to the project in `modrinth_project_id`. The workflow uses GitHub's built-in `GITHUB_TOKEN` with `contents: write` only in the release job. No GitHub personal access token is required.

`gradle.properties` is the single source of truth. `enabled_platforms=fabric,neoforge` declares the published loaders, and `archives_base_name=Pupper Client` is their shared filename prefix. The root build always builds and checks both loader projects. The version inside both JARs includes the Minecraft version, for example `9.0.0-alpha.7+mc26.2`, with Git tag `v9.0.0-alpha.7+mc26.2`. Each GitHub Release contains both loader JARs under this one tag. Modrinth receives separate versions named `9.0.0-alpha.7+mc26.2-fabric` and `9.0.0-alpha.7+mc26.2-neoforge`, each declaring its own loader. The suffix prevents ambiguous version lookup and permits retrying either loader independently.

Versions containing `beta` are Modrinth beta releases; other versions with a prerelease suffix are alpha releases. Both are GitHub prereleases and do not replace the latest stable release. Versions without a prerelease suffix are stable releases. Before the first release after the multiloader migration, bump `mod_version`; the already published Fabric-only version must not be replaced with different bytes.

## Publishing

1. Bump `mod_version` and commit it with the changes to publish.
2. Push the commits to `architectury/26.2` (or the matching `architectury/<minecraft_version>` branch). The historical matching `ver/<minecraft_version>` branch is also supported. No local tag or separate tag push is needed.
3. The workflow builds with Java 25, runs common and loader `build` checks and the release script's offline tests. It validates each JAR's loader metadata, exact game/mod version and compiled client class. Fabric uses `fabric.mod.json`; NeoForge uses `META-INF/neoforge.mods.toml`, whose Minecraft dependency is pinned to `[<minecraft_version>]`.
4. The root build stages the two distributable Shadow JARs at `build/libs/Pupper Client-Fabric-<version>.jar` and `build/libs/Pupper Client-NeoForge-<version>.jar`. Only these exact paths and the release notes are uploaded as the verified Actions artifact.
5. The release job downloads these same files and performs all tag, GitHub asset and Modrinth version conflict checks before writing to either platform. It creates a tag on the checked commit, creates a GitHub draft, uploads both JARs, publishes each file through its loader's Modrinth task, then publishes the GitHub draft after both loaders succeed.

Publishing calls `:fabric:modrinth -x :fabric:shadowJar` and `:neoforge:modrinth -x :neoforge:shadowJar`. `RELEASE_ARTIFACT_FILE` points the task's `uploadFile` to the absolute path of its downloaded, verified JAR in root `build/libs`; `CHANGELOG_FILE` points to the downloaded notes. When that artifact path is supplied, the shared publishing configuration disables Minotaur's automatic archive-input wiring and removes its default `assemble` dependency. The release task therefore does not rebuild the client. Ordinary local `modrinth` calls without the environment variable keep automatic assembly. Sources JARs and stale artifacts cannot be selected for the automated upload. Archive entries use reproducible ordering and timestamps, and GitHub/Modrinth are checked against SHA-256/SHA-512 respectively.

GitHub normalizes spaces in asset filenames to dots, so its assets are named `Pupper.Client-Fabric-<version>.jar` and `Pupper.Client-NeoForge-<version>.jar`; the verified bytes are unchanged. Release notes come from commits since the previous reachable release tag for the same Minecraft version; the first release includes the latest 100 commits. Both loaders and platforms receive the same notes.

## Retrying

If publication fails, use **Re-run failed jobs** for the original Actions run. Its verified artifact remains in the run. A GitHub draft is left unpublished if either Modrinth loader fails. Retrying reuses the draft, skips matching assets and loader versions, and completes publication. For example, if Fabric succeeds and NeoForge fails, the retry publishes only NeoForge before making the GitHub draft public. The release job stops if a tag points to another commit, a loader/game/version identity conflicts, or an existing file has different bytes; bump the version instead of overwriting an existing release.

The workflow also offers **Run workflow**, with `publish` enabled to publish/retry the selected branch's current version, or disabled to only build. GitHub requires a workflow with `workflow_dispatch` to be available on the default branch before showing this button.

If the release scripts themselves needed a fix, **Re-run failed jobs** still executes the old scripts. Instead, select the same branch under **Run workflow**, enable `publish`, and set `retry_run_id` to the original failed run's numeric ID. This executes the current scripts against the original commit, JARs and release notes. The original build must have passed, its artifact must still be available, and its commit must belong to the selected branch's history. Pull request runs and builds from other repositories or branches are rejected. The original release tag is preserved; no JAR is rebuilt or replaced.

Historical Fabric-only builds remain retryable. If the original commit's `gradle.properties` has no `enabled_platforms`, the current script preserves the old single `archives_base_name` filename and unsuffixed Modrinth version, and calls `modrinth -x jar` against that original checkout. It does not require NeoForge artifacts or apply the new layout to an old build.

GPU checks remain local optional tasks because the hosted build runner has no graphics device. The workflow does not publish from pull requests, cancel an in-progress publication, or depend on a `release.published` event starting another workflow: events created by `GITHUB_TOKEN` do not start ordinary downstream workflows.

Local validation without publishing credentials (Python 3.11+ and Java 25; Gradle may download dependencies):

```powershell
python -m unittest discover -s .github/scripts -p 'test_*.py' -v
python .github/scripts/release.py prepare
.\gradlew.bat build
python .github/scripts/release.py verify
```

Official references: [GitHub workflow triggering](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow), [GitHub release API](https://docs.github.com/en/rest/releases/releases), [Modrinth version API](https://docs.modrinth.com/api/operations/getprojectversions/), and [Modrinth Minotaur](https://github.com/modrinth/minotaur).
