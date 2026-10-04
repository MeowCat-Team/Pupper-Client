package cn.pupperclient.ui.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.Rect;
import java.util.*;
import java.util.function.Consumer;
import org.lwjgl.system.MemoryUtil;

/** CPU glyph rasterization once per size, with a GPU atlas. No framebuffer readback. */
final class UiAssets implements AutoCloseable {
    record Sprite(UiTexture texture, Rect uv, Rect bounds) {}
    private record Glyph(Font font, short id, float size) {}
    private final Map<Glyph, Sprite> glyphs = new HashMap<>();
    private final Map<Image, Sprite> images = new IdentityHashMap<>();
    private final List<Page> pages = new ArrayList<>();
    private long imageBytes;
    private int rasterizations;
    private static final int ATLAS_SIZE = 1024, MAX_PAGES = 16;

    Sprite glyph(Font font, short id, float scale) {
        // Size quantization bounds animation cache growth without changing a visible pixel.
        float size = Math.max(1 / 64f, Math.round(font.getSize() * scale * 64) / 64f);
        return glyphs.computeIfAbsent(new Glyph(font, id, size), key -> {
            // Hint at the actual device size. Scaling a logical-size bitmap blurs small text.
            try (var rasterFont = font.makeWithSize(size).setEdging(FontEdging.ANTI_ALIAS).setSubpixel(false)) {
                Rect b = rasterFont.getBounds(new short[]{id})[0];
                if (b.isEmpty()) return null;
                int left = (int) Math.floor(b.getLeft()) - 2, top = (int) Math.floor(b.getTop()) - 2;
                int width = (int) Math.ceil(b.getRight()) - left + 2, height = (int) Math.ceil(b.getBottom()) - top + 2;
                return raster(width, height, Rect.makeXYWH(left, top, width, height), canvas -> {
                    canvas.translate(-left, -top);
                    try (var blob = TextBlob.makeFromPos(new short[]{id}, new Point[]{new Point(0, 0)}, rasterFont);
                         var paint = new Paint().setColor(0xFFFFFFFF).setAntiAlias(true)) { canvas.drawTextBlob(blob, 0, 0, paint); }
                }, true);
            }
        });
    }

    Sprite image(Image image) {
        Sprite cached = images.get(image);
        if (cached != null) return cached;
        int width = image.getWidth(), height = image.getHeight();
        Sprite sprite = raster(width, height, Rect.makeWH(width, height), canvas -> canvas.drawImage(image, 0, 0), false);
        images.put(image, sprite);
        imageBytes += (long) width * height * 4;
        return sprite;
    }

    private Sprite raster(int width, int height, Rect bounds, Consumer<Canvas> drawing, boolean atlas) {
        rasterizations++;
        Page page = null;
        int x = 0, y = 0;
        if (atlas && width + 2 <= ATLAS_SIZE && height + 2 <= ATLAS_SIZE) {
            if (pages.isEmpty() || !pages.getLast().fits(width, height)) pages.add(new Page());
            page = pages.getLast();
            if (page.x + width + 1 > ATLAS_SIZE) { page.y += page.rowHeight; page.x = 1; page.rowHeight = 0; }
            x = page.x; y = page.y;
            page.x += width + 2; page.rowHeight = Math.max(page.rowHeight, height + 2);
        }
        UiTexture texture = page == null ? new UiTexture("Pupper Client UI image", width, height, GpuFormat.RGBA8_UNORM) : page.texture;
        try (var pixels = new NativeImage(NativeImage.Format.RGBA, width, height, true);
             var surface = Surface.makeRasterDirect(new ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL), pixels.getPointer(), (long) width * 4)) {
            surface.getCanvas().clear(0);
            drawing.accept(surface.getCanvas());
            // upload only the new glyph rectangle, not the full atlas or full UI.
            var bytes = MemoryUtil.memByteBuffer(pixels.getPointer(), width * height * 4);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.texture, bytes, 0, 0, x, y, width, height);
        } catch (RuntimeException | Error failure) {
            if (page == null) texture.close();
            throw failure;
        }
        float tw = texture.texture.getWidth(0), th = texture.texture.getHeight(0);
        return new Sprite(texture, Rect.makeLTRB(x / tw, y / th, (x + width) / tw, (y + height) / th), bounds);
    }

    /** Flush bounded caches between frames, while no recorded command references the old textures. */
    void beginFrame() {
        if (pages.size() > MAX_PAGES || imageBytes > 128L * 1024 * 1024) clear();
        glyphs.keySet().removeIf(glyph -> glyph.font.isClosed());
        // Closed CPU images can otherwise retain artwork replaced by the music GUI indefinitely.
        var it = images.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getKey().isClosed()) {
                imageBytes -= (long) entry.getValue().bounds.getWidth() * (long) entry.getValue().bounds.getHeight() * 4;
                entry.getValue().texture.close(); it.remove();
            }
        }
    }
    int rasterizations() { return rasterizations; }
    private void clear() {
        pages.forEach(p -> p.texture.close()); pages.clear(); glyphs.clear();
        images.values().forEach(s -> s.texture.close()); images.clear(); imageBytes = 0;
    }
    @Override public void close() { clear(); }
    private static final class Page {
        final UiTexture texture = new UiTexture("Pupper Client glyph atlas", ATLAS_SIZE, ATLAS_SIZE, GpuFormat.RGBA8_UNORM);
        int x = 1, y = 1, rowHeight;
        Page() { RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture.texture, new org.joml.Vector4f()); }
        boolean fits(int w, int h) { return x + w + 1 <= ATLAS_SIZE ? y + h + 1 <= ATLAS_SIZE : y + rowHeight + h + 1 <= ATLAS_SIZE; }
    }
}
