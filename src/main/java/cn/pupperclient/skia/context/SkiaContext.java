package cn.pupperclient.skia.context;

import java.util.Objects;
import java.util.function.Consumer;

import cn.pupperclient.PupperLogger;
import cn.pupperclient.skia.GlassRenderer;
import cn.pupperclient.skia.api.WrappedBackendRenderTarget;
import cn.pupperclient.skia.gl.States;
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

    /**
     * Gets the current Skia canvas for drawing.
     * @return The Skia Canvas object, or null if surface is not initialized.
     */
    public static Canvas getCanvas() {
        return surface.getCanvas();
    }

    /**
     * Creates or recreates the Skia surface with the given dimensions.
     * This should be called when the window size changes.
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
}
