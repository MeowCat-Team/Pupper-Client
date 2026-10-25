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

package cn.pupperclient.skia;

import java.awt.Color;

import cn.pupperclient.skia.font.TextMetrics;
import cn.pupperclient.ui.render.UiCanvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.types.Rect;

/** Text placement and measurement on the active canvas. */
final class SkiaText {

    private SkiaText() {}

    private static UiCanvas canvas() {
        return Skia.getCanvas();
    }

    static void drawText(String text, float x, float y, Color color, Font font) {
        Rect bounds = TextMetrics.bounds(font, text);
        canvas().drawString(text, x - bounds.getLeft(), y - bounds.getTop(), font, SkiaPaint.of(color));
    }

    static void drawCenteredText(String text, float x, float y, Color color, Font font) {
        Rect bounds = TextMetrics.bounds(font, text);
        canvas().drawString(text, x - bounds.getLeft() - (bounds.getWidth() / 2), y - bounds.getTop(), font,
            SkiaPaint.of(color));
    }

    static void drawHeightCenteredText(String text, float x, float y, Color color, Font font) {
        FontMetrics metrics = TextMetrics.metrics(font);
        Rect bounds = TextMetrics.bounds(font, text);
        float centerY = y + (metrics.getAscent() - metrics.getDescent()) / 2 - metrics.getAscent();
        canvas().drawString(text, x - bounds.getLeft(), centerY, font, SkiaPaint.of(color));
    }

    static void drawFullCenteredText(String text, float x, float y, Color color, Font font) {
        Rect bounds = TextMetrics.bounds(font, text);
        FontMetrics metrics = TextMetrics.metrics(font);
        float centerX = x - bounds.getLeft() - (bounds.getWidth() / 2);
        float centerY = y + (metrics.getAscent() - metrics.getDescent()) / 2 - metrics.getAscent();
        canvas().drawString(text, centerX, centerY, font, SkiaPaint.of(color));
    }

    static Rect getTextBounds(String text, Font font) {
        return TextMetrics.bounds(font, text);
    }

    static String getLimitText(String text, Font font, float width) {
        if (text == null || text.isEmpty() || !(width > 0)) return "";
        if (getTextBounds(text, font).getWidth() <= width) return text;
        String ellipsis = "…";
        if (getTextBounds(ellipsis, font).getWidth() > width) return "";
        int low = 0, high = text.codePointCount(0, text.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            int end = text.offsetByCodePoints(0, middle);
            if (getTextBounds(text.substring(0, end) + ellipsis, font).getWidth() <= width) low = middle;
            else high = middle - 1;
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + ellipsis;
    }
}
