/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
