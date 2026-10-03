package cn.pupperclient.hud;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.GlBackend;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import net.minecraft.resources.Identifier;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Native calls are confined to test-device setup, pixel readback and timer queries. */
final class UiGpuFixture implements AutoCloseable {
    private final GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err);
    private final long window;
    UiGpuFixture() {
        glfwSetErrorCallback(errors);
        if (!glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        glfwDefaultWindowHints();
        var backend = new GlBackend(); backend.setWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        window = glfwCreateWindow(128, 128, "Pupper Client Blaze3D verification", 0, 0);
        if (window == 0) throw new IllegalStateException("Hidden GPU device creation failed");
        RenderSystem.initRenderThread();
        var device = backend.createDevice(window, UiGpuFixture::shader, new GpuDebugOptions(0, false, false, false), () -> {});
        RenderSystem.initRenderer(device);
        System.out.println("Blaze3D GPU: " + glGetString(GL_RENDERER) + ", " + glGetString(GL_VERSION));
    }
    private static String shader(Identifier id, ShaderType type) {
        if (id.getPath().equals("test_lightmap") && type == ShaderType.FRAGMENT) return """
                #version 330
                uniform sampler2D Base;
                uniform sampler2D Lightmap;
                in vec2 texCoord;
                out vec4 fragColor;
                void main() { fragColor = texture(Base, texCoord) * texture(Lightmap, texCoord); }
                """;
        String path = "/assets/" + id.getNamespace() + "/shaders/" + id.getPath() + (type == ShaderType.VERTEX ? ".vsh" : ".fsh");
        try (InputStream source = UiGpuFixture.class.getResourceAsStream(path)) {
            if (source == null) throw new IllegalStateException("Missing shader " + path);
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    static void finish() { RenderSystem.getDevice().createCommandEncoder().submit(); glFinish(); }
    @Override public void close() {
        finish();
        RenderSystem.shutdownRenderer();
        glfwMakeContextCurrent(0); GL.setCapabilities(null); glfwDestroyWindow(window);
        glfwTerminate(); glfwSetErrorCallback(null); errors.free();
    }
    static final class Target implements AutoCloseable {
        final int width, height, framebuffer;
        final GpuTexture texture;
        final GpuTextureView view;
        Target(int width, int height) {
            this.width = width; this.height = height;
            texture = RenderSystem.getDevice().createTexture("UI verification scene",
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                    GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            view = RenderSystem.getDevice().createTextureView(texture);
            framebuffer = glGenFramebuffers();
            int previous = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, ((GlTexture) texture).glId(), 0);
            if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Incomplete fixture framebuffer");
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previous);
        }
        void clear(float r, float g, float b, float a) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, new Vector4f(r, g, b, a));
        }
        byte[] read() {
            finish();
            int previous = glGetInteger(GL_READ_FRAMEBUFFER_BINDING), pack = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
            int alignment = glGetInteger(GL_PACK_ALIGNMENT), row = glGetInteger(GL_PACK_ROW_LENGTH);
            int skipX = glGetInteger(GL_PACK_SKIP_PIXELS), skipY = glGetInteger(GL_PACK_SKIP_ROWS);
            ByteBuffer data = MemoryUtil.memAlloc(width * height * 4);
            try {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer); glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
                glPixelStorei(GL_PACK_ALIGNMENT, 1); glPixelStorei(GL_PACK_ROW_LENGTH, 0); glPixelStorei(GL_PACK_SKIP_PIXELS, 0); glPixelStorei(GL_PACK_SKIP_ROWS, 0);
                glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, data);
                byte[] bytes = new byte[data.remaining()]; data.get(bytes); return bytes;
            } finally {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, previous); glBindBuffer(GL_PIXEL_PACK_BUFFER, pack);
                glPixelStorei(GL_PACK_ALIGNMENT, alignment); glPixelStorei(GL_PACK_ROW_LENGTH, row); glPixelStorei(GL_PACK_SKIP_PIXELS, skipX); glPixelStorei(GL_PACK_SKIP_ROWS, skipY);
                MemoryUtil.memFree(data);
            }
        }
        int channel(byte[] bytes, int x, int y, int channel) { return bytes[((height - y - 1) * width + x) * 4 + channel] & 255; }
        void pixel(int x, int y, int r, int g, int b, int a, String message) {
            byte[] data = read(); int[] expected = {r, g, b, a};
            for (int c = 0; c < 4; c++) if (Math.abs(channel(data, x, y, c) - expected[c]) > 2)
                throw new AssertionError(message + ": channel " + c + ", expected " + expected[c] + ", got " + channel(data, x, y, c));
        }
        @Override public void close() { glDeleteFramebuffers(framebuffer); view.close(); texture.close(); }
    }
}
