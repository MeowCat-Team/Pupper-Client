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

package cn.pupperclient.management.cape;

import cn.pupperclient.PupperLogger;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.ui.render.UiCanvas.ImageSampling;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class CapeRenderer {
    private CapeRenderer() {}

    public static void renderCapePreview(Identifier capeTexture, float x, float y) {
        if (capeTexture == null) return;
        try {
            var view = Minecraft.getInstance().getTextureManager().getTexture(capeTexture).getTextureView();
            if (view == null || view.isClosed()) return;
            draw(view, 1, Rect.makeXYWH(x + 2, y + 8, 20, 32));
            draw(view, 12, Rect.makeXYWH(x + 26, y + 8, 20, 32));
        } catch (Exception failure) {
            PupperLogger.warn("CapeRenderer", "Failed to render cape preview: " + failure.getMessage());
        }
    }

    public static void renderRoundedCapePreview(Identifier capeTexture, float x, float y,
                                                float width, float height, float radius) {
        if (capeTexture == null) return;
        try {
            var view = Minecraft.getInstance().getTextureManager().getTexture(capeTexture).getTextureView();
            if (view == null || view.isClosed()) return;
            try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
                int saved = Skia.getCanvas().save();
                try {
                    Skia.getCanvas().clipPath(path, ClipMode.INTERSECT, true);
                    draw(view, 1, Rect.makeXYWH(x, y, width, height));
                } finally { Skia.getCanvas().restoreToCount(saved); }
            }
        } catch (Exception failure) {
            PupperLogger.warn("CapeRenderer", "Failed to render rounded cape preview: " + failure.getMessage());
        }
    }

    private static void draw(GpuTextureView view, int sourceX, Rect destination) {
        // Canonical cape coordinates are 64 x 32; resource packs may supply larger images.
        float sx = view.getWidth(0) / 64f, sy = view.getHeight(0) / 32f;
        Skia.getCanvas().drawTexture(view, Rect.makeXYWH(sourceX * sx, sy, 10 * sx, 16 * sy), destination, 1,
                ImageSampling.PIXEL);
    }
}
