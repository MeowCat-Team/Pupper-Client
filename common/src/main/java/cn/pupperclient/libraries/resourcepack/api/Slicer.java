package cn.pupperclient.libraries.resourcepack.api;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

public class Slicer {
	
    public static void writeImage(final Path path, final BufferedImage image) throws IOException {
        Files.createDirectories(path.getParent());
        Files.deleteIfExists(path);
        try (final OutputStream os = Files.newOutputStream(path)) {
            ImageIO.write(image, "png", os);
        }
    }
}
