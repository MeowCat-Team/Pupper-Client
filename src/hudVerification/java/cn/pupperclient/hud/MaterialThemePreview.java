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
import cn.pupperclient.ui.theme.MaterialControls;
import cn.pupperclient.ui.theme.MaterialControls.ButtonStyle;
import cn.pupperclient.ui.theme.MaterialControls.TextState;
import cn.pupperclient.ui.theme.MaterialTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;

/** Real production control painters at their logical sizes; not a running settings screen. */
public final class MaterialThemePreview {
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 920;
    private static final float OPACITY = MaterialTheme.DEFAULT_OPACITY;

    private MaterialThemePreview() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected an output PNG path");
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        Field field = SkiaContext.class.getDeclaredField("surface");
        field.setAccessible(true);
        Object previous = field.get(null);
        try (Surface surface = Surface.makeRasterN32Premul(WIDTH * 2, HEIGHT * 2)) {
            field.set(null, surface);
            try {
                verifyPainters(surface);
                Canvas canvas = surface.getCanvas();
                int count = canvas.getSaveCount();
                canvas.clear(0xffe5e9ed);
                canvas.save();
                try {
                    canvas.scale(2, 2);
                    drawSpecimen();
                } finally {
                    canvas.restore();
                }
                assertSaveCount(canvas, count, "specimen");
                Files.write(output, encode(surface));
            } finally {
                field.set(null, previous);
            }
        }
        System.out.println("Material control checks passed: light/dark alpha, slider endpoints, clipped text, canvas state.");
        System.out.println("Production control specimen (offscreen; not an in-game shader test): " + output);
    }

    private static void verifyPainters(Surface surface) throws Exception {
        if (Math.abs(OPACITY - .45f) > .0001f) throw new AssertionError("Default opacity must be 45%");
        Canvas canvas = surface.getCanvas();
        for (boolean dark : new boolean[] { false, true }) {
            ColorPalette p = palette(dark);
            for (float opacity : new float[] { 0, OPACITY, 1 }) {
                canvas.clear(0);
                checked(canvas, "panel", () -> MaterialTheme.panel(24, 24, 128, 128, 28, p, opacity));
                assertAlpha(surface, 88, 88, Math.round(opacity * 255), "panel center");
                canvas.clear(0);
                checked(canvas, "control surface", () -> MaterialControls.surface(24, 24, 128, 64, 12, p, opacity, 0, 0));
                assertAlpha(surface, 88, 56, Math.round(opacity * .38f * 255), "control surface center");
            }
            canvas.clear(0);
            checked(canvas, "switch off", () -> MaterialControls.switchControl(24, 24, p, OPACITY, 0, 0, 0));
            assertAlpha(surface, 62, 40, Math.round(OPACITY * 255), "inactive switch track");
            canvas.clear(0);
            checked(canvas, "switch on", () -> MaterialControls.switchControl(24, 24, p, OPACITY, 1, 0, 0));
            assertAlpha(surface, 34, 40, 255, "active switch track");
            checked(canvas, "switch state", () -> MaterialControls.switchControl(24, 24, p, OPACITY, .5f, 1, 1));
            for (float fraction : new float[] { 0, .45f, 1 }) {
                canvas.clear(0);
                checked(canvas, "slider " + fraction,
                        () -> MaterialControls.slider(24, 48, 126, p, OPACITY, fraction, 1, Float.toString(fraction * 100)));
                if (fraction == 0) assertAlpha(surface, 87, 67, Math.round(OPACITY * 255), "inactive slider track");
                if (fraction == 1) assertAlpha(surface, 87, 67, 255, "active slider track");
            }
            for (boolean search : new boolean[] { false, true }) {
                canvas.clear(0);
                checked(canvas, "long input", () -> MaterialControls.textInput(24, 24, 126, 40, search, p, OPACITY, 0, 1,
                        new TextState("很长的设置文本 / Long setting text", 1000, 1, 0, 12, "", 0)));
                assertAlpha(surface, 155, 44, 0, "text clipped at right edge");
                assertAlpha(surface, 19, 44, 0, "text clipped at left edge");
                checked(canvas, "empty input", () -> MaterialControls.textInput(24, 24, 126, 40, search, p, OPACITY, 1, 0,
                        new TextState("", 0, 0, 0, 0, "搜索", 1)));
            }
            checked(canvas, "choice", () -> MaterialControls.choice(24, 24, 126, 32, "跟随系统", p, OPACITY, 1, 12, .5f));
            checked(canvas, "keybind", () -> MaterialControls.keybind(24, 24, 126, 32, "Right Shift", false, p, OPACITY, 0));
            checked(canvas, "binding", () -> MaterialControls.keybind(24, 24, 126, 32, "", true, p, OPACITY, 1));
            checked(canvas, "file selector", () -> MaterialControls.fileSelector(24, 24, 126, 32, "很长的文件名.png", p, OPACITY, 1));
            checked(canvas, "hue", () -> MaterialControls.hue(24, 24, 126, 32, .45f, p.getPrimary(), p, OPACITY, 1));
            checked(canvas, "icon button", () -> MaterialControls.iconButton(24, 24, 40, 40, 12, 24, Icon.SETTINGS,
                    p.getSecondaryContainer(), p.getOnSecondaryContainer(), p, OPACITY, 1));
            for (ButtonStyle style : ButtonStyle.values())
                checked(canvas, "button " + style, () -> MaterialControls.button(24, 24, 126, 40, "完成", p, OPACITY, style, 1));
        }
    }

    private static void checked(Canvas canvas, String name, Runnable draw) {
        int count = canvas.getSaveCount();
        draw.run();
        assertSaveCount(canvas, count, name);
    }

    private static void assertAlpha(Surface surface, int x, int y, int expected, String name) throws Exception {
        int actual = ImageIO.read(new ByteArrayInputStream(encode(surface))).getRGB(x, y) >>> 24;
        if (actual != expected) throw new AssertionError(name + ": expected alpha " + expected + ", got " + actual);
    }

    private static void assertSaveCount(Canvas canvas, int expected, String operation) {
        if (canvas.getSaveCount() != expected) throw new AssertionError(operation + " leaked canvas save/clip state");
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
        text("Pupper · Material 控件样张", 28, 24, new Color(0x1e2b36), 28);
        text("直接调用游戏控件的绘制函数 · 真实逻辑尺寸 · 45% 默认背景不透明度", 30, 66, new Color(0x4c5e6b), 13);
        column(24, 108, false);
        column(552, 108, true);
        text("离屏控件样张：用于核对控件尺寸与状态，不代表游戏布局或 GPU 玻璃效果。", 30, 890, new Color(0x4c5e6b), 12);
    }

    private static void column(float x, float y, boolean dark) {
        ColorPalette p = palette(dark);
        Skia.drawRoundedRect(x, y, 504, 758, 28, p.getSurfaceContainerLow());
        Skia.save();
        try {
            Skia.clip(x, y, 504, 758, 28);
            Skia.drawCircle(x + 452, y + 168, 184, MaterialTheme.alpha(p.getPrimaryContainer(), .62f));
            Skia.drawCircle(x + 55, y + 606, 176, MaterialTheme.alpha(p.getTertiaryContainer(), .46f));
            for (int line = 0; line < 24; line++)
                Skia.drawLine(x, y + line * 32, x + 504, y + line * 32, .5f, MaterialTheme.alpha(p.getOutlineVariant(), .2f));
        } finally {
            Skia.restore();
        }
        text(dark ? "深色主题" : "浅色主题", x + 24, y + 20, p.getOnSurface(), 18);
        text("默认不透明度 45%", x + 366, y + 23, p.getPrimary(), 12);
        MaterialTheme.panel(x + 14, y + 58, 476, 680, 28, p, OPACITY);

        section("开关 · 52 × 32", x + 32, y + 76, p);
        String[] labels = { "关闭", "开启", "悬停", "按下" };
        for (int i = 0; i < 4; i++) {
            float itemX = x + 36 + i * 108;
            MaterialControls.switchControl(itemX, y + 106, p, OPACITY, i == 0 ? 0 : 1, i > 1 ? 1 : 0, i == 3 ? 1 : 0);
            caption(labels[i], itemX, y + 145, p);
        }

        section("滑块 · 126 × 38", x + 32, y + 178, p);
        float[] fractions = { 0, .45f, 1 };
        for (int i = 0; i < fractions.length; i++) {
            float itemX = x + 36 + i * 154;
            MaterialControls.slider(itemX, y + 236, 126, p, OPACITY, fractions[i], i == 1 ? 1 : 0,
                    Float.toString(fractions[i] * 100));
            caption(i == 1 ? "45% · 悬停 / 拖动" : i == 0 ? "0%" : "100%", itemX, y + 283, p);
        }

        section("文本输入 · 40 高 / 搜索 · 42 高", x + 32, y + 320, p);
        MaterialControls.textInput(x + 32, y + 349, 208, MaterialControls.TEXT_HEIGHT, false, p, OPACITY, 0, 0,
                new TextState("默认文本", 0, 0, 0, 0, "", 0));
        String focused = "正在输入";
        float cursor = Skia.getTextBounds(focused, Fonts.getRegular(16)).getWidth();
        MaterialControls.textInput(x + 264, y + 349, 208, MaterialControls.TEXT_HEIGHT, false, p, OPACITY, 0, 1,
                new TextState(focused, cursor, 1, focused.length(), focused.length(), "", 0));
        caption("未聚焦", x + 32, y + 396, p);
        caption("已聚焦 · 光标", x + 264, y + 396, p);
        MaterialControls.textInput(x + 32, y + 424, 440, MaterialControls.SEARCH_HEIGHT, true, p, OPACITY, 0, 0,
                new TextState("", 0, 0, 0, 0, "搜索设置与模组", 1));

        section("选择器与绑定 · 126 × 32", x + 32, y + 490, p);
        MaterialControls.choice(x + 32, y + 520, 126, 32, "跟随系统", p, OPACITY, 0, 0, 1);
        MaterialControls.fileSelector(x + 190, y + 520, 126, 32, "背景图片.png", p, OPACITY, 0);
        MaterialControls.keybind(x + 348, y + 520, 126, 32, "Right Shift", false, p, OPACITY, 0);
        MaterialControls.keybind(x + 32, y + 576, 126, 32, "", true, p, OPACITY, 0);
        MaterialControls.hue(x + 190, y + 576, 126, 32, .45f, new Color(Hct.from(162, 40, 65).toInt(), true), p, OPACITY, 0);
        MaterialControls.iconButton(x + 348, y + 572, 40, 40, 12, 24, Icon.SETTINGS,
                p.getSecondaryContainer(), p.getOnSecondaryContainer(), p, OPACITY, 0);
        MaterialControls.iconButton(x + 416, y + 572, 40, 40, 12, 24, Icon.FOLDER,
                p.getPrimaryContainer(), p.getOnPrimaryContainer(), p, OPACITY, 1);
        caption("等待按键", x + 32, y + 615, p);
        caption("色相", x + 190, y + 615, p);
        caption("图标按钮", x + 348, y + 619, p);
        MaterialControls.button(x + 32, y + 665, 128, 40, "完成", p, OPACITY, ButtonStyle.FILLED, 0);
        MaterialControls.button(x + 188, y + 665, 128, 40, "恢复默认", p, OPACITY, ButtonStyle.TONAL, 0);
        MaterialControls.button(x + 344, y + 665, 128, 40, "取消", p, OPACITY, ButtonStyle.ELEVATED, 0);
    }

    private static void section(String value, float x, float y, ColorPalette p) {
        text(value, x, y, p.getOnSurface(), 13);
    }

    private static void caption(String value, float x, float y, ColorPalette p) {
        text(value, x, y, p.getOnSurfaceVariant(), 11);
    }

    private static void text(String value, float x, float y, Color color, float size) {
        Skia.drawText(value, x, y, color, Fonts.getRegular(size));
    }
}
