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

import java.io.File;

import net.minecraft.resources.Identifier;

import cn.pupperclient.skia.image.ImageHelper;
import cn.pupperclient.ui.render.UiCanvas;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

/** Image and texture drawing, including the skin and head previews. */
final class SkiaImage {

    private SkiaImage() {}

    private static final ImageHelper HELPER = new ImageHelper();

    static ImageHelper helper() {
        return HELPER;
    }

    private static UiCanvas canvas() {
        return Skia.getCanvas();
    }

    static void drawImage(String path, float x, float y, float width, float height) {
        Image image = HELPER.getOrLoad("/assets/pupper/" + path);
        if (image != null) {
            canvas().drawImageRect(image, Rect.makeXYWH(x, y, width, height));
        }
    }

    static void drawImage(GpuTextureView texture, float x, float y, float width, float height, float alpha,
                          SurfaceOrigin origin) {
        if (texture == null || texture.isClosed()) return;
        Rect source = origin == SurfaceOrigin.TOP_LEFT ? Rect.makeWH(texture.getWidth(0), texture.getHeight(0))
                : Rect.makeLTRB(0, texture.getHeight(0), texture.getWidth(0), 0);
        canvas().drawTexture(texture, source, Rect.makeXYWH(x, y, width, height), alpha);
    }

    static void drawImage(GpuTextureView texture, float x, float y, float width, float height, float alpha) {
        drawImage(texture, x, y, width, height, alpha, SurfaceOrigin.TOP_LEFT);
    }

    static void drawImage(File file, float x, float y, float width, float height) {
        Image image = HELPER.getOrLoad(file);
        if (image != null) {
            canvas().drawImageRect(image, Rect.makeXYWH(x, y, width, height));
        }
    }

    static void drawImage(GpuTextureView texture, float x, float y, float width, float height, SurfaceOrigin origin) {
        drawImage(texture, x, y, width, height, 1, origin);
    }

    static void drawImage(GpuTextureView texture, float x, float y, float width, float height) {
        drawImage(texture, x, y, width, height, SurfaceOrigin.TOP_LEFT);
    }

    static void drawRoundedImage(GpuTextureView texture, float x, float y, float width, float height, float radius) {
        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            SkiaTransform.save();
            canvas().clipPath(path, ClipMode.INTERSECT, true);
            drawImage(texture, x, y, width, height);
            SkiaTransform.restore();
        }
    }

    static void drawRoundedImage(String filePath, float x, float y, float width, float height, float radius) {
        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            SkiaTransform.save();
            canvas().clipPath(path, ClipMode.INTERSECT, true);
            drawImage(filePath, x, y, width, height);
            SkiaTransform.restore();
        }
    }

    static void drawRoundedImage(File file, float x, float y, float width, float height, float radius) {
        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            SkiaTransform.save();
            canvas().clipPath(path, ClipMode.INTERSECT, true);
            drawImage(file, x, y, width, height);
            SkiaTransform.restore();
        }
    }

    static void drawRoundedImage(GpuTextureView texture, float x, float y, float width, float height, float radius,
                                 float alpha, SurfaceOrigin origin) {
        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            SkiaTransform.save();
            canvas().clipPath(path, ClipMode.INTERSECT, true);
            drawImage(texture, x, y, width, height, alpha, origin);
            SkiaTransform.restore();
        }
    }

    static void drawRoundedImage(GpuTextureView texture, float x, float y, float width, float height, float radius,
                                 float alpha) {
        drawRoundedImage(texture, x, y, width, height, radius, alpha, SurfaceOrigin.TOP_LEFT);
    }

    static void drawPlayerHead(File file, float x, float y, float width, float height, float radius) {
        Image image = HELPER.getOrLoad(file);
        if (image == null) return;

        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            Rect srcRect = Rect.makeXYWH(8, 8, 8, 8);
            Rect srcRect1 = Rect.makeXYWH(40, 8, 8, 8);
            Rect dstRect = Rect.makeXYWH(x, y, width, height);

            SkiaTransform.save();
            canvas().clipPath(path, ClipMode.INTERSECT, true);
            canvas().drawImageRect(image, srcRect, dstRect, null, false, UiCanvas.ImageSampling.PIXEL);
            canvas().drawImageRect(image, srcRect1, dstRect, null, false, UiCanvas.ImageSampling.PIXEL);
            SkiaTransform.restore();
        }
    }

    static void drawSkin(File file, float x, float y, float scale) {
        Image image = HELPER.getOrLoad(file);
        if (image == null) return;

        SkiaTransform.save();
        SkiaTransform.scale(x, y, scale);

        // Pseudo-3D top and left-side faces keep the overlay layer visible in the
        // flat preview instead of showing only the front faces.
        face(image, 8, 0, 8, 8, x + 4, y, 8, 4);
        face(image, 40, 0, 8, 8, x + 4, y, 8, 4);
        face(image, 16, 8, 8, 8, x + 12, y + 4, 2, 8);
        face(image, 48, 8, 8, 8, x + 12, y + 4, 2, 8);
        face(image, 20, 16, 8, 4, x + 4, y + 8, 8, 2);
        face(image, 20, 32, 8, 4, x + 4, y + 8, 8, 2);
        face(image, 28, 20, 4, 12, x + 12, y + 12, 1, 12);
        face(image, 28, 36, 4, 12, x + 12, y + 12, 1, 12);

        // Front faces start below the top faces.
        float front = y + 4;
        face(image, 8, 8, 8, 8, x + 4, front, 8, 8);
        face(image, 40, 8, 8, 8, x + 4, front, 8, 8);
        face(image, 20, 20, 8, 12, x + 4, front + 8, 8, 12);
        face(image, 20, 36, 8, 12, x + 4, front + 8, 8, 12);
        face(image, 36, 52, 4, 12, x, front + 8, 4, 12);
        face(image, 52, 52, 4, 12, x, front + 8, 4, 12);
        face(image, 44, 20, 4, 12, x + 12, front + 8, 4, 12);
        face(image, 44, 36, 4, 12, x + 12, front + 8, 4, 12);
        face(image, 20, 52, 4, 12, x + 4, front + 20, 4, 12);
        face(image, 4, 52, 4, 12, x + 4, front + 20, 4, 12);
        face(image, 4, 20, 4, 12, x + 8, front + 20, 4, 12);
        face(image, 4, 36, 4, 12, x + 8, front + 20, 4, 12);
        SkiaTransform.restore();
    }

    private static void face(Image image, int srcX, int srcY, int srcW, int srcH,
                             float dstX, float dstY, float dstW, float dstH) {
        canvas().drawImageRect(image, Rect.makeXYWH(srcX, srcY, srcW, srcH),
            Rect.makeXYWH(dstX, dstY, dstW, dstH), null, false, UiCanvas.ImageSampling.PIXEL);
    }

    static void drawMinecraftImage(String path, float x, float y, float width, float height) {
        Identifier identifier = Identifier.fromNamespaceAndPath("minecraft", path);
        Image image = HELPER.getOrLoad(identifier);
        if (image != null) {
            canvas().drawImageRect(image, Rect.makeXYWH(x, y, width, height));
        }
    }

    static void drawBlurredImage(Image image, float x, float y, float width, float height, float radius) {
        if (image == null || width <= 0 || height <= 0) return;
        float safeRadius = Float.isFinite(radius) ? Math.max(0, Math.min(6, radius)) : 0;
        canvas().drawBlurredImage(image, Rect.makeXYWH(x, y, width, height), safeRadius);
    }
}
