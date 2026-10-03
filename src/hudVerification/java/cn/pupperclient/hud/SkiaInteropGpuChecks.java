package cn.pupperclient.hud;

import cn.pupperclient.skia.GlassRenderer;
import cn.pupperclient.skia.context.SkiaContext;
import cn.pupperclient.skia.context.SkiaUiLayer;
import cn.pupperclient.skia.gl.States;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.opengl.GlBackend;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Uses the production compositor and real Blaze3D caches, not a raster-only rendering mock. */
public final class SkiaInteropGpuChecks {
    private static int checks;
    private static final String LIGHTMAP_FRAGMENT = """
            #version 330
            uniform sampler2D Base;
            uniform sampler2D Lightmap;
            in vec2 texCoord;
            out vec4 fragColor;
            void main() { fragColor = texture(Base, texCoord) * texture(Lightmap, texCoord); }
            """;
    public static void main(String[] args) throws Exception {
        var error = GLFWErrorCallback.createPrint(System.err);
        glfwSetErrorCallback(error);
        long window = 0;
        boolean ready = false;
        try {
            require(glfwInit(), "GLFW initialization failed");
            glfwDefaultWindowHints();
            var backend = new GlBackend();
            backend.setWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            window = glfwCreateWindow(128, 128, "Pupper Client Skia interop checks", 0, 0);
            require(window != 0, "Hidden GPU window creation failed");
            RenderSystem.initRenderThread();
            var device = backend.createDevice(window, SkiaInteropGpuChecks::shader,
                    new GpuDebugOptions(0, false, false, false), () -> {});
            RenderSystem.initRenderer(device);
            ready = true;
            System.out.println("Skia interop GPU: " + glGetString(GL_RENDERER) + ", " + glGetString(GL_VERSION));
            checkBindings();
            try (var target = new Target(64, 48)) {
                for (int frame = 0; frame < 4; frame++) {
                    float alpha = frame % 2;
                    clear(target, 0.2F, 0.4F, 0.6F, alpha);
                    byte[] before = read(target);
                    glEnable(GL_DITHER); glDisable(GL_PROGRAM_POINT_SIZE);
                    SkiaContext.drawOffscreen(target.view, canvas -> {
                        require(Arrays.equals(before, read(target)), "Skia changed the scene before composition");
                        markers(canvas);
                    }, false);
                    require(glIsEnabled(GL_DITHER), "Native Skia reset leaked dither state");
                    require(!glIsEnabled(GL_PROGRAM_POINT_SIZE), "Native Skia reset leaked program point-size state");
                    assertMarkers(target, alpha);
                    // Transparent areas are byte-for-byte identical, including a scene alpha of zero.
                    pixel(target, 60, 44, 51, 102, 153, (int) alpha * 255, "Unchanged scene outside UI");
                }
                clear(target, 0.2F, 0.4F, 0.6F, 0);
                SkiaContext.drawOffscreen(target.view, canvas -> {
                    rect(canvas, 20, 16, 16, 16, 0xFFFF00FF);
                    GlassRenderer.draw(canvas, 16, 12, 28, 24, 4, 0, 0);
                }, true);
                pixel(target, 28, 24, 51, 102, 153, 255, "Glass samples the scene, not preceding UI");
                pixel(target, 60, 44, 51, 102, 153, 0, "Glass leaves exterior unchanged");
                clear(target, 0.2F, 0.4F, 0.6F, 1);
                byte[] beforeDisabled = read(target);
                SkiaContext.drawOffscreen(target.view, canvas -> GlassRenderer.draw(canvas, 0, 0, 64, 48, 0, 0, 0), false);
                require(Arrays.equals(read(target), beforeDisabled), "Disabled glass capture changed the scene");
                pixel(target, 28, 24, 51, 102, 153, 255, "Disabled glass capture preserves scene");
                checkCallbackFailure(target);
                checkLightmap(target);
                // Exercise the backend-independent upload path on the available GL device.
                try (var raster = new SkiaUiLayer(64, 48, null)) {
                    clear(target, 0.2F, 0.4F, 0.6F, 1);
                    raster.draw(target.view, SkiaInteropGpuChecks::markers, false);
                    assertMarkers(target, 1);
                }
            }
            // Different dimensions and a replacement destination texture must reallocate only UI storage.
            try (var resized = new Target(96, 72)) {
                clear(resized, 0.1F, 0.2F, 0.3F, 0);
                SkiaContext.drawOffscreen(resized.view, canvas -> rect(canvas, 0, 0, 12, 12, 0xFFFF0000), true);
                pixel(resized, 4, 4, 255, 0, 0, 255, "Resize retains top-left UI origin");
                pixel(resized, 90, 64, 26, 51, 76, 0, "Resize preserves scene exterior");
                var sharedContext = SkiaContext.getContext();
                SkiaContext.drawOffscreen(resized.view, canvas -> {}, false);
                require(sharedContext == SkiaContext.getContext(), "Resize replaced the shared image context");
            }
            SkiaContext.close();
            SkiaContext.close();
            require(SkiaContext.getContext() == null, "Context not released on shutdown");
            require(glGetError() == GL_NO_ERROR, "OpenGL error after interop checks");
            System.out.println("Skia interop GPU checks passed: " + checks + " assertions; state/cache isolation, scene-only glass, premultiplied composition, orientation, resize, failed callbacks and raster upload.");
        } finally {
            if (ready) {
                SkiaContext.close();
                GlassRenderer.releaseResources();
                RenderSystem.shutdownRenderer();
            }
            if (window != 0) { glfwMakeContextCurrent(0); GL.setCapabilities(null); glfwDestroyWindow(window); }
            glfwTerminate();
            glfwSetErrorCallback(null);
            error.free();
        }
    }

