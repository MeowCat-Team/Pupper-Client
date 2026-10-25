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
import java.io.File;

import cn.pupperclient.management.mod.api.hud.design.HUDText;
import cn.pupperclient.skia.context.SkiaContext;
import cn.pupperclient.skia.font.TextMetrics;
import cn.pupperclient.skia.image.ImageHelper;
import cn.pupperclient.ui.render.UiCanvas;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.types.Rect;

/**
 * The UI drawing facade.
 *
 * <p>Every method here is a named entry point for the rest of the client; the work is
 * done by the package-private helpers behind it: {@link SkiaShape} for shapes,
 * {@link SkiaImage} for images, {@link SkiaText} for text, {@link SkiaTransform} for
 * canvas state, and {@link SkiaPaint} for the shared paint.
 */
public class Skia {

    private Skia() {}

    // Shapes

    public static void drawRect(float x, float y, float width, float height, Color color) {
        SkiaShape.drawRect(x, y, width, height, color);
    }

    public static void drawCircle(float x, float y, float radius, Color color) {
        SkiaShape.drawCircle(x, y, radius, color);
    }

    public static void drawCircle(float x, float y, float radius, float strokeWidth, Color color) {
        SkiaShape.drawCircle(x, y, radius, strokeWidth, color);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        SkiaShape.drawRoundedRect(x, y, width, height, radius, color);
    }

    public static void drawRoundedRectVarying(float x, float y, float width, float height, float topLeft,
                                              float topRight, float bottomRight, float bottomLeft, Color color) {
        SkiaShape.drawRoundedRectVarying(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, color);
    }

    public static void drawShadow(float x, float y, float width, float height, float radius) {
        SkiaShape.drawShadow(x, y, width, height, radius);
    }

    public static void drawBackdropBlur(float x, float y, float width, float height, float radius) {
        SkiaShape.drawBackdropBlur(x, y, width, height, radius);
    }

    public static void drawBackdropBlur(float x, float y, float width, float height, float radius, float strength) {
        SkiaShape.drawBackdropBlur(x, y, width, height, radius, strength);
    }

    public static void drawGlassBackdrop(float x, float y, float width, float height, float radius,
                                         float strength, float refraction) {
        SkiaShape.drawGlassBackdrop(x, y, width, height, radius, strength, refraction);
    }

    public static void drawOutline(float x, float y, float width, float height, float radius, float strokeWidth,
                                   Color color) {
        SkiaShape.drawOutline(x, y, width, height, radius, strokeWidth, color);
    }

    public static void drawArc(float x, float y, float radius, float startAngle, float endAngle, float strokeWidth,
                               Color color) {
        SkiaShape.drawArc(x, y, radius, startAngle, endAngle, strokeWidth, color);
    }

    public static void drawLine(float x, float y, float endX, float endY, float width, Color color) {
        SkiaShape.drawLine(x, y, endX, endY, width, color);
    }

    public static void drawGradientRoundedRect(float x, float y, float width, float height, float radius, Color color1,
                                               Color color2) {
        SkiaShape.drawGradientRoundedRect(x, y, width, height, radius, color1, color2);
    }

    // Images

    public static void drawImage(String path, float x, float y, float width, float height) {
        SkiaImage.drawImage(path, x, y, width, height);
    }

    public static void drawImage(GpuTextureView texture, float x, float y, float width, float height, float alpha,
                                 SurfaceOrigin origin) {
        SkiaImage.drawImage(texture, x, y, width, height, alpha, origin);
    }

    public static void drawImage(GpuTextureView texture, float x, float y, float width, float height, float alpha) {
        SkiaImage.drawImage(texture, x, y, width, height, alpha);
    }

    public static void drawImage(File file, float x, float y, float width, float height) {
        SkiaImage.drawImage(file, x, y, width, height);
    }

    public static void drawImage(GpuTextureView texture, float x, float y, float width, float height, SurfaceOrigin origin) {
        SkiaImage.drawImage(texture, x, y, width, height, origin);
    }

    public static void drawImage(GpuTextureView texture, float x, float y, float width, float height) {
        SkiaImage.drawImage(texture, x, y, width, height);
    }

    public static void drawRoundedImage(GpuTextureView texture, float x, float y, float width, float height, float radius) {
        SkiaImage.drawRoundedImage(texture, x, y, width, height, radius);
    }

