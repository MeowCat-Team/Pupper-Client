package cn.pupperclient.hud;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.Arrays;

import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.context.SkiaContext;
import cn.pupperclient.skia.font.Fonts;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SaveLayerRec;
import io.github.humbleui.types.Rect;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Optional, real-GPU microbenchmark; timings are diagnostic and have no hardware-dependent pass threshold. */
public final class GlassGpuBenchmark {
    private static final int WIDTH = 2560;
    private static final int HEIGHT = 1540;
    private static final int WARMUP_FRAMES = 30;
    private static final int MEASURED_FRAMES = 120;
    private static final Color TINT = new Color(28, 31, 38, 115);
    private static final Color RIM = new Color(232, 236, 246, 28);
    private static final Color ACCENT = new Color(172, 198, 255);
    private static final float[][] HUD_PANELS = {
            {16, 16, 150, 700, 16}, {1110, 24, 340, 64, 24}, {2336, 28, 192, 76, 18},
            {2300, 136, 54, 54, 12}, {2238, 198, 54, 54, 12}, {2300, 198, 54, 54, 12},
            {2362, 198, 54, 54, 12}, {2238, 260, 178, 38, 12}, {2250, 1340, 250, 92, 20},
            {24, 1450, 280, 56, 18}, {950, 1430, 280, 78, 18}, {2000, 380, 210, 92, 18}
    };
    private static final float[] MENU_PANEL = {980, 570, 600, 400, 24};

    private GlassGpuBenchmark() {}

    public static void main(String[] args) {
        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err);
        glfwSetErrorCallback(errorCallback);
        long window = 0;
        try {
            if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_STENCIL_BITS, 8);
            glfwWindowHint(GLFW_SAMPLES, 0);
            window = glfwCreateWindow(WIDTH, HEIGHT, "Pupper Client glass GPU benchmark", 0, 0);
            if (window == 0) throw new IllegalStateException("Unable to create hidden OpenGL context");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.printf("Glass GPU benchmark: %s; %s; %dx%d; warmup %d + measured %d frames per case.%n",
                    glGetString(GL_RENDERER), glGetString(GL_VERSION), WIDTH, HEIGHT, WARMUP_FRAMES, MEASURED_FRAMES);
            System.out.println("CPU = Skia bridge submission including state/snapshot work; GPU = GL_TIME_ELAPSED. "
                    + "World simulation and timer-result waiting are excluded. This is an isolated UI cost, not game FPS.");
            System.out.println("Layout: 12 HUD panels including a 150x700 list, with/without a 600x400 menu; no full-screen blur.");
            try (Framebuffer target = new Framebuffer(); Paint textPaint = new Paint().setColor(0xFFF0F2F8);
                 ImageFilter legacyBlur = ImageFilter.makeBlur(5, 5, FilterTileMode.CLAMP)) {
                Font font = Fonts.getRegular(14);
                if (font.measureText("Pupper Client 144 FPS").getWidth() <= 0)
                    throw new AssertionError("Benchmark font must contain visible glyphs");
                glBindFramebuffer(GL_FRAMEBUFFER, target.id);
                SkiaContext.createSurface(WIDTH, HEIGHT, target.id);
                for (boolean menu : new boolean[]{false, true}) {
                    for (Mode mode : Mode.values()) runCase(target, font, textPaint, legacyBlur, mode, menu);
                }
                glFinish();
                int error = glGetError();
                if (error != GL_NO_ERROR) throw new AssertionError("OpenGL benchmark error: " + error);
            }
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

