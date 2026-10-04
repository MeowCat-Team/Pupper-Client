package cn.pupperclient.hud;

import cn.pupperclient.skia.context.SkiaContext;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Export the actual Material painters after the production Blaze3D geometry path. */
public final class BlazeUiPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        try (var fixture = new UiGpuFixture(); var target = new UiGpuFixture.Target(2160, 1840)) {
            target.clear(229 / 255f, 233 / 255f, 237 / 255f, 1);
            SkiaContext.drawOffscreen(target.view, canvas -> {
                canvas.scale(2, 2);
                MaterialThemePreview.drawSpecimen();
                if (canvas.getSaveCount() != 1) throw new AssertionError("Material specimen leaked drawing state");
            }, true);
            byte[] rgba = target.read();
            var image = new BufferedImage(target.width, target.height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                int r = target.channel(rgba, x, y, 0), g = target.channel(rgba, x, y, 1), b = target.channel(rgba, x, y, 2);
                image.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | b);
            }
            ImageIO.write(image, "PNG", output.toFile());
            SkiaContext.close();
            UiSamplingGpuChecks.export(output.resolveSibling("blaze-quality-preview.png"));
        }
        System.out.println("Production Blaze3D Material specimen: " + output);
    }
}
