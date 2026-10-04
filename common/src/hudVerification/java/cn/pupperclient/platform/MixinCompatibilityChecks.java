package cn.pupperclient.platform;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Detects cross-loader annotation format regressions without launching Minecraft. */
public final class MixinCompatibilityChecks {
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String MIXIN_API = "org/spongepowered/asm/mixin/injection/Redirect.class";
    private static final String FACTORY = "com/llamalad7/mixinextras/wrapper/factory/FactoryRedirectWrapperMixinTransformer.class";
    private static final String HUD = "net/minecraft/client/gui/Hud";
    private static final String HUD_EXTRACT = "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V";

    private MixinCompatibilityChecks() { }

    public static void main(String[] arguments) throws Exception {
        Map<String, String> options = new HashMap<>();
        require(arguments.length == 10, "Expected classes, fixture and three dependency classpaths");
        for (int i = 0; i < arguments.length; i += 2) options.put(arguments[i], arguments[i + 1]);
        List<Path> compile = manifest(options, "compile");
        try (URLClassLoader api = loader(compile, false)) {
            require(returnType(api, "at").equals("org.spongepowered.asm.mixin.injection.At"),
                "Common Redirect.at must compile as a single annotation, not an array");
            require(returnType(api, "slice").equals("org.spongepowered.asm.mixin.injection.Slice"),
                "Common Redirect.slice must compile as a single annotation, not an array");
        }

        List<byte[]> classes = new ArrayList<>();
        for (Path directory : paths(options.get("--classes"))) {
            try (var files = Files.walk(directory.resolve("cn/pupperclient/mixin"))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                    classes.add(Files.readAllBytes(file));
                }
            }
        }
        int redirects = 0;
        for (byte[] bytes : classes) redirects += checkScalarAnnotations(bytes, false);
        require(redirects >= 3, "Production redirect scan was incomplete");
        checkSharedHudMixin(classes);
        Path fixture = paths(options.get("--fixture")).stream()
            .map(p -> p.resolve("cn/pupperclient/platform/MixinAnnotationFixture.class"))
            .filter(Files::isRegularFile).findFirst().orElseThrow();
        byte[] probe = Files.readAllBytes(fixture);
        require(checkScalarAnnotations(probe, true) == 1, "Fixture must test both at and slice");
        classes.add(probe);

