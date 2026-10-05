package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.management.music.MusicExperienceStore;
import cn.pupperclient.management.music.lyric.LyricsManager;
import cn.pupperclient.management.music.lyric.SongLyrics;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.theme.MaterialTheme;
import io.github.humbleui.skija.Font;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.RRect;
import java.awt.Color;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;

/** Full-window lyrics presentation using the shared Skia/Blaze3D canvas. */
public final class MusicNowPlayingUi {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private record Row(List<String> original, List<String> translation, float top, float height) {
        float center() { return top + height / 2; }
    }
    private static SongLyrics cachedDocument;
    private static float cachedWidth, cachedSize;
    private static boolean cachedTranslations;
    private static List<Row> cachedRows = List.of();
    private static int cachedPrimary;
    private static ColorPalette cachedImmersive;

    private MusicNowPlayingUi() { }

    /** Transport controls are painted separately, with the same NowPlaying layout. */
    public static void frame(float width, float height, MusicUi.Playback state, double mx, double my,
            ColorPalette palette) {
        frame(width, height, state, mx, my, palette, null);
    }
    public static ColorPalette immersivePalette(ColorPalette palette) {
        int primary = palette.getPrimary().getRGB();
        if (cachedImmersive == null || cachedPrimary != primary) {
            cachedPrimary = primary;
            cachedImmersive = new ColorPalette(cn.pupperclient.libraries.material3.hct.Hct.fromInt(primary), true);
        }
        return cachedImmersive;
    }
    public static void frame(float width, float height, MusicUi.Playback state, double mx, double my,
            ColorPalette palette, MusicArtworkPalette.Tones tones) {
        var layout = MusicPlayerLayout.nowPlaying(width, height);
        if (tones == null) tones = new MusicArtworkPalette.Tones(palette.getPrimary(), palette.getTertiary(),
            palette.getSecondaryContainer(), palette.getPrimaryContainer());
        Skia.getCanvas().drawGradient(RRect.makeXYWH(0, 0, width, height, 0),
            new Point(0, 0), new Point(width, height),
            new int[] { backdrop(tones.first()).getRGB(), backdrop(tones.fourth()).getRGB() },
            new float[] { 0, 1 }, 0);
        glow(width * .72f, height * .18f, width * .7f, backdrop(tones.second()));
        glow(width * .18f, height * .86f, width * .6f, backdrop(tones.third()));
        var back = layout.back();
        MusicUi.iconButton(back.x(), back.y(), Icon.EXPAND_MORE, false, true, back.contains(mx, my), palette);

        var artwork = layout.artwork();
        Skia.drawShadow(artwork.x(), artwork.y(), artwork.width(), artwork.height(), 20);
        MusicUi.artwork(state.cover(), artwork.x(), artwork.y(), artwork.width(), palette);
        metadata(layout.metadata(), state, palette);
        if (back.contains(mx, my)) MusicUi.tooltip(MusicText.get("music.lyrics.back"), mx, my + 84, width, palette);
    }
    private static Color backdrop(Color color) {
        return new Color(16 + (int) (color.getRed() * .30f), 16 + (int) (color.getGreen() * .30f), 20 + (int) (color.getBlue() * .30f));
    }
    private static void glow(float x, float y, float radius, Color color) {
        Skia.getCanvas().drawRadialCircle(x, y, radius, color.getRGB(), MaterialTheme.alpha(color, 0).getRGB());
    }

    private static void metadata(MusicPlayerLayout.Box box, MusicUi.Playback state, ColorPalette palette) {
        Font titleFont = Fonts.getMedium(32), artistFont = Fonts.getRegular(20);
        String title = state.title() == null || state.title().isBlank() ? MusicText.get("music.player.choose") : state.title();
        var titleLines = wrap(title, box.width(), text -> Skia.getTextBounds(text, titleFont).getWidth());
        int count = Math.min(2, titleLines.size());
        Skia.save();
        try {
            Skia.clip(box.x(), box.y(), box.width(), box.height(), 0);
            for (int i = 0; i < count; i++) {
                String line = i == 1 && titleLines.size() > 2
                    ? Skia.getLimitText(String.join(" ", titleLines.subList(1, titleLines.size())), titleFont, box.width())
                    : titleLines.get(i);
                Skia.drawText(line, box.x(), box.y() + i * 38, palette.getOnSurface(), titleFont);
            }
            if (state.artist() == null || state.artist().isBlank()) return;
            var artists = wrap(state.artist(), box.width(), text -> Skia.getTextBounds(text, artistFont).getWidth());
            for (int i = 0; i < Math.min(3 - count, artists.size()); i++)
                Skia.drawText(artists.get(i), box.x(), box.y() + count * 38 + 4 + i * 26,
                    palette.getOnSurfaceVariant(), artistFont);
        } finally { Skia.restore(); }
    }

