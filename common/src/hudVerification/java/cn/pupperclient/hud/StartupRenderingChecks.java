package cn.pupperclient.hud;

import cn.pupperclient.mixin.mixins.minecraft.client.render.MixinTextureAtlas;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import static org.objectweb.asm.Opcodes.*;

/** Exercises the actual mixin guard with a CPU buffer fixture, without a window or graphics device. */
public final class StartupRenderingChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var guard = MixinTextureAtlas.class.getDeclaredMethod("pupper$waitForGlobalUniform", CallbackInfo.class);
        guard.setAccessible(true);
        var mixin = new MixinTextureAtlas();
        GpuBuffer previous = RenderSystem.getGlobalSettingsUniform();
        try (GpuBuffer fixture = new GpuBuffer(GpuBuffer.USAGE_UNIFORM, 64) {
            private boolean closed;
            @Override public boolean isClosed() { return closed; }
            @Override public void close() { closed = true; }
            @Override public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
                throw new AssertionError("Startup guard must never map or fabricate a uniform buffer");
            }
        }) {
            RenderSystem.setGlobalSettingsUniform(null);
            CallbackInfo cold = new CallbackInfo("uploadAnimationFrames", true);
            guard.invoke(mixin, cold);
            require(cold.isCancelled(), "Cold-start texture animation still draws without Globals");
            require(RenderSystem.getGlobalSettingsUniform() == null, "Guard fabricated rendering state");

            RenderSystem.setGlobalSettingsUniform(fixture);
            CallbackInfo ready = new CallbackInfo("uploadAnimationFrames", true);
            guard.invoke(mixin, ready);
            require(!ready.isCancelled(), "Atlas animations did not resume when Globals became ready");
            require(RenderSystem.getGlobalSettingsUniform() == fixture && !fixture.isClosed(), "Guard changed or closed renderer-owned Globals");

            RenderSystem.setGlobalSettingsUniform(null);
            CallbackInfo reset = new CallbackInfo("uploadAnimationFrames", true);
            guard.invoke(mixin, reset);
            require(reset.isCancelled(), "Guard cached readiness across a render-state reset");
        } finally { RenderSystem.setGlobalSettingsUniform(previous); }

        AtomicBoolean target = new AtomicBoolean(), bindsGlobals = new AtomicBoolean(), staticBindsGlobals = new AtomicBoolean();
        try (InputStream source = resource("net/minecraft/client/renderer/texture/TextureAtlas.class")) {
            new ClassReader(source).accept(new ClassVisitor(ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!desc.equals("()V")) return null;
                    boolean animated = name.equals("uploadAnimationFrames");
                    if (!animated && !name.equals("uploadInitialContents")) return null;
                    if (animated) target.set((access & ACC_STATIC) == 0);
                    return new MethodVisitor(ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                            if (owner.equals("com/mojang/blaze3d/systems/RenderSystem") && name.equals("bindDefaultUniforms")) {
                                (animated ? bindsGlobals : staticBindsGlobals).set(true);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        require(target.get(), "Minecraft atlas animation hook changed");
        require(bindsGlobals.get(), "Atlas upload no longer binds the default Globals uniform");
        require(staticBindsGlobals.get(), "Static atlas upload contract changed");
        checkConstructorHook();
        try (var reader = new InputStreamReader(resource("pupper.mixins.json"), StandardCharsets.UTF_8)) {
            var clients = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("client").asList();
            require(clients.stream()
                .anyMatch(v -> v.getAsString().equals("minecraft.client.render.MixinTextureAtlas")), "Startup guard was not registered");
            require(clients.stream()
                .anyMatch(v -> v.getAsString().equals("minecraft.client.render.MixinGameRenderer")), "Startup initializer was not registered");
        }
        System.out.println("Startup rendering checks passed: " + checks + " assertions; early initialization, cold/ready/reset states and static/animated atlas contracts.");
    }

    private static void checkConstructorHook() throws Exception {
        AtomicBoolean ownsUniform = new AtomicBoolean(), constructorAllocates = new AtomicBoolean();
        try (InputStream source = resource("net/minecraft/client/renderer/GameRenderer.class")) {
            new ClassReader(source).accept(new ClassVisitor(ASM9) {
                @Override public org.objectweb.asm.FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                    if (name.equals("globalSettingsUniform") && desc.equals("Lnet/minecraft/client/renderer/GlobalSettingsUniform;")) {
                        ownsUniform.set((access & ACC_FINAL) != 0);
                    }
                    return null;
                }
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!name.equals("<init>")) return null;
                    return new MethodVisitor(ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                            if (owner.equals("net/minecraft/client/renderer/GlobalSettingsUniform") && name.equals("<init>")) constructorAllocates.set(true);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        require(ownsUniform.get(), "Renderer-owned uniform shadow changed");
        require(constructorAllocates.get(), "Globals is no longer allocated before constructor return");

        AtomicBoolean constructorTarget = new AtomicBoolean(), atReturn = new AtomicBoolean(), callsInitializer = new AtomicBoolean();
        try (InputStream source = resource("cn/pupperclient/mixin/mixins/minecraft/client/render/MixinGameRenderer.class")) {
            new ClassReader(source).accept(new ClassVisitor(ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!name.equals("pupper$initializeStartupUniforms")) return null;
                    return new MethodVisitor(ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                            if (!desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) return null;
                            return new AnnotationVisitor(ASM9) {
                                @Override public AnnotationVisitor visitArray(String name) {
                                    if (name.equals("method")) return new AnnotationVisitor(ASM9) {
                                        @Override public void visit(String name, Object value) { constructorTarget.set("<init>".equals(value)); }
                                    };
                                    if (name.equals("at")) return new AnnotationVisitor(ASM9) {
                                        @Override public AnnotationVisitor visitAnnotation(String name, String desc) {
                                            return new AnnotationVisitor(ASM9) {
                                                @Override public void visit(String name, Object value) {
                                                    if (name.equals("value")) atReturn.set("RETURN".equals(value));
                                                }
                                            };
                                        }
                                    };
                                    return null;
                                }
                            };
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                            if (owner.equals("cn/pupperclient/utils/render/StartupRenderUniforms") && name.equals("initialize")) callsInitializer.set(true);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        require(constructorTarget.get() && atReturn.get(), "Globals initializer must run at constructor return");
        require(callsInitializer.get(), "Constructor hook no longer invokes the production initializer");
    }
    private static InputStream resource(String name) {
        InputStream stream = StartupRenderingChecks.class.getClassLoader().getResourceAsStream(name);
        if (stream == null) throw new AssertionError("Missing resource: " + name);
        return stream;
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
