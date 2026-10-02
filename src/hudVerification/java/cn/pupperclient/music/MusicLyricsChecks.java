package cn.pupperclient.music;

import cn.pupperclient.management.music.Music;
import cn.pupperclient.management.music.MusicTrack;
import cn.pupperclient.management.music.NeteaseMusicApi;
import cn.pupperclient.management.music.lyric.LyricsManager;
import cn.pupperclient.management.music.lyric.SongLyrics;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class MusicLyricsChecks {
    private static int checks;
    static void run() throws Exception {
        SongLyrics lyrics = SongLyrics.parse("[ar:Fixture]\n[00:10][00:30.500]Repeated line\n[00:20.25]Second line\n[00:40.001]\n",
            "[00:10.000]重复的句子\n[00:20.250]第二句");
        require(lyrics.lines().size() == 4 && lyrics.lines().get(0).getTranslation().equals("重复的句子"), "Multiple tags/translation lost");
        require(lyrics.lines().get(1).getMillis() == 20_250 && lyrics.lines().get(2).getMillis() == 30_500, "LRC fractions/order wrong");
        require(lyrics.currentIndex(9.99f) == -1 && lyrics.currentIndex(10) == 0 && lyrics.currentIndex(30.5f) == 2,
            "Lyrics advance early or miss exact boundaries");
        require(lyrics.currentIndex(40.01f) == 3 && lyrics.lines().get(3).getText().isEmpty(), "Instrumental blank line discarded");
        require(lyrics.currentIndex(Float.NaN) == -1, "Invalid player time advanced lyrics");
        require(SongLyrics.parse("[offset:-500]\n[00:01.00]Shifted", "").lines().getFirst().getMillis() == 500, "Negative offset ignored");
        require(SongLyrics.parse("[00:01.1]Tenths\n[00:02.01]Hundredths", "").lines().getFirst().getMillis() == 1_100, "Tenths parsed as milliseconds");
        require(SongLyrics.parse("[ar:Fixture]\nUntimed first line\nUntimed second line", "").plainText().size() == 2,
            "Plain lyrics lost or metadata rendered as lyrics");
        require(SongLyrics.parse("[00:99.01]Malformed", "").isEmpty(), "Invalid seconds accepted");

        Path root = Files.createTempDirectory("pupper-lyrics-checks-");
        try {
            Path audio = root.resolve("fixture.mp3");
            try (var source = MusicLyricsChecks.class.getResourceAsStream("/music/silence.mp3")) { Files.copy(source, audio); }
            MusicTrack metadata = new MusicTrack(321, "Fixture", "Artist", "", "", 0);
            Music song = new Music(audio.toFile(), metadata.title(), metadata.artist(), null, Color.BLACK, metadata);
            List<Runnable> queue = new ArrayList<>();
            AtomicInteger requests = new AtomicInteger();
            var raw = new NeteaseMusicApi.Lyrics("[00:01.000]Generated fixture line", "[00:01.000]测试歌词");
            LyricsManager manager = new LyricsManager(_ -> { requests.incrementAndGet(); return raw; }, root.resolve("cache"), queue::add);
            require(manager.get(song).state() == LyricsManager.State.LOADING && queue.size() == 1, "Lyric read blocks caller");
            manager.get(song); require(queue.size() == 1, "Duplicate requests scheduled on every frame");
            queue.removeFirst().run();
            require(manager.getCurrentLyric(song, 1).equals("Generated fixture line"), "Fetched lyric not synchronized");
            LyricsManager offline = new LyricsManager(_ -> { throw new Exception("Offline fixture"); }, root.resolve("cache"), Runnable::run);
            require(offline.get(song).state() == LyricsManager.State.READY, "Persistent lyrics unavailable offline");
            require(offline.get(song).lyrics().lines().getFirst().getTranslation().equals("测试歌词"), "Translation not cached");
            Files.writeString(root.resolve("fixture.lrc"), "[00:01.000]Local LRC");
            manager.retry(song);
            require(manager.get(song).state() == LyricsManager.State.LOADING, "Retry did not schedule");
            queue.removeFirst().run();
            require(manager.getCurrentLyric(song, 1).equals("Local LRC") && requests.get() == 1, "Local LRC precedence lost");
            manager.retry(song); manager.get(song); manager.clearCache(); manager.get(song);
            queue.removeFirst().run();
            require(manager.get(song).state() == LyricsManager.State.LOADING, "A stale request replaced the refreshed entry");
            queue.removeFirst().run();
            require(manager.get(song).state() == LyricsManager.State.READY, "Refreshed request not delivered");
            Files.delete(root.resolve("fixture.lrc"));
            LyricsManager empty = new LyricsManager(_ -> new NeteaseMusicApi.Lyrics("", ""), root.resolve("empty"), Runnable::run);
            require(empty.get(song).state() == LyricsManager.State.EMPTY, "No-lyric song not distinguished");
            LyricsManager failed = new LyricsManager(_ -> { throw new Exception("Expected outage"); }, root.resolve("failed"), Runnable::run);
            require(failed.get(song).state() == LyricsManager.State.ERROR, "Network outage confused with no lyrics");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("Music lyrics checks passed: " + checks + " assertions; LRC timing/translation, async deduplication, offline/local cache and stale-request isolation.");
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
