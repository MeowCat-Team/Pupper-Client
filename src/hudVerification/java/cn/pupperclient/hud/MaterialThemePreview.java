package cn.pupperclient.hud;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.context.SkiaContext;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.theme.MaterialTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;

/**
 * Offscreen theme specimen using the production Material surface drawing functions.
 * The layout and control states are illustrative, not a running Minecraft screenshot.
 */
public final class MaterialThemePreview {
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 720;

    private MaterialThemePreview() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected an output PNG path");
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output.getParent());

        // Verification owns this process. Restore the previous context even if a check fails.
        Field surfaceField = SkiaContext.class.getDeclaredField("surface");
        surfaceField.setAccessible(true);
        Object previousSurface = surfaceField.get(null);
        try (Surface surface = Surface.makeRasterN32Premul(WIDTH * 2, HEIGHT * 2)) {
            surfaceField.set(null, surface);
            try {
                verifySurfaces(surface);
                Canvas canvas = surface.getCanvas();
                int saveCount = canvas.getSaveCount();
                canvas.clear(0xffe5e9ed);
                canvas.save();
                try {
                    canvas.scale(2, 2);
                    drawSpecimen();
                } finally {
                    canvas.restore();
                }
                assertSaveCount(canvas, saveCount, "specimen");
                Files.write(output, encode(surface));
            } finally {
                surfaceField.set(null, previousSurface);
            }
        }
        System.out.println("Material rendering checks passed: light/dark panel alpha = 115; canvas state restored.");
        System.out.println("Offscreen Material theme specimen (not a client screenshot): " + output);
    }

    private static void verifySurfaces(Surface surface) throws Exception {
        int expectedAlpha = Math.round(MaterialTheme.DEFAULT_OPACITY * 255);
        if (Math.abs(MaterialTheme.opacity() - .45f) > .0001f)
            throw new AssertionError("The standalone theme must default to 45% opacity");

        Canvas canvas = surface.getCanvas();
        for (boolean dark : new boolean[] { false, true }) {
            ColorPalette palette = palette(dark);
            canvas.clear(0);
            int saveCount = canvas.getSaveCount();
            MaterialTheme.panel(24, 24, 128, 128, MaterialTheme.SURFACE_RADIUS, palette);
            assertSaveCount(canvas, saveCount, "panel");
            var pixels = ImageIO.read(new ByteArrayInputStream(encode(surface)));
            int actualAlpha = pixels.getRGB(88, 88) >>> 24;
            if (actualAlpha != expectedAlpha)
                throw new AssertionError("Panel center alpha: expected " + expectedAlpha + ", got " + actualAlpha);

            MaterialTheme.card(36, 36, 104, 80, MaterialTheme.CARD_RADIUS, palette);
            assertSaveCount(canvas, saveCount, "card");
            MaterialTheme.outline(36, 124, 104, 24, MaterialTheme.CONTROL_RADIUS, palette);
            assertSaveCount(canvas, saveCount, "outline");
        }
    }

    private static void assertSaveCount(Canvas canvas, int expected, String operation) {
        if (canvas.getSaveCount() != expected)
            throw new AssertionError(operation + " leaked canvas save/clip state");
    }

    private static byte[] encode(Surface surface) {
        try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData(EncodedImageFormat.PNG)) {
            if (data == null) throw new IllegalStateException("PNG encoding failed");
            return data.getBytes();
        }
    }

    private static ColorPalette palette(boolean dark) {
        return new ColorPalette(Hct.from(220, 26, 6), dark);
    }

    private static void drawSpecimen() {
        text("Pupper · Material Glass", 28, 25, new Color(0x1e2b36), 28);
        text("真实主题函数与内置字体 · 45% 背景不透明度 · 浅色 / 深色", 30, 67, new Color(0x4c5e6b), 13);
        Skia.drawRoundedRect(835, 28, 216, 30, 15, new Color(0xd2dce3));
        Skia.drawFullCenteredText("OFFSCREEN SPECIMEN", 943, 43, new Color(0x364956), Fonts.getMedium(11));
        column(24, 112, false);
        column(552, 112, true);
        text("离屏样张：展示主题材质与控件状态，并非游戏运行截图。", 30, 691, new Color(0x4c5e6b), 11);
        text("检查通过：中心 alpha 115 / 255 · Canvas 状态恢复", 735, 691, new Color(0x4c5e6b), 10);
    }

    private static void column(float x, float y, boolean dark) {
        ColorPalette p = palette(dark);
        Skia.drawRoundedRect(x, y, 504, 558, 28, p.getSurfaceContainerLow());
        Skia.save();
        try {
            Skia.clip(x, y, 504, 558, 28);
            // Abstract background geometry makes translucency visible without implying a game scene.
            Skia.drawCircle(x + 452, y + 148, 184, MaterialTheme.alpha(p.getPrimaryContainer(), .62f));
            Skia.drawCircle(x + 55, y + 426, 146, MaterialTheme.alpha(p.getTertiaryContainer(), .46f));
            for (int line = 0; line < 18; line++) {
                Skia.drawLine(x, y + line * 32, x + 504, y + line * 32, .5f,
                        MaterialTheme.alpha(p.getOutlineVariant(), .2f));
            }
        } finally {
            Skia.restore();
        }
        text(dark ? "深色主题 / Dark" : "浅色主题 / Light", x + 24, y + 24, p.getOnSurface(), 16);
        text("45%", x + 444, y + 26, p.getPrimary(), 14);

        Skia.drawBackdropBlur(x + 18, y + 66, 468, 462, MaterialTheme.SURFACE_RADIUS);
        MaterialTheme.panel(x + 18, y + 66, 468, 462, MaterialTheme.SURFACE_RADIUS, p);
        MaterialTheme.card(x + 30, y + 86, 56, 416, MaterialTheme.CARD_RADIUS, p);
        Skia.drawRoundedRect(x + 42, y + 100, 32, 32, 12, p.getPrimary());
        icon(Icon.PETS, x + 58, y + 116, p.getOnPrimary(), 20);
        Skia.drawRoundedRect(x + 38, y + 156, 40, 40, 14, MaterialTheme.surface(p.getSecondaryContainer()));
        icon(Icon.PALETTE, x + 58, y + 176, p.getOnSecondaryContainer(), 22);
        icon(Icon.WIDGETS, x + 58, y + 230, p.getOnSurfaceVariant(), 22);
        icon(Icon.MUSIC_NOTE, x + 58, y + 284, p.getOnSurfaceVariant(), 22);
        icon(Icon.FOLDER, x + 58, y + 338, p.getOnSurfaceVariant(), 22);
        icon(Icon.SETTINGS, x + 58, y + 472, p.getOnSurfaceVariant(), 22);

        float contentX = x + 104;
        text("外观与界面", contentX, y + 89, p.getOnSurface(), 20);
        text("Material 3 · 柔和玻璃与清晰内容", contentX, y + 117, p.getOnSurfaceVariant(), 11);

        MaterialTheme.card(contentX, y + 144, 346, 38, 19, p);
        icon(Icon.SEARCH, contentX + 20, y + 163, p.getPrimary(), 20);
        text("搜索设置与模组", contentX + 40, y + 155, p.getOnSurfaceVariant(), 13);

        MaterialTheme.card(contentX, y + 198, 346, 84, MaterialTheme.CARD_RADIUS, p);
        icon(Icon.PALETTE, contentX + 26, y + 224, p.getPrimary(), 20);
        text("界面主题", contentX + 46, y + 215, p.getOnSurface(), 14);
        text("Material Glass", contentX + 18, y + 248, p.getOnSurfaceVariant(), 12);
        Skia.drawCircle(contentX + 244, y + 247, 10, p.getPrimary());
        Skia.drawCircle(contentX + 272, y + 247, 10, p.getSecondary());
        Skia.drawCircle(contentX + 300, y + 247, 10, p.getTertiary());

        MaterialTheme.card(contentX, y + 294, 346, 70, MaterialTheme.CARD_RADIUS, p);
        text("背景模糊", contentX + 18, y + 310, p.getOnSurface(), 14);
        text("柔化背景，保持内容清晰", contentX + 18, y + 335, p.getOnSurfaceVariant(), 11);
        toggle(contentX + 278, y + 313, p);

        MaterialTheme.card(contentX, y + 376, 346, 80, MaterialTheme.CARD_RADIUS, p);
        text("背景不透明度", contentX + 18, y + 390, p.getOnSurface(), 14);
        text("45%", contentX + 292, y + 391, p.getPrimary(), 13);
        slider(contentX + 18, y + 420, 310, p);

        MaterialTheme.card(contentX, y + 470, 162, 38, 19, p);
        Skia.drawFullCenteredText("恢复默认", contentX + 81, y + 489, p.getPrimary(), Fonts.getMedium(13));
        Skia.drawRoundedRect(contentX + 174, y + 470, 172, 38, 19, p.getPrimary());
        Skia.drawFullCenteredText("完成", contentX + 260, y + 489, p.getOnPrimary(), Fonts.getMedium(13));
    }

    private static void toggle(float x, float y, ColorPalette p) {
        Skia.drawRoundedRect(x, y, 52, 32, 16, p.getPrimary());
        Skia.drawCircle(x + 36, y + 16, 12, p.getOnPrimary());
        icon(Icon.CHECK, x + 36, y + 16, p.getPrimary(), 14);
    }

    private static void slider(float x, float y, float width, ColorPalette p) {
        float selected = width * .45f;
        Skia.drawRoundedRect(x, y, selected - 8, 14, 7, p.getPrimary());
        Skia.drawRoundedRect(x + selected + 8, y, width - selected - 8, 14, 7,
                MaterialTheme.surface(p.getPrimaryContainer()));
        Skia.drawRoundedRect(x + selected - 2, y - 5, 4, 24, 2, p.getPrimary());
    }

    private static void text(String value, float x, float y, Color color, float size) {
        Skia.drawText(value, x, y, color, Fonts.getRegular(size));
    }

    private static void icon(String value, float x, float y, Color color, float size) {
        Skia.drawFullCenteredText(value, x, y, color, Fonts.getIcon(size));
    }
}
