package cn.pupperclient.music;

import cn.pupperclient.management.music.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Real device-local persistence, recovery bounds, safe basenames and atomic publication failures. */
public final class MusicExperienceChecks {
    private static int checks;
    private static final MusicQueue.Entry A = remote("netease", "42", ""), B = remote("audius", "42", "");
    private MusicExperienceChecks() { }
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        checks = 0;
        Path root = Files.createTempDirectory("pupper-experience-checks-");
        try {
            settingsBounds();
            persistence(root.resolve("saved"));
            recoveryBounds(root.resolve("bounded"));
            malformed(root.resolve("corrupt"));
            rollback(root.resolve("failed"));
            System.out.println("Music experience checks passed: " + checks + " assertions; recent identity/count limits, "
                + "settings and queue recovery, unsafe basename filtering, corruption backups and failed-save rollback.");
        } finally { try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } }
    }
    private static void settingsBounds() {
        var finite = new MusicExperienceStore.Settings(999, false, -999, false, 999, true);
        require(finite.lyricSize() == 48 && finite.lyricOffset() == -10 && finite.crossfadeSeconds() == 12, "Finite playback settings escaped their bounds");
        var invalid = new MusicExperienceStore.Settings(Float.NaN, true, Float.NEGATIVE_INFINITY, true, Float.POSITIVE_INFINITY, false);
        require(invalid.lyricSize() == 36 && invalid.lyricOffset() == 0 && invalid.crossfadeSeconds() == 0, "Non-finite settings did not use safe defaults");
        var audio = finite.audio(true, 2.5f, false);
        require(audio.lyricSize() == finite.lyricSize() && audio.translations() == finite.translations() && audio.lyricOffset() == finite.lyricOffset()
            && audio.gapless() && audio.crossfadeSeconds() == 2.5f && !audio.normalize(), "Audio setting edit changed lyric preferences");
        var lyrics = audio.lyrics(28, true, 1.25f);
        require(lyrics.gapless() == audio.gapless() && lyrics.crossfadeSeconds() == audio.crossfadeSeconds() && lyrics.normalize() == audio.normalize()
            && lyrics.lyricSize() == 28 && lyrics.translations() && lyrics.lyricOffset() == 1.25f, "Lyric setting edit changed playback preferences");
        var empty = new MusicExperienceStore.Resume(null, 80, true, Float.NaN);
        require(!empty.available() && empty.seconds() == 0 && empty.volume() == .5f, "Empty recovery retained a phantom playback position");
        var bounded = new MusicExperienceStore.Resume(List.of(A), Double.POSITIVE_INFINITY, false, 20);
        require(bounded.seconds() == 0 && bounded.volume() == 1, "Non-finite recovery time/volume was accepted");
    }
    private static void persistence(Path root) throws Exception {
        var store = new MusicExperienceStore(root);
        require(store.settings().equals(MusicExperienceStore.Settings.DEFAULT) && store.recent().isEmpty() && !store.resume().available(), "New listening store changed defaults");
        store.played(A, 100); store.played(B, 200);
        var downloaded = remote("netease", "42", "曲目 🎧.mp3"); store.played(downloaded, 300);
        var recent = store.recent();
        require(recent.size() == 2 && recent.getFirst().entry().equals(downloaded) && recent.getFirst().plays() == 2
            && recent.getFirst().playedAt() == 300 && recent.getLast().entry().equals(B), "Recent dedup lost provider identity, latest metadata or play count");
        require(recent.getFirst().entry().filename().equals("曲目 🎧.mp3"), "Recent list lost Unicode downloaded filename");
        var local = local("session.flac");
        var resume = new MusicExperienceStore.Resume(List.of(A, local, B, A), 142.25, true, .73f);
        var settings = new MusicExperienceStore.Settings(30, false, .75f, false, 7, true);
        store.settings(settings); store.resume(resume);
        var restored = new MusicExperienceStore(root);
        require(restored.settings().equals(settings) && restored.resume().equals(resume), "Settings or actual recovery data changed after restart");
        require(restored.resume().entries().equals(List.of(A, local, B, A)), "Recovery deduplicated intentional queue slots or changed file/provider identity");
        require(restored.recent().equals(recent), "Recent play counts/Unicode metadata did not survive real disk reload");
        store.clearRecent();
        restored = new MusicExperienceStore(root);
        require(restored.recent().isEmpty() && restored.settings().equals(settings) && restored.resume().equals(resume), "Clearing recent erased unrelated preferences/recovery");
    }
    private static void recoveryBounds(Path root) throws Exception {
        var store = new MusicExperienceStore(root);
        var unsafe = List.of("../outside.mp3", "..\\outside.flac", "C:outside.mp3", "..", ".", "bad\0.mp3", "a?.mp3", "a*.mp3",
            "bad\".mp3", "<track>.mp3", "a|b.flac", "line\n.mp3", "trailing.mp3.", "trailing.mp3 ", "CON.mp3", "NUL", "LPT1.flac");
        var entries = new ArrayList<MusicQueue.Entry>(); entries.add(A); entries.add(local("safe song.mp3"));
        for (String name : unsafe) { entries.add(local(name)); entries.add(remote("audius", "unsafe-" + entries.size(), name)); }
        entries.add(local(""));
        var filtered = new MusicExperienceStore.Resume(entries, -10, false, -2);
        require(filtered.entries().equals(List.of(A, local("safe song.mp3"))) && filtered.seconds() == 0 && filtered.volume() == 0,
            "Recovery accepted traversal, Windows-invalid/device names or empty local files");
        var many = IntStream.range(1, MusicExperienceStore.MAX_QUEUE + 10).mapToObj(id -> remote("audius", "q-" + id, "")).toList();
        var capped = new MusicExperienceStore.Resume(many, 9 * 86400, true, .2f);
        require(capped.entries().size() == MusicExperienceStore.MAX_QUEUE && capped.entries().getFirst().equals(many.getFirst())
            && capped.entries().getLast().equals(many.get(MusicExperienceStore.MAX_QUEUE - 1)) && capped.seconds() == 7 * 86400,
            "Recovery queue/time bounds changed ordering or exceeded limits");
        store.resume(filtered);
        Path file = root.resolve(".pupper-listening.json");
        var data = JsonParser.parseString(Files.readString(file)).getAsJsonObject(); var recent = new JsonArray(); var gson = new Gson();
        recent.add(gson.toJsonTree(new MusicExperienceStore.Recent(A, -1, Integer.MAX_VALUE)));
        recent.add(gson.toJsonTree(new MusicExperienceStore.Recent(A, 900, 10)));
        for (String name : unsafe) recent.add(gson.toJsonTree(new MusicExperienceStore.Recent(local(name), 20, 1)));
        for (int i = 1; i <= MusicExperienceStore.MAX_RECENT + 5; i++)
            recent.add(gson.toJsonTree(new MusicExperienceStore.Recent(remote("audius", "r-" + i, ""), i, 0)));
        data.add("recent", recent); Files.writeString(file, data.toString());
        store = new MusicExperienceStore(root);
        require(store.recent().size() == MusicExperienceStore.MAX_RECENT && store.recent().getFirst().entry().equals(A)
            && store.recent().getFirst().playedAt() == 0 && store.recent().getFirst().plays() == Integer.MAX_VALUE
            && store.recent().stream().map(item -> item.entry().key()).distinct().count() == MusicExperienceStore.MAX_RECENT,
            "Saved recent bounds/invalid names/duplicates were not sanitized");
        require(store.recent().getLast().plays() == 1, "Invalid stored play count was not repaired");
        store.played(A, 1234); require(store.recent().getFirst().plays() == Integer.MAX_VALUE, "Maximum recent play count overflowed");
        var latest = remote("audius", "latest", ""); store.played(latest, 500);
        require(store.recent().size() == MusicExperienceStore.MAX_RECENT && store.recent().getFirst().entry().equals(latest), "A newly played track exceeded recent limit or lost front placement");
        require(new MusicExperienceStore(root).recent().equals(store.recent()), "Sanitized bounded recent state did not persist");
    }
    private static void malformed(Path root) throws Exception {
        Files.createDirectories(root); Path file = root.resolve(".pupper-listening.json"); String broken = "{corrupt listening state";
        Files.writeString(file, broken); var store = new MusicExperienceStore(root);
        require(store.recent().isEmpty() && !store.resume().available() && store.settings().equals(MusicExperienceStore.Settings.DEFAULT), "Corrupt listening file prevented safe defaults");
        require(Files.readString(root.resolve(".pupper-listening.json.bak")).equals(broken), "Corruption backup did not preserve the user's original bytes");
        var changed = MusicExperienceStore.Settings.DEFAULT.audio(false, 3, true); store.settings(changed);
        require(new MusicExperienceStore(root).settings().equals(changed) && Files.readString(root.resolve(".pupper-listening.json.bak")).equals(broken),
            "Repair save lost healthy settings or overwrote the original corrupt backup");
    }
    private static void rollback(Path root) throws Exception {
        var store = new MusicExperienceStore(root); store.played(A, 1); store.resume(new MusicExperienceStore.Resume(List.of(A, B), 42, true, .4f));
        var settings = store.settings(); var recent = store.recent(); var resume = store.resume();
        Path file = root.resolve(".pupper-listening.json"); Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("marker"), "fixture");
        expectIo(() -> store.played(B, 10)); expectIo(() -> store.settings(settings.audio(false, 2, true)));
        expectIo(() -> store.resume(new MusicExperienceStore.Resume(List.of(B), 30, false, .8f))); expectIo(store::clearRecent);
        require(store.settings().equals(settings) && store.recent().equals(recent) && store.resume().equals(resume), "A failed listening save committed in-memory state");
        require(Files.readString(file.resolve("marker")).equals("fixture"), "Failed listening save destroyed its existing destination");
        try (var files = Files.list(root)) { require(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")), "Failed listening save leaked temporary state"); }
    }
    private static MusicQueue.Entry remote(String provider, String id, String filename) {
        return new MusicQueue.Entry(new MusicTrack(0, "曲目 " + id, "Artist", "", "", 1000, provider, id, true, true), filename);
    }
    private static MusicQueue.Entry local(String filename) { return new MusicQueue.Entry(new MusicTrack(0, "Local", "", "", "", 1000), filename); }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void expectIo(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected listening save failure"); } catch (IOException expected) { checks++; }
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
