plugins {
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.modrinth.minotaur)
}

val lwjglVersion = "3.4.1"

val modVersion = providers.gradleProperty("mod_version").get()
val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val loaderVersion = providers.gradleProperty("loader_version").get()
val fabricApiVersion = providers.gradleProperty("fabric_api_version").get()

version = "$modVersion+mc$minecraftVersion"
group = property("maven_group") as String

base {
    archivesName = property("archives_base_name") as String
}

loom {
    accessWidenerPath = file("src/main/resources/pupper.classtweaker")
    runs {
        named("client") {
            vmArgs.addAll(
                listOf(
                    "-Xms512M",
                    "-Xmx4G",
                    "-XX:HeapBaseMinAddress=34g",
                    "--enable-native-access=ALL-UNNAMED"
                )
            )
        }
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://api.modrinth.com/maven")
    maven("https://maven.lenni0451.net/everything")
    maven("https://repo.viaversion.com/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
    maven("https://maven.terraformersmc.com/")
    maven("https://maven.florianreuth.de/snapshots")
}

configurations {
    create("modJij")
    "include" {
        extendsFrom(getByName("modJij"))
    }
    "implementation" {
        extendsFrom(getByName("modJij"))
    }
}

dependencies {
    // Minecraft & Fabric base
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // Mod runtime
    implementation(libs.viafabricplus.api)
    runtimeOnly(libs.sodium)
    // Optional fast vertex writer; ordinary VertexConsumer remains the fallback without Sodium.
    compileOnly(libs.sodium)
    runtimeOnly(libs.iris)
    runtimeOnly(libs.lithium)
    runtimeOnly(libs.immediatelyfast)
    runtimeOnly(libs.entityculling)
    runtimeOnly(libs.ias)
    runtimeOnly(libs.modmenu)
    runtimeOnly(libs.placeholder.api)
    runtimeOnly(libs.viafabricplus)

    // lib
    "modJij"(libs.caffeine)
    "modJij"(libs.humbleui.types)
    "modJij"(libs.skija.windows)
    "modJij"(libs.junixsocket.common)
    "modJij"(libs.java.websocket)
    "modJij"(libs.mp3agic)
    "modJij"(libs.mp3spi)
    "modJij"(libs.jaudiotagger)
    "modJij"(libs.jlayer.google)
    "modJij"(libs.mcping)
    "modJij"(libs.reflect)

    // JNA
    implementation(libs.jna)
    implementation(libs.jna.platform)

    // LWJGL NFD
    "modJij"(libs.lwjgl.nfd)

    // LWJGL NFD Natives
    val nfdNatives = listOf(
        "natives-linux",
        "natives-macos",
        "natives-macos-arm64",
        "natives-windows"
    )
    nfdNatives.forEach { classifier ->
        "modJij"("org.lwjgl:lwjgl-nfd:$lwjglVersion:$classifier")
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("loader_version", loaderVersion)
    inputs.property("fabric_api_version", fabricApiVersion)

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to project.version,
                "minecraft_version" to minecraftVersion,
                "loader_version" to loaderVersion,
                "fabric_api_version" to fabricApiVersion
            )
        )
    }

    doLast {
        val resourcePath = sourceSets.main.get().resources.srcDirs.first()
        val iconFile = File(resourcePath, "assets/pupper/logo.png")
        if (!iconFile.exists()) {
            throw GradleException("Pupper Client icon not found: ${iconFile.absolutePath}")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

// Standalone checks have their own source set; they are not JUnit tests.
val hudVerification = sourceSets.create("hudVerification") {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath + configurations.runtimeClasspath.get()
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

val releaseType = when {
    modVersion.contains("alpha", ignoreCase = true) -> "alpha"
    modVersion.contains("beta", ignoreCase = true) -> "beta"
    else -> "release"
}

modrinth {
    token.set(providers.environmentVariable("MODRINTH_TOKEN"))
    projectId.set("pupper-client")
    versionNumber.set(project.version.toString())
    versionName.set("Pupper Client $modVersion for Minecraft $minecraftVersion")
    versionType.set(releaseType)
    uploadFile.set(tasks.named("jar"))
    gameVersions.add(minecraftVersion)
    loaders.add("fabric")
    changelog.set(providers.environmentVariable("CHANGELOG").orElse("No changelog was provided."))

    dependencies {
        required.project("fabric-api")
        required.project("viafabricplus")
    }
}
