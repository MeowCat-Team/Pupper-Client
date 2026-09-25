package cn.pupperclient.management.mod.api.hud.design;

import java.awt.Color;
import cn.pupperclient.management.color.api.ColorPalette;

/** Opaque, paired roles: the world behind a HUD must not determine text contrast. */
public record HUDColors(Color surface, Color raised, Color text, Color secondaryText,
                        Color accent, Color accentContainer, Color onAccentContainer,
                        Color outline, Color track, Color danger) {
    public static HUDColors from(ColorPalette palette, Color surface) {
        Color raised = palette.getSurfaceContainerHigh();
        Color container = palette.getPrimaryContainer();
        return new HUDColors(surface, raised,
            readable(palette.getOnSurface(), surface, raised),
            readable(palette.getOnSurfaceVariant(), surface, raised),
            palette.getPrimary(), container,
            readable(palette.getOnPrimaryContainer(), container, container),
            palette.getOutlineVariant(), palette.getSurfaceContainerHighest(),
            readable(palette.getError(), surface, raised));
    }

    /** Keep the intended role whenever possible; fall back for inaccessible pairs. */
    public static Color readable(Color preferred, Color background, Color secondBackground) {
        if (contrast(preferred, background) >= 4.5 && contrast(preferred, secondBackground) >= 4.5)
            return preferred;
        double black = Math.min(contrast(Color.BLACK, background), contrast(Color.BLACK, secondBackground));
        double white = Math.min(contrast(Color.WHITE, background), contrast(Color.WHITE, secondBackground));
        return black >= white ? Color.BLACK : Color.WHITE;
    }

    public static double contrast(Color first, Color second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen())
            + 0.0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}