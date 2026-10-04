import dev.architectury.plugin.TransformingTask
import dev.architectury.transformer.transformers.base.TinyRemapperTransformer
import dev.architectury.transformer.shadowed.impl.dev.architectury.tinyremapper.IMappingProvider

plugins {
    `java-library`
    alias(libs.plugins.architectury.plugin)
    alias(libs.plugins.architectury.loom)
}

base { archivesName.set("${rootProject.property("archives_base_name")}-Common") }
architectury { common(listOf("fabric", "neoforge")) }
loom { accessWidenerPath.set(file("src/main/resources/pupper.classtweaker")) }
apply(from = rootProject.file("gradle/libraries.gradle.kts"))

// Loom adds Fabric Loader's Mixin directly; select the common annotation API only
// for compilation. Each loader keeps its own unmodified Mixin runtime.
configurations.named("compileClasspath") {
    resolutionStrategy.force("net.fabricmc:sponge-mixin:${libs.versions.sponge.mixin.common.get()}")
}

// NeoForge's environment conversion also normalizes generated parameter/local-variable
// metadata. Run the same official remapper with no name mappings for Fabric, retaining
// debug information and enabling strict byte-for-byte comparison of the shared classes.
tasks.named<TransformingTask>("transformProductionFabric") {
    inputs.property("commonParameterMetadataNormalization", "v1")
    transformers.add(object : TinyRemapperTransformer {
        override fun collectMappings(): List<IMappingProvider> = listOf(IMappingProvider { _ -> })
    })
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft_version")}")
    compileOnly(libs.architectury.common)
    // Minecraft and the common API carry Fabric's side annotations; no loader implementation is used here.
    compileOnly("net.fabricmc:fabric-loader:${rootProject.property("loader_version")}")
    // 0.17.4 compiles Redirect.at/slice as arrays, while NeoForge's MixinExtras
    // expects single annotation nodes. Keep common bytecode compatible with both.
    compileOnly(libs.sponge.mixin.common) {
        because("Shared mixin annotations must use the oldest supported loader API")
    }
    compileOnly(libs.sodium)
    // The standalone checks load platform API implementations without starting a game.
    runtimeOnly(libs.architectury.fabric)
    runtimeOnly(libs.sodium)
    runtimeOnly(libs.iris)
}

// Standalone checks have their own source set; they are not JUnit tests.
val hudVerification = sourceSets.create("hudVerification") {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + sourceSets.main.get().output + configurations.runtimeClasspath.get()
}

// Dependency-free design checks run on the same Java/toolchain and color library as the client.
val verifyHudDesign = tasks.register<JavaExec>("verifyHudDesign") {
    group = "verification"
    description = "Checks HUD color contrast and frame-rate-independent motion."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.hud.HUDDesignChecks")
}
tasks.check { dependsOn(verifyHudDesign) }

tasks.register<JavaExec>("previewHudTheme") {
    group = "verification"
    description = "Renders the HUD theme specimen with the bundled Skia fonts (Windows)."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.hud.HUDThemePreview")
    args(layout.buildDirectory.file("reports/hud/theme-preview.png").get().asFile.absolutePath)
}

tasks.register<JavaExec>("previewMaterialTheme") {
    group = "verification"
    description = "Renders light and dark Material glass surfaces and checks their compositing (Windows)."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.hud.MaterialThemePreview")
    args(layout.buildDirectory.file("reports/ui/material-preview.png").get().asFile.absolutePath)
}

tasks.register<JavaExec>("verifyGlassGpu") {
    group = "verification"
    description = "Checks Blaze3D glass brightness, clipping and scene coordinates on a hidden GPU device."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.hud.GlassGpuChecks")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir(layout.buildDirectory.dir("verification"))
    doFirst { workingDir.mkdirs() }
}

val verifySkiaInterop = tasks.register<JavaExec>("verifySkiaInterop") {
    group = "verification"
    description = "Checks UI presentation order and absence of backend-specific GPU access."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.hud.SkiaInteropChecks")
}
tasks.check { dependsOn(verifySkiaInterop) }

tasks.register<JavaExec>("verifySkiaInteropGpu") {
    group = "verification"
    description = "Checks Blaze3D UI geometry, compositing, clips, glyph/image caching and ownership on a hidden GPU device."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.hud.SkiaInteropGpuChecks")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir(layout.buildDirectory.dir("verification"))
    doFirst { workingDir.mkdirs() }
}

val verifyItemRendering = tasks.register<JavaExec>("verifyItemRendering") {
    group = "verification"
    description = "Checks item shadow budgets and Minecraft render hook compatibility."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.hud.ItemRenderChecks")
}
tasks.check { dependsOn(verifyItemRendering) }

val verifyStartupRendering = tasks.register<JavaExec>("verifyStartupRendering") {
    group = "verification"
    description = "Checks early Globals initialization, atlas deferral and Minecraft render hook compatibility."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.hud.StartupRenderingChecks")
    workingDir(layout.buildDirectory.dir("verification"))
    doFirst { workingDir.mkdirs() }
}
tasks.check { dependsOn(verifyStartupRendering) }

tasks.register<JavaExec>("verifyStartupGpu") {
    group = "verification"
    description = "Checks startup Globals publication and GPU contents in a hidden OpenGL context."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.hud.StartupGpuChecks")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir(layout.buildDirectory.dir("verification"))
    doFirst { workingDir.mkdirs() }
}

