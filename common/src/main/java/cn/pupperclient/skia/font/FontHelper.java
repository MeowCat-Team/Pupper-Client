package cn.pupperclient.skia.font;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import cn.pupperclient.skia.utils.SkiaUtils;

import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.Typeface;

public class FontHelper {

    private static final Map<String, Typeface> typefaceCache = new HashMap<>();
    private static final int MAX_INTEGER_SIZE = 128;
    private static final Map<String, Font[]> integerFontCache = new HashMap<>();
    private static final Map<FontKey, Font> fontCache = new HashMap<>();

    private record FontKey(String name, int sizeBits) {}

    private static Typeface getTypeface(String font, FontType type) {
        return typefaceCache.computeIfAbsent(font, k -> loadTypeface(k, type));
    }

    private static Typeface loadTypeface(String font, FontType type) {
        Optional<Data> fontDataOptional = SkiaUtils.convertToData("/assets/pupper/fonts/" + font);
        return fontDataOptional.map(data -> FontMgr.getDefault().makeFromData(data))
            .orElseThrow(() -> new IllegalArgumentException("Font not found: " + font));
    }

    public static Font load(String font, float size, FontType fontType) {
        if (size >= 0 && size <= MAX_INTEGER_SIZE && size == (int) size) {
            Font[] sizes = integerFontCache.computeIfAbsent(font, key -> new Font[MAX_INTEGER_SIZE + 1]);
            int index = (int) size;
            if (sizes[index] == null) sizes[index] = new Font(getTypeface(font, fontType), size);
            return sizes[index];
        }
        return fontCache.computeIfAbsent(new FontKey(font, Float.floatToIntBits(size)),
            key -> new Font(getTypeface(key.name(), fontType), size));
    }

    public static Font load(String font, float size) {
        return load(font, size, getFontType(font));
    }

    private static FontType getFontType(String font) {
        String fileExtension = font.substring(font.lastIndexOf('.') + 1).toLowerCase();
        return FontType.fromString(fileExtension);
    }

    public static void clearCache() {
        for (Font[] sizes : integerFontCache.values())
            for (Font cached : sizes) if (cached != null) cached.close();
        integerFontCache.clear();
        fontCache.values().forEach(Font::close);
        fontCache.clear();
        typefaceCache.values().forEach(Typeface::close);
        typefaceCache.clear();
    }

    public static void preloadFonts(String... fonts) {
        for (String font : fonts) {
            getTypeface(font, getFontType(font));
        }
    }
}