    private static void runCase(Framebuffer target, Font font, Paint textPaint, ImageFilter legacyBlur,
                                Mode mode, boolean menu) {
        for (int frame = 0; frame < WARMUP_FRAMES; frame++) {
            clearWorld(target, frame);
            drawUi(font, textPaint, legacyBlur, mode, menu);
            glFinish();
        }
        long[] cpuNanos = new long[MEASURED_FRAMES];
        long[] gpuNanos = new long[MEASURED_FRAMES];
        int query = glGenQueries();
        try {
            for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
                clearWorld(target, frame);
                glBeginQuery(GL_TIME_ELAPSED, query);
                long start = System.nanoTime();
                drawUi(font, textPaint, legacyBlur, mode, menu);
                cpuNanos[frame] = System.nanoTime() - start;
                glEndQuery(GL_TIME_ELAPSED);
                // The blocking query result read is deliberately outside the CPU submission timer.
                gpuNanos[frame] = glGetQueryObjectui64(query, GL_QUERY_RESULT);
            }
        } finally {
            glDeleteQueries(query);
        }
        System.out.printf("%-10s %-14s CPU ms avg/median/p95 %.3f / %.3f / %.3f; "
                        + "GPU ms avg/median/p95 %.3f / %.3f / %.3f%n",
                menu ? "HUD+menu" : "HUD", mode.label,
                averageMillis(cpuNanos), percentileMillis(cpuNanos, 0.5), percentileMillis(cpuNanos, 0.95),
                averageMillis(gpuNanos), percentileMillis(gpuNanos, 0.5), percentileMillis(gpuNanos, 0.95));
    }

    private static void drawUi(Font font, Paint textPaint, ImageFilter legacyBlur, Mode mode, boolean menu) {
        SkiaContext.draw(canvas -> {
            for (int index = 0; index < HUD_PANELS.length; index++) {
                float[] panel = HUD_PANELS[index];
                drawPanel(panel, mode, legacyBlur);
                int rows = index == 0 ? 24 : (panel[3] > 60 ? 2 : 1);
                for (int row = 0; row < rows; row++) {
                    canvas.drawString(index == 0 ? "Enabled module " + row : "Pupper Client 144 FPS",
                            panel[0] + 12, panel[1] + 23 + row * 26, font, textPaint);
                }
            }
            if (menu) {
                drawPanel(MENU_PANEL, mode, legacyBlur);
                float x = MENU_PANEL[0], y = MENU_PANEL[1];
                canvas.drawString("Welcome back. Settings and modules", x + 32, y + 42, font, textPaint);
                for (int card = 0; card < 3; card++) {
                    float cardX = x + 24 + card * 186;
                    Skia.drawRoundedRect(cardX, y + 72, 172, 130, 16, new Color(150, 163, 186, 35));
                    canvas.drawString("Material controls", cardX + 12, y + 102, font, textPaint);
                    for (int row = 0; row < 3; row++) {
                        Skia.drawRoundedRect(cardX + 12, y + 114 + row * 22, 148, 4, 2, ACCENT);
                    }
                }
                for (int row = 0; row < 5; row++) {
                    canvas.drawString("Glass opacity and appearance", x + 32, y + 235 + row * 27, font, textPaint);
                    Skia.drawRoundedRect(x + 370, y + 224 + row * 27, 140, 4, 2, ACCENT);
                    Skia.drawCircle(x + 420, y + 226 + row * 27, 7, ACCENT);
                }
            }
        }, mode == Mode.GLASS);
    }

    private static void drawPanel(float[] panel, Mode mode, ImageFilter legacyBlur) {
        if (mode == Mode.GLASS) {
            Skia.drawGlassBackdrop(panel[0], panel[1], panel[2], panel[3], panel[4], 2, 1.5F);
        } else if (mode == Mode.LEGACY_BLUR) {
            Skia.save();
            try {
                Skia.clip(panel[0], panel[1], panel[2], panel[3], panel[4]);
                Skia.getCanvas().saveLayer(new SaveLayerRec(
                        Rect.makeXYWH(panel[0], panel[1], panel[2], panel[3]), null, legacyBlur));
                Skia.restore();
            } finally {
                Skia.restore();
            }
        }
        if (mode != Mode.CONTENT) {
            Skia.drawRoundedRect(panel[0], panel[1], panel[2], panel[3], panel[4], TINT);
            Skia.drawOutline(panel[0], panel[1], panel[2], panel[3], panel[4], 1, RIM);
        }
    }

    private static void clearWorld(Framebuffer target, int frame) {
        glBindFramebuffer(GL_FRAMEBUFFER, target.id);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_FRAMEBUFFER_SRGB);
        glColorMask(true, true, true, true);
        glViewport(0, 0, WIDTH, HEIGHT);
        glClearColor(0.2F + (frame % 8) * 0.002F, 0.4F, 0.6F, 0);
        glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);
        glScissor(0, 0, WIDTH, HEIGHT / 3);
        glClearColor(0.32F, 0.26F, 0.14F, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST);
    }

    private static double averageMillis(long[] values) {
        return Arrays.stream(values).average().orElse(0) / 1_000_000.0;
    }

    private static double percentileMillis(long[] values, double percentile) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(percentile * sorted.length) - 1)] / 1_000_000.0;
    }

    private enum Mode {
        CONTENT("Skia content"), TINT("45% tint"), LEGACY_BLUR("legacy blur"), GLASS("tint + glass");
        private final String label;
        Mode(String label) { this.label = label; }
    }

    private static final class Framebuffer implements AutoCloseable {
        private final int id = glGenFramebuffers();
        private final int texture = glGenTextures();
        private final int stencil = glGenRenderbuffers();

        private Framebuffer() {
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glBindFramebuffer(GL_FRAMEBUFFER, id);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            glBindRenderbuffer(GL_RENDERBUFFER, stencil);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_STENCIL_INDEX8, WIDTH, HEIGHT);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, stencil);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Incomplete benchmark framebuffer");
            }
        }

        @Override
        public void close() {
            glDeleteFramebuffers(id);
            glDeleteTextures(texture);
            glDeleteRenderbuffers(stencil);
        }
    }
}