    public static void drawRoundedImage(String filePath, float x, float y, float width, float height, float radius) {
        SkiaImage.drawRoundedImage(filePath, x, y, width, height, radius);
    }

    public static void drawRoundedImage(File file, float x, float y, float width, float height, float radius) {
        SkiaImage.drawRoundedImage(file, x, y, width, height, radius);
    }

    public static void drawRoundedImage(GpuTextureView texture, float x, float y, float width, float height, float radius,
                                        float alpha, SurfaceOrigin origin) {
        SkiaImage.drawRoundedImage(texture, x, y, width, height, radius, alpha, origin);
    }

    public static void drawRoundedImage(GpuTextureView texture, float x, float y, float width, float height, float radius,
                                        float alpha) {
        SkiaImage.drawRoundedImage(texture, x, y, width, height, radius, alpha);
    }

    public static void drawPlayerHead(File file, float x, float y, float width, float height, float radius) {
        SkiaImage.drawPlayerHead(file, x, y, width, height, radius);
    }

    public static void drawSkin(File file, float x, float y, float scale) {
        SkiaImage.drawSkin(file, x, y, scale);
    }

    public static void drawMinecraftImage(String path, float x, float y, float width, float height) {
        SkiaImage.drawMinecraftImage(path, x, y, width, height);
    }

    public static void drawBlurredImage(Image image, float x, float y, float width, float height, float radius) {
        SkiaImage.drawBlurredImage(image, x, y, width, height, radius);
    }

    // Text

    public static void drawText(String text, float x, float y, Color color, Font font) {
        SkiaText.drawText(text, x, y, color, font);
    }

    public static void drawCenteredText(String text, float x, float y, Color color, Font font) {
        SkiaText.drawCenteredText(text, x, y, color, font);
    }

    public static void drawHeightCenteredText(String text, float x, float y, Color color, Font font) {
        SkiaText.drawHeightCenteredText(text, x, y, color, font);
    }

    public static void drawFullCenteredText(String text, float x, float y, Color color, Font font) {
        SkiaText.drawFullCenteredText(text, x, y, color, font);
    }

    public static Rect getTextBounds(String text, Font font) {
        return SkiaText.getTextBounds(text, font);
    }

    public static String getLimitText(String text, Font font, float width) {
        return SkiaText.getLimitText(text, font, width);
    }

    // Canvas state

    public static Paint setupPaint(Color color) {
        return SkiaPaint.of(color);
    }

    public static void save() {
        SkiaTransform.save();
    }

    public static void restore() {
        SkiaTransform.restore();
    }

    public static void scale(float scale) {
        SkiaTransform.scale(scale);
    }

    public static void scale(float x, float y, float scale) {
        SkiaTransform.scale(x, y, scale);
    }

    public static void scale(float x, float y, float width, float height, float scale) {
        SkiaTransform.scale(x, y, width, height, scale);
    }

    public static void translate(float x, float y) {
        SkiaTransform.translate(x, y);
    }

    public static void rotate(float x, float y, float width, float height, float rotate) {
        SkiaTransform.rotate(x, y, width, height, rotate);
    }

    public static void setAlpha(int alpha) {
        SkiaTransform.setAlpha(alpha);
    }

    public static void setAlpha(int alpha, float x, float y, float width, float height) {
        SkiaTransform.setAlpha(alpha, x, y, width, height);
    }

    public static void clipPath(Path path, ClipMode mode, boolean arg) {
        SkiaTransform.clipPath(path, mode, arg);
    }

    public static void clipPath(Path path) {
        SkiaTransform.clipPath(path);
    }

    public static void clip(float x, float y, float width, float height, float radius, ClipMode mode) {
        SkiaTransform.clip(x, y, width, height, radius, mode);
    }

    public static void clip(float x, float y, float width, float height, float topLeft, float topRight,
                            float bottomRight, float bottomLeft) {
        SkiaTransform.clip(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft);
    }

    public static void clip(float x, float y, float width, float height, float radius) {
        SkiaTransform.clip(x, y, width, height, radius);
    }

    // Lifecycle

    public static void releaseResources() {
        HUDText.releaseResources();
        TextMetrics.clear();
        SkiaImage.helper().clear();
        GlassRenderer.releaseResources();
        SkiaPaint.release();
    }

    public static UiCanvas getCanvas() {
        return SkiaContext.getCanvas();
    }

    public static ImageHelper getImageHelper() {
        return SkiaImage.helper();
    }
}
