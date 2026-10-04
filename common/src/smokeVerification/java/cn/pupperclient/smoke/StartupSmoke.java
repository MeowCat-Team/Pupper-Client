package cn.pupperclient.smoke;

import cn.pupperclient.PupperClient;
import cn.pupperclient.mixin.interfaces.IMixinMinecraftClient;
import cn.pupperclient.platform.ProtocolAccess;
import cn.pupperclient.platform.ProtocolSnapshot;
import cn.pupperclient.utils.file.FileLocation;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.lwjgl.util.nfd.NativeFileDialog;
import org.newsclub.net.unix.AFUNIXSocket;

/** Real-loader startup checks. Compiled only into smokeVerification, never the released mod. */
public final class StartupSmoke {
    private static final int MIN_TICKS = 60;
    private static final int MIN_FRAMES = 60;
    private static final AtomicBoolean ARMED = new AtomicBoolean();
    private static final AtomicBoolean FINISHED = new AtomicBoolean();
    private static final AtomicBoolean SHUTDOWN_EXIT_STARTED = new AtomicBoolean();
    private static int ticks;
    private static int frames;
    private static long launchTime;
    private static List<Object> managers;
    private static String loader;
    private static Supplier<ProtocolSnapshot> expectedProtocol;

    private StartupSmoke() { }

