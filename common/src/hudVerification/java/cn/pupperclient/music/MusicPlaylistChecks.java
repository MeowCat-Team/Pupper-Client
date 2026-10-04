package cn.pupperclient.music;

import cn.pupperclient.management.music.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Local persistence, provider identity and playback-cycle regression fixtures. */
public final class MusicPlaylistChecks {
    private static int checks;
    private static final MusicQueue.Entry A = remote("netease", "42"), B = remote("audius", "42"), C = remote("netease", "43");
    private static MusicQueue.Entry remote(String provider, String id) {
        return new MusicQueue.Entry(new MusicTrack(0, id, "Artist", "", "", 1000, provider, id, true, true), "");
    }
    public static void run() throws Exception {
        checkCycles();
        Path root = Files.createTempDirectory("pupper-playlist-checks-");
        try {
            var store = new MusicLibraryStore(root);
            require(store.repeatMode() == MusicRepeatMode.OFF && store.playlists().isEmpty(), "New library changed playback defaults");
            var localA = new MusicQueue.Entry(new MusicTrack(0, "Local A", "", "", "", 0), "a.mp3");
            var localB = new MusicQueue.Entry(new MusicTrack(0, "Local B", "", "", "", 0), "b.mp3");
            var first = store.createPlaylist("  我的歌单 🎧  ", List.of(A, B, A, localA, localB));
            require(first.name().equals("我的歌单 🎧") && first.entries().equals(List.of(A, B, localA, localB)), "Playlist lost Unicode, provider identity or duplicate filtering");
            store.addToPlaylist(first.id(), List.of(B, C));
            var updated = store.playlist(first.id());
            require(updated.entries().equals(List.of(A, B, localA, localB, C)), "Adding a song lost playlist order");
            require(first.entries().size() == 4, "Saved playlist snapshot mutated");
            require(!store.moveInPlaylist(first.id(), 0, 1, first.revision()), "Stale reorder changed newer playlist");
            require(store.moveInPlaylist(first.id(), 4, 0, updated.revision()), "Playlist reorder failed");
            updated = store.playlist(first.id());
            require(updated.entries().equals(List.of(C, A, B, localA, localB)), "Playlist reorder changed the wrong song");
            require(!store.removeFromPlaylist(first.id(), B.key(), first.revision()), "Stale deletion removed a newer entry");
            require(store.removeFromPlaylist(first.id(), B.key(), updated.revision()), "Playlist removal failed");
            store.renamePlaylist(first.id(), "Evening mix");
            store.repeatMode(MusicRepeatMode.ALL);
            store.provider("audius");
            var restart = new MusicLibraryStore(root);
            require(restart.playlist(first.id()).name().equals("Evening mix") && restart.playlist(first.id()).entries().equals(List.of(C, A, localA, localB)), "Restart lost playlist name/order");
            require(restart.repeatMode() == MusicRepeatMode.ALL && restart.provider().equals("audius"), "Unrelated settings discarded playback mode");
            Files.writeString(root.resolve("repaired.mp3"), "fixture");
            restart.register("repaired.mp3", A.track(), "music_42.mp3");
            require(restart.playlist(first.id()).entries().get(1).filename().equals("repaired.mp3"), "Downloaded metadata did not update playlist file reference");
            require(restart.playlist(first.id()).entries().get(2).filename().equals("a.mp3"), "Download repair changed an unrelated local file");
            var second = restart.createPlaylist("Evening mix");
            require(!second.id().equals(first.id()), "Two playlists with the same name collided");
            var unrelated = restart.createPlaylist("Local songs", List.of(localA));
            restart.register("repaired.mp3", A.track(), "music_42.mp3");
            require(restart.playlist(unrelated.id()).equals(unrelated), "Unrelated metadata update invalidated a playlist action");
            var repairedTwice = restart.createPlaylist("Repair identity", List.of(A, new MusicQueue.Entry(localA.track(), "music_42.mp3")));
            restart.register("repaired.mp3", A.track(), "music_42.mp3");
            require(restart.playlist(repairedTwice.id()).entries().size() == 1, "Metadata repair left duplicate identities in a playlist");
            restart.deletePlaylist(second.id());
            require(new MusicLibraryStore(root).playlist(second.id()) == null && restart.playlist(first.id()) != null, "Deleting one playlist deleted another");
            for (String invalid : new String[]{"", "  ", "x".repeat(81), "two\nlines"}) {
                try { restart.createPlaylist(invalid); throw new AssertionError("Invalid playlist name accepted"); }
                catch (IllegalArgumentException expected) { checks++; }
            }
            require(restart.createPlaylist("🎧".repeat(80)).name().codePointCount(0, 160) == 80, "Name limit counted UTF-16 instead of characters");
            Path oldDir = root.resolve("legacy"); Files.createDirectories(oldDir);
            Files.writeString(oldDir.resolve(".pupper-music.json"), "{\"provider\":\"netease\",\"downloads\":{},\"favorites\":{}}");
            var legacy = new MusicLibraryStore(oldDir);
            require(legacy.playlists().isEmpty() && legacy.repeatMode() == MusicRepeatMode.OFF, "Legacy library migration failed");
            legacy.createPlaylist("Imported");
            require(new MusicLibraryStore(oldDir).playlists().size() == 1, "Legacy index cannot save playlists");

            Path failedDir = root.resolve("failed"); var failed = new MusicLibraryStore(failedDir);
            Files.createDirectory(failedDir.resolve(".pupper-music.json")); Files.writeString(failedDir.resolve(".pupper-music.json/marker"), "fixture");
            try { failed.createPlaylist("Never saved", List.of(A)); throw new AssertionError("Expected failed playlist save"); }
            catch (IOException expected) { require(failed.playlists().isEmpty(), "Failed save published playlist state"); }
            try { failed.repeatMode(MusicRepeatMode.ONE); throw new AssertionError("Expected failed mode save"); }
            catch (IOException expected) { require(failed.repeatMode() == MusicRepeatMode.OFF, "Failed save changed playback mode"); }
            require(MusicRepeatMode.OFF.next() == MusicRepeatMode.ALL && MusicRepeatMode.ALL.next() == MusicRepeatMode.ONE
                && MusicRepeatMode.ONE.next() == MusicRepeatMode.OFF && MusicRepeatMode.parse("unknown") == MusicRepeatMode.OFF, "Repeat mode cycling/fallback failed");
            System.out.println("Music playlist checks passed: " + checks + " assertions; list repeat, queue edits, shuffle, local persistence, provider/file identity and save rollback.");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }
    private static void checkCycles() {
        var queue = new MusicQueue(); queue.start(List.of(A, B, C), 1);
        require(queue.advance(false, true).equals(C) && queue.advance(false, true).equals(A), "List repeat did not include songs before the starting selection");
        require(queue.advance(false, true).equals(B) && queue.advance(false, true).equals(C) && queue.advance(false, true).equals(A), "Second list cycle lost its context");
        require(queue.previous().equals(C), "Previous across list wrap lost actual playback history");
        queue.start(List.of(A), 0);
        require(queue.advance(false, true).equals(A), "Single-entry list did not repeat");
        require(queue.advance(false, false) == null, "Disabling list repeat did not stop at queue end");
        queue.start(List.of(A, B, C), 0);
        queue.remove(0, queue.snapshot().revision()); queue.enqueue(B, true);
        require(queue.advance(false, true).equals(B) && queue.advance(false, true).equals(C) && queue.advance(false, true).equals(A)
            && queue.advance(false, true).equals(B), "Queue insert/remove was lost when repeating");
        queue.start(List.of(A, B, C), 0); queue.move(1, 0, queue.snapshot().revision());
        require(queue.advance(false, true).equals(C) && queue.advance(false, true).equals(B) && queue.advance(false, true).equals(A)
            && queue.advance(false, true).equals(C), "Queue reorder was lost next cycle");
        queue.start(List.of(A, B, B, C), 0); queue.remove(0, queue.snapshot().revision());
        require(queue.advance(false, true).equals(B) && queue.advance(false, true).equals(C) && queue.advance(false, true).equals(A)
            && queue.advance(false, true).equals(B) && queue.snapshot().upcoming().equals(List.of(C)), "Removing one duplicate removed another cycle slot");
        queue.clear(); require(queue.advance(false, true).equals(B) && queue.snapshot().upcoming().isEmpty(), "Cleared queue resurrected removed tracks");
        queue.start(List.of(A, B, C), 0);
        for (int i = 0; i < 3; i++) queue.advance(false, true);
        queue.previous(); queue.previous(); queue.move(1, 0, queue.snapshot().revision());
        for (int i = 0; i < 4; i++) queue.advance(false, true);
        require(queue.advance(false, true).equals(A) && queue.advance(false, true).equals(C) && queue.advance(false, true).equals(B),
            "Reordering revisited history duplicated or dropped a slot in the next list cycle");
        queue.start(List.of(A, B, C), 0);
        for (int i = 0; i < 20; i++) {
            String previous = queue.snapshot().current().key();
            require(!queue.advance(true, true).key().equals(previous), "Shuffle immediately repeated the same track despite alternatives");
        }
        var longList = java.util.stream.IntStream.range(1, 151).mapToObj(i -> remote("audius", "id-" + i)).toList();
        queue.start(longList, 0);
        for (int i = 1; i < longList.size(); i++) queue.advance(false, true);
        require(queue.advance(false, true).equals(longList.getFirst()) && queue.snapshot().upcoming().size() == 149, "History cap truncated list repeat");
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
