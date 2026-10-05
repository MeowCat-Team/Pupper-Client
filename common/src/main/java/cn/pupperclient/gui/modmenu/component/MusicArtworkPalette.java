package cn.pupperclient.gui.modmenu.component;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/** A bounded, asynchronous cover sample supplies colors to the Blaze3D gradient painter. */
public final class MusicArtworkPalette implements AutoCloseable {
    public record Tones(Color first, Color second, Color third, Color fourth) { }
    private record Source(File file, long modified, long length) { }
    private Source source;
    private volatile Tones tones;
    private Thread worker;

    public synchronized Tones colors(File cover) {
        var next = cover == null ? null : new Source(cover, cover.lastModified(), cover.length());
        if (!java.util.Objects.equals(source, next)) {
            source = next; tones = null;
            if (worker != null) worker.interrupt();
            worker = next == null ? null : Thread.ofVirtual().name("Pupper Client artwork colors").start(() -> {
                try {
                    var sampled = sample(next.file());
                    synchronized (this) { if (source == next) tones = sampled; }
                } catch (IOException | RuntimeException unavailable) { /* The theme supplies a fallback. */ }
            });
        }
        return tones;
    }

    public static Tones sample(File file) throws IOException {
        try (var input = ImageIO.createImageInputStream(file)) {
            if (input == null) throw new IOException("Missing artwork");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported artwork");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0) throw new IOException("Empty artwork");
                var options = reader.getDefaultReadParam();
                options.setSourceSubsampling((width - 1) / 32 + 1, (height - 1) / 32 + 1, 0, 0);
                var image = reader.read(0, options);
                var sums = new long[4][4];
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int quadrant = (y < image.getHeight() / 2 ? 0 : 2) + (x < image.getWidth() / 2 ? 0 : 1);
                    int pixel = image.getRGB(x, y), alpha = pixel >>> 24;
                    sums[quadrant][0] += ((pixel >>> 16) & 255) * alpha;
                    sums[quadrant][1] += ((pixel >>> 8) & 255) * alpha;
                    sums[quadrant][2] += (pixel & 255) * alpha; sums[quadrant][3] += alpha;
                }
                return new Tones(color(sums[0]), color(sums[1]), color(sums[2]), color(sums[3]));
            } finally { reader.dispose(); }
        }
    }
    private static Color color(long[] sum) {
        if (sum[3] == 0) return new Color(48, 48, 56);
        return new Color((int) (sum[0] / sum[3]), (int) (sum[1] / sum[3]), (int) (sum[2] / sum[3]));
    }
    @Override public synchronized void close() {
        source = null; tones = null;
        if (worker != null) worker.interrupt(); worker = null;
    }
}
