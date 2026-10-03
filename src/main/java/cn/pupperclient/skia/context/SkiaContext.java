package cn.pupperclient.skia.context;

import java.util.Objects;
import java.util.function.Consumer;

import cn.pupperclient.PupperLogger;
import cn.pupperclient.skia.GlassRenderer;
import cn.pupperclient.skia.api.WrappedBackendRenderTarget;
import cn.pupperclient.skia.gl.States;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.humbleui.skija.*;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Skia rendering context manager for Pupper Client.
 * Handles Skia DirectContext, Surface, and BackendRenderTarget creation and drawing.
 */
public class SkiaContext {
    private static DirectContext context = null; // Skia GPU context
    private static Surface surface; // Skia drawing surface
    private static WrappedBackendRenderTarget renderTarget; // Backend render target for GL
    private static SkiaUiLayer uiLayer;
    private static boolean reportedFailure;

    /** Called before GpuSurface.blitFromTexture, while Minecraft still owns the frame. */
    public static void drawOffscreen(GpuTextureView destination, Consumer<Canvas> drawing, boolean captureGlass) {
        if (destination == null || destination.isClosed()) return;
        int width = destination.getWidth(0), height = destination.getHeight(0);
        if (width <= 0 || height <= 0) return;
        boolean gpu = destination.texture() instanceof GlTexture;
        try {
            if (gpu && context == null) {
                States.push();
                try { context = DirectContext.makeGL(); }
                finally { States.popMinecraft(); }
            }
            if (uiLayer == null || !uiLayer.matches(width, height, gpu)) {
                if (uiLayer != null) { uiLayer.close(); uiLayer = null; surface = null; }
                uiLayer = new SkiaUiLayer(width, height, gpu ? context : null);
            }
            surface = uiLayer.surface();
            uiLayer.draw(destination, drawing, captureGlass);
            reportedFailure = false;
        } catch (RuntimeException failure) {
            if (!reportedFailure) PupperLogger.error("Skia", "Failed to render the UI layer", failure);
            reportedFailure = true;
        }
    }

    /**
     * Gets the current Skia canvas for drawing.
     * @return The Skia Canvas object, or null if surface is not initialized.
     */
    public static Canvas getCanvas() {
        return surface.getCanvas();
    }

    /**
     * Explicit framebuffer path for isolated GPU verification. Production uses drawOffscreen.
     * @param width The width of the surface in pixels.
     * @param height The height of the surface in pixels.
     * @param fboid framebuffer object id, or null to use the currently bound draw framebuffer
     */
    public static void createSurface(int width, int height, Integer fboid) {
        States.push();
        try {
            if (context == null) context = DirectContext.makeGL();
            GlassRenderer.endFrame();
            if (surface != null) {
                surface.close();
                surface = null;
            }
            if (renderTarget != null) {
                renderTarget.close();
                renderTarget = null;
            }
            // Get current framebuffer binding
            int currentFbo = fboid == null ? GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) : fboid;

            // Create GL backend render target using current framebuffer
            renderTarget = WrappedBackendRenderTarget.makeGL(
                width,
                height,
                0, // sample count
                8, // stencil bits
                currentFbo, // framebuffer ID (use current)
                FramebufferFormat.GR_GL_RGBA8 // RGBA8 format
            );

            // Wrap the render target into a Skia surface
            surface = Surface.wrapBackendRenderTarget(
                Objects.requireNonNull(context, "Context must not be null"),
                Objects.requireNonNull(renderTarget, "RenderTarget must not be null"),
                SurfaceOrigin.BOTTOM_LEFT, // Origin for GL
                ColorType.RGBA_8888, // Color format
                // Minecraft's presented RGBA8 scene already contains display-ready RGB. Keep
                // it untagged here so snapshots and offscreen layers preserve those bytes.
                null
            );

            PupperLogger.info("Skia", "Created surface with fbo=" + currentFbo + ", size=" + width + "x" + height);
        } catch (Exception e) {
            PupperLogger.error("Skia", "Failed to create Skia surface: ", e);
        } finally {
            States.pop();
        }
    }

    /**
     * Performs Skia drawing operations.
     * Pushes GL states, resets Skia context, executes drawing logic, and submits to GL.
     * @param drawingLogic A consumer that takes a Canvas and performs drawing operations.
     */
    public static void draw(Consumer<Canvas> drawingLogic) {
        draw(drawingLogic, true);
    }

    /** With glass disabled, skip the full framebuffer snapshot entirely. */
    public static void draw(Consumer<Canvas> drawingLogic, boolean captureGlass) {
        if (context == null || surface == null) {
            PupperLogger.warn("Skia", "Context or surface is null, skipping draw");
            return;
        }

        if (renderTarget == null) {
            PupperLogger.warn("Skia", "RenderTarget is null, skipping draw");
            return;
        }

        States.push();
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            context.resetGLAll();
            // Minecraft writes this wrapped render target between Skia frames. Invalidate
            // Skia's cached image generation while retaining the scene we are about to sample.
            surface.notifyContentWillChange(ContentChangeMode.RETAIN);
            Canvas canvas = getCanvas();
            int saved = canvas.save();
            try {
                if (captureGlass) GlassRenderer.beginFrame(surface);
                drawingLogic.accept(canvas);
            } finally {
                canvas.restoreToCount(saved);
                context.flushAndSubmit(surface);
            }
        } finally {
            try {
                GlassRenderer.endFrame();
            } finally {
                States.pop();
            }
        }
    }

    /**
     * Gets the current Skia DirectContext.
     * @return The DirectContext, or null if not initialized.
     */
    public static DirectContext getContext() {
        return context;
    }

    /** Release native wrappers before Blaze3D shuts down its device. */
    public static void close() {
        if (uiLayer != null) { uiLayer.close(); uiLayer = null; surface = null; }
        if (context == null) return;
        States.push();
        try {
            GlassRenderer.endFrame();
            if (surface != null) { surface.close(); surface = null; }
            if (renderTarget != null) { renderTarget.close(); renderTarget = null; }
            context.close();
            context = null;
        } finally {
            if (RenderSystem.tryGetDevice() != null) States.popMinecraft();
            else States.pop();
        }
        reportedFailure = false;
    }
}
