package cn.pupperclient.hud;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.api.hud.design.HUDColors;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.skia.Skia;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;

/** Offscreen token/layout specimen, not a screenshot of a running Minecraft client. */
public final class HUDThemePreview {
    public static void main(String[] args) throws Exception {
        verifyTextBounds();
        Path output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        try (Surface surface = Surface.makeRasterN32Premul(1280, 840)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0xffd5dce3);
            canvas.scale(2, 2);
            column(canvas, false, 16);
            column(canvas, true, 332);
            try (Image image = surface.makeImageSnapshot(); Data png = image.encodeToData(EncodedImageFormat.PNG)) {
                if (png == null) throw new IllegalStateException("PNG encoding failed");
                Files.write(output, png.getBytes());
            }
        }
        System.out.println("HUD theme specimen: " + output);
    }

    private static void verifyTextBounds() {
        Font font = HUDTokens.body();
        for (String value : new String[]{"", "速度 II", "A very long player name", "Player 😀 name"}) {
            for (int width : new int[]{0, 1, 4, 8, 20, 100}) {
                String result = Skia.getLimitText(value, font, width);
                if (font.measureText(result).getWidth() > width + 0.01f)
                    throw new AssertionError("Truncated text exceeds its bounds");
                for (int i = 0; i < result.length(); i++) {
                    if (Character.isHighSurrogate(result.charAt(i))) {
                        if (++i >= result.length() || !Character.isLowSurrogate(result.charAt(i)))
                            throw new AssertionError("Split surrogate pair");
                    } else if (Character.isLowSurrogate(result.charAt(i))) throw new AssertionError("Split surrogate pair");
                }
            }
        }
        String exact = "HUD";
        if (!Skia.getLimitText(exact, font, font.measureText(exact).getWidth()).equals(exact))
            throw new AssertionError("Exact-fit text was truncated");
    }

    private static void column(Canvas canvas, boolean dark, float x) {
        ColorPalette palette = new ColorPalette(Hct.from(220, 26, 6), dark);
        HUDColors c = HUDColors.from(palette, palette.getSurfaceContainer());
        rect(canvas, x, 16, 292, 388, 14, dark ? new Color(0x25303a) : new Color(0xeef1ef));
        text(canvas, dark ? "深色主题 / Dark" : "浅色主题 / Light", x + 14, 30, c.text(), HUDTokens.title());
        float left = x + 14;
        rect(canvas, left, 52, 102, 24, 12, c.surface());
        rect(canvas, left + 5, 55, 18, 18, 6, c.accentContainer());
        text(canvas, Icon.MONITOR, left + 8, 58, c.onAccentContainer(), HUDTokens.icon());
        text(canvas, "144", left + 30, 59, c.text(), HUDTokens.value());
        text(canvas, "FPS", left + 54, 60, c.secondaryText(), HUDTokens.label());
        rect(canvas, left + 112, 52, 90, 24, 12, c.surface());
        text(canvas, "24", left + 124, 59, c.text(), HUDTokens.value());
        text(canvas, "ms", left + 144, 60, c.secondaryText(), HUDTokens.label());

        rect(canvas, left, 88, 264, 80, 10, c.surface());
        text(canvas, "药水效果", left + 8, 98, c.text(), HUDTokens.title());
        text(canvas, "速度 II", left + 26, 123, c.text(), HUDTokens.body());
        text(canvas, Icon.SCIENCE, left + 8, 122, c.accent(), HUDTokens.icon());
        text(canvas, "1:24", left + 222, 124, c.secondaryText(), HUDTokens.label());
        text(canvas, "力量", left + 26, 143, c.text(), HUDTokens.body());
        text(canvas, Icon.TIMER, left + 8, 142, c.danger(), HUDTokens.icon());
        text(canvas, "8s", left + 232, 144, c.danger(), HUDTokens.label());

        rect(canvas, left, 180, 180, 56, 10, c.surface());
        rect(canvas, left + 8, 192, 32, 32, 8, c.accentContainer());
        text(canvas, Icon.PERSON, left + 18, 202, c.onAccentContainer(), HUDTokens.icon());
        text(canvas, "Pupper", left + 48, 190, c.text(), HUDTokens.title());
        text(canvas, "4.0 / 20.0 HP", left + 48, 205, c.danger(), HUDTokens.label());
        rect(canvas, left + 48, 222, 124, 5, 2.5f, c.track());
        rect(canvas, left + 48, 222, 24.8f, 5, 2.5f, c.danger());
        rect(canvas, left + 196, 180, 28, 28, 8, c.surface());
        text(canvas, "W", left + 205, 189, c.text(), HUDTokens.title());
        rect(canvas, left + 232, 182, 26, 26, 12, c.accentContainer());
        text(canvas, "D", left + 241, 190, c.onAccentContainer(), HUDTokens.title());
        text(canvas, "常态 / 按下", left + 192, 221, c.secondaryText(), HUDTokens.label());

        rect(canvas, left, 248, 264, 56, 10, c.surface());
        rect(canvas, left + 8, 258, 36, 36, 8, c.accentContainer());
        text(canvas, Icon.MUSIC_NOTE, left + 20, 270, c.onAccentContainer(), HUDTokens.icon());
        text(canvas, "夜空中的旋律", left + 56, 258, c.text(), HUDTokens.title());
        text(canvas, "Pupper Radio", left + 56, 274, c.secondaryText(), HUDTokens.label());
        rect(canvas, left + 56, 292, 198, 3, 1.5f, c.track());
        rect(canvas, left + 56, 292, 92, 3, 1.5f, c.accent());

        rect(canvas, left, 316, 264, 68, 10, c.surface());
        text(canvas, "Pupper Client", left + 8, 326, c.text(), HUDTokens.title());
        text(canvas, "Player · 单人游戏 · 144 FPS", left + 8, 342, c.secondaryText(), HUDTokens.label());
        rect(canvas, left + 4, 358, 256, 22, 7, c.raised());
        text(canvas, Icon.CHECK, left + 10, 363, c.accent(), HUDTokens.icon());
        text(canvas, "自动疾跑", left + 28, 365, c.text(), HUDTokens.body());
        text(canvas, "已启用", left + 220, 365, c.secondaryText(), HUDTokens.label());
    }

    private static void rect(Canvas canvas, float x, float y, float width, float height, float radius, Color color) {
        try (Paint paint = new Paint().setColor(color.getRGB()).setAntiAlias(true)) {
            canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
        }
    }
    private static void text(Canvas canvas, String text, float x, float y, Color color, Font font) {
        var bounds = font.measureText(text);
        try (Paint paint = new Paint().setColor(color.getRGB()).setAntiAlias(true)) {
            canvas.drawString(text, x - bounds.getLeft(), y - bounds.getTop(), font, paint);
        }
    }
}
