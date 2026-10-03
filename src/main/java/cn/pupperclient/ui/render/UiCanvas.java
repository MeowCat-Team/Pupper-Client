package cn.pupperclient.ui.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.*;

/** UI drawing contract. Skia objects here describe CPU assets, never a GPU context. */
public interface UiCanvas {
    enum ImageSampling { SMOOTH, PIXEL }
    int save();
    int saveLayer(Rect bounds, Paint paint);
    void restore();
    void restoreToCount(int count);
    int getSaveCount();
    void scale(float x, float y);
    void translate(float x, float y);
    void rotate(float degrees);
    void clipRect(Rect rect);
    void clipPath(Path path, ClipMode mode, boolean antialias);
    boolean quickReject(Rect rect);
    Matrix33 getLocalToDeviceAsMatrix33();
    void clear(int color);
    void drawRect(Rect rect, Paint paint);
    void drawRRect(RRect rect, Paint paint);
    void drawCircle(float x, float y, float radius, Paint paint);
    void drawLine(float x, float y, float endX, float endY, Paint paint);
    void drawArc(float left, float top, float right, float bottom, float start, float sweep, boolean center, Paint paint);
    void drawPath(Path path, Paint paint);
    void drawString(String text, float x, float baseline, Font font, Paint paint);
    void drawImageRect(Image image, Rect source, Rect destination, Paint paint, boolean strict, ImageSampling sampling);
    void drawTexture(GpuTextureView texture, Rect source, Rect destination, float alpha, ImageSampling sampling);
    void drawGradient(RRect rect, Point start, Point end, int[] colors, float[] stops, float stroke);
    void drawRadialCircle(float x, float y, float radius, int inner, int outer);
    void drawShadow(RRect rect, float sigma, int color);
    void drawBlurredImage(Image image, Rect destination, float radius);

    default void drawImageRect(Image image, Rect source, Rect destination, Paint paint, boolean strict) {
        drawImageRect(image, source, destination, paint, strict, ImageSampling.SMOOTH);
    }
    default void drawTexture(GpuTextureView texture, Rect source, Rect destination, float alpha) {
        drawTexture(texture, source, destination, alpha, ImageSampling.SMOOTH);
    }

    default void drawImageRect(Image image, Rect destination) { drawImageRect(image, destination, null); }
    default void drawImageRect(Image image, Rect destination, Paint paint) {
        drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()), destination, paint, false);
    }
}