    /** Retry and scroll routing use these exact same boxes as the painter. */
    public static MusicPlayerLayout.Box retry(MusicPlayerLayout.Box box) {
        return new MusicPlayerLayout.Box(box.x() + box.width() - 48, box.y(), 48, 48);
    }
    public static MusicPlayerLayout.Box body(MusicPlayerLayout.Box box) {
        return new MusicPlayerLayout.Box(box.x(), box.y() + 48, box.width(), Math.max(0, box.height() - 48));
    }
    public static boolean canRetry(String title, LyricsManager.Result result) {
        return title != null && (result.state() == LyricsManager.State.ERROR || result.state() == LyricsManager.State.EMPTY);
    }
    public static boolean pendingSelection(boolean loading, String selectedKey, String playingKey) {
        return loading || selectedKey != null && !selectedKey.equals(playingKey);
    }

    public static void lyrics(MusicPlayerLayout.Box box, String title, LyricsManager.Result result, int active,
            float focus, double mx, double my, ColorPalette palette) {
        lyrics(box, title, result, active, focus, mx, my, palette, MusicExperienceStore.Settings.DEFAULT);
    }
    public static void lyrics(MusicPlayerLayout.Box box, String title, LyricsManager.Result result, int active,
            float focus, double mx, double my, ColorPalette palette, MusicExperienceStore.Settings options) {
        var retry = retry(box);
        if (canRetry(title, result)) MusicUi.iconButton(retry.x(), retry.y(), Icon.REFRESH, false,
            true, retry.contains(mx, my), palette);
        var body = body(box);
        if (body.width() <= 0 || body.height() <= 0) return;
        var document = result.lyrics();
        Skia.save();
        try {
            Skia.clip(body.x(), body.y(), body.width(), body.height(), 16);
            if (title == null || document.isEmpty()) {
                String key = title == null ? "music.lyrics.choose" : switch (result.state()) {
                    case LOADING -> "music.lyrics.loading";
                    case ERROR -> "music.lyrics.error";
                    default -> "music.lyrics.empty";
                };
                empty(body, MusicText.get(key), palette);
                return;
            }
            float textWidth = Math.max(1, body.width() - 64);
            float fontSize = options.lyricSize();
            float translatedSize = Math.clamp(fontSize * .61f, 16, 28);
            Font originalFont = Fonts.getMedium(fontSize), translatedFont = Fonts.getRegular(translatedSize);
            var rows = rows(document, textWidth, fontSize, options.translations(), originalFont, translatedFont);
            float safeFocus = Float.isFinite(focus) ? Math.clamp(focus, 0, rows.size() - 1) : 0;
            int before = (int) safeFocus, after = Math.min(rows.size() - 1, before + 1);
            float center = rows.get(before).center() + (rows.get(after).center() - rows.get(before).center()) * (safeFocus - before);
            float origin = body.y() + body.height() * .44f - center;
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                float y = origin + row.top();
                if (y + row.height() < body.y() || y >= body.y() + body.height()) continue;
                boolean current = document.synced() && i == active;
                float distance = Math.abs(y + row.height() / 2 - (body.y() + body.height() * .44f));
                float emphasis = document.synced() ? Math.clamp(.62f - distance / Math.max(1, body.height()), .22f, .52f) : .9f;
                Color textColor = current ? palette.getOnSurface() : MaterialTheme.alpha(palette.getOnSurface(), emphasis);
                if (document.synced() && body.contains(mx, my) && my >= y && my < y + row.height())
                    Skia.drawRoundedRect(body.x() + 8, y + 4, body.width() - 16, row.height() - 8, 16,
                        MaterialTheme.alpha(palette.getOnSurface(), .06f));
                float textY = y + 16;
                for (String line : row.original()) {
                    Skia.drawText(line, body.x() + 32, textY, textColor, originalFont);
                    textY += fontSize * 1.3f;
                }
                if (!row.translation().isEmpty()) textY += 8;
                for (String line : row.translation()) {
                    Skia.drawText(line, body.x() + 32, textY,
                        MaterialTheme.alpha(palette.getOnSurface(), current ? .72f : emphasis * .8f), translatedFont);
                    textY += translatedSize * 1.35f;
                }
            }
        } finally { Skia.restore(); }
    }

    private static void empty(MusicPlayerLayout.Box body, String message, ColorPalette palette) {
        Font font = Fonts.getRegular(22);
        var lines = wrap(message, Math.max(1, body.width() - 64), text -> Skia.getTextBounds(text, font).getWidth());
        float top = body.y() + body.height() / 2 - (64 + lines.size() * 30) / 2f;
        Skia.drawFullCenteredText(Icon.LYRICS, body.x() + body.width() / 2, top + 24,
            palette.getPrimary(), Fonts.getIcon(48));
        for (int i = 0; i < lines.size(); i++)
            Skia.drawCenteredText(lines.get(i), body.x() + body.width() / 2, top + 64 + i * 30,
                palette.getOnSurfaceVariant(), font);
    }

    /** Wrapping is cached by document identity and viewport so animation does not remeasure entire songs. */
    private static List<Row> rows(SongLyrics document, float width, float size, boolean translations, Font originalFont, Font translatedFont) {
        if (document == cachedDocument && width == cachedWidth && size == cachedSize && translations == cachedTranslations) return cachedRows;
        List<Row> rows = new ArrayList<>();
        int count = document.synced() ? document.lines().size() : document.plainText().size();
        float top = 0;
        for (int i = 0; i < count; i++) {
            String text = document.synced() ? document.lines().get(i).getText() : document.plainText().get(i);
            String translated = translations && document.synced() ? document.lines().get(i).getTranslation() : "";
            var original = wrap(text.isBlank() ? "♪" : text, width, line -> Skia.getTextBounds(line, originalFont).getWidth());
            var translation = translated.isBlank() ? List.<String>of()
                : wrap(translated, width, line -> Skia.getTextBounds(line, translatedFont).getWidth());
            float height = 32 + original.size() * size * 1.3f + (translation.isEmpty() ? 0 : 8 + translation.size() * Math.clamp(size * .61f, 16, 28) * 1.35f);
            rows.add(new Row(original, translation, top, height));
            top += height;
        }
        cachedDocument = document; cachedWidth = width; cachedSize = size; cachedTranslations = translations; cachedRows = List.copyOf(rows);
        return cachedRows;
    }

    public static int lineAt(MusicPlayerLayout.Box box, SongLyrics document, float focus,
            MusicExperienceStore.Settings options, double mx, double my) {
        var viewport = body(box);
        if (!document.synced() || !viewport.contains(mx, my)) return -1;
        float size = options.lyricSize();
        var measured = rows(document, Math.max(1, viewport.width() - 64), size, options.translations(),
            Fonts.getMedium(size), Fonts.getRegular(Math.clamp(size * .61f, 16, 28)));
        return hitLine(viewport, measured.stream().map(Row::height).toList(), focus, mx, my);
    }

    /** Hit testing uses wrapped row heights and the same interpolated focus as drawing. */
    public static int hitLine(MusicPlayerLayout.Box viewport, List<Float> heights, float focus, double mx, double my) {
        if (!viewport.contains(mx, my) || heights.isEmpty()) return -1;
        float[] centers = new float[heights.size()];
        float top = 0;
        for (int i = 0; i < heights.size(); i++) {
            float height = heights.get(i);
            if (!Float.isFinite(height) || height <= 0) return -1;
            centers[i] = top + height / 2; top += height;
        }
        float safe = Float.isFinite(focus) ? Math.clamp(focus, 0, heights.size() - 1) : 0;
        int before = (int) safe, after = Math.min(heights.size() - 1, before + 1);
        float origin = viewport.y() + viewport.height() * .44f
            - (centers[before] + (centers[after] - centers[before]) * (safe - before));
        top = origin;
        for (int i = 0; i < heights.size(); i++) {
            float bottom = top + heights.get(i);
            if (my >= top && my < bottom) return i;
            top = bottom;
        }
        return -1;
    }

    /** Unicode line breaks prefer complete words; oversized words break at extended grapheme boundaries. */
    public static List<String> wrap(String text, float width, ToDoubleFunction<String> measure) {
        Objects.requireNonNull(measure);
        if (text == null || text.isEmpty()) return List.of();
        double limit = Float.isFinite(width) && width > 0 ? width : 1;
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R", -1)) {
            if (paragraph.isBlank()) { lines.add(""); continue; }
            BreakIterator breaks = BreakIterator.getLineInstance(Locale.ROOT);
            breaks.setText(paragraph);
            StringBuilder line = new StringBuilder();
            int start = breaks.first();
            for (int end = breaks.next(); end != BreakIterator.DONE; start = end, end = breaks.next()) {
                String segment = paragraph.substring(start, end);
                if (line.isEmpty()) segment = segment.stripLeading();
                if (!line.isEmpty() && measure.applyAsDouble(line + segment.stripTrailing()) > limit) {
                    lines.add(line.toString().stripTrailing()); line.setLength(0); segment = segment.stripLeading();
                }
                if (measure.applyAsDouble(segment.stripTrailing()) <= limit) { line.append(segment); continue; }
                var graphemes = GRAPHEME.matcher(segment);
                while (graphemes.find()) {
                    String grapheme = graphemes.group();
                    if (!line.isEmpty() && measure.applyAsDouble(line + grapheme) > limit) {
                        lines.add(line.toString().stripTrailing()); line.setLength(0);
                    }
                    if (!line.isEmpty() || !grapheme.isBlank()) line.append(grapheme);
                }
            }
            if (!line.isEmpty()) lines.add(line.toString().stripTrailing());
        }
        return List.copyOf(lines);
    }
}
