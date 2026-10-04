package cn.pupperclient.ui.theme;

import java.awt.Color;

import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;

/** Stateless production painters shared by interactive components and the offscreen specimen. */
public final class MaterialControls {
    public static final float SWITCH_WIDTH = 52;
    public static final float SWITCH_HEIGHT = 32;
    public static final float SLIDER_HEIGHT = 38;
    public static final float COMPACT_WIDTH = 126;
    public static final float COMPACT_HEIGHT = 32;
    public static final float TEXT_HEIGHT = 40;
    public static final float SEARCH_HEIGHT = 42;

    public enum ButtonStyle { FILLED, ELEVATED, TONAL }

    /** Cursor offset is measured in unscrolled text coordinates; selection indexes are UTF-16 offsets. */
    public record TextState(String text, float cursorOffset, float cursorAlpha,
            int selectionStart, int selectionEnd, String hint, float hintAlpha) { }

    private MaterialControls() { }

    public static void surface(float x, float y, float width, float height, float radius,
            ColorPalette palette, float opacity, float hover, float focus) {
        MaterialTheme.card(x, y, width, height, radius, palette, opacity);
        Skia.drawRoundedRect(x, y, width, height, radius,
                MaterialTheme.alpha(palette.getPrimary(), unit(hover) * .08f));
        if (focus > 0) {
            Skia.drawOutline(x, y, width, height, radius, 2,
                    MaterialTheme.alpha(palette.getPrimary(), unit(focus)));
        }
    }

    public static void switchControl(float x, float y, ColorPalette palette, float opacity,
            float enabled, float hover, float pressed) {
        float selected = unit(enabled);
        float down = unit(pressed);
        float centerX = x + 16 + 20 * selected;
        float centerY = y + SWITCH_HEIGHT / 2;
        Skia.drawRoundedRect(x, y, SWITCH_WIDTH, SWITCH_HEIGHT, 16,
                MaterialTheme.surface(palette.getSurfaceContainerHighest(), opacity));
        Skia.drawOutline(x, y, SWITCH_WIDTH, SWITCH_HEIGHT, 16, 2,
                MaterialTheme.alpha(palette.getOutline(), (1 - selected) * .75f));
        Skia.drawRoundedRect(x, y, SWITCH_WIDTH, SWITCH_HEIGHT, 16,
                MaterialTheme.alpha(palette.getPrimary(), selected));
        // State layer stays behind the opaque handle and check, including the pressed endpoint.
        Skia.drawCircle(centerX, centerY, 20,
                MaterialTheme.alpha(palette.getPrimary(), unit(hover) * .08f + down * .04f));
        float radius = 8 + selected * 4 + down;
        Skia.drawCircle(centerX, centerY, radius, blend(palette.getOutline(), palette.getOnPrimary(), selected));
        Skia.drawFullCenteredText(Icon.CHECK, centerX, centerY,
                MaterialTheme.alpha(palette.getPrimary(), selected), Fonts.getIcon(14));
    }

    public static void slider(float x, float y, float width, ColorPalette palette, float opacity,
            float fraction, float emphasis, String valueLabel) {
        if (width <= 0) return;
        float selected = unit(fraction) * width;
        float state = unit(emphasis);
        float centerX = x + selected;
        float centerY = y + SLIDER_HEIGHT / 2;
        Skia.drawCircle(centerX, centerY, 20, MaterialTheme.alpha(palette.getPrimary(), state * .08f));
        Skia.save();
        try {
            Skia.clip(x, y, width, SLIDER_HEIGHT, 0);
            float activeWidth = Math.max(0, selected - 8);
            float remainingStart = selected + 8;
            float remainingWidth = Math.max(0, width - remainingStart);
            if (activeWidth > 0)
                Skia.drawRoundedRectVarying(x, y + 11, activeWidth, 16, 8, 4, 4, 8, palette.getPrimary());
            if (remainingWidth > 0)
                Skia.drawRoundedRectVarying(x + remainingStart, y + 11, remainingWidth, 16, 4, 8, 8, 4,
                        MaterialTheme.surface(palette.getPrimaryContainer(), opacity));
        } finally {
            Skia.restore();
        }
        Skia.drawRoundedRect(centerX - 2, y, 4, SLIDER_HEIGHT, 2, palette.getPrimary());
        if (state > 0 && valueLabel != null && !valueLabel.isEmpty()) {
            float labelWidth = Math.max(38, Skia.getTextBounds(valueLabel, Fonts.getMedium(12)).getWidth() + 20);
            float labelCenter = Math.max(x + labelWidth / 2, Math.min(x + width - labelWidth / 2, centerX));
            float labelY = y - 34 + 10 * (1 - state);
            Skia.drawRoundedRect(labelCenter - labelWidth / 2, labelY, labelWidth, 28, 14,
                    MaterialTheme.alpha(palette.getPrimary(), state));
            Skia.drawFullCenteredText(valueLabel, labelCenter, labelY + 14,
                    MaterialTheme.alpha(palette.getOnPrimary(), state), Fonts.getMedium(12));
        }
    }

