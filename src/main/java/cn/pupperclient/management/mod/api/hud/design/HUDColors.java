package cn.pupperclient.management.mod.api.hud.design;

import java.awt.Color;
import cn.pupperclient.management.color.api.ColorPalette;

/** Translucent surfaces with opaque foregrounds checked against composited backgrounds. */
public record HUDColors(Color surface, Color raised, Color text, Color secondaryText,
                        Color accent, Color accentContainer, Color onAccentContainer,
                        Color outline, Color track, Color danger) {
    public static final float MIN_OPACITY = 0.70f;
    public static final float DEFAULT_OPACITY = 0.80f;

    public static HUDColors from(ColorPalette palette, Color surface) {
        return from(palette, surface, DEFAULT_OPACITY);
    }

    public static HUDColors from(ColorPalette palette, Color surface, float opacity) {
        float safeOpacity = Float.isFinite(opacity) ? Math.max(MIN_OPACITY, Math.min(1, opacity)) : DEFAULT_OPACITY;
        int alpha = Math.round(safeOpacity * 255);
        Color base = withAlpha(surface, alpha);
        Color raised = withAlpha(palette.getSurfaceContainerHigh(), alpha);
        Color container = palette.getPrimaryContainer();
        Color track = palette.getSurfaceContainerHighest();
        // Black and white bound every sRGB world pixel, including nested raised surfaces.
        Color[] backgrounds = {composite(base, Color.BLACK), composite(base, Color.WHITE),
            composite(raised, Color.BLACK), composite(raised, Color.WHITE)};
        return new HUDColors(base, raised,
            ensureContrast(palette.getOnSurface(), 4.5, backgrounds),
            ensureContrast(palette.getOnSurfaceVariant(), 4.5, backgrounds),
            ensureContrast(palette.getPrimary(), 3, backgrounds[0], backgrounds[1], backgrounds[2], backgrounds[3], track),
            container, readable(palette.getOnPrimaryContainer(), container, container),
            palette.getOutlineVariant(), track,
            ensureContrast(palette.getError(), 4.5, backgrounds));
    }

    public static Color readable(Color preferred, Color background, Color secondBackground) {
        return ensureContrast(preferred, 4.5, background, secondBackground);
    }

    /** Move only as far toward black/white as needed, retaining the role's hue when possible. */
    private static Color ensureContrast(Color preferred, double minimum, Color... backgrounds) {
        if (minimumContrast(preferred, backgrounds) >= minimum) return preferred;
        Color target = minimumContrast(Color.BLACK, backgrounds) >= minimumContrast(Color.WHITE, backgrounds)
            ? Color.BLACK : Color.WHITE;
        double low = 0, high = 1;
        for (int i = 0; i < 14; i++) {
            double middle = (low + high) / 2;
            if (minimumContrast(mix(preferred, target, middle), backgrounds) >= minimum) high = middle;
            else low = middle;
        }
        return mix(preferred, target, high);
    }

    private static double minimumContrast(Color foreground, Color[] backgrounds) {
        double minimum = Double.MAX_VALUE;
        for (Color background : backgrounds) minimum = Math.min(minimum, contrast(foreground, background));
        return minimum;
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /** Source-over on an opaque sRGB backdrop, used by the contrast checks as well. */
    public static Color composite(Color foreground, Color background) {
        return mix(background, foreground, foreground.getAlpha() / 255.0);
    }

    private static Color mix(Color first, Color second, double amount) {
        return new Color((int) Math.round(first.getRed() + (second.getRed() - first.getRed()) * amount),
            (int) Math.round(first.getGreen() + (second.getGreen() - first.getGreen()) * amount),
            (int) Math.round(first.getBlue() + (second.getBlue() - first.getBlue()) * amount));
    }

    /** Colors must already be opaque/composited; raw alpha is not a contrast measurement. */
    public static double contrast(Color first, Color second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}