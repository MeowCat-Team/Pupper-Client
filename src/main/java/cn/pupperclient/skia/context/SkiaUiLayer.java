package cn.pupperclient.skia.context;

import cn.pupperclient.skia.GlassRenderer;
import cn.pupperclient.skia.gl.States;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.humbleui.skija.*;
import java.util.Objects;
import java.util.function.Consumer;
import static org.lwjgl.opengl.GL33C.*;

/** Transparent UI storage; native Skia never draws to a Minecraft-owned framebuffer. */
public final class SkiaUiLayer implements AutoCloseable {
    private final DirectContext context;
    private final GpuTexture texture;
    private GpuTextureView view;
    private SkiaGlSurface gpuSurface, scene;
    private GpuTexture sceneTexture;
    private int sceneMip;
    private NativeImage pixels;
    private Surface surface;
    private boolean closed;

    /** A null context selects raster Skia + Blaze3D upload, with no OpenGL interop. */
    public SkiaUiLayer(int width, int height, DirectContext context) {
        this.context = context;
        texture = RenderSystem.getDevice().createTexture(() -> "Pupper Client Skia UI",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST,
                GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        try {
            view = RenderSystem.getDevice().createTextureView(texture);
            if (context != null) {
                States.push();
                try {
                    context.resetGLAll();
                    gpuSurface = new SkiaGlSurface(context, (GlTexture) texture, 0, true);
                    surface = gpuSurface.surface();
                } finally { States.popMinecraft(); }
            } else {
                pixels = new NativeImage(NativeImage.Format.RGBA, width, height, true);
                surface = Objects.requireNonNull(Surface.makeRasterDirect(
                        new ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL),
                        pixels.getPointer(), (long) width * 4), "Unable to create raster UI surface");
            }
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    public Surface surface() { return surface; }
    public boolean matches(int width, int height, boolean gpu) {
        return !closed && texture.getWidth(0) == width && texture.getHeight(0) == height && (context != null) == gpu;
    }

    public void draw(GpuTextureView destination, Consumer<Canvas> drawing, boolean glass) {
        if (closed) throw new IllegalStateException("Skia UI layer is closed");
        if (context != null) States.push();
        try {
            if (context != null) {
                glDisable(GL_CULL_FACE);
                glDisable(GL_FRAMEBUFFER_SRGB);
                context.resetGLAll();
                if (glass && destination.texture() instanceof GlTexture glTexture
                        && glTexture.getFormat() == GpuFormat.RGBA8_UNORM) {
                    if (sceneTexture != glTexture || sceneMip != destination.baseMipLevel()) {
                        if (scene != null) scene.close();
                        scene = null;
                        sceneTexture = null;
                        scene = new SkiaGlSurface(context, glTexture, destination.baseMipLevel(), false);
                        sceneTexture = glTexture;
                        sceneMip = destination.baseMipLevel();
                    }
                    scene.surface().notifyContentWillChange(ContentChangeMode.RETAIN);
                    GlassRenderer.beginFrame(scene.surface());
                }
            }
            Canvas canvas = surface.getCanvas();
            int saved = canvas.save();
            try {
                canvas.clear(0);
                drawing.accept(canvas);
            } finally {
                canvas.restoreToCount(saved);
                if (context != null) context.flushAndSubmit(surface);
            }
        } finally {
            try { GlassRenderer.endFrame(); }
            finally { if (context != null) States.popMinecraft(); }
        }
        // A failed callback never composites a partial UI onto the scene.
        if (context == null) RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, pixels);
        SkiaUiCompositor.composite(destination, view, context == null);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (context != null) States.push();
        try {
            GlassRenderer.endFrame();
            if (scene != null) { scene.close(); scene = null; }
            if (gpuSurface != null) { gpuSurface.close(); gpuSurface = null; }
            else if (surface != null) surface.close();
            surface = null;
        } finally { if (context != null) States.popMinecraft(); }
        if (pixels != null) { pixels.close(); pixels = null; }
        if (view != null) view.close();
        texture.close();
    }
}
