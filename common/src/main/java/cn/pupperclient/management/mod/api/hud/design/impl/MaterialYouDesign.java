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

package cn.pupperclient.management.mod.api.hud.design.impl;
import java.awt.Color;
import cn.pupperclient.management.mod.api.hud.design.HUDDesign;
import cn.pupperclient.management.mod.api.hud.design.HUDColors;
import cn.pupperclient.skia.Skia;

/** Material surfaces with a quiet glass rim; accent colors belong to content and states. */
public class MaterialYouDesign extends HUDDesign {
    public MaterialYouDesign() { super("design.materialyou"); }
    @Override protected boolean outlined() { return true; }
    @Override protected Color outlineColor(HUDColors colors) {
        Color outline = colors.outline();
        return new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), 68);
    }

    @Override public void drawBackground(float x, float y, float width, float height, float corner) {
        super.drawBackground(x, y, width, height, corner);
        if (width <= 2 || height <= 2) return;
        float opacity = colors().surface().getAlpha() / 255f;
        if (opacity <= 0 || opacity >= 1) return;
        float r = Math.min(radius(corner), Math.min(width, height) / 2);
        // A clipped inner rim suggests reflected light without tinting the whole panel.
        Skia.save();
        try {
            Skia.clip(x, y, width, height / 2, 0);
            Skia.drawOutline(x + 0.75f, y + 0.75f, width - 1.5f, height - 1.5f,
                Math.max(0, r - 0.75f), 0.65f, new Color(255, 255, 255, Math.round(54 * (1 - opacity))));
        } finally { Skia.restore(); }
    }
}