val verifyItemVertices = tasks.register<JavaExec>("verifyItemVertices") {
    group = "verification"
    description = "Compares packed item vertices byte-for-byte with Sodium's encoder."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.hud.ItemVertexChecks")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir(layout.buildDirectory.dir("verification"))
    doFirst { workingDir.mkdirs() }
}
tasks.check { dependsOn(verifyItemVertices) }

val verifyMusicService = tasks.register<JavaExec>("verifyMusicService") {
    group = "verification"
    description = "Checks NetEase/Audius access, metadata migration, downloads, playback and isolated favorites using local HTTP fixtures."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.music.MusicServiceChecks")
}
tasks.check { dependsOn(verifyMusicService) }

val verifyChatCompletion = tasks.register<JavaExec>("verifyChatCompletion") {
    group = "verification"
    description = "Checks MiniMessage tag completion, replacement ranges and Minecraft chat hook compatibility."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.chat.MiniMessageCompletionChecks")
}
tasks.check { dependsOn(verifyChatCompletion) }

val verifyServerTextPrivacy = tasks.register<JavaExec>("verifyServerTextPrivacy") {
    group = "verification"
    description = "Checks remote mod text probes, local/vanilla text preservation and Minecraft network/sign hooks."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.network.ServerTextPrivacyChecks")
}
tasks.check { dependsOn(verifyServerTextPrivacy) }

tasks.register<JavaExec>("verifyWindowsSmtc") {
    group = "verification"
    description = "Checks real Windows WinRT SMTC metadata, timeline and FFM callbacks with a hidden window."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.management.music.media.WindowsSmtcChecks")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("previewMusicPlayer") {
    group = "verification"
    description = "Renders English/light and Chinese/dark player layouts with the production MD3 painters."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.music.MusicPlayerPreview")
    args(layout.buildDirectory.dir("reports/music").get().asFile.absolutePath)
}

tasks.register<JavaExec>("benchmarkItemVertices") {
    group = "verification"
    description = "Measures dropped-item encoding and native staging CPU cost, excluding game/GPU work."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.hud.ItemVertexChecks")
    args("--benchmark")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir(layout.buildDirectory.dir("verification"))
    doFirst { workingDir.mkdirs() }
}

tasks.register<JavaExec>("previewBlazeUi") {
    group = "verification"
    description = "Renders production Material controls through the Blaze3D GPU geometry path."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.hud.BlazeUiPreview")
    args(layout.buildDirectory.file("reports/ui/blaze-preview.png").get().asFile.absolutePath)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir(layout.buildDirectory.dir("verification"))
    doFirst { workingDir.mkdirs() }
}

tasks.register<JavaExec>("benchmarkGlassGpu") {
    group = "verification"
    description = "Measures representative Blaze3D UI and glass GPU/CPU cost in a hidden window."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.hud.GlassGpuBenchmark")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir(layout.buildDirectory.dir("verification"))
    doFirst { workingDir.mkdirs() }
}


val verifyProtocol = tasks.register<JavaExec>("verifyProtocol") {
    group = "verification"
    description = "Checks the shared protocol snapshot and native fallback without connecting to a server."
    dependsOn(hudVerification.classesTaskName)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.platform.ProtocolAccessChecks")
}
tasks.check { dependsOn(verifyProtocol) }

val mixinCompileManifest = layout.buildDirectory.file("verification/mixin-compile-classpath.txt")
val exportMixinCompileClasspath = tasks.register("exportMixinCompileClasspath") {
    group = "verification"
    inputs.files(configurations.compileClasspath)
    outputs.file(mixinCompileManifest)
    doLast {
        val manifest = mixinCompileManifest.get().asFile
        manifest.parentFile.mkdirs()
        manifest.writeText(configurations.compileClasspath.get().asPath + System.lineSeparator(), Charsets.UTF_8)
    }
}

val verifyMixinCompatibility = tasks.register<JavaExec>("verifyMixinCompatibility") {
    group = "verification"
    description = "Checks compiled common annotations against both loaders' actual Mixin and MixinExtras runtimes."
    dependsOn(hudVerification.classesTaskName, exportMixinCompileClasspath)
    dependsOn(":fabric:exportMixinRuntimeClasspath", ":neoforge:exportMixinRuntimeClasspath")
    val fabricRuntimeManifest = rootProject.file("fabric/build/verification/mixin-runtime-classpath.txt")
    val neoForgeRuntimeManifest = rootProject.file("neoforge/build/verification/mixin-runtime-classpath.txt")
    inputs.files(mixinCompileManifest, fabricRuntimeManifest, neoForgeRuntimeManifest)
    classpath = hudVerification.runtimeClasspath
    mainClass.set("cn.pupperclient.platform.MixinCompatibilityChecks")
    doFirst {
        args("--classes", sourceSets.main.get().output.classesDirs.asPath,
            "--fixture", hudVerification.output.classesDirs.asPath,
            "--compile-file", mixinCompileManifest.get().asFile.absolutePath,
            "--fabric-file", fabricRuntimeManifest.absolutePath,
            "--neoforge-file", neoForgeRuntimeManifest.absolutePath)
    }
}
tasks.check { dependsOn(verifyMixinCompatibility) }
