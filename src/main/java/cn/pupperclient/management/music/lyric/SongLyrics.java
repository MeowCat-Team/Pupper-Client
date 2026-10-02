package cn.pupperclient.management.music.lyric;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Immutable LRC timeline. Untimed lyrics stay untimed instead of advancing at invented timestamps. */
public record SongLyrics(List<LyricLine> lines, List<String> plainText) {
    private static final Pattern TIME = Pattern.compile("\\[(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:([+-]?\\d+)]", Pattern.CASE_INSENSITIVE);
    public static final SongLyrics EMPTY = new SongLyrics(List.of(), List.of());

    public SongLyrics { lines = List.copyOf(lines); plainText = List.copyOf(plainText); }
    public boolean isEmpty() { return lines.isEmpty() && plainText.isEmpty(); }
    public boolean synced() { return !lines.isEmpty(); }

    public int currentIndex(float seconds) {
        if (!Float.isFinite(seconds)) return -1;
        long time = Math.round(seconds * 1000d);
        int low = 0, high = lines.size() - 1, result = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).getMillis() <= time) { result = mid; low = mid + 1; }
            else high = mid - 1;
        }
        return result;
    }

    public static SongLyrics parse(String original, String translated) {
        TreeMap<Long, String> timeline = timed(original), translations = timed(translated);
        List<LyricLine> lines = timeline.entrySet().stream().map(e -> new LyricLine(e.getKey(), e.getValue(),
            translations.getOrDefault(e.getKey(), ""))).toList();
        List<String> plain = new ArrayList<>();
        if (lines.isEmpty() && original != null) for (String raw : original.split("\\R")) {
            String text = raw.strip().replace("\uFEFF", "");
            if (!text.isBlank() && !text.matches("\\[[a-zA-Z]+:.*]") && !TIME.matcher(text).find()) plain.add(text);
        }
        return new SongLyrics(lines, plain);
    }

    private static TreeMap<Long, String> timed(String content) {
        TreeMap<Long, String> result = new TreeMap<>();
        if (content == null) return result;
        long offset = 0;
        var offsetMatch = OFFSET.matcher(content);
        if (offsetMatch.find()) try { offset = Long.parseLong(offsetMatch.group(1)); }
        catch (NumberFormatException ignored) { }
        for (String raw : content.split("\\R")) {
            var match = TIME.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (match.find()) {
                try {
                    long seconds = Long.parseLong(match.group(2));
                    String fraction = match.group(3);
                    long millis = fraction == null ? 0 : Long.parseLong((fraction + "000").substring(0, 3));
                    if (seconds < 60) times.add(Math.max(0, Math.addExact(Math.multiplyExact(
                        Long.parseLong(match.group(1)), 60_000), seconds * 1000 + millis + offset)));
                } catch (ArithmeticException | NumberFormatException ignored) { }
                end = match.end();
            }
            String text = raw.substring(end).strip();
            for (long time : times) result.merge(time, text, (a, b) -> a.equals(b) ? a : a + " / " + b);
        }
        return result;
    }
}
