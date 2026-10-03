package cn.pupperclient.ui.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.*;

/** CPU-only adapter for exported UI previews and asset rasterization. */
public final class RasterUiCanvas implements UiCanvas {
    private final Canvas canvas;
    public RasterUiCanvas(Canvas canvas) { this.canvas = canvas; }
    public Canvas raster() { return canvas; }
    @Override public int save() { return canvas.save(); }
    @Override public int saveLayer(Rect bounds, Paint paint) { return canvas.saveLayer(bounds, paint); }
    @Override public void restore() { canvas.restore(); }
    @Override public void restoreToCount(int count) { canvas.restoreToCount(count); }
    @Override public int getSaveCount() { return canvas.getSaveCount(); }
    @Override public void scale(float x, float y) { canvas.scale(x, y); }
    @Override public void translate(float x, float y) { canvas.translate(x, y); }
    @Override public void rotate(float degrees) { canvas.rotate(degrees); }
    @Override public void clipRect(Rect rect) { canvas.clipRect(rect); }
    @Override public void clipPath(Path path, ClipMode mode, boolean aa) { canvas.clipPath(path, mode, aa); }
    @Override public boolean quickReject(Rect rect) { return canvas.quickReject(rect); }
    @Override public Matrix33 getLocalToDeviceAsMatrix33() { return canvas.getLocalToDeviceAsMatrix33(); }
    @Override public void clear(int color) { canvas.clear(color); }
    @Override public void drawRect(Rect rect, Paint paint) { canvas.drawRect(rect, paint); }
    @Override public void drawRRect(RRect rect, Paint paint) { canvas.drawRRect(rect, paint); }
    @Override public void drawCircle(float x, float y, float radius, Paint paint) { canvas.drawCircle(x, y, radius, paint); }
    @Override public void drawLine(float x, float y, float endX, float endY, Paint paint) { canvas.drawLine(x, y, endX, endY, paint); }
    @Override public void drawArc(float l, float t, float r, float b, float start, float sweep, boolean center, Paint paint) { canvas.drawArc(l, t, r, b, start, sweep, center, paint); }
    @Override public void drawPath(Path path, Paint paint) { canvas.drawPath(path, paint); }
    @Override public void drawString(String text, float x, float baseline, Font font, Paint paint) { canvas.drawString(text, x, baseline, font, paint); }
    @Override public void drawImageRect(Image image, Rect src, Rect dst, Paint paint, boolean strict) { canvas.drawImageRect(image, src, dst, paint, strict); }
    @Override public void drawTexture(GpuTextureView texture, Rect src, Rect dst, float alpha) {
        throw new UnsupportedOperationException("A CPU preview cannot read a GPU-owned texture");
    }
    @Override public void drawGradient(RRect rect, Point start, Point end, int[] colors, float[] stops, float stroke) {
        try (Shader shader = stops == null ? Shader.makeLinearGradient(start, end, colors)
                : Shader.makeLinearGradient(start, end, colors, stops); Paint paint = new Paint().setAntiAlias(true).setShader(shader)) {
            if (stroke > 0) paint.setMode(PaintMode.STROKE).setStrokeWidth(stroke);
            canvas.drawRRect(rect, paint);
        }
    }
    @Override public void drawRadialCircle(float x, float y, float radius, int inner, int outer) {
        try (var shader = Shader.makeRadialGradient(new Point(x, y), radius, new int[]{inner, outer}, new float[]{0, 1});
             var paint = new Paint().setAntiAlias(true).setShader(shader)) { canvas.drawCircle(x, y, radius, paint); }
    }
    @Override public void drawShadow(RRect rect, float sigma, int color) {
        try (var blur = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL); var paint = new Paint().setColor(color).setImageFilter(blur)) { canvas.drawRRect(rect, paint); }
    }
    @Override public void drawBlurredImage(Image image, Rect dst, float radius) {
        try (var blur = ImageFilter.makeBlur(radius, radius, FilterTileMode.CLAMP); var paint = new Paint().setImageFilter(blur)) { canvas.drawImageRect(image, dst, paint); }
    }
}
