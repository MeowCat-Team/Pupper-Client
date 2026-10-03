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
        self.asset = {"name": Path(self.info["jar"]).name, "digest": f"sha256:{DIGEST['sha256']}"}

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
        with self.assertRaises(ValueError):
            release.has_asset("owner/repo", {"assets": [self.asset | {"digest": "sha256:wrong"}]}, self.info, DIGEST)

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

    def publication(self, existing, versions, upload_failure=False):
        events = []
        draft = {"id": 1, "draft": True, "assets": [], "target_commitish": SHA, "body": "Original notes"}

        def api(repo, endpoint, data=None, **kwargs):
            if data:
                events.append((endpoint, data))
                return draft
            return draft | {"assets": [self.asset]}

        def run(*args, **kwargs):
            if args[0] == "git":
                return subprocess.CompletedProcess(args, 0, SHA + "\n", "")
            events.append(("asset", args))
            return subprocess.CompletedProcess(args, 0, "", "")

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
            with patch.dict(os.environ, {"GH_TOKEN": "fixture", "MODRINTH_TOKEN": "fixture", "GITHUB_REPOSITORY": "owner/repo", "GITHUB_SHA": SHA}, clear=True), \
                 patch.multiple(release, command=run, verify=Mock(return_value=DIGEST),
                                check_tag=Mock(return_value=existing is not None), find_release=Mock(return_value=existing),
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

    def test_missing_secret_stops_before_any_network_call(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(release, "github") as api:
            with self.assertRaisesRegex(ValueError, "MODRINTH_TOKEN"):
                release.publish(self.info)
            api.assert_not_called()


if __name__ == "__main__":
    unittest.main()
