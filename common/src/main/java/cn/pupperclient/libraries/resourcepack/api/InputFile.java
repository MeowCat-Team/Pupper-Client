package cn.pupperclient.libraries.resourcepack.api;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

public class InputFile {
	
    private final String path;
    private final List<OutputFile> outputs = new ArrayList<>();

    public InputFile(final String path) {
        this.path = path;
    }

    public InputFile outputs(final OutputFile... files) {
        Collections.addAll(outputs, files);
        return this;
    }

    public void process(final Path inputRoot, final Path outputRoot) throws IOException {
        final Path inputPath = inputRoot.resolve(this.path);
        if (Files.exists(inputPath)) {
            try (final InputStream is = Files.newInputStream(inputPath)) {
                final BufferedImage image = ImageIO.read(is);
                final BufferedImage leftoverImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                final Graphics2D leftoverGraphics = leftoverImage.createGraphics();
                leftoverGraphics.drawImage(image, 0, 0, null);

                for (final OutputFile outputFile : outputs) {
                    outputFile.process(outputRoot, inputPath, image, leftoverGraphics);
                }

                leftoverGraphics.dispose();
            }
        }
    }
}
