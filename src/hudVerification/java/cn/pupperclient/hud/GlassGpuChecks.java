package cn.pupperclient.hud;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.Arrays;

import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.context.SkiaContext;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.SaveLayerRec;
import io.github.humbleui.types.Rect;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Real OpenGL interop checks; a raster surface cannot represent non-premultiplied game pixels. */
public final class GlassGpuChecks {
    private static final int SIZE = 128;
    private static final int PANEL_START = 24;
    private static final int PANEL_SIZE = 80;
    private static int scenarios;

    private GlassGpuChecks() {}

    public static void main(String[] args) {
        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err);
        glfwSetErrorCallback(errorCallback);
        long window = 0;
        try {
            require(glfwInit(), "Unable to initialize GLFW for glass GPU verification");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_STENCIL_BITS, 8);
            glfwWindowHint(GLFW_SAMPLES, 0);
            window = glfwCreateWindow(SIZE, SIZE, "Pupper Client glass GPU checks", 0, 0);
            require(window != 0, "Unable to create a hidden OpenGL context");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("Glass GPU checks: " + glGetString(GL_RENDERER) + ", " + glGetString(GL_VERSION));

            try (Framebuffer scene = new Framebuffer(); Framebuffer originalRead = new Framebuffer()) {
                glBindFramebuffer(GL_FRAMEBUFFER, scene.id);
                SkiaContext.createSurface(SIZE, SIZE, scene.id);
                diagnoseLegacyBackdrop(scene, originalRead);
                for (boolean framebufferSrgb : new boolean[]{false, true}) {
                    for (float alpha : new float[]{0, 1}) {
                        // Reuse the same Skia surface across differently colored Minecraft frames.
                        for (float[] rgb : new float[][]{{0.2F, 0.4F, 0.6F}, {0.65F, 0.3F, 0.15F}}) {
                            checkFrame(scene, originalRead, framebufferSrgb, alpha, rgb);
                        }
                    }
                }
                checkTransformedClip(scene, originalRead);
                checkAnimatedCoordinates(scene, originalRead);
                checkDisabledCapture(scene, originalRead);
                glFinish();
                require(glGetError() == GL_NO_ERROR, "OpenGL error after glass checks");
            }
            System.out.printf("Glass GPU checks passed: %d frames; raw RGB with alpha 0/1, sRGB state, "
                    + "scene-only capture, unchanged outside pixels, transforms/clips and GL state restoration.%n", scenarios);
        } finally {
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

    private static void diagnoseLegacyBackdrop(Framebuffer scene, Framebuffer originalRead) {
        for (float alpha : new float[]{0, 1}) {
            clearScene(scene, new float[]{0.2F, 0.4F, 0.6F}, alpha);
            byte[] before = readScene(scene);
            setIncomingState(scene, originalRead, false);
            SkiaContext.draw(canvas -> {
                try (ImageFilter blur = ImageFilter.makeBlur(3, 3, FilterTileMode.CLAMP)) {
                    canvas.save();
                    try {
                        Skia.clip(PANEL_START, PANEL_START, PANEL_SIZE, PANEL_SIZE, 16);
                        canvas.saveLayer(new SaveLayerRec(
                                Rect.makeXYWH(PANEL_START, PANEL_START, PANEL_SIZE, PANEL_SIZE), null, blur));
                        canvas.restore();
                    } finally {
                        canvas.restore();
                    }
                }
            });
            assertIncomingState(scene, originalRead, false);
            byte[] after = readScene(scene);
            System.out.printf("Legacy backdrop diagnostic, alpha %.0f: center RGBA (%d,%d,%d,%d) -> (%d,%d,%d,%d).%n",
                    alpha, channel(before, 64, 64, 0), channel(before, 64, 64, 1),
                    channel(before, 64, 64, 2), channel(before, 64, 64, 3),
                    channel(after, 64, 64, 0), channel(after, 64, 64, 1),
                    channel(after, 64, 64, 2), channel(after, 64, 64, 3));
        }
    }

    private static void checkFrame(Framebuffer scene, Framebuffer originalRead, boolean srgb,
                                   float alpha, float[] rgb) {
        clearScene(scene, rgb, alpha);
        byte[] before = readScene(scene);
        setIncomingState(scene, originalRead, srgb);
        SkiaContext.draw(canvas -> {
            // Glass must sample the frame captured before custom content, not this marker.
            Skia.drawRect(48, 48, 24, 24, Color.MAGENTA);
            Skia.drawGlassBackdrop(PANEL_START, PANEL_START, PANEL_SIZE, PANEL_SIZE, 16, 3.5F, 1.5F);
        });
        assertIncomingState(scene, originalRead, srgb);
        byte[] after = readScene(scene);
        String label = "frame " + scenarios + ", alpha=" + alpha + ", sRGB=" + srgb;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (x < PANEL_START || x >= PANEL_START + PANEL_SIZE
                        || y < PANEL_START || y >= PANEL_START + PANEL_SIZE) {
                    assertPixel(before, after, x, y, 0, true, label + " outside glass");
                }
            }
        }
        // A constant source must stay constant through filtering and refraction. Requiring alpha
        // one also catches an unavailable shader silently falling back to drawing nothing.
        for (int y = 42; y < 86; y++) {
            for (int x = 42; x < 86; x++) {
                assertPixel(before, after, x, y, 2, false, label + " glass interior");
                require(channel(after, x, y, 3) == 255, label + ": glass did not write opaque scene alpha");
            }
        }
        assertPixel(before, after, 25, 25, 0, true, label + " rounded corner");
        scenarios++;
    }

    private static void checkTransformedClip(Framebuffer scene, Framebuffer originalRead) {
        clearScene(scene, new float[]{0.25F, 0.5F, 0.75F}, 0);
        byte[] before = readScene(scene);
        setIncomingState(scene, originalRead, true);
        SkiaContext.draw(canvas -> {
            canvas.save();
            try {
                canvas.translate(16, 16);
                canvas.scale(2, 2);
                Skia.clip(8, 8, 32, 32, 8);
                Skia.drawGlassBackdrop(0, 0, 48, 48, 8, 2, 1.5F);
            } finally {
                canvas.restore();
            }
        });
        assertIncomingState(scene, originalRead, true);
        byte[] after = readScene(scene);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean outsideClipBounds = x < 32 || x >= 96 || y < 32 || y >= 96;
                if (outsideClipBounds) {
                    assertPixel(before, after, x, y, 0, true, "transformed clip exterior");
                }
            }
        }
        assertPixel(before, after, 64, 64, 2, false, "transformed glass interior");
        require(channel(after, 64, 64, 3) == 255, "Transformed glass did not write opaque scene alpha");
        scenarios++;
    }

    private static void checkAnimatedCoordinates(Framebuffer scene, Framebuffer originalRead) {
        clearScene(scene, new float[]{0.15F, 0.3F, 0.45F}, 0);
        glEnable(GL_SCISSOR_TEST);
        // Asymmetric color boundaries reveal any layer-local versus framebuffer coordinate mixup.
        glScissor(57, 0, SIZE - 57, SIZE);
        glClearColor(0.65F, 0.4F, 0.2F, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        glScissor(0, 73, SIZE, SIZE - 73);
        glClearColor(0.25F, 0.7F, 0.4F, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        byte[] before = readScene(scene);
        setIncomingState(scene, originalRead, false);
        SkiaContext.draw(canvas -> {
            canvas.save();
            try {
                Skia.clip(20, 30, 76, 66, 12);
                // Exercise the production entrance-animation layer, bounded away from the origin.
                canvas.translate(28, 38);
                canvas.scale(1.25F, 1.25F);
                Skia.setAlpha(128, -8, -8, 64, 56);
                try {
                    Skia.drawGlassBackdrop(0, 0, 48, 40, 8, 0, 0);
                } finally {
                    canvas.restore();
                }
            } finally {
                canvas.restore();
            }
        });
        assertIncomingState(scene, originalRead, false);
        byte[] after = readScene(scene);
        // With blur/refraction disabled, re-presenting the same scene at half opacity must not
        // move either color boundary or change brightness, including when source alpha is zero.
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                assertPixel(before, after, x, y, 2, false, "animated glass coordinates");
            }
        }
        require(channel(after, 48, 64, 3) >= 126 && channel(after, 48, 64, 3) <= 130,
                "Animated glass did not reach the expected half-opacity layer");
        scenarios++;
    }

    private static void checkDisabledCapture(Framebuffer scene, Framebuffer originalRead) {
        // The preceding frames captured a real scene. A disabled frame must release that snapshot
        // and leave glass drawing inert rather than drawing a stale scene over new content.
        clearScene(scene, new float[]{0.6F, 0.2F, 0.1F}, 0);
        byte[] before = readScene(scene);
        setIncomingState(scene, originalRead, true);
        SkiaContext.draw(canvas -> {
            Skia.drawRect(48, 48, 24, 24, Color.MAGENTA);
            Skia.drawGlassBackdrop(PANEL_START, PANEL_START, PANEL_SIZE, PANEL_SIZE, 16, 3.5F, 1.5F);
        }, false);
        assertIncomingState(scene, originalRead, true);
        byte[] after = readScene(scene);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean inMarker = x >= 48 && x < 72 && y >= SIZE - 72 && y < SIZE - 48;
                if (!inMarker) assertPixel(before, after, x, y, 0, true, "disabled glass capture");
            }
        }
        require(channel(after, 64, 64, 0) == 255 && channel(after, 64, 64, 1) == 0
                        && channel(after, 64, 64, 2) == 255 && channel(after, 64, 64, 3) == 255,
                "Disabled glass capture repainted the marker or reused a stale scene");
        scenarios++;
    }

    private static void clearScene(Framebuffer scene, float[] rgb, float alpha) {
        glBindFramebuffer(GL_FRAMEBUFFER, scene.id);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_FRAMEBUFFER_SRGB);
        glColorMask(true, true, true, true);
        glViewport(0, 0, SIZE, SIZE);
        glClearColor(rgb[0], rgb[1], rgb[2], alpha);
        glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    }

    private static void setIncomingState(Framebuffer scene, Framebuffer originalRead, boolean srgb) {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, scene.id);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, originalRead.id);
        if (srgb) glEnable(GL_FRAMEBUFFER_SRGB); else glDisable(GL_FRAMEBUFFER_SRGB);
        glColorMask(true, false, true, false);
        glViewport(3, 5, 101, 99);
        glEnable(GL_SCISSOR_TEST);
        glScissor(2, 7, 107, 95);
    }

    private static void assertIncomingState(Framebuffer scene, Framebuffer originalRead, boolean srgb) {
        require(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == scene.id, "Draw framebuffer was not restored");
        require(glGetInteger(GL_READ_FRAMEBUFFER_BINDING) == originalRead.id, "Read framebuffer was not restored");
        require(glIsEnabled(GL_FRAMEBUFFER_SRGB) == srgb, "Framebuffer sRGB state was not restored");
        require(glIsEnabled(GL_SCISSOR_TEST), "Scissor enable state was not restored");
        int[] viewport = new int[4];
        int[] scissor = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_SCISSOR_BOX, scissor);
        require(Arrays.equals(viewport, new int[]{3, 5, 101, 99}), "Viewport was not restored");
        require(Arrays.equals(scissor, new int[]{2, 7, 107, 95}), "Scissor bounds were not restored");
        ByteBuffer colorMask = MemoryUtil.memAlloc(4);
        try {
            glGetBooleanv(GL_COLOR_WRITEMASK, colorMask);
            require(colorMask.get(0) != 0 && colorMask.get(1) == 0
                    && colorMask.get(2) != 0 && colorMask.get(3) == 0, "Color write mask was not restored");
        } finally {
            MemoryUtil.memFree(colorMask);
        }
    }

    private static byte[] readScene(Framebuffer scene) {
        int previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, scene.id);
        ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        try {
            glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            byte[] result = new byte[pixels.remaining()];
            pixels.get(result);
            return result;
        } finally {
            MemoryUtil.memFree(pixels);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead);
        }
    }

    private static void assertPixel(byte[] expected, byte[] actual, int x, int y, int tolerance,
                                     boolean compareAlpha, String label) {
        for (int component = 0; component < (compareAlpha ? 4 : 3); component++) {
            int before = channel(expected, x, y, component);
            int after = channel(actual, x, y, component);
            require(Math.abs(before - after) <= tolerance, label + " changed pixel (" + x + "," + y
                    + ") channel " + component + " from " + before + " to " + after);
        }
    }

    private static int channel(byte[] pixels, int x, int y, int component) {
        return Byte.toUnsignedInt(pixels[(y * SIZE + x) * 4 + component]);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Framebuffer implements AutoCloseable {
        private final int id = glGenFramebuffers();
        private final int texture = glGenTextures();
        private final int stencil = glGenRenderbuffers();

        private Framebuffer() {
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SIZE, SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glBindFramebuffer(GL_FRAMEBUFFER, id);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            glBindRenderbuffer(GL_RENDERBUFFER, stencil);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_STENCIL_INDEX8, SIZE, SIZE);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, stencil);
            require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "Incomplete test framebuffer");
        }

        @Override
        public void close() {
            glDeleteFramebuffers(id);
            glDeleteTextures(texture);
            glDeleteRenderbuffers(stencil);
        }
    }
}
