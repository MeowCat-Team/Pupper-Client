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

import cn.pupperclient.ui.render.UiCanvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

/** Filled and stroked shapes, gradients and the glass backdrop. */
final class SkiaShape {

    private SkiaShape() {}

    private static UiCanvas canvas() {
        return Skia.getCanvas();
    }

    static void drawRect(float x, float y, float width, float height, Color color) {
        canvas().drawRect(Rect.makeXYWH(x, y, width, height), SkiaPaint.of(color));
    }

    static void drawCircle(float x, float y, float radius, Color color) {
        canvas().drawCircle(x, y, radius, SkiaPaint.of(color));
    }

    static void drawCircle(float x, float y, float radius, float strokeWidth, Color color) {
        Paint paint = SkiaPaint.of(color);
        paint.setMode(PaintMode.STROKE);
        paint.setStrokeWidth(strokeWidth);
        canvas().drawCircle(x, y, radius, paint);
        paint.setMode(PaintMode.FILL);
    }

    static void drawRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        canvas().drawRRect(RRect.makeXYWH(x, y, width, height, radius), SkiaPaint.of(color));
    }

    static void drawRoundedRectVarying(float x, float y, float width, float height, float topLeft,
                                       float topRight, float bottomRight, float bottomLeft, Color color) {
        float[] corners = new float[] {topLeft, topLeft, topRight, topRight, bottomRight, bottomRight,
            bottomLeft, bottomLeft};
        canvas().drawRRect(RRect.makeComplexXYWH(x, y, width, height, corners), SkiaPaint.of(color));
    }

    static void drawShadow(float x, float y, float width, float height, float radius) {
        SkiaTransform.save();
        try {
            SkiaTransform.clip(x, y, width, height, radius, ClipMode.DIFFERENCE);
            canvas().drawShadow(RRect.makeXYWH(x, y, width, height, radius), 2.5f, 0x78000000);
        } finally {
            SkiaTransform.restore();
        }
    }

    static void drawBackdropBlur(float x, float y, float width, float height, float radius) {
        drawBackdropBlur(x, y, width, height, radius, 5);
    }

    static void drawBackdropBlur(float x, float y, float width, float height, float radius, float strength) {
        drawGlassBackdrop(x, y, width, height, radius, strength, 0);
    }

    static void drawGlassBackdrop(float x, float y, float width, float height, float radius,
                                  float strength, float refraction) {
        GlassRenderer.draw(canvas(), x, y, width, height, radius, strength, refraction);
    }

    static void drawOutline(float x, float y, float width, float height, float radius, float strokeWidth,
                            Color color) {
        float halfStroke = strokeWidth / 2;
        Paint paint = SkiaPaint.of(color);
        try {
            paint.setStrokeWidth(strokeWidth);
            paint.setMode(PaintMode.STROKE);
            canvas().drawRRect(RRect.makeXYWH(x + halfStroke, y + halfStroke,
                width - strokeWidth, height - strokeWidth, radius - halfStroke), paint);
        } finally {
            paint.setMode(PaintMode.FILL);
            paint.setStrokeWidth(1);
        }
    }

    static void drawArc(float x, float y, float radius, float startAngle, float endAngle, float strokeWidth,
                        Color color) {
        Paint paint = SkiaPaint.of(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setMode(PaintMode.STROKE);
        canvas().drawArc(x - radius, y - radius, x + radius, y + radius, startAngle - 90, endAngle, false, paint);
        paint.setMode(PaintMode.FILL);
    }

    static void drawLine(float x, float y, float endX, float endY, float width, Color color) {
        Paint paint = SkiaPaint.of(color);
        paint.setStroke(true);
        paint.setStrokeWidth(width);
        paint.setAntiAlias(true);
        canvas().drawLine(x, y, endX, endY, paint);
        paint.setStroke(false);
    }

    static void drawGradientRoundedRect(float x, float y, float width, float height, float radius, Color color1,
                                        Color color2) {
        long currentTime = System.nanoTime();
        double speed = 0.0000000006;
        double tick = (currentTime * speed) % (2 * Math.PI);
        float max = Math.max(width, height);

        float startX = x + width / 2 - (max / 2) * (float) Math.cos(tick);
        float startY = y + height / 2 - (max / 2) * (float) Math.sin(tick);
        float endX = x + width / 2 + (max / 2) * (float) Math.cos(tick);
        float endY = y + height / 2 + (max / 2) * (float) Math.sin(tick);

        int skColor1 = io.github.humbleui.skija.Color.makeARGB(color1.getAlpha(), color1.getRed(),
            color1.getGreen(), color1.getBlue());
        int skColor2 = io.github.humbleui.skija.Color.makeARGB(color2.getAlpha(), color2.getRed(),
            color2.getGreen(), color2.getBlue());
        int skColorMid = io.github.humbleui.skija.Color.makeARGB(color1.getAlpha(),
            (color1.getRed() + color2.getRed()) / 2, (color1.getGreen() + color2.getGreen()) / 2,
            (color1.getBlue() + color2.getBlue()) / 2);

        canvas().drawGradient(RRect.makeXYWH(x, y, width, height, radius),
            new Point(startX, startY), new Point(endX, endY),
            new int[] {skColor1, skColorMid, skColor2}, new float[] {0, .5f, 1}, 0);
    }
}