    private static void checkBindings() {
        int[] slots = {0, 1, 2, 11, glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS) - 1};
        int[] textures = new int[slots.length], samplers = new int[slots.length];
        int buffer = glGenBuffers(), vao = glGenVertexArrays(), element = glGenBuffers();
        int alignment = glGetInteger(GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT);
        try {
            for (int i = 0; i < slots.length; i++) {
                textures[i] = glGenTextures(); samplers[i] = glGenSamplers();
                glActiveTexture(GL_TEXTURE0 + slots[i]); glBindTexture(GL_TEXTURE_2D, textures[i]); glBindSampler(slots[i], samplers[i]);
            }
            glBindBuffer(GL_UNIFORM_BUFFER, buffer);
            glBufferData(GL_UNIFORM_BUFFER, alignment * 2L, GL_STATIC_DRAW);
            glBindBufferRange(GL_UNIFORM_BUFFER, 2, buffer, alignment, alignment);
            glBindVertexArray(vao); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, element);
            glDepthFunc(GL_GREATER); glDepthRange(0.125, 0.875);
            glStencilFuncSeparate(GL_FRONT, GL_EQUAL, 3, 0x3F); glStencilMaskSeparate(GL_BACK, 0x15);
            glStencilOpSeparate(GL_BACK, GL_REPLACE, GL_INCR, GL_DECR);
            glColorMaski(1, true, false, true, false); glEnablei(GL_BLEND, 1);
            glActiveTexture(GL_TEXTURE2);
            States.push();
            try {
                for (int slot : slots) { glActiveTexture(GL_TEXTURE0 + slot); glBindTexture(GL_TEXTURE_2D, 0); glBindSampler(slot, 0); }
                glBindBufferBase(GL_UNIFORM_BUFFER, 2, 0); glBindBuffer(GL_UNIFORM_BUFFER, 0);
                glBindVertexArray(0); glDepthFunc(GL_LESS); glDepthRange(0, 1);
                glStencilFuncSeparate(GL_FRONT, GL_ALWAYS, 0, -1); glStencilMaskSeparate(GL_BACK, -1);
                glStencilOpSeparate(GL_BACK, GL_KEEP, GL_KEEP, GL_KEEP);
                glColorMaski(1, false, true, false, true); glDisablei(GL_BLEND, 1);
                // Emulate a lazy Minecraft texture upload inside a UI callback.
                GlStateManager._activeTexture(GL_TEXTURE3); GlStateManager._bindTexture(0);
            } finally { States.popMinecraft(); }
            require(glGetInteger(GL_ACTIVE_TEXTURE) == GL_TEXTURE2, "Active texture slot was not restored");
            for (int i = 0; i < slots.length; i++) {
                glActiveTexture(GL_TEXTURE0 + slots[i]);
                require(glGetInteger(GL_TEXTURE_BINDING_2D) == textures[i], "Texture slot " + slots[i] + " leaked");
                require(glGetInteger(GL_SAMPLER_BINDING) == samplers[i], "Sampler slot " + slots[i] + " leaked");
            }
            // This setter is cached after reconciliation; a missing physical restore would stay wrong.
            GlStateManager._activeTexture(GL_TEXTURE1); GlStateManager._bindTexture(textures[1]);
            require(glGetInteger(GL_TEXTURE_BINDING_2D) == textures[1], "Minecraft texture cache disagrees with GL");
            require(glGetIntegeri(GL_UNIFORM_BUFFER_BINDING, 2) == buffer
                    && glGetInteger64i(GL_UNIFORM_BUFFER_START, 2) == alignment
                    && glGetInteger64i(GL_UNIFORM_BUFFER_SIZE, 2) == alignment, "Uniform buffer range leaked");
            require(glGetInteger(GL_VERTEX_ARRAY_BINDING) == vao && glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING) == element, "VAO element buffer leaked");
            require(glGetInteger(GL_DEPTH_FUNC) == GL_GREATER, "Depth function leaked");
            double[] range = new double[2]; glGetDoublev(GL_DEPTH_RANGE, range);
            require(range[0] == 0.125 && range[1] == 0.875, "Depth range leaked");
            require(glGetInteger(GL_STENCIL_FUNC) == GL_EQUAL && glGetInteger(GL_STENCIL_REF) == 3
                    && glGetInteger(GL_STENCIL_BACK_WRITEMASK) == 0x15
                    && glGetInteger(GL_STENCIL_BACK_PASS_DEPTH_PASS) == GL_DECR, "Stencil state leaked");
            require(glIsEnabledi(GL_BLEND, 1), "Indexed blend enable leaked");
            ByteBuffer mask = MemoryUtil.memAlloc(4);
            try { glGetBooleani_v(GL_COLOR_WRITEMASK, 1, mask); require(mask.get(0) != 0 && mask.get(1) == 0 && mask.get(2) != 0 && mask.get(3) == 0, "Indexed color mask leaked"); }
            finally { MemoryUtil.memFree(mask); }
        } finally {
            for (int i = 0; i < slots.length; i++) { glActiveTexture(GL_TEXTURE0 + slots[i]); glBindTexture(GL_TEXTURE_2D, 0); glBindSampler(slots[i], 0); glDeleteTextures(textures[i]); glDeleteSamplers(samplers[i]); }
            GlStateManager._activeTexture(GL_TEXTURE0); GlStateManager._bindTexture(0);
            GlStateManager._depthFunc(GL_LEQUAL); glDepthRange(0, 1);
            glBindVertexArray(0); glBindBufferBase(GL_UNIFORM_BUFFER, 2, 0); glBindBuffer(GL_UNIFORM_BUFFER, 0);
            glDeleteBuffers(buffer); glDeleteBuffers(element); glDeleteVertexArrays(vao);
            GlStateManager._disableBlend(1); GlStateManager._colorMask(1, ColorTargetState.WRITE_ALL);
        }
    }

    private static void checkCallbackFailure(Target target) {
        clear(target, 0.2F, 0.4F, 0.6F, 1);
        byte[] before = read(target);
        int previous = glGetInteger(GL_ACTIVE_TEXTURE);
        try (var isolated = new SkiaUiLayer(target.width, target.height, SkiaContext.getContext())) {
            try {
                isolated.draw(target.view, canvas -> { markers(canvas); throw new IllegalStateException("Expected callback failure"); }, false);
                throw new AssertionError("Callback failure was swallowed by the UI layer");
            } catch (IllegalStateException expected) { require(expected.getMessage().equals("Expected callback failure"), "Unexpected rendering failure"); }
            require(Arrays.equals(before, read(target)), "Failed UI callback modified the scene");
            require(glGetInteger(GL_ACTIVE_TEXTURE) == previous, "Failed callback leaked texture state");
            isolated.draw(target.view, SkiaInteropGpuChecks::markers, false);
            assertMarkers(target, 1);
        }
    }

    private static void checkLightmap(Target target) {
        var id = Identifier.fromNamespaceAndPath("pupper", "interop_lightmap");
        var pipeline = RenderPipeline.builder().withLocation(id).withVertexShader(Identifier.fromNamespaceAndPath("pupper", "skia_ui"))
                .withFragmentShader(id).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withBindGroupLayout(BindGroupLayout.builder().withSampler("Base").withSampler("Lightmap").build())
                .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                .withDepthStencilState(Optional.empty()).withCull(false).build();
        try (var base = new Target(2, 2); var light = new Target(2, 2)) {
            clear(base, 0, 1, 1, 1); clear(light, 1, 1, 0, 1);
            for (int frame = 0; frame < 4; frame++) {
                try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Lightmap regression", target.view, Optional.empty())) {
                    pass.setPipeline(pipeline);
                    var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                    pass.bindTexture("Base", base.view, sampler); pass.bindTexture("Lightmap", light.view, sampler);
                    pass.draw(3, 1, 0, 0);
                }
                pixel(target, 60, 44, 0, 255, 0, 255, "Blaze3D texture + lightmap after Skia frame");
                SkiaContext.drawOffscreen(target.view, SkiaInteropGpuChecks::markers, false);
            }
        }
    }

    private static String shader(Identifier id, ShaderType type) {
        if (id.getPath().equals("interop_lightmap")) return LIGHTMAP_FRAGMENT;
        String path = "/assets/" + id.getNamespace() + "/shaders/" + id.getPath() + (type == ShaderType.VERTEX ? ".vsh" : ".fsh");
        try (InputStream input = SkiaInteropGpuChecks.class.getResourceAsStream(path)) {
            return input == null ? null : new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception failure) { throw new IllegalStateException(path, failure); }
    }
    private static void markers(Canvas canvas) {
        rect(canvas, 2, 2, 12, 10, 0xFFFF0000);
        rect(canvas, 2, 34, 12, 10, 0xFF0000FF);
        rect(canvas, 24, 16, 16, 16, 0x80FF0000);
    }
    private static void rect(Canvas canvas, int x, int y, int width, int height, int color) {
        try (var paint = new Paint().setColor(color)) { canvas.drawRect(Rect.makeXYWH(x, y, width, height), paint); }
    }
    private static void assertMarkers(Target target, float alpha) {
        pixel(target, 6, 6, 255, 0, 0, 255, "Top marker orientation");
        pixel(target, 6, 38, 0, 0, 255, 255, "Bottom marker orientation");
        pixel(target, 32, 24, 153, 51, 76, alpha == 0 ? 128 : 255, "Premultiplied alpha blended once");
    }
    private static void clear(Target target, float r, float g, float b, float a) {
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.texture, new Vector4f(r, g, b, a));
    }
    private static void pixel(Target target, int x, int y, int r, int g, int b, int a, String message) {
        byte[] data = read(target); int start = ((target.height - y - 1) * target.width + x) * 4;
        int[] expected = {r, g, b, a};
        for (int k = 0; k < 4; k++) require(Math.abs((data[start + k] & 255) - expected[k]) <= 2,
                message + ": channel " + k + ", expected " + expected[k] + ", got " + (data[start + k] & 255));
    }
    private static byte[] read(Target target) {
        int saved = glGetInteger(GL_READ_FRAMEBUFFER_BINDING), pack = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = glGetInteger(GL_PACK_ALIGNMENT), row = glGetInteger(GL_PACK_ROW_LENGTH);
        int skipX = glGetInteger(GL_PACK_SKIP_PIXELS), skipY = glGetInteger(GL_PACK_SKIP_ROWS);
        ByteBuffer data = MemoryUtil.memAlloc(target.width * target.height * 4);
        try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, target.framebuffer); glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            glPixelStorei(GL_PACK_ALIGNMENT, 1); glPixelStorei(GL_PACK_ROW_LENGTH, 0); glPixelStorei(GL_PACK_SKIP_PIXELS, 0); glPixelStorei(GL_PACK_SKIP_ROWS, 0);
            glReadPixels(0, 0, target.width, target.height, GL_RGBA, GL_UNSIGNED_BYTE, data);
            byte[] bytes = new byte[data.remaining()]; data.get(bytes); return bytes;
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, saved); glBindBuffer(GL_PIXEL_PACK_BUFFER, pack);
            glPixelStorei(GL_PACK_ALIGNMENT, alignment); glPixelStorei(GL_PACK_ROW_LENGTH, row); glPixelStorei(GL_PACK_SKIP_PIXELS, skipX); glPixelStorei(GL_PACK_SKIP_ROWS, skipY);
            MemoryUtil.memFree(data);
        }
    }
    private static final class Target implements AutoCloseable {
        final int width, height, framebuffer;
        final GpuTexture texture;
        final GpuTextureView view;
        Target(int width, int height) {
            this.width = width; this.height = height;
            texture = RenderSystem.getDevice().createTexture(() -> "Interop test scene",
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                    GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            view = RenderSystem.getDevice().createTextureView(texture);
            framebuffer = glGenFramebuffers();
            int saved = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, ((GlTexture) texture).glId(), 0);
            require(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "Test framebuffer incomplete");
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, saved);
        }
        @Override public void close() { glDeleteFramebuffers(framebuffer); view.close(); texture.close(); }
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
