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
        checkQueueLookahead();
        checkQueueNotifications();
        checkPreparedAdvance();
        checkLoadedPages();
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
            System.out.println("Music playlist checks passed: " + checks + " assertions; list repeat, queue edits, reserved lookahead, shuffle, local persistence, provider/file identity and save rollback.");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }
    private static void checkLoadedPages() {
        var queue = new MusicQueue(); queue.start(List.of(A, B), 0);
        long selectedList = queue.listRevision();
        require(queue.advance(false) == B && queue.listRevision() == selectedList,
            "Moving through a playlist invalidated its background pages");
        long playing = queue.snapshot().generation();
        require(queue.appendLoaded(List.of(C, C), selectedList)
                && queue.snapshot().current() == B && queue.snapshot().generation() == playing
                && queue.snapshot().upcoming().equals(List.of(C, C)),
            "Background paging changed current playback or removed duplicate playlist occurrences");
        require(queue.advance(false) == C && queue.advance(false) == C && queue.advance(false, true) == A,
            "Loaded pages did not extend playback and list repeat in order");
        queue.enqueue(B, true); var edited = queue.snapshot();
        require(!queue.appendLoaded(List.of(A), selectedList) && queue.snapshot().equals(edited),
            "Late pages changed a queue that the user edited");
        queue.start(List.of(B), 0); edited = queue.snapshot();
        require(!queue.appendLoaded(List.of(C), selectedList) && queue.snapshot().equals(edited),
            "Old playlist pages appeared in a newly selected list");
        selectedList = queue.listRevision();
        require(queue.appendLoaded(List.of(C), selectedList), "A new playlist rejected its own page");
        queue.clear(); edited = queue.snapshot();
        require(!queue.appendLoaded(List.of(A), selectedList) && queue.snapshot().equals(edited),
            "A cleared queue was repopulated by background paging");
    }
    private static void checkPreparedAdvance() {
        var queue = new MusicQueue(); queue.start(List.of(A, B, C), 0);
        var notifications = new java.util.concurrent.atomic.AtomicInteger(); queue.setChangeListener(notifications::incrementAndGet);
        var before = queue.snapshot(); var planned = queue.peekNext(false, false);
        require(queue.advancePrepared(before.generation() - 1, before.revision(), planned.key(), false, false) == null
            && queue.snapshot().equals(before) && notifications.get() == 0, "Stale prepared generation mutated the queue or emitted a change");
        require(queue.advancePrepared(before.generation(), before.revision() - 1, planned.key(), false, false) == null
            && queue.snapshot().equals(before) && notifications.get() == 0, "Stale prepared revision mutated the queue or emitted a change");
        require(queue.advancePrepared(before.generation(), before.revision(), A.key(), false, false) == null
            && queue.snapshot().equals(before) && queue.peekNext(false, false) == planned && notifications.get() == 0,
            "Wrong prepared key consumed the next reservation/history");
        require(queue.advancePrepared(before.generation(), before.revision(), planned.key(), false, false) == B && notifications.get() == 1,
            "Valid prepared handoff did not commit its reserved next entry exactly once");
        var advanced = queue.snapshot();
        require(advanced.generation() == before.generation() + 1 && advanced.revision() == before.revision() + 1,
            "Prepared handoff changed queue tokens more than once");
        require(queue.advancePrepared(before.generation(), before.revision(), planned.key(), false, false) == null
            && queue.snapshot().equals(advanced) && notifications.get() == 1, "Repeated prepared callback advanced twice");
        require(queue.previous() == A, "Prepared handoff lost actual previous-song history");
        before = queue.snapshot(); planned = queue.peekNext(false, false); queue.enqueue(C, true); var edited = queue.snapshot(); notifications.set(0);
        require(queue.advancePrepared(before.generation(), before.revision(), planned.key(), false, false) == null
            && queue.snapshot().equals(edited) && notifications.get() == 0, "Queue edit allowed stale prepared audio to commit");
        queue.start(List.of(A), 0); before = queue.snapshot(); notifications.set(0);
        require(queue.advancePrepared(before.generation(), before.revision(), A.key(), false, false) == null
            && queue.snapshot().equals(before) && notifications.get() == 0, "Prepared end-of-list consumed current/history without a next song");

        var firstCopy = new MusicQueue.Entry(B.track(), "first-copy.mp3");
        var secondCopy = new MusicQueue.Entry(B.track(), "second-copy.mp3");
        queue.start(List.of(A, firstCopy, secondCopy, C), 0); before = queue.snapshot(); planned = queue.peekNext(false, false);
        require(planned == firstCopy && queue.advancePrepared(before.generation(), before.revision(), planned.key(), false, false) == firstCopy
            && queue.snapshot().upcoming().equals(List.of(secondCopy, C)), "Prepared handoff removed both equal-key duplicate slots or chose the wrong file");
        before = queue.snapshot();
        require(queue.advancePrepared(before.generation(), before.revision(), secondCopy.key(), false, false) == secondCopy
            && queue.snapshot().upcoming().equals(List.of(C)), "Second duplicate prepared slot lost its precise file identity");

        queue.start(List.of(A, firstCopy, secondCopy, C), 0);
        for (int i = 0; i < 100; i++) {
            before = queue.snapshot(); planned = queue.peekNext(true, true); notifications.set(0);
            require(queue.advancePrepared(before.generation(), before.revision(), planned.key(), true, true) == planned
                && notifications.get() == 1, "Shuffle prepared handoff discarded the exact reserved slot");
            advanced = queue.snapshot();
            require(queue.advancePrepared(before.generation(), before.revision(), planned.key(), true, true) == null
                && queue.snapshot().equals(advanced) && notifications.get() == 1, "Shuffle prepared callback was consumed twice");
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
    private static void checkQueueLookahead() {
        var queue = new MusicQueue(); queue.start(List.of(A, B, C), 1);
        var before = queue.snapshot();
        require(queue.peekNext(false, false) == C && queue.peekNext(false, false) == C,
            "Ordered lookahead did not reserve the next entry");
        require(queue.snapshot().equals(before) && queue.current(before.generation()),
            "Lookahead changed playback tokens, current entry or visible queue order");
        require(queue.previous() == A && queue.advance(false, false) == B,
            "Lookahead changed actual playback history");
        require(queue.peekNext(false, false) == C && queue.advance(false, false) == C,
            "Ordered advance disagreed with lookahead");
        before = queue.snapshot();
        require(queue.peekNext(false, false) == null && queue.peekNext(false, true) == A,
            "Lookahead did not distinguish end-of-list from list repeat");
        require(queue.snapshot().equals(before) && queue.advance(false, true) == A,
            "Repeat lookahead changed ordering or disagreed with the wrapped advance");

        queue.start(List.of(A, B, C), 0);
        for (int i = 0; i < 100; i++) {
            before = queue.snapshot();
            var selected = queue.peekNext(true, true);
            require(selected != null && !selected.key().equals(before.current().key()),
                "Shuffle preview immediately repeated a song despite alternatives");
            require(queue.peekNext(true, true) == selected && queue.snapshot().equals(before),
                "Repeated shuffle lookahead chose again or changed playback state");
            require(queue.advance(true, true) == selected,
                "Shuffle advance discarded the prefetched reservation");
        }

        var firstCopy = new MusicQueue.Entry(B.track(), "first-copy.mp3");
        var secondCopy = new MusicQueue.Entry(B.track(), "second-copy.mp3");
        queue.start(List.of(A, firstCopy, A, secondCopy), 0);
        before = queue.snapshot();
        var duplicate = queue.peekNext(true, false);
        var remaining = new java.util.ArrayList<>(before.upcoming());
        remaining.remove(duplicate == firstCopy ? 0 : 2);
        require(duplicate == firstCopy || duplicate == secondCopy, "Shuffle selected an immediate duplicate of the current song");
        require(queue.advance(true, false) == duplicate && queue.snapshot().upcoming().equals(remaining),
            "Reserved selection removed a different slot with the same provider/song identity");

        queue.start(List.of(A, A, B), 0);
        require(queue.peekNext(true, false) == B && queue.peekNext(false, false) == A
            && queue.peekNext(true, false) == B && queue.advance(false, false) == A,
            "Changing shuffle mode reused a reservation from the previous mode");
        queue.start(List.of(A, B), 1);
        require(queue.peekNext(false, true) == A && queue.advance(false, false) == null,
            "Disabling repeat reused a wrapped reservation");
        require(queue.peekNext(false, false) == null && queue.advance(false, true) == A,
            "Enabling repeat reused an end-of-list reservation");

        queue.start(List.of(A, B, C), 0);
        require(queue.peekNext(false, false) == B && queue.remove(0, queue.snapshot().revision())
            && queue.peekNext(false, false) == C && queue.advance(false, false) == C,
            "Removing the prefetched slot did not invalidate lookahead");
        queue.start(List.of(A, B, C), 0);
        queue.peekNext(false, false);
        require(queue.move(1, 0, queue.snapshot().revision()) && queue.peekNext(false, false) == C,
            "Reordering the queue did not update lookahead");
        queue.enqueue(B, true);
        require(queue.peekNext(false, false) == B && queue.advance(false, false) == B,
            "Play-next insertion did not replace the prefetched candidate");
        queue.start(List.of(A, B, C), 0); queue.peekNext(false, false);
        require(queue.jump(1, queue.snapshot().revision()) == C && queue.peekNext(false, false) == null,
            "Jump kept the pre-jump reservation");
        require(queue.previous() == B && queue.peekNext(false, false) == C,
            "Previous did not invalidate the end-of-list reservation");
        queue.clear();
        require(queue.peekNext(false, false) == null && queue.peekNext(true, true) == B,
            "Clearing upcoming tracks retained a removed reservation or lost single-song repeat");
        queue.start(List.of(C), 0);
        require(queue.peekNext(false, false) == null && queue.peekNext(false, true) == C,
            "Starting a new queue retained the old playback reservation");
        before = queue.snapshot(); queue.cancelPending();
        var canceled = queue.snapshot();
        require(canceled.revision() == before.revision() && canceled.generation() == before.generation() + 1
            && canceled.current() == before.current() && canceled.upcoming().equals(before.upcoming())
            && !queue.current(before.generation()), "Canceling prefetch changed ordering or retained a valid playback token");
        require(queue.peekNext(false, true) == C && queue.advance(false, true) == C,
            "Canceling pending work prevented a fresh reservation");
    }
    private static void checkQueueNotifications() {
        var queue = new MusicQueue();
        var notifications = new java.util.ArrayList<MusicQueue.Snapshot>();
        queue.setChangeListener(() -> {
            require(!Thread.holdsLock(queue), "Queue change listener ran while holding the queue monitor");
            notifications.add(queue.snapshot());
        });
        require(notifications.isEmpty(), "Installing the optional listener reported a queue mutation");
        queue.start(List.of(A, B, C), 0);
        require(notifications.size() == 1 && notifications.getFirst().equals(queue.snapshot()),
            "Start did not notify once with committed queue state");
        var before = queue.snapshot();
        queue.peekNext(false, false); queue.peekNext(true, true);
        require(!queue.remove(0, before.revision() - 1) && !queue.move(-1, 0, before.revision())
            && queue.move(0, 0, before.revision()) && queue.jump(-1, before.revision()) == null
            && queue.previous() == null && queue.snapshot().equals(before) && notifications.size() == 1,
            "Lookahead, failed edits or no-op reorder notified or changed queue state");
        queue.enqueue(C, false); require(notifications.size() == 2, "Direct enqueue did not notify");
        queue.remove(2, queue.snapshot().revision()); require(notifications.size() == 3, "Direct removal did not notify");
        queue.move(1, 0, queue.snapshot().revision()); require(notifications.size() == 4, "Direct reorder did not notify");
        queue.jump(1, queue.snapshot().revision());
        require(notifications.size() == 5 && notifications.getLast().current() == B,
            "Jump reported intermediate advances instead of one committed selection");
        queue.cancelPending(); require(notifications.size() == 6, "Canceling speculative work did not notify");
        queue.previous(); require(notifications.size() == 7, "Previous did not notify");
        queue.clear(); require(notifications.size() == 8, "Direct clear did not notify");
        before = queue.snapshot(); queue.clear();
        require(notifications.size() == 8 && queue.snapshot().equals(before), "Clearing an already cleared queue notified or changed tokens");
        queue.start(List.of(A), 0); queue.advance(false, false);
        require(notifications.size() == 10 && notifications.getLast().current() == null,
            "End-of-list advance did not report its committed stop");
        queue.clear(); require(notifications.size() == 11, "Clearing the stopped cycle did not notify");
        queue.clear(); queue.setChangeListener(null); queue.enqueue(B, false);
        require(notifications.size() == 11, "No-op clear or detached listener reported a mutation");
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
