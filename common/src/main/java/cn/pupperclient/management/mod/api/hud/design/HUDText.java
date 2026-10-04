package cn.pupperclient.management.mod.api.hud.design;

import java.awt.Color;
import cn.pupperclient.management.mod.impl.settings.HUDModSettings;
import cn.pupperclient.skia.Skia;
import io.github.humbleui.skija.Canvas;
import cn.pupperclient.ui.render.UiCanvas;
import cn.pupperclient.ui.render.RasterUiCanvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;

/** Original filled glyphs with a faint atlas-rendered shadow on translucent glass. */
public final class HUDText {
    private static Paint shadowPaint;

    private HUDText() {}

    public static void drawText(String text, float x, float y, Color color, Font font) {
        var bounds = font.measureText(text);
        draw(text, x - bounds.getLeft(), y - bounds.getTop(), color, font);
    }

    public static void drawHeightCenteredText(String text, float x, float y, Color color, Font font) {
        var bounds = font.measureText(text);
        var metrics = font.getMetrics();
        draw(text, x - bounds.getLeft(), y - (metrics.getAscent() + metrics.getDescent()) / 2, color, font);
    }

    public static void drawFullCenteredText(String text, float x, float y, Color color, Font font) {
        drawFullCenteredText(text, x, y, color, font, backgroundOpacity());
    }

    public static void drawFullCenteredText(String text, float x, float y, Color color, Font font, float opacity) {
        var bounds = font.measureText(text);
        var metrics = font.getMetrics();
        draw(text, x - bounds.getLeft() - bounds.getWidth() / 2,
            y - (metrics.getAscent() + metrics.getDescent()) / 2, color, font, opacity);
    }

    public static float backgroundOpacity() {
        HUDModSettings settings = HUDModSettings.getInstance();
        return settings == null ? HUDColors.DEFAULT_OPACITY : settings.getBackgroundOpacity();
    }

    private static void draw(String text, float x, float baseline, Color color, Font font) {
        draw(text, x, baseline, color, font, backgroundOpacity());
    }

    private static void draw(String text, float x, float baseline, Color color, Font font, float opacity) {
        drawAtBaseline(Skia.getCanvas(), Skia.setupPaint(color), text, x, baseline, color, font, opacity);
    }

    /** Also used by the offscreen specimen so it exercises the same glyph treatment. */
    public static void drawAtBaseline(Canvas canvas, Paint paint, String text, float x, float baseline,
                                      Color color, Font font, float opacity) {
        drawAtBaseline(new RasterUiCanvas(canvas), paint, text, x, baseline, color, font, opacity);
    }

    public static void drawAtBaseline(UiCanvas canvas, Paint paint, String text, float x, float baseline,
                                      Color color, Font font, float opacity) {
        if (text.isEmpty() || color.getAlpha() == 0) return;
        float shadowStrength = Math.max(0, Math.min(1, (0.70f - opacity) / 0.25f));
        if (shadowStrength > 0) {
            if (shadowPaint == null) {
                // An image blur here creates offscreen work for every line of every HUD.
                // Reuse the normal glyph atlas for this faint offset copy instead.
                shadowPaint = new Paint().setAntiAlias(true);
            }
            shadowPaint.setARGB(Math.round(color.getAlpha() * shadowStrength * 0.14f), 0, 0, 0);
            canvas.drawString(text, x, baseline + 0.6f, font, shadowPaint);
        }
        // The foreground is always the original filled glyph; never stroke or expand it.
        canvas.drawString(text, x, baseline, font, paint.setColor(color.getRGB()));
    }

    public static void releaseResources() {
        if (shadowPaint != null) {
            shadowPaint.close();
            shadowPaint = null;
        }
    }
}
