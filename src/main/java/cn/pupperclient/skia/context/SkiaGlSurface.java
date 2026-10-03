package cn.pupperclient.skia.context;

import cn.pupperclient.skia.api.WrappedBackendRenderTarget;
import com.mojang.blaze3d.opengl.GlTexture;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import java.util.Objects;
import static org.lwjgl.opengl.GL33C.*;

/** The only native framebuffer bridge. The attached texture remains owned by Blaze3D. */
final class SkiaGlSurface implements AutoCloseable {
    private final int framebuffer;
    private int stencil;
    private WrappedBackendRenderTarget target;
    private Surface surface;

    // Call creation and disposal inside States.push()/popMinecraft().
    SkiaGlSurface(DirectContext context, GlTexture texture, int mip, boolean withStencil) {
        framebuffer = glGenFramebuffers();
        try {
            int width = texture.getWidth(mip), height = texture.getHeight(mip);
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture.glId(), mip);
            if (withStencil) {
                stencil = glGenRenderbuffers();
                glBindRenderbuffer(GL_RENDERBUFFER, stencil);
                glRenderbufferStorage(GL_RENDERBUFFER, GL_STENCIL_INDEX8, width, height);
                glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, stencil);
            }
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete Skia UI framebuffer");
            // Skia's UI is RGBA8. Scene wrapping is limited to the same format by the caller.
            target = WrappedBackendRenderTarget.makeGL(width, height, 0, withStencil ? 8 : 0, framebuffer, GL_RGBA8);
            surface = Objects.requireNonNull(Surface.wrapBackendRenderTarget(context, target,
                    SurfaceOrigin.BOTTOM_LEFT, ColorType.RGBA_8888, null), "Unable to wrap Skia UI texture");
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    Surface surface() { return surface; }

    @Override public void close() {
        if (surface != null) { surface.close(); surface = null; }
        if (target != null) { target.close(); target = null; }
        if (stencil != 0) { glDeleteRenderbuffers(stencil); stencil = 0; }
        glDeleteFramebuffers(framebuffer);
    }
}
