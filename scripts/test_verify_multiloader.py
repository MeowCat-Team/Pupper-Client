"""Regression fixtures for checking independently packaged loader artifacts."""

import copy
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

import verify_multiloader as verifier


TWEAKER = """classTweaker v1 official
accessible class net/minecraft/client/gui/Hud$HeartType
accessible method net/minecraft/client/Minecraft startUseItem ()V
accessible method net/minecraft/client/Minecraft startAttack ()Z
accessible method com/mojang/blaze3d/pipeline/RenderPipeline$Builder <init> ()V
accessible method com/mojang/blaze3d/pipeline/RenderPipeline$Builder withSnippet (Lcom/mojang/blaze3d/pipeline/RenderPipeline$Snippet;)V
accessible field net/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen serverSelectionList Lnet/minecraft/client/gui/screens/multiplayer/ServerSelectionList;
accessible field com/mojang/blaze3d/vertex/PoseStack$Pose trustedNormals Z
"""
VERSION = "9.0.0-alpha.6+mc26.2"


def jar_bytes(files):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as jar:
        for name, value in files.items():
            jar.writestr(name, value)
    return output.getvalue()


def mod_class(mod_id="pupper"):
    """A minimal real Java 25 class with a RuntimeVisible @Mod annotation."""
    def utf(value):
        encoded = value.encode()
        return b"\x01" + struct.pack(">H", len(encoded)) + encoded
    constants = [
        utf("cn/pupperclient/neoforge/PupperNeoForge"), b"\x07\x00\x01",
        utf("RuntimeVisibleAnnotations"), utf("Lnet/neoforged/fml/common/Mod;"),
        utf("value"), utf(mod_id), utf("java/lang/Object"), b"\x07\x00\x07",
    ]
    annotation = struct.pack(">HHHH", 1, 4, 1, 5) + b"s" + struct.pack(">H", 6)
    return (b"\xca\xfe\xba\xbe" + struct.pack(">HHH", 0, 69, 9) + b"".join(constants)
            + struct.pack(">HHHHHHH", 0x21, 2, 8, 0, 0, 0, 1)
            + struct.pack(">HI", 3, len(annotation)) + annotation)


class ArtifactFixture:
    def __init__(self, directory):
        self.directory = Path(directory)
        self.tweaker = self.directory / "pupper.classtweaker"
        self.tweaker.write_bytes(TWEAKER.encode())
        self.fabric_path = self.directory / "fabric.jar"
        self.neo_path = self.directory / "neoforge.jar"
        self.common = {
            "cn/pupperclient/PupperClient.class": b"same common client bytecode",
            "cn/pupperclient/mixin/TestMixin.class": b"same common mixin bytecode",
            "cn/pupperclient/music/Player.class": b"same common music bytecode",
            "assets/pupper/fonts/font.ttf": b"font bytes",
            "assets/pupper/lang/zh_cn.json": b'{"name":"Pupper Client"}',
            "pupper.mixins.json": json.dumps({"package": "cn.pupperclient.mixin", "client": ["TestMixin"]}),
        }
        self.fabric = copy.copy(self.common)
        self.neo = copy.copy(self.common)
        self.fabric["cn/pupperclient/fabric/PupperFabric.class"] = b"fabric bootstrap"
        self.neo["cn/pupperclient/neoforge/PupperNeoForge.class"] = mod_class()
        self.fabric["pupper.classtweaker"] = TWEAKER
        rules = verifier.access_rules(TWEAKER)
        self.neo["META-INF/accesstransformer.cfg"] = "\n".join(
            "public " + owner + (" " + member if member else "") for _, owner, member in sorted(rules))
        self.fabric_meta = {
            "schemaVersion": 1, "id": "pupper", "version": VERSION, "environment": "client",
            "entrypoints": {"client": ["cn.pupperclient.fabric.PupperFabric"]},
            "accessWidener": "pupper.classtweaker", "mixins": ["pupper.mixins.json"], "jars": [],
        }
        self.neo_toml = ("modLoader = 'javafml'\nloaderVersion = '[1,)'\nlicense = 'MIT'\n"
                         f"[[mods]]\nmodId = 'pupper'\nversion = '{VERSION}'\n"
                         "[[mixins]]\nconfig = 'pupper.mixins.json'\n")
        self.neo_jij = {"jars": []}
        self.libraries = {}
        for artifact in verifier.REQUIRED_LIBRARIES:
            name = artifact + "-1.0.0.jar"
            files = {"library/" + artifact + ".class": b"same library bytes"}
            for class_file in verifier.REQUIRED_LIBRARY_CLASSES.get(artifact, ()):
                files[class_file] = b"required transitive runtime class"
            for prefix, suffix in verifier.REQUIRED_LIBRARY_NATIVES.get(artifact, ()):
                files[prefix + "junixsocket-native-1.0.0" + suffix] = b"required JNI native"
            if artifact == "lwjgl-nfd":
                files["org/lwjgl/util/nfd/NativeFileDialog.class"] = b"NFD Java bindings"
            self.libraries[name] = files
            self.fabric_meta["jars"].append({"file": "META-INF/jars/" + name})
            self.neo_jij["jars"].append({
                "identifier": {"group": "test", "artifact": artifact},
                "version": {"artifactVersion": "1.0.0", "range": "[1.0.0,)"},
                "path": "META-INF/jars/" + name,
            })
            self.fabric["META-INF/jars/" + name] = jar_bytes(files | {"fabric.mod.json": b"generated Fabric metadata"})
            self.neo["META-INF/jars/" + name] = jar_bytes(files | {verifier.NESTING_METADATA: b"generated NeoForge metadata"})
        for number, resource in enumerate(verifier.NFD_NATIVE_PATHS):
            native_name = f"lwjgl-nfd-1.0.0-natives-{number}.jar"
            native_data = resource.encode()
            self.fabric_meta["jars"].append({"file": "META-INF/jars/" + native_name})
            native_files = {resource: native_data, "META-INF/" + resource + ".sha1": b"checksum"}
            self.fabric["META-INF/jars/" + native_name] = jar_bytes(native_files)
            self.neo.update(native_files)

    def save(self):
        self.fabric["fabric.mod.json"] = json.dumps(self.fabric_meta)
        self.neo["META-INF/neoforge.mods.toml"] = self.neo_toml
        self.neo["META-INF/jarjar/metadata.json"] = json.dumps(self.neo_jij)
        self.fabric_path.write_bytes(jar_bytes(self.fabric))
        self.neo_path.write_bytes(jar_bytes(self.neo))

    def verify(self):
        self.save()
        return verifier.verify(self.fabric_path, self.neo_path, self.tweaker, version=VERSION)


