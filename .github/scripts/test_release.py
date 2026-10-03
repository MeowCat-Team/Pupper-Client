"""Release checks use local archives and mock both platforms; never publish anything."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
import zipfile

import release


PROPS = {"mod_version": "9.0.0-alpha.6", "minecraft_version": "26.2",
         "archives_base_name": "Pupper Client-Fabric", "modrinth_project_id": "pupper-client"}
DIGEST = {"sha256": "a" * 64, "sha512": "b" * 128}
SHA = "c" * 40


class MetadataChecks(unittest.TestCase):
    def test_alpha_beta_rc_stable_and_game_identity(self):
        for mod, kind in (("9.0.0-alpha.6", "alpha"), ("9.0.0-beta.1", "beta"), ("9.0.0-rc.1", "alpha"), ("9.0.0", "release")):
            with self.subTest(mod=mod):
                info = release.metadata(PROPS | {"mod_version": mod})
                self.assertEqual(info["kind"], kind)
                self.assertEqual(info["tag"], f"v{mod}+mc26.2")
                self.assertEqual(Path(info["jar"]).name, f"Pupper Client-Fabric-{mod}+mc26.2.jar")
        self.assertNotEqual(release.metadata(PROPS)["tag"], release.metadata(PROPS | {"minecraft_version": "26.3"})["tag"])

    def test_unsafe_metadata_rejected(self):
        for key, value in (("mod_version", "../x"), ("mod_version", "9.0.0\npublish=true"),
                           ("minecraft_version", "../26.2"), ("archives_base_name", "../artifact"),
                           ("modrinth_project_id", "x/y")):
            with self.subTest(key=key):
                with self.assertRaises(ValueError):
                    release.metadata(PROPS | {key: value})

    def test_release_triggers(self):
        previous = PROPS | {"mod_version": "9.0.0-alpha.5"}
        for branch in ("main", "master", "ver/26.2"):
            self.assertTrue(release.should_publish("push", branch, PROPS, previous))
            self.assertTrue(release.should_publish("push", branch, PROPS, None))
        self.assertFalse(release.should_publish("push", "ver/26.2", PROPS, PROPS))
        self.assertFalse(release.should_publish("push", "ver/26.2", PROPS | {"loader_version": "new"}, PROPS))
        self.assertTrue(release.should_publish("push", "ver/26.2", PROPS, PROPS | {"minecraft_version": "26.1"}))
        for event in ("pull_request", "release", "workflow_run", "local"):
            self.assertFalse(release.should_publish(event, "ver/26.2", PROPS, previous))
        self.assertTrue(release.should_publish("workflow_dispatch", "ver/26.2", PROPS, PROPS))
        self.assertFalse(release.should_publish("workflow_dispatch", "ver/26.2", PROPS, previous, False))
        for branch in ("feature/music", "ver/26.3", "ver/26.2/feature"):
            self.assertFalse(release.should_publish("push", branch, PROPS, previous))
            with self.assertRaises(ValueError):
                release.should_publish("workflow_dispatch", branch, PROPS, previous)

    def test_jar_requires_compiled_mod_and_matching_version(self):
        info = release.metadata(PROPS)
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / Path(info["jar"]).name
            info = info | {"jar": str(path)}
            for version, game, compiled, valid in ((info["version"], info["game"], True, True),
                    ("old", info["game"], True, False), (info["version"], "26.1", True, False),
                    (info["version"], info["game"], False, False)):
                with zipfile.ZipFile(path, "w") as jar:
                    jar.writestr("fabric.mod.json", json.dumps({"id": "pupper", "version": version, "depends": {"minecraft": game}}))
                    if compiled:
                        jar.writestr("cn/pupperclient/PupperClient.class", b"compiled fixture")
                if valid:
                    self.assertEqual(release.verify(info), release.hashes(path))
                else:
                    with self.assertRaises(ValueError):
                        release.verify(info)


class PlatformChecks(unittest.TestCase):
    def setUp(self):
        self.info = release.metadata(PROPS)
        self.version = {"version_number": self.info["version"], "version_type": "alpha",
                        "files": [{"primary": True, "hashes": {"sha512": DIGEST["sha512"]}}]}
        self.asset = {"name": release.asset_name(self.info), "digest": f"sha256:{DIGEST['sha256']}"}

    def test_modrinth_duplicate_and_conflict(self):
        self.assertTrue(release.needs_modrinth_upload([], self.info, DIGEST))
        self.assertFalse(release.needs_modrinth_upload([self.version], self.info, DIGEST))
        for version in (self.version | {"version_type": "release"},
                        self.version | {"files": [{"primary": True, "hashes": {"sha512": "wrong"}}]},
                        self.version | {"files": [{"primary": False, "hashes": {"sha512": DIGEST["sha512"]}}]}):
            with self.assertRaises(ValueError):
                release.needs_modrinth_upload([version], self.info, DIGEST)
        with self.assertRaises(ValueError):
            release.needs_modrinth_upload([self.version, self.version], self.info, DIGEST)

    def test_github_asset_digest(self):
        self.assertFalse(release.has_asset("owner/repo", {"assets": []}, self.info, DIGEST))
        self.assertTrue(release.has_asset("owner/repo", {"assets": [self.asset]}, self.info, DIGEST))
        original = self.asset | {"name": Path(self.info["jar"]).name}
        self.assertTrue(release.has_asset("owner/repo", {"assets": [original]}, self.info, DIGEST))
        with self.assertRaises(ValueError):
            release.has_asset("owner/repo", {"assets": [self.asset | {"digest": "sha256:wrong"}]}, self.info, DIGEST)
        with self.assertRaises(ValueError):
            release.has_asset("owner/repo", {"assets": [self.asset, original | {"digest": "sha256:wrong"}]}, self.info, DIGEST)

    def test_upload_normalizes_filename_without_changing_verified_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / Path(self.info["jar"]).name
            path.write_bytes(b"original verified artifact")

            def upload(*args):
                self.assertEqual(args[:4], ("gh", "release", "upload", self.info["tag"]))
                staged = Path(args[4])
                self.assertEqual(staged.name, "Pupper.Client-Fabric-9.0.0-alpha.6+mc26.2.jar")
                self.assertEqual(staged.read_bytes(), path.read_bytes())
                self.assertEqual(args[5:], ("--repo", "owner/repo"))

            with patch.object(release, "command", side_effect=upload) as command:
                release.upload_asset("owner/repo", self.info | {"jar": str(path)})
                command.assert_called_once()

    def test_tag_commit_and_annotation(self):
        with patch.object(release, "github", return_value=None):
            self.assertFalse(release.check_tag("owner/repo", self.info, SHA))
        with patch.object(release, "github", return_value={"object": {"type": "commit", "sha": SHA}}):
            self.assertTrue(release.check_tag("owner/repo", self.info, SHA))
        with patch.object(release, "github", side_effect=[{"object": {"type": "tag", "sha": "d" * 40}}, {"object": {"type": "commit", "sha": SHA}}]):
            self.assertTrue(release.check_tag("owner/repo", self.info, SHA))
        with patch.object(release, "github", return_value={"object": {"type": "commit", "sha": "e" * 40}}):
            with self.assertRaises(ValueError):
                release.check_tag("owner/repo", self.info, SHA)

    def test_draft_lookup_is_paginated(self):
        draft = {"tag_name": self.info["tag"], "draft": True}
        with patch.object(release, "github", side_effect=[None, [{"tag_name": "other"}] * 100, [draft]]) as api:
            self.assertEqual(release.find_release("owner/repo", self.info), draft)
            self.assertIn("page=2", api.call_args.args[1])

    def test_auth_errors_are_not_missing_releases(self):
        for code in (403, 500):
            with patch.object(release, "command", return_value=subprocess.CompletedProcess([], 1, "", f"HTTP {code}")):
                with self.assertRaises(RuntimeError):
                    release.github("owner/repo", "releases/tags/v1", missing=True)
        with patch.object(release, "command", return_value=subprocess.CompletedProcess([], 1, "", "HTTP 404")):
            self.assertIsNone(release.github("owner/repo", "releases/tags/v1", missing=True))

    def publication(self, existing, versions, upload_failure=False, release_sha=None):
        events = []
        draft = {"id": 1, "draft": True, "assets": [], "target_commitish": SHA, "body": "Original notes"}

        def api(repo, endpoint, data=None, **kwargs):
            if data:
                events.append((endpoint, data))
                return draft
            return draft | {"assets": [self.asset]}

        def run(*args, **kwargs):
            self.assertEqual(args, ("git", "rev-parse", "HEAD"))
            return subprocess.CompletedProcess(args, 0, SHA + "\n", "")

        def gradle(args, **kwargs):
            events.append(("modrinth", args))
            self.assertIn("-x", args)
            self.assertEqual(args[args.index("-x") + 1], "jar")
            self.assertIn("CHANGELOG_FILE", kwargs["env"])
            if upload_failure:
                raise subprocess.CalledProcessError(1, args)

        with tempfile.TemporaryDirectory() as temporary:
            notes = Path(temporary) / "notes.md"
            notes.write_text("Verified notes", encoding="utf-8")
            environment = {"GH_TOKEN": "fixture", "MODRINTH_TOKEN": "fixture", "GITHUB_REPOSITORY": "owner/repo",
                           "GITHUB_SHA": "d" * 40 if release_sha else SHA}
            if release_sha:
                environment["RELEASE_SHA"] = release_sha
            with patch.dict(os.environ, environment, clear=True), \
                 patch.multiple(release, command=run, verify=Mock(return_value=DIGEST),
                                check_tag=Mock(return_value=existing is not None), find_release=Mock(return_value=existing),
                                upload_asset=Mock(side_effect=lambda *args: events.append(("asset", args))),
                                versions=Mock(side_effect=versions), github=api, NOTES=notes), \
                 patch.object(release.subprocess, "run", side_effect=gradle):
                if upload_failure:
                    with self.assertRaises(subprocess.CalledProcessError):
                        release.publish(self.info)
                else:
                    release.publish(self.info)
        return events

    def test_new_release_order_and_same_artifact(self):
        events = self.publication(None, [[], [self.version]])
        self.assertEqual([event[0] for event in events], ["git/refs", "releases", "asset", "modrinth", "releases/1"])
        self.assertEqual(events[1][1]["target_commitish"], SHA)
        self.assertTrue(events[1][1]["draft"])
        self.assertTrue(events[-1][1]["prerelease"])
        self.assertFalse(events[-1][1]["draft"])

    def test_failed_modrinth_upload_preserves_github_draft(self):
        events = self.publication(None, [[]], upload_failure=True)
        self.assertNotIn("releases/1", [event[0] for event in events])

    def test_retry_does_not_reupload_existing_assets_or_versions(self):
        draft = {"id": 1, "draft": True, "assets": [self.asset], "body": "Original notes"}
        events = self.publication(draft, [[self.version]])
        self.assertEqual([event[0] for event in events], ["releases/1"])
        self.assertEqual(self.publication(draft | {"draft": False}, [[self.version]]), [])

    def test_repaired_workflow_releases_original_verified_commit(self):
        events = self.publication(None, [[], [self.version]], release_sha=SHA)
        self.assertEqual(events[0], ("git/refs", {"ref": f"refs/tags/{self.info['tag']}", "sha": SHA}))
        self.assertEqual(events[1][1]["target_commitish"], SHA)

    def test_missing_secret_stops_before_any_network_call(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(release, "github") as api:
            with self.assertRaisesRegex(ValueError, "MODRINTH_TOKEN"):
                release.publish(self.info)
            api.assert_not_called()


class RetryChecks(unittest.TestCase):
    def setUp(self):
        self.run = {"event": "push", "status": "completed", "path": ".github/workflows/build.yml",
                    "head_repository": {"full_name": "owner/repo"}, "head_branch": "ver/26.2", "head_sha": SHA}
        self.jobs = [{"name": "build", "conclusion": "success"}, {"name": "release", "conclusion": "failure"}]
        self.artifacts = [{"name": f"pupper-client-{SHA}", "expired": False}]

    def retry(self, run=None, jobs=None, artifacts=None, ancestor=True):
        responses = {"actions/runs/123": self.run if run is None else run,
                     "actions/runs/123/jobs?filter=latest&per_page=100": {"jobs": self.jobs if jobs is None else jobs},
                     "actions/runs/123/artifacts?per_page=100": {"artifacts": self.artifacts if artifacts is None else artifacts}}

        def command(*args):
            if args[:2] == ("git", "merge-base"):
                self.assertEqual(args[2:], ("--is-ancestor", SHA, "HEAD"))
                if not ancestor:
                    raise RuntimeError("Original commit is not an ancestor")
                return subprocess.CompletedProcess(args, 0, "", "")
            self.assertEqual(args, ("git", "show", f"{SHA}:gradle.properties"))
            return subprocess.CompletedProcess(args, 0, "\n".join(f"{key}={value}" for key, value in PROPS.items()), "")

        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "output"
            with patch.dict(os.environ, {"GITHUB_REPOSITORY": "owner/repo", "GITHUB_REF_NAME": "ver/26.2",
                                         "GITHUB_OUTPUT": str(output)}, clear=True), \
                 patch.object(release, "github", side_effect=lambda repo, endpoint: responses[endpoint]), \
                 patch.object(release, "command", side_effect=command):
                release.retry("123")
            return dict(line.split("=", 1) for line in output.read_text(encoding="utf-8").splitlines())

    def test_retry_uses_original_build_sha_and_artifact(self):
        self.assertEqual(self.retry(), {"run_id": "123", "sha": SHA, "version": "9.0.0-alpha.6+mc26.2"})

    def test_retry_rejects_untrusted_or_incomplete_runs(self):
        for update in ({"event": "pull_request"}, {"status": "in_progress"},
                       {"head_repository": {"full_name": "other/repo"}}, {"head_branch": "ver/26.3"},
                       {"path": ".github/workflows/other.yml"}, {"head_sha": "unsafe"}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                self.retry(run=self.run | update)
        with self.assertRaises(RuntimeError):
            self.retry(ancestor=False)

    def test_retry_requires_passed_build_and_unique_unexpired_artifact(self):
        with self.assertRaises(ValueError):
            self.retry(jobs=[{"name": "build", "conclusion": "failure"}])
        for artifacts in ([], [self.artifacts[0] | {"expired": True}], self.artifacts * 2):
            with self.subTest(artifacts=artifacts), self.assertRaises(ValueError):
                self.retry(artifacts=artifacts)

    def test_retry_input_is_not_executed(self):
        with patch.object(release, "github") as api, patch.object(release, "command") as command:
            with self.assertRaises(ValueError):
                release.retry("123; echo unsafe")
            api.assert_not_called()
            command.assert_not_called()


if __name__ == "__main__":
    unittest.main()
