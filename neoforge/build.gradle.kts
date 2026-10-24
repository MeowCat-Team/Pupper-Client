import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.jvm.tasks.Jar

plugins {
    `java-library`
    alias(libs.plugins.architectury.plugin)
    alias(libs.plugins.architectury.loom)
    alias(libs.plugins.shadow)
    alias(libs.plugins.modrinth.minotaur)
}

val commonProject = project(":common")
val minecraftVersion = rootProject.property("minecraft_version") as String
val modVersion = rootProject.property("mod_version") as String

base { archivesName.set("${rootProject.property("archives_base_name")}-NeoForge") }
architectury {
    platformSetupLoomIde()
    neoForge()
}

val jarTask = tasks.named<Jar>("jar") { archiveClassifier.set("raw") }

loom {
    accessWidenerPath.set(commonProject.file("src/main/resources/pupper.classtweaker"))
    neoForge {
        convertAccessWideners(tasks.named<Jar>("shadowJar"), "pupper.classtweaker")
    }
    runs {
        named("client") {
            runDir = "../run/neoforge"
            vmArgs("-Xms512M", "-Xmx4G", "-XX:HeapBaseMinAddress=34g", "--enable-native-access=ALL-UNNAMED")
        }
    }
}

val commonConfiguration = configurations.create("common")
val shadowCommonConfiguration = configurations.create("shadowCommon")
configurations.named("compileClasspath") { extendsFrom(commonConfiguration) }
configurations.named("runtimeClasspath") { extendsFrom(commonConfiguration) }
configurations.named("developmentNeoForge") { extendsFrom(commonConfiguration) }

apply(from = rootProject.file("gradle/libraries.gradle.kts"))

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    neoForge("net.neoforged:neoforge:${rootProject.property("neoforge_version")}")
    implementation(libs.architectury.neoforge)
    runtimeOnly(libs.sodium.neoforge)
    runtimeOnly(libs.iris.neoforge)
    runtimeOnly(libs.lithium.neoforge)
    runtimeOnly(libs.immediatelyfast.neoforge)
    runtimeOnly(libs.entityculling.neoforge)
    runtimeOnly(libs.ias.neoforge)
    add(commonConfiguration.name, project(":common")) { isTransitive = false }
    add(shadowCommonConfiguration.name, project(":common", "transformProductionNeoForge")) { isTransitive = false }
}

tasks.processResources {
    val metadata = mapOf(
        "version" to project.version,
        "minecraft_version" to minecraftVersion,
        "neoforge_version" to rootProject.property("neoforge_version"),
        "architectury_api_version" to rootProject.property("architectury_api_version"),
    )
    inputs.properties(metadata)
    filesMatching("META-INF/neoforge.mods.toml") { expand(metadata) }
}

val nfdNatives = configurations.getByName("nfdNatives")
val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
    dependsOn(jarTask)
    // The Shadow plugin copies the main source set into the jar; this build shades the raw jar
    // instead, so drop that implicit source. Gradle exposes the jar's copy spec only to
    // subclasses, and the root spec also holds an empty wrapper, so resolve the main spec itself.
    val mainSpec = AbstractCopyTask::class.java.getDeclaredMethod("getMainSpec")
        .apply { isAccessible = true }.invoke(this)
    DefaultCopySpec::class.java.cast(mainSpec).sourcePaths.clear()
    from(jarTask.map { zipTree(it.archiveFile.get().asFile) })
    configurations.set(setOf(shadowCommonConfiguration))
    archiveClassifier.set("")
    exclude("fabric.mod.json", "architectury.common.json")
    // JarJar identifiers omit classifiers; expose all NFD native resources directly.
    from(provider { nfdNatives.map { zipTree(it) } }) {
        exclude("**/*.class")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    from(rootProject.file("LICENSE"))
    from(rootProject.file("NOTICE"))
    from(rootProject.file("THIRD-PARTY.md"))
}
tasks.named("assemble") { dependsOn(shadowJarTask) }

