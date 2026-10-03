# Releases

The `Build and Release` workflow builds and checks pushes and pull requests targeting `main`, `master` and `ver/**`. Pull requests never publish. A push that changes `mod_version` or `minecraft_version` in `gradle.properties` automatically publishes after the build passes. Automatic publishing is limited to `main`, `master`, and the exact `ver/<minecraft_version>` branch; other version subbranches only build.

## Setup

Add the repository Actions Secret `MODRINTH_TOKEN`, using a Modrinth personal access token with the `CREATE_VERSION` scope and access to the project in `modrinth_project_id`. The workflow uses GitHub's built-in `GITHUB_TOKEN` with `contents: write` only in the release job. No GitHub personal access token is required.

`gradle.properties` is the single source of truth. The release version includes the Minecraft version, for example `9.0.0-alpha.6+mc26.2`, with Git tag `v9.0.0-alpha.6+mc26.2`. Versions containing `beta` are Modrinth beta releases; other versions with a prerelease suffix are alpha releases. Both are GitHub prereleases and do not replace the latest stable release. Versions without a prerelease suffix are stable releases.

## Publishing

1. Bump `mod_version` and commit it with the changes to publish.
2. Push the commits to `ver/26.2` (or the matching version branch). No local tag or separate tag push is needed.
3. The workflow builds with Java 25, runs `build` checks and the release script's offline tests, and validates the packaged `fabric.mod.json` and compiled client class.
4. The release job downloads that exact JAR and its notes. It creates a tag on the checked commit, creates a GitHub draft and uploads the JAR, publishes the same file through the existing Modrinth Gradle plugin, then publishes the GitHub draft.

The workflow uses `jar` because this Minecraft 26.2 Loom project builds the distributable directly, without a remapping task. Sources JARs and old artifacts in `build/libs` cannot be selected for publication. Archive entries use reproducible ordering and timestamps. Publishing never rebuilds the JAR, and both platforms are checked against its hash. GitHub normalizes spaces in asset filenames to dots, so its asset is named `Pupper.Client-Fabric-<version>.jar`; the verified bytes are unchanged. Release notes come from commits since the previous reachable release tag for the same Minecraft version; the first release includes the latest 100 commits. Both platforms receive the same notes.

## Retrying

If publication fails, use **Re-run failed jobs** for the original Actions run. Its verified artifact remains in the run. A GitHub draft is left unpublished if Modrinth fails. Retrying reuses the draft, skips matching assets and Modrinth versions, and completes publication. The release job stops if a tag points to another commit or an existing file has different bytes; bump the version instead of overwriting an existing release.

The workflow also offers **Run workflow**, with `publish` enabled to publish/retry the selected branch's current version, or disabled to only build. GitHub requires a workflow with `workflow_dispatch` to be available on the default branch before showing this button.

If the release scripts themselves needed a fix, **Re-run failed jobs** still executes the old scripts. Instead, select the same branch under **Run workflow**, enable `publish`, and set `retry_run_id` to the original failed run's numeric ID. This executes the current scripts against the original commit, JAR and release notes. The original build must have passed, its artifact must still be available, and its commit must belong to the selected branch's history. Pull request runs and builds from other repositories or branches are rejected. The original release tag is preserved; no JAR is rebuilt or replaced.

GPU checks remain local optional tasks because the hosted build runner has no graphics device. The workflow does not publish from pull requests, cancel an in-progress publication, or depend on a `release.published` event starting another workflow: events created by `GITHUB_TOKEN` do not start ordinary downstream workflows.

Local validation without credentials or network:

```powershell
python -m unittest discover -s .github/scripts -p 'test_*.py' -v
python .github/scripts/release.py prepare
.\gradlew.bat build
python .github/scripts/release.py verify
```

Official references: [GitHub workflow triggering](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow), [GitHub release API](https://docs.github.com/en/rest/releases/releases), and [Modrinth Minotaur](https://github.com/modrinth/minotaur).
