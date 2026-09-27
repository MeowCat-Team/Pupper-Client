package cn.pupperclient.ui.theme;

import java.awt.Color;

import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.impl.settings.ModMenuSettings;
import cn.pupperclient.skia.Skia;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;
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
        return alpha(color, opacity());
    }

    public static void panel(float x, float y, float width, float height, float radius, ColorPalette palette) {
        if (width <= 0 || height <= 0 || opacity() <= 0) return;
        float r = Math.min(radius, Math.min(width, height) / 2);
        Skia.drawShadow(x, y, width, height, r);
        // A single fill keeps the configured opacity exact, including both gradient stops.
        try (Shader shader = Shader.makeLinearGradient(new Point(x, y), new Point(x + width * .35f, y + height),
                new int[] { surface(palette.getSurfaceContainerHigh()).getRGB(), surface(palette.getSurface()).getRGB() });
             Paint paint = new Paint().setAntiAlias(true).setShader(shader)) {
            Skia.getCanvas().drawRRect(RRect.makeXYWH(x, y, width, height, r), paint);
        }
        outline(x, y, width, height, r, palette);
    }

    /** Nested cards need a light tonal layer so the parent glass remains visible. */
    public static void card(float x, float y, float width, float height, float radius, ColorPalette palette) {
        if (width <= 0 || height <= 0) return;
        float r = Math.min(radius, Math.min(width, height) / 2);
        Skia.drawRoundedRect(x, y, width, height, r, alpha(palette.getSurfaceContainerHighest(), opacity() * .38f));
        outline(x, y, width, height, r, palette);
    }

    public static void outline(float x, float y, float width, float height, float radius, ColorPalette palette) {
        if (width <= 1 || height <= 1) return;
        Skia.drawOutline(x, y, width, height, radius, 1,
                alpha(palette.getOutlineVariant(), opacity() * .65f));
        Skia.save();
        try {
            // A fixed upper rim gives glass depth without distracting animated gradients.
            Skia.clip(x, y, width, height * .46f, 0);
            Skia.drawOutline(x, y, width, height, radius, 1,
                    alpha(Color.WHITE, opacity() * (palette.isDarkMode() ? .36f : .85f)));
        } finally {
            Skia.restore();
        }
    }
}
