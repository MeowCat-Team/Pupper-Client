package cn.pupperclient.music;

import cn.pupperclient.gui.modmenu.component.MusicNowPlayingUi;
import cn.pupperclient.gui.modmenu.component.MusicPlayerLayout;
import cn.pupperclient.gui.modmenu.component.MusicArtworkPalette;
import cn.pupperclient.management.music.lyric.LyricsManager;
import cn.pupperclient.management.music.lyric.SongLyrics;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Lyrics wrapping and resize/pointer geometry contracts without Minecraft or native canvas setup. */
public final class MusicNowPlayingChecks {
    private static int checks;
    private static final ToDoubleFunction<String> COLUMNS = text -> text.codePointCount(0, text.length());

    private MusicNowPlayingChecks() { }
    public static void main(String[] args) { run(); }
    public static void run() {
        checks = 0;
        wordsAndChinese();
        unicodeAndExplicitBreaks();
        pointerGeometry();
        selectionHandoff();
        lyricSeeking();
        retryVisibility();
        artworkColors();
        System.out.println("Music now-playing checks: " + checks + " passed");
    }
    private static void retryVisibility() {
        require(!MusicNowPlayingUi.canRetry(null, new LyricsManager.Result(LyricsManager.State.EMPTY, SongLyrics.EMPTY)),
            "No selected track has no invisible refresh action");
        require(!MusicNowPlayingUi.canRetry("song", new LyricsManager.Result(LyricsManager.State.LOADING, SongLyrics.EMPTY)),
            "Loading does not expose a competing refresh action");
        require(!MusicNowPlayingUi.canRetry("song", new LyricsManager.Result(LyricsManager.State.READY, SongLyrics.parse("[00:00]hello", ""))),
            "Available lyrics leave the top of the column uncluttered");
        for (var state : List.of(LyricsManager.State.EMPTY, LyricsManager.State.ERROR))
            require(MusicNowPlayingUi.canRetry("song", new LyricsManager.Result(state, SongLyrics.EMPTY)), "Unavailable lyrics allow a retry");
    }
    private static void artworkColors() {
        java.nio.file.Path file = null;
        try {
            file = java.nio.file.Files.createTempFile("pupper-artwork-", ".png");
            var image = new java.awt.image.BufferedImage(1024, 1024, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var colors = List.of(java.awt.Color.RED, java.awt.Color.GREEN, java.awt.Color.BLUE, new java.awt.Color(128, 64, 192));
            var graphics = image.createGraphics();
            try {
                for (int i = 0; i < 4; i++) {
                    graphics.setColor(colors.get(i)); graphics.fillRect(i % 2 * 512, i / 2 * 512, 512, 512);
                }
            } finally { graphics.dispose(); }
            javax.imageio.ImageIO.write(image, "png", file.toFile());
            var sample = MusicArtworkPalette.sample(file.toFile());
            require(List.of(sample.first(), sample.second(), sample.third(), sample.fourth()).equals(colors),
                "Large artwork preserves its four quadrant colors after bounded decoding");
            try (var loader = new MusicArtworkPalette()) {
                loader.colors(file.toFile());
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
                MusicArtworkPalette.Tones async;
                while ((async = loader.colors(file.toFile())) == null && System.nanoTime() < deadline) Thread.sleep(5);
                require(sample.equals(async), "Asynchronous cover colors match the actual image");
                require(loader.colors(null) == null, "Clearing artwork immediately clears the old palette");
            }
            java.nio.file.Files.writeString(file, "invalid artwork");
            try {
                MusicArtworkPalette.sample(file.toFile());
                throw new AssertionError("Invalid artwork must allow theme fallback");
            } catch (java.io.IOException expected) { checks++; }
        } catch (Exception failure) { throw new AssertionError("Artwork palette check failed", failure); }
        finally {
            if (file != null) try { java.nio.file.Files.deleteIfExists(file); } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }
    }
    private static void lyricSeeking() {
        var body = new MusicPlayerLayout.Box(500, 100, 400, 400);
        var heights = List.of(80f, 120f, 160f);
        require(MusicNowPlayingUi.hitLine(body, heights, 1, 550, 276) == 1, "Active wrapped lyric is selectable");
        require(MusicNowPlayingUi.hitLine(body, heights, 1, 550, 190) == 0, "Previous visible lyric is selectable");
        require(MusicNowPlayingUi.hitLine(body, heights, 1, 550, 370) == 2, "Tall translated row selects the correct line");
        require(MusicNowPlayingUi.hitLine(body, heights, 1, 499, 276) == -1, "Outside body cannot seek");
        require(MusicNowPlayingUi.hitLine(body, List.of(0f), 0, 550, 276) == -1, "Invalid height cannot seek");
        require(MusicNowPlayingUi.hitLine(body, List.of(Float.NaN), 0, 550, 276) == -1, "Invalid measured rows are rejected");
        require(MusicNowPlayingUi.hitLine(body, heights, 1.5f, 550, 276) == 2, "Animated focus uses interpolated row centers");
    }

    private static void wordsAndChinese() {
        var english = MusicNowPlayingUi.wrap("We sing together under the stars", 14, COLUMNS);
        require(english.equals(List.of("We sing", "together under", "the stars")), "English words remain intact when they fit");
        require(String.join(" ", english).equals("We sing together under the stars"), "English lyrics keep all words");
        String chinese = "让音乐陪你走过每一个春夏秋冬";
        var wrapped = MusicNowPlayingUi.wrap(chinese, 6, COLUMNS);
        require(wrapped.size() > 1, "Chinese lyrics wrap instead of being ellipsized");
        require(String.join("", wrapped).equals(chinese), "Chinese lyrics retain every character");
        require(wrapped.stream().allMatch(line -> COLUMNS.applyAsDouble(line) <= 6), "Chinese lines fit available width");
        var longWord = MusicNowPlayingUi.wrap("supercalifragilisticexpialidocious", 8, COLUMNS);
        require(longWord.size() > 1, "Oversized words can still be displayed");
        require(String.join("", longWord).equals("supercalifragilisticexpialidocious"), "Long words retain every character");
        require(longWord.stream().allMatch(line -> line.length() <= 8), "Long word fragments fit");
        var mixed = MusicNowPlayingUi.wrap("明天 Hello world 再见", 9, COLUMNS);
        require(mixed.stream().noneMatch(line -> line.contains("…")), "Mixed-language lyrics never acquire an ellipsis");
        require(String.join("", mixed).replace(" ", "").equals("明天Helloworld再见"), "Mixed-language lyrics preserve content");
        var measured = MusicNowPlayingUi.wrap("星光abc月亮xyz", 7,
            text -> text.codePoints().map(cp -> cp > 127 ? 2 : 1).sum());
        require(measured.stream().allMatch(line -> line.codePoints().map(cp -> cp > 127 ? 2 : 1).sum() <= 7),
            "Wrapping uses measured glyph width rather than string length");
    }

    private static void unicodeAndExplicitBreaks() {
        String supplementary = "A\uD83D\uDE3AB\uD834\uDD1EC\uD83D\uDE80D";
        var wrapped = MusicNowPlayingUi.wrap(supplementary, 2, COLUMNS);
        require(String.join("", wrapped).equals(supplementary), "Supplementary characters are retained");
        for (String line : wrapped) {
            require(!Character.isLowSurrogate(line.charAt(0)), "No line starts with an isolated low surrogate");
            require(!Character.isHighSurrogate(line.charAt(line.length() - 1)), "No line ends with an isolated high surrogate");
        }
        String combining = "e\u0301e\u0301e\u0301";
        var accents = MusicNowPlayingUi.wrap(combining, 1, COLUMNS);
        require(accents.equals(List.of("e\u0301", "e\u0301", "e\u0301")), "Combining marks stay attached even when a grapheme is wider than the box");
        String family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66";
        require(MusicNowPlayingUi.wrap(family + family, 1, COLUMNS).equals(List.of(family, family)), "Joined emoji are not torn apart");
        String flag = "\uD83C\uDDE8\uD83C\uDDF3";
        require(MusicNowPlayingUi.wrap(flag + flag, 1, COLUMNS).equals(List.of(flag, flag)), "Flag pairs stay together");
        require(MusicNowPlayingUi.wrap("first\r\n\r\nsecond\n", 80, COLUMNS).equals(List.of("first", "", "second", "")),
            "Explicit line breaks and stanza gaps are retained");
        require(MusicNowPlayingUi.wrap(null, 80, COLUMNS).isEmpty(), "Absent text has no fabricated lines");
        require(MusicNowPlayingUi.wrap("", 80, COLUMNS).isEmpty(), "Empty text has no fabricated lines");
        require(MusicNowPlayingUi.wrap("a", 0, COLUMNS).equals(List.of("a")), "Zero width still makes progress");
        require(MusicNowPlayingUi.wrap("abc", Float.NaN, COLUMNS).equals(List.of("a", "b", "c")), "Invalid width is bounded and terminates");
    }

    private static void pointerGeometry() {
        var scene = MusicPlayerLayout.nowPlaying(1360, 820);
        var retry = MusicNowPlayingUi.retry(scene.lyrics());
        var body = MusicNowPlayingUi.body(scene.lyrics());
        require(retry.width() == 48 && retry.height() == 48, "Refresh retains the MD3 minimum hit target");
        require(retry.contains(retry.x(), retry.y()), "Refresh includes its top-left edge");
        require(!retry.contains(retry.x() + 48, retry.y() + 24), "Refresh excludes the neighboring right edge");
        require(!body.contains(retry.x() + 24, retry.y() + 24), "Scrolling does not steal the refresh gesture");
        require(body.contains(body.x() + 32, body.y() + 1), "Visible lyrics accept wheel browsing");
        require(body.y() + body.height() == scene.lyrics().y() + scene.lyrics().height(), "Body ends at the layout boundary");
        var resized = MusicPlayerLayout.nowPlaying(1920, 1080);
        var resizedRetry = MusicNowPlayingUi.retry(resized.lyrics());
        require(resizedRetry.x() > retry.x(), "Refresh follows a resized lyrics column");
        require(!resizedRetry.contains(retry.x() + 24, retry.y() + 24), "Old refresh coordinates stop being interactive after resize");
        require(MusicNowPlayingUi.body(resized.lyrics()).height() > body.height(), "Lyrics use the larger viewport height");
        require(MusicNowPlayingUi.body(new MusicPlayerLayout.Box(0, 0, 100, 12)).height() == 0,
            "Tiny layouts never produce negative clipping rectangles");
    }

    private static void selectionHandoff() {
        require(!MusicNowPlayingUi.pendingSelection(false, "netease:1", "netease:1"), "Matching finished playback may show its lyrics");
        require(MusicNowPlayingUi.pendingSelection(true, "netease:1", "netease:1"), "A repeated selection still waits for preparation to finish");
        require(MusicNowPlayingUi.pendingSelection(false, "netease:2", "netease:1"), "A new selection never inherits the old song's lyrics");
        require(MusicNowPlayingUi.pendingSelection(false, "audius:1", "netease:1"), "Matching IDs from different providers are different selections");
        require(MusicNowPlayingUi.pendingSelection(false, "netease:2", null), "A first selection waits until playback is installed");
        require(MusicNowPlayingUi.pendingSelection(false, "file:new.mp3", "file:old.mp3"), "Local selection identity also uses the actual filename");
        require(!MusicNowPlayingUi.pendingSelection(false, null, "netease:1"), "Existing playback without a queue selection remains visible");
        require(!MusicNowPlayingUi.pendingSelection(false, null, null), "No selection has no pending song to invent");
        require(MusicNowPlayingUi.pendingSelection(true, null, null), "An explicit loading state remains loading without stale lyrics");
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
