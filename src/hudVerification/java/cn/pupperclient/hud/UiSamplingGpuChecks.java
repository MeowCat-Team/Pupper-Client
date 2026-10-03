package cn.pupperclient.hud;

import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.ui.render.BlazeUiRenderer;
import cn.pupperclient.ui.render.UiCanvas.ImageSampling;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.Rect;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Pixel-art edges and small-font coverage must survive fractional GUI transforms. */
final class UiSamplingGpuChecks {
    static int verify(BlazeUiRenderer renderer) {
        int cases = 0;
        try (var target = new UiGpuFixture.Target(320, 128); var source = pattern();
             var image = source.makeImageSnapshot(); var borrowed = new UiGpuFixture.Target(8, 8)) {
            // A borrowed Minecraft texture has straight alpha, unlike CPU premultiplied sprites.
            ByteBuffer bytes = ByteBuffer.allocateDirect(8 * 8 * 4);
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                boolean red = (x + y) % 2 == 0;
                bytes.put((byte) (red ? 255 : 0)).put((byte) 0).put((byte) (red ? 0 : 255)).put((byte) 255);
            }
            bytes.flip();
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(borrowed.texture, bytes, 0, 0, 0, 0, 8, 8);
            for (float scale : new float[]{.75f, 1, 1.25f, 1.5f, 2}) {
                target.clear(0, 0, 0, 0);
                renderer.draw(target.view, canvas -> {
                    canvas.translate(3.35f, 2.65f); canvas.scale(scale, scale);
                    try (var opacity = new Paint().setAlpha(128)) {
                        int saved = canvas.saveLayer(Rect.makeXYWH(0, 0, 145, 48), opacity);
                        canvas.drawImageRect(image, Rect.makeXYWH(2, 2, 4, 4), Rect.makeXYWH(4, 4, 40, 40), null, true, ImageSampling.PIXEL);
                        canvas.drawTexture(borrowed.view, Rect.makeXYWH(2, 2, 4, 4), Rect.makeXYWH(52, 4, 40, 40), 1, ImageSampling.PIXEL);
                        canvas.drawImageRect(image, Rect.makeXYWH(2, 2, 4, 4), Rect.makeXYWH(100, 4, 40, 40), null, true);
                        canvas.restoreToCount(saved);
                    }
                }, false);
                byte[] actual = target.read();
                for (int offset : new int[]{4, 52}) {
                    for (int y = (int) Math.ceil(2.65f + 5 * scale); y < 2.65f + 43 * scale; y++) {
                        for (int x = (int) Math.ceil(3.35f + (offset + 1) * scale); x < 3.35f + (offset + 39) * scale; x++) {
                            int r = target.channel(actual, x, y, 0), b = target.channel(actual, x, y, 2);
                            require((r == 128 && b == 0) || (r == 0 && b == 128), "Pixel art blurred at scale " + scale);
                            require(target.channel(actual, x, y, 3) == 128, "Pixel-art layer alpha changed");
                        }
                    }
                }
                boolean blended = false;
                for (int x = (int) (3.35f + 101 * scale); x < 3.35f + 139 * scale; x++) {
                    int r = target.channel(actual, x, (int) (2.65f + 20 * scale), 0);
                    if (r > 4 && r < 124) blended = true;
                }
                require(blended, "Smooth artwork accidentally uses pixel-art filtering");
                cases++;
            }
            for (float scale : new float[]{.75f, 1, 1.25f, 1.5f, 2}) for (boolean layer : new boolean[]{false, true}) {
                var font = Fonts.getMedium(12);
                String text = "Cosmetics 字体";
                try (var reference = Surface.makeRasterN32Premul(320, 128);
                     var deviceFont = font.makeWithSize(font.getSize() * scale).setEdging(FontEdging.ANTI_ALIAS).setSubpixel(false);
                     var paint = new Paint().setColor(-1); var opacity = new Paint().setAlpha(128)) {
                    var cpu = reference.getCanvas(); cpu.clear(0);
                    int saved = layer ? cpu.saveLayer(null, opacity) : cpu.save();
                    short[] glyphs = font.getStringGlyphs(text); float[] advances = font.getWidths(glyphs);
                    Point[] positions = new Point[glyphs.length]; float x = 7.35f;
                    for (int i = 0; i < glyphs.length; i++) {
                        positions[i] = new Point(Math.round(11.4f + x * scale), Math.round(5.65f + 44.25f * scale));
                        x += advances[i];
                    }
                    // Native Skia draws directly into the final-resolution reference, with no atlas/filter.
                    try (var blob = TextBlob.makeFromPos(glyphs, positions, deviceFont)) { cpu.drawTextBlob(blob, 0, 0, paint); }
                    cpu.restoreToCount(saved);
                    BufferedImage expected = snapshot(reference);
                    target.clear(0, 0, 0, 0);
                    renderer.draw(target.view, canvas -> {
                        canvas.translate(11.4f, 5.65f); canvas.scale(scale, scale);
                        int state = layer ? canvas.saveLayer(Rect.makeXYWH(-4, -4, 310 / scale, 120 / scale), opacity) : canvas.save();
                        canvas.drawString(text, 7.35f, 44.25f, font, paint);
                        canvas.restoreToCount(state);
                    }, false);
                    byte[] actual = target.read(); int different = 0;
                    for (int y = 0; y < 128; y++) for (int px = 0; px < 320; px++) {
                        if (Math.abs((expected.getRGB(px, y) >>> 24) - target.channel(actual, px, y, 3)) > 2) different++;
                    }
                    require(different == 0, "Small-font coverage differs from native device-size rasterization at " + scale + ", layer=" + layer + ": " + different + " pixels");
                    int rasterizations = renderer.assetRasterizations();
                    target.clear(0, 0, 0, 0);
                    renderer.draw(target.view, canvas -> {
                        canvas.translate(11.4f, 5.65f); canvas.scale(scale, scale);
                        int state = layer ? canvas.saveLayer(Rect.makeXYWH(-4, -4, 310 / scale, 120 / scale), opacity) : canvas.save();
                        canvas.drawString(text, 7.35f, 44.25f, font, paint); canvas.restoreToCount(state);
                    }, false);
                    require(renderer.assetRasterizations() == rasterizations, "Device-size glyphs missed the cache");
                }
                cases++;
            }
        }
        return cases;
    }

    static void export(Path output) throws IOException {
        try (var renderer = new BlazeUiRenderer(); var target = new UiGpuFixture.Target(900, 480);
             var source = pattern(); var image = source.makeImageSnapshot(); var paint = new Paint().setColor(0xFFE7EDF0)) {
            target.clear(.06f, .08f, .09f, 1);
            renderer.draw(target.view, canvas -> {
                canvas.drawString("Pixel art and device-size text", 24.35f, 36.65f, Fonts.getMedium(20), paint);
                canvas.drawImageRect(image, Rect.makeXYWH(2, 2, 4, 4), Rect.makeXYWH(24, 64, 160, 160), null, true, ImageSampling.PIXEL);
                canvas.drawImageRect(image, Rect.makeXYWH(2, 2, 4, 4), Rect.makeXYWH(208, 64, 160, 160), null, true);
                canvas.drawString("Skin / cape: pixel", 24.35f, 252.65f, Fonts.getRegular(14), paint);
                canvas.drawString("Artwork: smooth", 208.35f, 252.65f, Fonts.getRegular(14), paint);
                int row = 0;
                for (float scale : new float[]{.75f, 1, 1.25f, 1.5f, 2}) {
                    int state = canvas.save(); canvas.translate(408.35f, 48.65f + row++ * 78); canvas.scale(scale, scale);
                    canvas.drawString("Home  Mods  Cosmetics", 0, 20, Fonts.getMedium(12), paint);
                    canvas.drawString("字体清晰  搜索  设置", 0, 42, Fonts.getRegular(14), paint);
                    canvas.restoreToCount(state);
                }
            }, false);
            byte[] rgba = target.read(); var result = new BufferedImage(target.width, target.height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                result.setRGB(x, y, 0xFF000000 | target.channel(rgba, x, y, 0) << 16 | target.channel(rgba, x, y, 1) << 8 | target.channel(rgba, x, y, 2));
            }
            ImageIO.write(result, "PNG", output.toFile());
        }
    }

    private static Surface pattern() {
        var surface = Surface.makeRasterN32Premul(8, 8);
        try (var paint = new Paint()) {
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                surface.getCanvas().drawRect(Rect.makeXYWH(x, y, 1, 1), paint.setColor((x + y) % 2 == 0 ? 0xFFFF0000 : 0xFF0000FF));
            }
        }
        return surface;
    }
    private static BufferedImage snapshot(Surface surface) {
        try (var image = surface.makeImageSnapshot(); var png = image.encodeToData(EncodedImageFormat.PNG)) {
            return ImageIO.read(new ByteArrayInputStream(png.getBytes()));
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
