// Both loaders package the same Java libraries through Loom's native jar-in-jar support.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val modJij = configurations.create("modJij")
configurations.named("implementation") { extendsFrom(modJij) }
if (project.name != "common") {
    configurations.named("include") { extendsFrom(modJij) }
}

dependencies {
    listOf("caffeine", "humbleui-types", "skija-windows", "skija-shared", "junixsocket-common", "junixsocket-native-common",
        "java-websocket", "mp3agic", "mp3spi", "tritonus-share", "jaudiotagger", "jlayer-google",
        "mcping", "reflect", "lwjgl-nfd").forEach {
        add("modJij", catalog.findLibrary(it).get())
    }
    // Minecraft 26.2 supplies JNA 5.17.0; do not shadow its OSHI-required runtime API.
    add("compileOnly", catalog.findLibrary("jna").get())
    add("compileOnly", catalog.findLibrary("jna-platform").get())
}

val nfdNatives = configurations.create("nfdNatives")
val lwjglVersion = catalog.findVersion("lwjgl").get().requiredVersion
dependencies {
    listOf("natives-linux", "natives-macos", "natives-macos-arm64", "natives-windows").forEach {
        add("nfdNatives", "org.lwjgl:lwjgl-nfd:$lwjglVersion:$it")
    }
}
configurations.named("runtimeClasspath") { extendsFrom(nfdNatives) }
if (project.name == "fabric") {
    configurations.named("include") { extendsFrom(nfdNatives) }
}
