package cn.pupperclient.hud;

import cn.pupperclient.utils.render.StartupRenderUniforms;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlBackend;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Reads the production startup uniform back from a real hidden OpenGL device. */
public final class StartupGpuChecks {
    private static int checks;

    public static void main(String[] args) {
        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err);
        glfwSetErrorCallback(errorCallback);
        long window = 0;
        boolean rendererReady = false;
        try {
            require(glfwInit(), "Unable to initialize GLFW");
            glfwDefaultWindowHints();
            var backend = new GlBackend();
            backend.setWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            window = glfwCreateWindow(128, 128, "Pupper Client startup GPU checks", 0, 0);
            require(window != 0, "Unable to create hidden OpenGL context");
            RenderSystem.initRenderThread();
            var device = backend.createDevice(window, (id, type) -> null,
                    new GpuDebugOptions(0, false, false, false), () -> {});
            RenderSystem.initRenderer(device);
            rendererReady = true;
            System.out.println("Startup GPU checks: " + glGetString(GL_RENDERER) + ", " + glGetString(GL_VERSION));
            try (var uniform = new GlobalSettingsUniform(); var otherUniform = new GlobalSettingsUniform()) {
                require(RenderSystem.getGlobalSettingsUniform() == null, "Globals unexpectedly initialized before startup hook");
                StartupRenderUniforms.initialize(uniform, 854, 480, 0.75, 4);
                GpuBuffer buffer = RenderSystem.getGlobalSettingsUniform();
                require(buffer != null && !buffer.isClosed(), "Startup initializer did not publish a live buffer");
                verifyContents(buffer, 854, 480, 0.75F, 0, 4, 0);
                StartupRenderUniforms.initialize(otherUniform, 1920, 1080, 0.1, 0);
                require(RenderSystem.getGlobalSettingsUniform() == buffer, "Startup hook replaced an already published uniform");
                verifyContents(buffer, 854, 480, 0.75F, 0, 4, 0);

                uniform.update(1920, 1080, 0.5, 6000L, DeltaTracker.ZERO, 2, Vec3.ZERO, true);
                require(RenderSystem.getGlobalSettingsUniform() == buffer, "First frame replaced renderer-owned buffer");
                verifyContents(buffer, 1920, 1080, 0.5F, 0.25F, 2, 1);
                RenderSystem.setGlobalSettingsUniform(null);
                StartupRenderUniforms.initialize(uniform, 0, -1, 1, 0);
                require(RenderSystem.getGlobalSettingsUniform() == buffer, "Reset did not reuse renderer-owned buffer");
                verifyContents(buffer, 1, 1, 1, 0, 0, 0);
                require(glGetError() == GL_NO_ERROR, "OpenGL error after Globals initialization");
            } finally { RenderSystem.setGlobalSettingsUniform(null); }
            System.out.println("Startup GPU checks passed: " + checks + " assertions; GPU contents, existing state, first-frame update and reset.");
        } finally {
            if (rendererReady) RenderSystem.shutdownRenderer();
            if (window != 0) {
                glfwMakeContextCurrent(0);
                GL.setCapabilities(null);
                glfwDestroyWindow(window);
            }
            glfwTerminate();
            glfwSetErrorCallback(null);
            errorCallback.free();
        }
    }

    private static void verifyContents(GpuBuffer buffer, int width, int height, float glint, float time, int blur, int rgss) {
        try (var stack = MemoryStack.stackPush()) {
            ByteBuffer contents = stack.malloc(GlobalSettingsUniform.UBO_SIZE).order(ByteOrder.nativeOrder());
            glBindBuffer(GL_UNIFORM_BUFFER, ((GlBuffer) buffer).handle());
            glGetBufferSubData(GL_UNIFORM_BUFFER, 0, contents);
            glBindBuffer(GL_UNIFORM_BUFFER, 0);
            require(contents.getInt(0) == 0 && contents.getInt(4) == 0 && contents.getInt(8) == 0, "Invalid initial camera block position");
            require(contents.getFloat(16) == 0 && contents.getFloat(20) == 0 && contents.getFloat(24) == 0, "Invalid initial camera offset");
            require(contents.getFloat(32) == width && contents.getFloat(36) == height, "Invalid framebuffer size");
            require(contents.getFloat(40) == glint && contents.getFloat(44) == time, "Invalid glint or game time");
            require(contents.getInt(48) == blur && contents.getInt(52) == rgss, "Invalid blur or texture filtering settings");
        }
    }

    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