tasks.named<Jar>("sourcesJar") {
    val commonSourcesJar = commonProject.tasks.named<Jar>("sourcesJar")
    dependsOn(commonSourcesJar)
    from(commonSourcesJar.map { zipTree(it.archiveFile.get().asFile) })
}

// Release automation receives only the final distributable jars, never raw or common jars.
val releaseType = when {
    modVersion.lowercase().contains("beta") -> "beta"
    modVersion.contains('-') -> "alpha"
    else -> "release"
}
val loader = project.name
val releaseArtifact = providers.environmentVariable("RELEASE_ARTIFACT_FILE")

modrinth {
    token.set(providers.environmentVariable("MODRINTH_TOKEN"))
    projectId.set(rootProject.property("modrinth_project_id") as String)
    versionNumber.set("${project.version}-$loader")
    versionName.set("Pupper Client $modVersion for Minecraft $minecraftVersion ($loader)")
    versionType.set(releaseType)
    if (releaseArtifact.isPresent) {
        uploadFile.set(releaseArtifact.map { file(it) })
    } else {
        uploadFile.set(shadowJarTask)
    }
    // A CI artifact is already built and checked; do not wire archive tasks as inputs.
    autoAddDependsOn.set(!releaseArtifact.isPresent)
    gameVersions.set(listOf(minecraftVersion))
    loaders.set(listOf(loader))
    changelog.set(
        providers.environmentVariable("CHANGELOG_FILE").map { file(it).readText(Charsets.UTF_8) }
            .orElse(providers.environmentVariable("CHANGELOG"))
            .orElse("No changelog was provided.")
    )
    required.project("architectury-api")
    if (loader == "fabric") {
        required.project("fabric-api")
        required.project("viafabricplus")
    }
}

if (releaseArtifact.isPresent) {
    tasks.named("modrinth") {
        // Minotaur 2.10 adds assemble unconditionally when registering this task.
        // Remove that default only for a downloaded artifact, retaining other dependencies.
        setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name == "assemble" })
    }
}

// This creates a separate test mod and run; no smoke classes/resources are part of main, jar or shadowJar.
val smoke = sourceSets.create("smokeVerification") {
    java.srcDir(rootProject.file("common/src/smokeVerification/java"))
    resources.srcDir(rootProject.file("common/src/smokeVerification/resources"))
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
}
configurations.named("smokeVerificationImplementation") { extendsFrom(configurations.getByName("implementation")) }
configurations.named("smokeVerificationRuntimeOnly") { extendsFrom(configurations.getByName("runtimeOnly")) }
val smokeRequested = gradle.startParameter.taskNames.any { it.endsWith("runSmokeClient") }

loom {
    mods {
        // NeoForge already registers main and Architectury transforms common-dev.jar into a
        // separate development mod. Grouping common's original output here would load the same
        // packages twice and fail Java module resolution.
        // Ordinary development runs must not discover the test mod after it was compiled.
        if (smokeRequested) {
            create("pupper_smoke") { sourceSet(smoke) }
        }
    }
    runs {
        create("smokeClient") {
            inherit(getByName("client"))
            client()
            source(smoke)
            runDir = "build/smoke"
            vmArgs("-Dpupper.smoke=true", "-Dpupper.smoke.loader=$loader",
                "-Dpupper.smoke.expectedVersion=${project.version}")
            programArgs("--username", "PupperSmoke")
        }
    }
}

tasks.named("runSmokeClient") {
    dependsOn(smoke.classesTaskName)
    doFirst {
        val isolatedDirectory = layout.buildDirectory.dir("smoke").get().asFile
        isolatedDirectory.mkdirs()
        // A previous successful run must never satisfy a failed new attempt.
        File(isolatedDirectory, "pupper-smoke-result.json").delete()
    }
}

apply(from = rootProject.file("gradle/mixin-verification.gradle.kts"))
apply(from = rootProject.file("gradle/dev-runtime.gradle.kts"))