    public static Color buttonContent(ColorPalette palette, ButtonStyle style) {
        return switch (style) {
            case FILLED -> palette.getOnPrimary();
            case ELEVATED -> palette.getPrimary();
            case TONAL -> palette.getOnSecondaryContainer();
        };
    }

    public static void button(float x, float y, float width, float height, String label,
            ColorPalette palette, float opacity, ButtonStyle style, float hover) {
        Color fill = switch (style) {
            case FILLED -> palette.getPrimary();
            case ELEVATED -> MaterialTheme.surface(palette.getSurfaceContainerLow(), opacity);
            case TONAL -> MaterialTheme.surface(palette.getSecondaryContainer(), opacity);
        };
        Color content = buttonContent(palette, style);
        Skia.drawRoundedRect(x, y, width, height, height / 2, fill);
        if (style != ButtonStyle.FILLED)
            MaterialTheme.outline(x, y, width, height, height / 2, palette, opacity);
        Skia.drawRoundedRect(x, y, width, height, height / 2, MaterialTheme.alpha(content, unit(hover) * .08f));
        Skia.drawFullCenteredText(label, x + width / 2, y + height / 2, content, Fonts.getRegular(16));
    }

    public static void choice(float x, float y, float width, float height, String option,
            ColorPalette palette, float opacity, float hover, float textOffset, float textAlpha) {
        surface(x, y, width, height, MaterialTokens.CONTROL_RADIUS, palette, opacity, hover, 0);
        Skia.drawFullCenteredText(Icon.CHEVRON_LEFT, x + 16, y + height / 2, palette.getPrimary(), Fonts.getIcon(20));
        Skia.drawFullCenteredText(Icon.CHEVRON_RIGHT, x + width - 16, y + height / 2, palette.getPrimary(), Fonts.getIcon(20));
        Skia.save();
        try {
            Skia.clip(x + 28, y, Math.max(0, width - 56), height, 0);
            Skia.drawFullCenteredText(Skia.getLimitText(option, Fonts.getMedium(14), Math.max(0, width - 56)),
                    x + width / 2 + textOffset, y + height / 2,
                    MaterialTheme.alpha(palette.getOnSurface(), unit(textAlpha)), Fonts.getMedium(14));
        } finally {
            Skia.restore();
        }
    }

    public static void keybind(float x, float y, float width, float height, String label, boolean binding,
            ColorPalette palette, float opacity, float hover) {
        surface(x, y, width, height, MaterialTokens.CONTROL_RADIUS, palette, opacity, hover, binding ? 1 : 0);
        iconLabel(x, y, width, height, Icon.KEYBOARD, binding ? "..." : label, palette, true);
    }

    public static void iconButton(float x, float y, float width, float height, float radius, float fontSize,
            String icon, Color background, Color foreground, ColorPalette palette, float opacity, float hover) {
        Skia.drawRoundedRect(x, y, width, height, radius, MaterialTheme.surface(background, opacity));
        MaterialTheme.outline(x, y, width, height, radius, palette, opacity);
        Skia.drawRoundedRect(x, y, width, height, radius, MaterialTheme.alpha(foreground, unit(hover) * .08f));
        Skia.drawFullCenteredText(icon, x + width / 2, y + height / 2, foreground, Fonts.getIconFill(fontSize));
    }

