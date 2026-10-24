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

package cn.pupperclient.management.mod.api.hud.design;

import cn.pupperclient.skia.font.Fonts;
import io.github.humbleui.skija.Font;

/** Dimensions are Minecraft GUI units, not Android dp or physical pixels. */
public final class HUDTokens {
    public static final float GAP = 4;
    public static final float PADDING = 8;
    public static final float RADIUS = 12;
    public static final float COMPACT_RADIUS = 8;
    public static final float ROW_HEIGHT = 20;
    public static final float LINE_HEIGHT = 14;
    public static final float BODY_SIZE = 10;
    public static final float LABEL_SIZE = 9;
    public static final float TITLE_SIZE = 11;
    public static final float ICON_SIZE = 12;
    public static final float CHIP_HEIGHT = 24;
    private HUDTokens() {}
    // Reuse native font objects instead of allocating one per label per frame.
    private static final class Typography {
        static final Font BODY = Fonts.getRegular(BODY_SIZE);
        static final Font VALUE = Fonts.getMedium(BODY_SIZE);
        static final Font LABEL = Fonts.getRegular(LABEL_SIZE);
        static final Font TITLE = Fonts.getMedium(TITLE_SIZE);
        static final Font ICON = Fonts.getIcon(ICON_SIZE);
    }
    public static Font body() { return Typography.BODY; }
    public static Font value() { return Typography.VALUE; }
    public static Font label() { return Typography.LABEL; }
    public static Font title() { return Typography.TITLE; }
    public static Font icon() { return Typography.ICON; }
}
