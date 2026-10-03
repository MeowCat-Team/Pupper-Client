package cn.pupperclient.hud;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

/** Guard the presentation order and the version-specific Minecraft state-cache boundary. */
public final class SkiaInteropChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        List<String> frameCalls = new ArrayList<>();
        AtomicInteger blits = new AtomicInteger(), textureSlots = new AtomicInteger();
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
        read("com/mojang/blaze3d/opengl/GlStateManager", new ClassVisitor(ASM9) {
            @Override public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                if (name.equals("TEXTURE_COUNT")) textureSlots.set((Integer) value);
                return null;
            }
        });
        require(textureSlots.get() == 12, "Minecraft texture cache size changed; update GlBindings reconciliation");
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
        System.out.println("Skia interop contracts passed: " + checks + " assertions.");
    }

    private static void read(String name, ClassVisitor visitor) throws Exception {
        try (InputStream source = SkiaInteropChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (source == null) throw new AssertionError("Missing class: " + name);
            new ClassReader(source).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