    public static void hue(float x, float y, float width, float height, float fraction, Color selectedColor,
            ColorPalette palette, float opacity, float hover) {
        surface(x, y, width, height, MaterialTokens.CONTROL_RADIUS, palette, opacity, hover, 0);
        float travel = Math.max(0, width - 24);
        float centerX = x + 12 + unit(fraction) * travel;
        Skia.drawRoundedImage("hue-h.png", x + 12, y + 8, travel, height - 16, 8);
        Skia.drawCircle(centerX, y + height / 2, 11, palette.getOnSurface());
        Skia.drawCircle(centerX, y + height / 2, 9, palette.getSurface());
        Skia.drawCircle(centerX, y + height / 2, 7, selectedColor);
    }

    public static void fileSelector(float x, float y, float width, float height, String fileName,
            ColorPalette palette, float opacity, float hover) {
        surface(x, y, width, height, MaterialTokens.CONTROL_RADIUS, palette, opacity, hover, 0);
        iconLabel(x, y, width, height, Icon.FOLDER_OPEN, fileName, palette, false);
    }

    private static void iconLabel(float x, float y, float width, float height, String icon, String label,
            ColorPalette palette, boolean centered) {
        Skia.drawFullCenteredText(icon, x + 18, y + height / 2, palette.getPrimary(), Fonts.getIcon(18));
        Skia.save();
        try {
            float available = Math.max(0, width - 42);
            Skia.clip(x + 32, y, available, height, 0);
            String limited = Skia.getLimitText(label, Fonts.getMedium(14), available);
            if (centered)
                Skia.drawFullCenteredText(limited, x + width / 2 + 12, y + height / 2, palette.getOnSurface(), Fonts.getMedium(14));
            else
                Skia.drawHeightCenteredText(limited, x + 32, y + height / 2, palette.getOnSurface(), Fonts.getMedium(14));
        } finally {
            Skia.restore();
        }
    }

    public static void textInput(float x, float y, float width, float height, boolean search,
            ColorPalette palette, float opacity, float hover, float focus, TextState state) {
        surface(x, y, width, height, search ? height / 2 : MaterialTokens.CONTROL_RADIUS,
                palette, opacity, hover, focus);
        float inset = search ? 40 : 12;
        if (search)
            Skia.drawHeightCenteredText(Icon.SEARCH, x + 12, y + height / 2, palette.getPrimary(), Fonts.getIcon(24));
        float available = Math.max(0, width - inset - 14);
        float textWidth = Skia.getTextBounds(state.text(), Fonts.getRegular(16)).getWidth();
        float offset = -Math.max(0, textWidth - available);
        Skia.save();
        try {
            Skia.clip(x + inset, y + 6, Math.max(0, width - inset - 12), height - 12, 0);
            if (state.hint() != null && state.hintAlpha() > 0)
                Skia.drawHeightCenteredText(state.hint(), x + inset - 25 * (1 - unit(state.hintAlpha())), y + height / 2,
                        MaterialTheme.alpha(palette.getOnSurfaceVariant(), unit(state.hintAlpha())), Fonts.getRegular(16));
            int start = Math.max(0, Math.min(state.text().length(), Math.min(state.selectionStart(), state.selectionEnd())));
            int end = Math.max(start, Math.min(state.text().length(), Math.max(state.selectionStart(), state.selectionEnd())));
            if (start != end) {
                float selectionX = Skia.getTextBounds(state.text().substring(0, start), Fonts.getRegular(16)).getWidth();
                float selectionWidth = Skia.getTextBounds(state.text().substring(start, end), Fonts.getRegular(16)).getWidth();
                Skia.drawRoundedRect(x + inset + selectionX + offset, y + (height - 24) / 2, selectionWidth, 24, 4,
                        MaterialTheme.alpha(palette.getPrimary(), .26f));
            }
            Skia.drawHeightCenteredText(state.text(), x + inset + offset, y + height / 2, palette.getOnSurface(), Fonts.getRegular(16));
            if (state.cursorAlpha() > 0)
                Skia.drawRect(x + inset + state.cursorOffset() + offset, y + (height - 24) / 2, 1, 24,
                        MaterialTheme.alpha(palette.getPrimary(), unit(state.cursorAlpha())));
        } finally {
            Skia.restore();
        }
    }

    private static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }

    private static Color blend(Color from, Color to, float amount) {
        return new Color(Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }
}
