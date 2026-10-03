package cn.pupperclient.ui.theme;

import java.awt.Color;

import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.impl.settings.ModMenuSettings;
import cn.pupperclient.skia.Skia;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.RRect;

/** Material surfaces: tinted glass, quiet edges, and opaque content/state colors. */
public final class MaterialTheme {
    public static final float DEFAULT_OPACITY = MaterialTokens.DEFAULT_OPACITY;
    public static final float SURFACE_RADIUS = MaterialTokens.SURFACE_RADIUS;
    public static final float CARD_RADIUS = MaterialTokens.CARD_RADIUS;
    public static final float CONTROL_RADIUS = MaterialTokens.CONTROL_RADIUS;

    private MaterialTheme() { }

    public static float opacity() {
        ModMenuSettings settings = ModMenuSettings.getInstance();
        return settings == null ? DEFAULT_OPACITY : settings.getBackgroundOpacity();
    }

    /** Multiply existing alpha rather than making translucent state layers opaque. */
    public static Color alpha(Color color, float amount) {
        float safe = Float.isFinite(amount) ? Math.max(0, Math.min(1, amount)) : DEFAULT_OPACITY;
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * safe));
    }

    public static Color surface(Color color) {
        return surface(color, opacity());
    }

    public static Color surface(Color color, float opacity) {
        return alpha(color, opacity);
    }

    /** Root windows sample the scene; nested cards only add a tonal surface. */
    public static void glassPanel(float x, float y, float width, float height, float radius, ColorPalette palette) {
        ModMenuSettings settings = ModMenuSettings.getInstance();
        float opacity = opacity();
        if (opacity > 0 && opacity < 1 && (settings == null || settings.getBlurSetting().isEnabled())) {
            float strength = settings == null ? 5 : settings.getBlurIntensitySetting().getValue();
            Skia.drawGlassBackdrop(x, y, width, height, radius, strength * .3f, 1.75f);
        }
        panel(x, y, width, height, radius, palette);
    }

    public static void panel(float x, float y, float width, float height, float radius, ColorPalette palette) {
        panel(x, y, width, height, radius, palette, opacity());
    }

    public static void panel(float x, float y, float width, float height, float radius, ColorPalette palette, float opacity) {
        if (width <= 0 || height <= 0 || opacity <= 0) return;
        float r = Math.min(radius, Math.min(width, height) / 2);
        Skia.drawShadow(x, y, width, height, r);
        // A single fill keeps the configured opacity exact, including both gradient stops.
        Skia.getCanvas().drawGradient(RRect.makeXYWH(x, y, width, height, r),
                new Point(x, y), new Point(x + width * .35f, y + height),
                new int[] { surface(palette.getSurfaceContainerHigh(), opacity).getRGB(), surface(palette.getSurface(), opacity).getRGB() },
                new float[] { 0, 1 }, 0);
        outline(x, y, width, height, r, palette, opacity);
    }

    /** Nested cards need a light tonal layer so the parent glass remains visible. */
    public static void card(float x, float y, float width, float height, float radius, ColorPalette palette) {
        card(x, y, width, height, radius, palette, opacity());
    }

    public static void card(float x, float y, float width, float height, float radius, ColorPalette palette, float opacity) {
        if (width <= 0 || height <= 0 || opacity <= 0) return;
        float r = Math.min(radius, Math.min(width, height) / 2);
        Skia.drawRoundedRect(x, y, width, height, r, alpha(palette.getSurfaceContainerHighest(), opacity * .38f));
        // Lists can contain dozens of controls. Reserve gradient shaders for root windows.
        if (width > 1 && height > 1)
            Skia.drawOutline(x, y, width, height, r, .75f, alpha(palette.getOutlineVariant(), opacity * .45f));
    }

    public static void outline(float x, float y, float width, float height, float radius, ColorPalette palette) {
        outline(x, y, width, height, radius, palette, opacity());
    }

    public static void outline(float x, float y, float width, float height, float radius, ColorPalette palette, float opacity) {
        if (width <= 1 || height <= 1 || opacity <= 0) return;
        float r = Math.max(.5f, Math.min(radius, Math.min(width, height) / 2) - .5f);
        // One continuous specular rim: no abrupt half-height clipping or additive glow.
        Skia.getCanvas().drawGradient(RRect.makeXYWH(x + .5f, y + .5f, width - 1, height - 1, r),
                new Point(x, y), new Point(x + width * .6f, y + height),
                new int[] {
                    alpha(Color.WHITE, opacity * (palette.isDarkMode() ? .50f : .90f)).getRGB(),
                    alpha(palette.getOutlineVariant(), opacity * .24f).getRGB(),
                    alpha(palette.getOutline(), opacity * .32f).getRGB()
                }, new float[] { 0, .48f, 1 }, 1);
    }
}
