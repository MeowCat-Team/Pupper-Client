package cn.pupperclient.management.music;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Playback order belongs to the chosen list, independently of the download directory. */
public final class MusicQueue {
    public record Entry(MusicTrack track, String filename) {
        public Entry { filename = filename == null ? "" : filename; }
        public String key() { return track.remote() ? track.key() : "file:" + filename; }
        public boolean playable() { return !filename.isBlank() || track.playable(); }
        public static Entry of(Music music) { return new Entry(music.getTrack(), music.getAudio().getName()); }
    }
    public record Snapshot(Entry current, List<Entry> upcoming, long revision, long generation) { }
    private final List<Entry> upcoming = new ArrayList<>(), history = new ArrayList<>();
    private Entry current;
    private long revision, generation;
    public synchronized void start(List<Entry> entries, int index) {
        if (index < 0 || index >= entries.size()) throw new IllegalArgumentException("Invalid playback selection");
        history.clear(); history.addAll(entries.subList(Math.max(0, index - 100), index));
        upcoming.clear(); upcoming.addAll(entries.subList(index + 1, entries.size()));
        current = entries.get(index); changed();
    }
    public synchronized Snapshot snapshot() { return new Snapshot(current, List.copyOf(upcoming), revision, generation); }
    public synchronized boolean current(long token) { return generation == token; }
    public synchronized boolean canSwitch() { return !history.isEmpty() || !upcoming.isEmpty(); }
    public synchronized void cancelPending() { generation++; }
    public synchronized void enqueue(Entry entry, boolean next) { upcoming.add(next ? 0 : upcoming.size(), entry); revision++; }
    public synchronized Entry advance(boolean shuffle) {
        if (current != null) { history.add(current); if (history.size() > 100) history.removeFirst(); }
        current = upcoming.isEmpty() ? null : upcoming.remove(shuffle ? ThreadLocalRandom.current().nextInt(upcoming.size()) : 0);
        changed(); return current;
    }
    public synchronized Entry previous() {
        if (history.isEmpty()) return null;
        if (current != null) upcoming.addFirst(current);
        current = history.removeLast(); changed(); return current;
    }
    public synchronized boolean remove(int index, long expectedRevision) {
        if (revision != expectedRevision || index < 0 || index >= upcoming.size()) return false;
        upcoming.remove(index); revision++; return true;
    }
    public synchronized boolean move(int from, int to, long expectedRevision) {
        if (revision != expectedRevision || from < 0 || to < 0 || from >= upcoming.size() || to >= upcoming.size()) return false;
        upcoming.add(to, upcoming.remove(from)); revision++; return true;
    }
    public synchronized Entry jump(int index, long expectedRevision) {
        if (revision != expectedRevision || index < 0 || index >= upcoming.size()) return null;
        Entry selected = null;
        for (int i = 0; i <= index; i++) selected = advance(false);
        return selected;
    }
    public synchronized void clear() { upcoming.clear(); revision++; }
    private void changed() { revision++; generation++; }
}
