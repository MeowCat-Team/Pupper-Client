package cn.pupperclient.hud;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import cn.pupperclient.skia.context.SkiaContext;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

/** Guard presentation timing and keep backend-specific GPU access out of production classes. */
public final class SkiaInteropChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        List<String> frameCalls = new ArrayList<>();
        AtomicInteger blits = new AtomicInteger();
        read("net/minecraft/client/Minecraft", new ClassVisitor(ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if (!name.equals("renderFrame")) return null;
                return new MethodVisitor(ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        if (owner.equals("com/mojang/blaze3d/systems/GpuSurface") && name.equals("blitFromTexture")) {
                            require(desc.equals("(Lcom/mojang/blaze3d/systems/CommandEncoder;Lcom/mojang/blaze3d/textures/GpuTextureView;)V"), "Presentation blit signature changed");
                            blits.incrementAndGet();
                        }
                        if (name.equals("blitFromTexture") || name.equals("submit") || name.equals("present")) frameCalls.add(name);
                    }
                };
            }
        });
        require(blits.get() == 1, "Expected one presentation blit in Minecraft.renderFrame");
        require(frameCalls.indexOf("blitFromTexture") < frameCalls.indexOf("submit")
                && frameCalls.indexOf("submit") < frameCalls.indexOf("present"), "UI hook is no longer before submission/presentation");
        List<String> targets = new ArrayList<>();
        read("cn/pupperclient/mixin/mixins/minecraft/client/MixinMinecraftClient", new ClassVisitor(ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodVisitor(ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        return new AnnotationVisitor(ASM9) {
                            @Override public AnnotationVisitor visitAnnotation(String name, String desc) { return this; }
                            @Override public AnnotationVisitor visitArray(String name) { return this; }
                            @Override public void visit(String name, Object value) { if ("target".equals(name)) targets.add(value.toString()); }
                        };
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        require(!owner.equals("cn/pupperclient/skia/context/SkiaContext") || !name.equals("createSurface"), "Production still wraps the window framebuffer");
                    }
                };
            }
        });
        require(targets.stream().anyMatch(t -> t.contains("GpuSurface;blitFromTexture(")), "Offscreen UI mixin is not hooked before the blit");
        require(targets.stream().noneMatch(t -> t.contains("GpuSurface;present(")), "UI still runs after GPU submission");
        Path classes = Path.of(SkiaContext.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        int scanned = 0;
        try (var files = Files.walk(classes.resolve("cn/pupperclient"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                new ClassReader(Files.readAllBytes(file)).accept(new ClassVisitor(ASM9) {
                    void check(String reference) {
                        require(reference == null || (!reference.contains("org/lwjgl/opengl/")
                                && !reference.contains("com/mojang/blaze3d/opengl/")
                                && !reference.contains("io/github/humbleui/skija/DirectContext")
                                && !reference.contains("io/github/humbleui/skija/BackendRenderTarget")),
                                "Backend-specific GPU access in " + file.getFileName() + ": " + reference);
                    }
                    @Override public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) { check(desc); return null; }
                    @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                        check(desc);
                        return new MethodVisitor(ASM9) {
                            @Override public void visitTypeInsn(int opcode, String type) { check(type); }
                            @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) { check(owner); check(desc); }
                            @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                                check(owner); check(desc);
                                require(!name.equals("adoptGLTextureFrom") && !name.equals("makeGL") && !name.equals("wrapBackendRenderTarget"),
                                        "Native Skia GPU bridge returned in " + file.getFileName());
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                scanned++;
            }
        }
        require(scanned > 100, "Production class scan was incomplete");
        System.out.println("Blaze3D UI contracts passed: presentation timing and " + scanned + " production classes without raw GL/Skia GPU access.");
    }

    private static void read(String name, ClassVisitor visitor) throws Exception {
        try (InputStream source = SkiaInteropChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (source == null) throw new AssertionError("Missing class: " + name);
            new ClassReader(source).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
