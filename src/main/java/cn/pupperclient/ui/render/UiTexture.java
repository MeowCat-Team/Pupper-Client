package cn.pupperclient.ui.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

/** Explicit ownership of a Blaze3D texture and view; borrowed game textures never enter this class. */
final class UiTexture implements AutoCloseable {
    final GpuTexture texture;
    final GpuTextureView view;
    UiTexture(String label, int width, int height, GpuFormat format) {
        texture = RenderSystem.getDevice().createTexture(label,
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST,
                format, width, height, 1, 1);
        try { view = RenderSystem.getDevice().createTextureView(texture); }
        catch (RuntimeException | Error failure) { texture.close(); throw failure; }
    }
    @Override public void close() { view.close(); texture.close(); }
}
