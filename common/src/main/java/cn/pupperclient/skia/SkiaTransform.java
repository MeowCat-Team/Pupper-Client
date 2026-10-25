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

import cn.pupperclient.ui.render.UiCanvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

/** Canvas state, clipping and transform helpers. */
final class SkiaTransform {

    private SkiaTransform() {}

    private static UiCanvas canvas() {
        return Skia.getCanvas();
    }

    static void save() {
        canvas().save();
    }

    static void restore() {
        canvas().restore();
    }

    static void scale(float scale) {
        canvas().scale(scale, scale);
    }

    static void scale(float x, float y, float scale) {
        canvas().translate(x, y);
        canvas().scale(scale, scale);
        canvas().translate(-x, -y);
    }

    static void scale(float x, float y, float width, float height, float scale) {
        float centerX = x + width / 2;
        float centerY = y + height / 2;
        canvas().translate(centerX, centerY);
        canvas().scale(scale, scale);
        canvas().translate(-centerX, -centerY);
    }

    static void translate(float x, float y) {
        canvas().translate(x, y);
    }

    static void rotate(float x, float y, float width, float height, float rotate) {
        float centerX = x + width / 2;
        float centerY = y + height / 2;
        canvas().translate(centerX, centerY);
        canvas().rotate(rotate);
        canvas().translate(-centerX, -centerY);
    }

    static void clipPath(Path path, ClipMode mode, boolean arg) {
        canvas().clipPath(path, mode, arg);
    }

    static void clipPath(Path path) {
        canvas().clipPath(path, ClipMode.INTERSECT, true);
    }

    static void clip(float x, float y, float width, float height, float radius, ClipMode mode) {
        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            clipPath(path, mode, true);
        }
    }

    static void clip(float x, float y, float width, float height, float topLeft, float topRight,
                     float bottomRight, float bottomLeft) {
        float[] corners = new float[] {topLeft, topLeft, topRight, topRight, bottomRight, bottomRight,
            bottomLeft, bottomLeft};
        try (Path path = Path.makeRRect(RRect.makeComplexXYWH(x, y, width, height, corners))) {
            clipPath(path, ClipMode.INTERSECT, true);
        }
    }

    static void clip(float x, float y, float width, float height, float radius) {
        clip(x, y, width, height, radius, ClipMode.INTERSECT);
    }

    static void setAlpha(int alpha) {
        setAlpha(alpha, null);
    }

    /**
     * Starts a bounded opacity group. Bounds use the current canvas coordinates, so
     * callers apply their transform first and include any shadow margin in the rectangle.
     * Both the layer and the opaque fast path require exactly one matching restore.
     */
    static void setAlpha(int alpha, float x, float y, float width, float height) {
        setAlpha(alpha, Rect.makeXYWH(x, y, Math.max(0, width), Math.max(0, height)));
    }

    private static void setAlpha(int alpha, Rect bounds) {
        if (alpha >= 255) {
            canvas().save();
            return;
        }
        if (alpha <= 0) {
            canvas().save();
            canvas().clipRect(Rect.makeXYWH(0, 0, 0, 0));
            return;
        }
        try (Paint paint = new Paint()) {
            paint.setAlpha(alpha);
            canvas().saveLayer(bounds, paint);
        }
    }
}
