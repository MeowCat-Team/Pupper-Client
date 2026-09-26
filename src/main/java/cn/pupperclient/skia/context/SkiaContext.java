package cn.pupperclient.skia.context;

import java.util.Objects;
import java.util.function.Consumer;

import cn.pupperclient.PupperLogger;
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
     * @param fboid framebuffer object id, if 0 or null, will use currently bound framebuffer
     */
    public static void createSurface(int width, int height, Integer fboid) {
        // Initialize Skia DirectContext if not already done
        if (context == null) {
            context = DirectContext.makeGL();
        }

        // Clean up existing surface and render target
        if (surface != null) surface.close();
        if (renderTarget != null) renderTarget.close();

        try {
            // Get current framebuffer binding
            int currentFbo = fboid == null || fboid == 0 ? GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) : fboid;

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
                ColorSpace.getSRGB() // sRGB color space
            );

            PupperLogger.info("Skia", "Created surface with fbo=" + currentFbo + ", size=" + width + "x" + height);
        } catch (Exception e) {
            PupperLogger.error("Skia", "Failed to create Skia surface: ", e);
        }
    }

    /**
     * Performs Skia drawing operations.
     * Pushes GL states, resets Skia context, executes drawing logic, and submits to GL.
     * @param drawingLogic A consumer that takes a Canvas and performs drawing operations.
     */
    public static void draw(Consumer<Canvas> drawingLogic) {
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
            context.resetGLAll();
            Canvas canvas = getCanvas();
            drawingLogic.accept(canvas);
            context.flushAndSubmit(surface);
        } finally {
            States.pop();
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
