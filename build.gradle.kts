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
                    "-XX:HeapBaseMinAddress=34g"
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
    runtimeOnly(libs.iris)
    runtimeOnly(libs.lithium)
    runtimeOnly(libs.immediatelyfast)
    runtimeOnly(libs.entityculling)
    runtimeOnly(libs.ias)
    implementation(libs.modmenu)
    implementation(libs.viafabricplus)

    // lib
    "modJij"(libs.smartboot.aio)
    "modJij"(libs.caffeine)
    "modJij"(libs.humbleui.types)
    "modJij"(libs.skija.windows)
    "modJij"(libs.junixsocket.common)
    "modJij"(libs.java.websocket)
    "modJij"(libs.mp3agic)
    "modJij"(libs.jlayer)
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

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to project.version,
                "minecraft_version" to minecraftVersion
            )
        )
    }

    doLast {
        val resourcePath = sourceSets.main.get().resources.srcDirs.first()
        val iconFile = File(resourcePath, "assets/pupper/logo.png")
        if (!iconFile.exists()) {
            throw GradleException("Pupper icon not found: ${iconFile.absolutePath}")
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