        for (String platform : List.of("fabric", "neoforge")) {
            List<Path> runtimeClasspath = manifest(options, platform);
            checkHudAnchor(runtimeClasspath, platform);
            try (URLClassLoader runtime = loader(runtimeClasspath, true)) {
                String at = returnType(runtime, "at");
                String slice = returnType(runtime, "slice");
                require(Set.of("org.spongepowered.asm.mixin.injection.At", "[Lorg.spongepowered.asm.mixin.injection.At;").contains(at),
                    "Unexpected " + platform + " Redirect.at API: " + at);
                require(Set.of("org.spongepowered.asm.mixin.injection.Slice", "[Lorg.spongepowered.asm.mixin.injection.Slice;").contains(slice),
                    "Unexpected " + platform + " Redirect.slice API: " + slice);
                for (byte[] bytes : classes) checkRuntime(runtime, bytes);
            }
        }
        System.out.println("Common Mixin compatibility passed: " + redirects
            + " production redirects, scalar at/slice and shared HUD anchor accepted by Fabric and NeoForge runtimes.");
    }

    private static void checkSharedHudMixin(List<byte[]> classes) {
        String mixin = "cn/pupperclient/mixin/mixins/minecraft/client/render/MixinInGameHud";
        for (byte[] bytes : classes) {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (!node.name.equals(mixin)) continue;
            for (var method : node.methods) {
                if (method.visibleAnnotations == null) continue;
                for (AnnotationNode annotation : method.visibleAnnotations) {
                    if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                    require(value(annotation, "method").equals(List.of("extractEffects")), "HUD mixin uses a loader-specific dispatcher");
                    Object points = value(annotation, "at");
                    require(points instanceof List<?> && ((List<?>) points).size() == 1, "Unexpected HUD injection points");
                    require("HEAD".equals(value((AnnotationNode) ((List<?>) points).getFirst(), "value")), "HUD event changed its extraction order");
                    require(Integer.valueOf(1).equals(value(annotation, "require")), "HUD hook may silently stop working");
                    return;
                }
            }
        }
        throw new AssertionError("Shared HUD mixin injection was not checked");
    }

    private static void checkHudAnchor(List<Path> runtime, String platform) throws Exception {
        Path minecraft = findJar(runtime, HUD + ".class");
        require(minecraft != null, "Missing " + platform + " Minecraft HUD class");
        ClassNode hud = new ClassNode();
        try (JarFile jar = new JarFile(minecraft.toFile()); var stream = jar.getInputStream(jar.getJarEntry(HUD + ".class"))) {
            new ClassReader(stream).accept(hud, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        require(hud.methods.stream().anyMatch(m -> m.name.equals("extractEffects") && m.desc.equals(HUD_EXTRACT)),
            platform + " removed the shared HUD hook");
        List<String> order = new ArrayList<>();
        String dispatcher = platform.equals("fabric") ? "extractRenderState" : "registerVanillaLayers";
        var method = hud.methods.stream().filter(m -> m.name.equals(dispatcher)).findFirst().orElseThrow();
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(HUD)) order.add(call.name);
            if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                for (Object argument : dynamic.bsmArgs) {
                    if (argument instanceof Handle handle && handle.getOwner().equals(HUD)) order.add(handle.getName());
                }
            }
        }
        String decorations = platform.equals("fabric") ? "extractHotbarAndDecorations" : "maybeExtractSpectatorTooltip";
        require(order.indexOf(decorations) >= 0 && order.indexOf("extractEffects") == order.indexOf(decorations) + 1,
            platform + " no longer extracts effects immediately after hotbar decorations");
    }

    private static int checkScalarAnnotations(byte[] bytes, boolean fixture) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        int found = 0;
        for (var method : node.methods) {
            if (method.visibleAnnotations == null) continue;
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (!REDIRECT.equals(annotation.desc)) continue;
                found++;
                require(value(annotation, "at") instanceof AnnotationNode,
                    node.name + "." + method.name + " compiled Redirect.at as an array");
                Object slice = value(annotation, "slice");
                require(slice == null || slice instanceof AnnotationNode,
                    node.name + "." + method.name + " compiled Redirect.slice as an array");
                require(!fixture || slice != null, "Fixture lost its explicit slice");
            }
        }
        return found;
    }

    private static Object value(AnnotationNode node, String name) {
        if (node.values == null) return null;
        for (int i = 0; i < node.values.size(); i += 2) {
            if (name.equals(node.values.get(i))) return node.values.get(i + 1);
        }
        return null;
    }

    private static void checkRuntime(ClassLoader loader, byte[] bytes) throws Exception {
        Class<?> nodeType = loader.loadClass("org.objectweb.asm.tree.ClassNode");
        Class<?> readerType = loader.loadClass("org.objectweb.asm.ClassReader");
        Class<?> visitorType = loader.loadClass("org.objectweb.asm.ClassVisitor");
        Class<?> annotationType = loader.loadClass("org.objectweb.asm.tree.AnnotationNode");
        Object node = nodeType.getConstructor().newInstance();
        Object reader = readerType.getConstructor(byte[].class).newInstance((Object) bytes);
        readerType.getMethod("accept", visitorType, int.class).invoke(reader, node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        Class<?> annotations = loader.loadClass("org.spongepowered.asm.util.Annotations");
        var readList = annotations.getMethod("getValue", annotationType, String.class, boolean.class);
        for (Object method : (List<?>) nodeType.getField("methods").get(node)) {
            Object visible = method.getClass().getField("visibleAnnotations").get(method);
            if (visible == null) continue;
            for (Object annotation : (List<?>) visible) {
                if (!REDIRECT.equals(annotationType.getField("desc").get(annotation))) continue;
                require(((List<?>) readList.invoke(null, annotation, "at", true)).size() == 1,
                    "Loader could not read the compiled scalar at annotation");
                Object slice = readList.invoke(null, annotation, "slice", true);
                require(slice instanceof List<?>, "Loader could not read the compiled scalar slice annotation");
            }
        }
        Class<?> factory = loader.loadClass(FACTORY.substring(0, FACTORY.length() - 6).replace('/', '.'));
        Class<?> mixinInfo = loader.loadClass("org.spongepowered.asm.mixin.extensibility.IMixinInfo");
        factory.getMethod("transform", mixinInfo, nodeType).invoke(factory.getConstructor().newInstance(), null, node);
    }

    private static String returnType(ClassLoader loader, String property) throws Exception {
        return loader.loadClass("org.spongepowered.asm.mixin.injection.Redirect").getMethod(property).getReturnType().getName();
    }

    private static List<Path> paths(String classpath) {
        require(classpath != null && !classpath.isBlank(), "Missing dependency classpath");
        return Pattern.compile(Pattern.quote(File.pathSeparator)).splitAsStream(classpath).map(Path::of).toList();
    }

    private static List<Path> manifest(Map<String, String> options, String name) throws Exception {
        String file = options.get("--" + name + "-file");
        require(file != null, "Missing " + name + " classpath manifest");
        return paths(Files.readString(Path.of(file), StandardCharsets.UTF_8).trim());
    }

    private static URLClassLoader loader(List<Path> classpath, boolean extras) throws Exception {
        LinkedHashSet<Path> jars = new LinkedHashSet<>();
        Path mixin = findJar(classpath, MIXIN_API);
        require(mixin != null, "Missing actual Mixin API JAR");
        jars.add(mixin);
        for (String name : List.of("asm", "asm-tree", "asm-commons", "asm-util", "asm-analysis", "guava")) {
            for (Path file : classpath) {
                if (Files.isRegularFile(file) && file.getFileName().toString().startsWith(name + "-")) jars.add(file);
            }
        }
        if (extras) {
            Path factory = findJar(classpath, FACTORY);
            if (factory == null) {
                for (Path file : classpath) {
                    if (!Files.isRegularFile(file) || !file.toString().endsWith(".jar")) continue;
                    try (JarFile jar = new JarFile(file.toFile())) {
                        var entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            var entry = entries.nextElement();
                            if (!entry.getName().endsWith(".jar") || !entry.getName().contains("mixinextras")) continue;
                            Path extracted = Files.createTempFile("pupper-mixinextras-", ".jar");
                            extracted.toFile().deleteOnExit();
                            try (var stream = jar.getInputStream(entry)) {
                                Files.copy(stream, extracted, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                            if (findJar(List.of(extracted), FACTORY) != null) factory = extracted;
                        }
                    }
                }
            }
            require(factory != null, "Missing actual loader MixinExtras factory transformer");
            jars.add(factory);
        }
        URL[] urls = new URL[jars.size()];
        int index = 0;
        for (Path jar : jars) urls[index++] = jar.toUri().toURL();
        return new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
    }

    private static Path findJar(List<Path> files, String entry) throws Exception {
        for (Path file : files) {
            if (!Files.isRegularFile(file) || !file.toString().endsWith(".jar")) continue;
            try (JarFile jar = new JarFile(file.toFile())) {
                if (jar.getJarEntry(entry) != null) return file;
            }
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