class ParityChecks(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.fixture = ArtifactFixture(self.directory.name)

    def test_same_common_code_with_different_bootstraps_and_native_packaging(self):
        result = self.fixture.verify()
        self.assertEqual(3, result["shared_classes"])
        self.assertEqual(2, result["shared_assets"])
        self.assertEqual(7, result["access_rules"])
        self.assertEqual(15, result["shared_libraries"])
        self.assertEqual(8, result["native_resources"])

    def test_shared_class_byte_difference_fails(self):
        self.fixture.neo["cn/pupperclient/music/Player.class"] = b"diverged music implementation"
        with self.assertRaisesRegex(verifier.VerificationError, "shared classes.*different bytes"):
            self.fixture.verify()

    def test_matching_smoke_classes_and_config_must_not_be_released(self):
        for name in ("cn/pupperclient/smoke/StartupSmoke.class", "pupper-smoke.mixins.json"):
            with self.subTest(entry=name):
                self.fixture.fabric[name] = self.fixture.neo[name] = b"same development fixture"
                with self.assertRaisesRegex(verifier.VerificationError, "development smoke entries"):
                    self.fixture.verify()
                del self.fixture.fabric[name]
                del self.fixture.neo[name]

    def test_matching_smoke_classes_in_nested_libraries_must_not_be_released(self):
        name = "caffeine-1.0.0.jar"
        files = self.fixture.libraries[name] | {"cn/pupperclient/smoke/StartupSmoke.class": b"development fixture"}
        self.fixture.fabric["META-INF/jars/" + name] = jar_bytes(files)
        self.fixture.neo["META-INF/jars/" + name] = jar_bytes(files)
        with self.assertRaisesRegex(verifier.VerificationError, "library caffeine.*development smoke entries"):
            self.fixture.verify()

    def test_smoke_mod_and_mixin_declarations_must_not_be_released(self):
        self.fixture.neo_toml += "\n[[mods]]\nmodId = 'pupper_smoke'\nversion = '1'\n"
        with self.assertRaisesRegex(verifier.VerificationError, "development smoke mod declared"):
            self.fixture.verify()
        self.fixture.neo_toml = self.fixture.neo_toml.split("\n[[mods]]\nmodId = 'pupper_smoke'", 1)[0]
        self.fixture.fabric_meta["mixins"].append("pupper-smoke.mixins.json")
        with self.assertRaisesRegex(verifier.VerificationError, "development smoke mixins declared"):
            self.fixture.verify()

    def test_shared_class_missing_fails(self):
        del self.fixture.neo["cn/pupperclient/music/Player.class"]
        with self.assertRaisesRegex(verifier.VerificationError, "shared classes.*inventories differ"):
            self.fixture.verify()

    def test_shared_asset_difference_fails(self):
        self.fixture.neo["assets/pupper/fonts/font.ttf"] = b"different font"
        with self.assertRaisesRegex(verifier.VerificationError, "shared assets.*different bytes"):
            self.fixture.verify()

    def test_unregistered_neoforge_common_mixins_fail(self):
        self.fixture.neo_toml = self.fixture.neo_toml.replace("[[mixins]]\nconfig = 'pupper.mixins.json'\n", "")
        with self.assertRaisesRegex(verifier.VerificationError, "common mixins not registered"):
            self.fixture.verify()

    def test_neoforge_missing_at_rule_fails(self):
        self.fixture.neo["META-INF/accesstransformer.cfg"] = "\n".join(
            self.fixture.neo["META-INF/accesstransformer.cfg"].splitlines()[1:])
        with self.assertRaisesRegex(verifier.VerificationError, "access rules differ.*missing"):
            self.fixture.verify()

    def test_neoforge_overbroad_access_rule_fails(self):
        self.fixture.neo["META-INF/accesstransformer.cfg"] += "\npublic net.minecraft.client.Minecraft otherField"
        with self.assertRaisesRegex(verifier.VerificationError, "access rules differ.*extra"):
            self.fixture.verify()

    def test_fabric_classtweaker_must_match_the_common_source(self):
        self.fixture.fabric["pupper.classtweaker"] = TWEAKER.replace("trustedNormals", "wrongField")
        with self.assertRaisesRegex(verifier.VerificationError, "classTweaker differs"):
            self.fixture.verify()

    def test_fabric_client_entrypoint_must_exist(self):
        self.fixture.fabric_meta["entrypoints"]["client"] = ["cn.pupperclient.fabric.Missing"]
        with self.assertRaisesRegex(verifier.VerificationError, "client entrypoint.*missing"):
            self.fixture.verify()

    def test_neoforge_annotation_must_use_the_real_mod_id(self):
        self.fixture.neo["cn/pupperclient/neoforge/PupperNeoForge.class"] = mod_class("wrong_mod")
        with self.assertRaisesRegex(verifier.VerificationError, "missing real @Mod"):
            self.fixture.verify()

    def test_metadata_versions_must_match(self):
        self.fixture.neo_toml = self.fixture.neo_toml.replace(VERSION, "9.0.0+mc26.2")
        with self.assertRaisesRegex(verifier.VerificationError, "different versions"):
            self.fixture.verify()

    def test_missing_packaged_library_fails(self):
        path = "META-INF/jars/mp3agic-1.0.0.jar"
        del self.fixture.neo[path]
        self.fixture.neo_jij["jars"] = [item for item in self.fixture.neo_jij["jars"] if item["path"] != path]
        with self.assertRaisesRegex(verifier.VerificationError, "missing packaged library mp3agic"):
            self.fixture.verify()

    def test_transitive_runtime_classes_required(self):
        for artifact, classes in verifier.REQUIRED_LIBRARY_CLASSES.items():
            with self.subTest(artifact=artifact):
                name = artifact + "-1.0.0.jar"
                original_left = self.fixture.fabric["META-INF/jars/" + name]
                original_right = self.fixture.neo["META-INF/jars/" + name]
                files = copy.copy(self.fixture.libraries[name])
                del files[classes[0]]
                self.fixture.fabric["META-INF/jars/" + name] = jar_bytes(files)
                self.fixture.neo["META-INF/jars/" + name] = jar_bytes(files)
                with self.assertRaisesRegex(verifier.VerificationError, "missing required runtime class"):
                    self.fixture.verify()
                self.fixture.fabric["META-INF/jars/" + name] = original_left
                self.fixture.neo["META-INF/jars/" + name] = original_right

    def test_matching_missing_junixsocket_native_must_fail(self):
        name = "junixsocket-native-common-1.0.0.jar"
        files = copy.copy(self.fixture.libraries[name])
        prefix, _ = verifier.REQUIRED_LIBRARY_NATIVES["junixsocket-native-common"][0]
        files = {entry: data for entry, data in files.items() if not entry.startswith(prefix)}
        self.fixture.fabric["META-INF/jars/" + name] = jar_bytes(files)
        self.fixture.neo["META-INF/jars/" + name] = jar_bytes(files)
        with self.assertRaisesRegex(verifier.VerificationError, "missing required native lib/amd64-Windows10"):
            self.fixture.verify()

    def test_unreferenced_nested_jar_fails(self):
        self.fixture.neo["META-INF/jars/unreferenced.jar"] = jar_bytes({"data": b"orphan"})
        with self.assertRaisesRegex(verifier.VerificationError, "nested JARs and metadata disagree"):
            self.fixture.verify()

    def test_duplicate_neoforge_jarjar_identifier_fails(self):
        first = copy.deepcopy(self.fixture.neo_jij["jars"][0])
        first["path"] = "META-INF/jars/second-caffeine.jar"
        self.fixture.neo_jij["jars"].append(first)
        self.fixture.neo[first["path"]] = self.fixture.neo["META-INF/jars/caffeine-1.0.0.jar"]
        with self.assertRaisesRegex(verifier.VerificationError, "duplicate JarJar library identifiers"):
            self.fixture.verify()

    def test_loader_added_nesting_metadata_is_allowed_but_library_code_must_match(self):
        name = "caffeine-1.0.0.jar"
        files = self.fixture.libraries[name] | {verifier.NESTING_METADATA: b"loader metadata", "library/caffeine.class": b"other code"}
        self.fixture.neo["META-INF/jars/" + name] = jar_bytes(files)
        with self.assertRaisesRegex(verifier.VerificationError, "library caffeine.*different bytes"):
            self.fixture.verify()

    def test_nfd_java_bindings_required(self):
        name = "lwjgl-nfd-1.0.0.jar"
        files = copy.copy(self.fixture.libraries[name])
        del files["org/lwjgl/util/nfd/NativeFileDialog.class"]
        self.fixture.neo["META-INF/jars/" + name] = jar_bytes(files)
        with self.assertRaisesRegex(verifier.VerificationError, "missing NFD Java bindings"):
            self.fixture.verify()

    def test_nfd_missing_native_or_checksum_fails(self):
        for resource in (verifier.NFD_NATIVE_PATHS[0], "META-INF/" + verifier.NFD_NATIVE_PATHS[0] + ".sha1"):
            with self.subTest(resource=resource):
                original = self.fixture.neo.pop(resource)
                with self.assertRaises(verifier.VerificationError):
                    self.fixture.verify()
                self.fixture.neo[resource] = original

    def test_sources_compare_common_java_and_assets_but_allow_platform_files(self):
        common = {"cn/pupperclient/PupperClient.java": b"shared source", "assets/pupper/fonts/font.ttf": b"font",
                  "pupper.classtweaker": TWEAKER, "architectury.common.json": b'{"accessWidener":"pupper.classtweaker"}'}
        fabric = self.fixture.directory / "fabric-sources.jar"
        neoforge = self.fixture.directory / "neoforge-sources.jar"
        fabric.write_bytes(jar_bytes(common | {"cn/pupperclient/fabric/Bootstrap.java": b"fabric"}))
        neoforge.write_bytes(jar_bytes(common | {"cn/pupperclient/neoforge/Bootstrap.java": b"neo"}))
        self.assertEqual(4, verifier.verify_sources(fabric, neoforge)["source_entries"])
        neoforge.write_bytes(jar_bytes(common | {"cn/pupperclient/PupperClient.java": b"other source"}))
        with self.assertRaisesRegex(verifier.VerificationError, "shared sources/resources.*different bytes"):
            verifier.verify_sources(fabric, neoforge)

    def test_sources_root_resource_difference_fails(self):
        fabric = self.fixture.directory / "fabric-sources.jar"
        neoforge = self.fixture.directory / "neoforge-sources.jar"
        fabric.write_bytes(jar_bytes({"pupper.classtweaker": TWEAKER}))
        neoforge.write_bytes(jar_bytes({"pupper.classtweaker": TWEAKER.replace("trustedNormals", "wrongField")}))
        with self.assertRaisesRegex(verifier.VerificationError, "shared sources/resources.*different bytes"):
            verifier.verify_sources(fabric, neoforge)

    def test_duplicate_zip_entry_fails(self):
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w") as jar:
            jar.writestr("duplicate", b"a")
            with self.assertWarns(UserWarning):
                jar.writestr("duplicate", b"b")
        with self.assertRaisesRegex(verifier.VerificationError, "duplicate ZIP entries"):
            verifier.entries(output.getvalue(), "fixture")


if __name__ == "__main__":
    unittest.main()