    public static void arm(String platform, Supplier<ProtocolSnapshot> expected) {
        if (!Boolean.getBoolean("pupper.smoke")) return;
        require(ARMED.compareAndSet(false, true), "Smoke initializer registered twice");
        require(platform.equals(System.getProperty("pupper.smoke.loader")), "Unexpected smoke loader");
        loader = platform;
        expectedProtocol = expected;
        var watchdog = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "pupper-smoke-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        watchdog.schedule(() -> {
            if (!FINISHED.get()) {
                System.err.println("PUPPER_SMOKE_FAILURE loader=" + loader + " reason=180-second-timeout");
                System.err.flush();
                Runtime.getRuntime().halt(2);
            }
        }, 180, TimeUnit.SECONDS);
        ClientTickEvent.CLIENT_POST.register(StartupSmoke::tick);
        System.out.println("PUPPER_SMOKE_ARMED loader=" + loader);
    }

    public static void framePresented() {
        if (ARMED.get() && !FINISHED.get()) frames++;
    }

    public static void shutdownCompleted() {
        if (!Boolean.getBoolean("pupper.smoke") || Boolean.getBoolean("pupper.smoke.packaged")
                || !"neoforge".equals(loader) || !SHUTDOWN_EXIT_STARTED.compareAndSet(false, true)) return;
        // Main's final return is after exitWorldAndClose. Wait for its caller thread
        // to finish the outer FML/DLI cleanup too. Only Architectury's NeoForge
        // development runtime needs this: packaged clients must exit naturally.
        Thread launcher = Thread.currentThread();
        AtomicBoolean outerCleanupFailed = new AtomicBoolean();
        Thread.UncaughtExceptionHandler originalHandler = launcher.getUncaughtExceptionHandler();
        launcher.setUncaughtExceptionHandler((thread, failure) -> {
            outerCleanupFailed.set(true);
            if (originalHandler != null) originalHandler.uncaughtException(thread, failure);
            else failure.printStackTrace(System.err);
        });
        Thread exit = new Thread(() -> {
            boolean interrupted = false;
            try {
                launcher.join(10_000L);
            } catch (InterruptedException failure) {
                interrupted = true;
                Thread.currentThread().interrupt();
            }
            boolean success = ARMED.get() && FINISHED.get() && !launcher.isAlive()
                    && !outerCleanupFailed.get() && !interrupted;
            System.out.println(success ? "PUPPER_SMOKE_SHUTDOWN_SUCCESS" : "PUPPER_SMOKE_SHUTDOWN_FAILURE");
            System.out.flush();
            System.exit(success ? 0 : 2);
        }, "pupper-smoke-dev-exit");
        exit.setDaemon(true);
        exit.start();
    }

    private static void tick(Minecraft minecraft) {
        if (FINISHED.get()) return;
        try {
            PupperClient client = PupperClient.getInstance();
            List<Object> current = managers(client);
            if (ticks == 0) {
                managers = current;
                launchTime = client.getLaunchTime();
                require(launchTime > 0, "Client startup did not run");
            } else {
                require(launchTime == client.getLaunchTime(), "Client startup ran again");
                for (int index = 0; index < managers.size(); index++) {
                    require(managers.get(index) == current.get(index), "Manager was initialized again: " + index);
                }
            }
            ticks++;
            if (ticks < MIN_TICKS || frames < MIN_FRAMES) return;
            verifyAndStop(minecraft, client);
        } catch (Throwable failure) {
            System.err.println("PUPPER_SMOKE_FAILURE loader=" + loader + " ticks=" + ticks + " frames=" + frames);
            failure.printStackTrace(System.err);
            minecraft.stop();
            throw new IllegalStateException("Pupper startup smoke validation failed", failure);
        }
    }

    private static List<Object> managers(PupperClient client) {
        Object[] values = {client.getModManager(), client.getColorManager(), client.getMusicManager(),
                client.getConfigManager(), client.getProfileManager(), client.getWebSocketManager(),
                client.getHypixelManager(), client.getKeybindManager(), client.getCapeManager()};
        for (Object value : values) require(value != null, "A client manager was not initialized");
        require(!client.getModManager().getMods().isEmpty(), "No feature modules were registered");
        return List.of(values);
    }

    private static void verifyAndStop(Minecraft minecraft, PupperClient client) throws Exception {
        String expectedVersion = System.getProperty("pupper.smoke.expectedVersion");
        require(expectedVersion != null && expectedVersion.equals(client.getVersion()), "Wrong mod metadata version");
        require(client.getVersion().equals(Platform.getMod("pupper").getVersion()), "Platform metadata differs");
        require(loader.equals("fabric") ? Platform.isFabric() : Platform.isNeoForge(), "Wrong active platform");
        Path game = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        require(game.endsWith(Path.of("build", "smoke")), "Smoke must use an isolated build/smoke directory");
        require(FileLocation.MAIN_DIR.toPath().toAbsolutePath().normalize().startsWith(game), "Config escaped the smoke directory");
        require(minecraft instanceof IMixinMinecraftClient, "Shared Minecraft startup/accessor mixin was not applied");
        ProtocolSnapshot actual = ProtocolAccess.target();
        ProtocolSnapshot expected = expectedProtocol.get();
        require(actual.equals(expected), "Wrong protocol provider: " + actual + " expected " + expected);
        require(loader.equals("fabric") == actual.translationAvailable(), "Translation capability was reported incorrectly");
        int mixins = verifyAppliedMixins(minecraft.getClass().getClassLoader());
        boolean packaged = Boolean.getBoolean("pupper.smoke.packaged");
        String artifact = packaged ? verifyPackagedRuntime() : null;
        JsonObject report = new JsonObject();
        report.addProperty("loader", loader);
        report.addProperty("version", client.getVersion());
        report.addProperty("ticks", ticks);
        report.addProperty("frames", frames);
        report.addProperty("mixins", mixins);
        report.addProperty("protocol", actual.name());
        report.addProperty("translationAvailable", actual.translationAvailable());
        report.addProperty("packaged", packaged);
        if (packaged) {
            report.addProperty("artifact", artifact);
            report.addProperty("nfdInitialized", true);
            report.addProperty("unixSocketSupported", true);
        }
        Files.writeString(game.resolve("pupper-smoke-result.json"), report.toString(), StandardCharsets.UTF_8);
        FINISHED.set(true);
        System.out.println("PUPPER_SMOKE_SUCCESS " + report);
        System.out.flush();
        minecraft.stop();
    }

    private static String verifyPackagedRuntime() throws Exception {
        String expected = System.getProperty("pupper.smoke.expectedArtifact");
        require(expected != null && !expected.isBlank(), "Packaged smoke needs the final artifact path");
        Path expectedArtifact = Path.of(expected).toRealPath();
        var source = PupperClient.class.getProtectionDomain().getCodeSource();
        require(source != null, "PupperClient has no actual code source");
        Path loadedArtifact = Path.of(source.getLocation().toURI()).toRealPath();
        require(Files.isRegularFile(loadedArtifact) && Files.isSameFile(expectedArtifact, loadedArtifact),
            "PupperClient did not load from the final artifact: " + loadedArtifact);
        int result = NativeFileDialog.NFD_Init();
        require(result == NativeFileDialog.NFD_OKAY, "Packaged NFD JNI initialization failed: " + result);
        try {
            require(AFUNIXSocket.isSupported(), "Packaged junixsocket JNI is unavailable");
        } finally {
            NativeFileDialog.NFD_Quit();
        }
        return loadedArtifact.toString();
    }

    @SuppressWarnings("deprecation")
    private static int verifyAppliedMixins(ClassLoader classLoader) throws Exception {
        require(MixinEnvironment.getCurrentEnvironment().getMixinConfigs().contains("pupper.mixins.json"),
                "The production mixin configuration was not registered");
        JsonObject config;
        try (InputStream stream = resource(classLoader, "pupper.mixins.json")) {
            config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<String> configured = new ArrayList<>();
        for (String group : List.of("mixins", "client")) {
            JsonArray entries = config.getAsJsonArray(group);
            if (entries != null) entries.forEach(entry -> configured.add(config.get("package").getAsString() + "." + entry.getAsString()));
        }
        require(!configured.isEmpty(), "Production mixin list was empty");
        for (String mixin : configured) {
            Set<String> targets = new LinkedHashSet<>();
            int[] implementations = {0};
            try (InputStream stream = resource(classLoader, mixin.replace('.', '/') + ".class")) {
                new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (!descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override public AnnotationVisitor visitArray(String name) {
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override public void visit(String ignored, Object value) {
                                        if (value instanceof Type type) targets.add(type.getClassName());
                                        else if (name.equals("targets") && value instanceof String target) targets.add(target.replace('/', '.'));
                                    }
                                };
                            }
                        };
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        if (name.startsWith("<")) return null;
                        boolean[] shadow = {false};
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                                if (annotation.equals("Lorg/spongepowered/asm/mixin/Shadow;")) shadow[0] = true;
                                return null;
                            }
                            @Override public void visitEnd() {
                                if (!shadow[0]) implementations[0]++;
                            }
                        };
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            require(!targets.isEmpty(), "No targets recorded for " + mixin);
            for (String target : targets) {
                // Load and transform our targets only, without initializing arbitrary game/mod
                // static state. Mixin audit() initializes every third-party target as well.
                Class<?> transformed = Class.forName(target, false, classLoader);
                boolean applied = implementations[0] == 0; // The existing LoadingOverlay mixin is intentionally empty.
                for (Class<?> implemented : transformed.getInterfaces()) {
                    if (implemented.getName().equals(mixin)) applied = true;
                }
                for (Method method : transformed.getDeclaredMethods()) {
                    for (Annotation annotation : method.getDeclaredAnnotations()) {
                        if (annotation.annotationType().getName().equals("org.spongepowered.asm.mixin.transformer.meta.MixinMerged")
                                && mixin.equals(annotation.annotationType().getMethod("mixin").invoke(annotation))) applied = true;
                    }
                }
                require(applied, "Mixin was not merged into its target: " + mixin + " -> " + target);
            }
        }
        return configured.size();
    }

    private static InputStream resource(ClassLoader loader, String name) throws IOException {
        InputStream resource = loader.getResourceAsStream(name);
        if (resource == null) throw new IOException("Missing smoke resource: " + name);
        return resource;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
