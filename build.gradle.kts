plugins {
    base
    alias(libs.plugins.architectury.plugin)
    alias(libs.plugins.architectury.loom) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.modrinth.minotaur) apply false
}

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
architectury { minecraft = minecraftVersion }
allprojects {
    group = rootProject.property("maven_group") as String
    version = "${rootProject.property("mod_version")}+mc$minecraftVersion"
    repositories {
        mavenCentral()
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.fabricmc.net/")
        maven("https://jitpack.io")
        maven("https://api.modrinth.com/maven")
        maven("https://maven.lenni0451.net/everything")
        maven("https://repo.viaversion.com/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
        maven("https://maven.terraformersmc.com/")
        maven("https://maven.florianreuth.de/snapshots")
    }
}

subprojects {
    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
            withSourcesJar()
        }
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(25)
        options.encoding = "UTF-8"
        // Keep complete parameter metadata so both Architectury production transforms agree.
        options.compilerArgs.add("-parameters")
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

// Release automation receives only the final distributable jars, never raw or common jars.
val stageReleaseJars = tasks.register<Copy>("stageReleaseJars") {
    group = "build"
    dependsOn(":fabric:shadowJar", ":neoforge:shadowJar")
    from(project(":fabric").layout.buildDirectory.dir("libs")) {
        include("${rootProject.property("archives_base_name")}-Fabric-${project.version}.jar")
    }
    from(project(":neoforge").layout.buildDirectory.dir("libs")) {
        include("${rootProject.property("archives_base_name")}-NeoForge-${project.version}.jar")
    }
    into(layout.buildDirectory.dir("libs"))
}

val verifyMultiloader = tasks.register<Exec>("verifyMultiloader") {
    group = "verification"
    description = "Checks byte-identical common classes/assets, loader metadata, permissions and native libraries."
    dependsOn(stageReleaseJars, ":fabric:sourcesJar", ":neoforge:sourcesJar")
    commandLine(providers.gradleProperty("python_executable").orElse("python").get(), "scripts/verify_multiloader.py",
        "--fabric-sources", project(":fabric").layout.buildDirectory.file("libs/${rootProject.property("archives_base_name")}-Fabric-${project.version}-sources.jar").get().asFile.absolutePath,
        "--neoforge-sources", project(":neoforge").layout.buildDirectory.file("libs/${rootProject.property("archives_base_name")}-NeoForge-${project.version}-sources.jar").get().asFile.absolutePath)
}

tasks.assemble { dependsOn(stageReleaseJars) }
tasks.check { dependsOn(":common:check", ":fabric:check", ":neoforge:check", verifyMultiloader) }

tasks.register("runClient") {
    group = "fabric"
    description = "Compatibility alias for the Fabric development client; NeoForge uses :neoforge:runClient."
    dependsOn(":fabric:runClient")
}
