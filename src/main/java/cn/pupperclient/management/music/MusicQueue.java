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
    private record Slot(long id, Entry entry) { }
    private final List<Slot> upcoming = new ArrayList<>(), history = new ArrayList<>(), cycle = new ArrayList<>();
    private Slot current;
    private long revision, generation, nextId;
    public synchronized void start(List<Entry> entries, int index) {
        if (index < 0 || index >= entries.size()) throw new IllegalArgumentException("Invalid playback selection");
        cycle.clear(); entries.forEach(entry -> cycle.add(new Slot(++nextId, entry)));
        history.clear(); history.addAll(cycle.subList(Math.max(0, index - 100), index));
        upcoming.clear(); upcoming.addAll(cycle.subList(index + 1, cycle.size()));
        current = cycle.get(index); changed();
    }
    public synchronized Snapshot snapshot() { return new Snapshot(current == null ? null : current.entry(), upcoming.stream().map(Slot::entry).toList(), revision, generation); }
    public synchronized boolean current(long token) { return generation == token; }
    public synchronized boolean canSwitch() { return !history.isEmpty() || !upcoming.isEmpty(); }
    public synchronized void cancelPending() { generation++; }
    public synchronized void enqueue(Entry entry, boolean next) {
        Slot slot = new Slot(++nextId, entry);
        int position = next && !upcoming.isEmpty() ? cycle.indexOf(upcoming.getFirst())
            : next && current != null ? cycle.indexOf(current) + 1 : cycle.size();
        cycle.add(Math.clamp(position, 0, cycle.size()), slot);
        upcoming.add(next ? 0 : upcoming.size(), slot); revision++;
    }
    public synchronized Entry advance(boolean shuffle) {
        return advance(shuffle, false);
    }
    public synchronized Entry advance(boolean shuffle, boolean repeatAll) {
        Slot previous = current;
        if (current != null) { history.add(current); if (history.size() > 100) history.removeFirst(); }
        if (upcoming.isEmpty() && repeatAll) upcoming.addAll(cycle);
        int index = 0;
        if (shuffle && !upcoming.isEmpty()) {
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < upcoming.size(); i++)
                if (previous == null || !upcoming.get(i).entry().key().equals(previous.entry().key())) candidates.add(i);
            index = candidates.isEmpty() ? 0 : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }
        current = upcoming.isEmpty() ? null : upcoming.remove(index);
        changed(); return current == null ? null : current.entry();
    }
    public synchronized Entry previous() {
        if (history.isEmpty()) return null;
        if (current != null) upcoming.addFirst(current);
        current = history.removeLast(); changed(); return current.entry();
    }
    public synchronized boolean remove(int index, long expectedRevision) {
        if (revision != expectedRevision || index < 0 || index >= upcoming.size()) return false;
        cycle.remove(upcoming.remove(index)); revision++; return true;
    }
    public synchronized boolean move(int from, int to, long expectedRevision) {
        if (revision != expectedRevision || from < 0 || to < 0 || from >= upcoming.size() || to >= upcoming.size()) return false;
        upcoming.add(to, upcoming.remove(from));
        // Previous can revisit a slot across a list wrap; each slot still appears only once in the next cycle.
        var ordered = upcoming.stream().filter(cycle::contains).distinct().toList();
        var pending = new java.util.HashSet<>(ordered);
        int index = 0;
        for (int i = 0; i < cycle.size(); i++) if (pending.contains(cycle.get(i))) cycle.set(i, ordered.get(index++));
        revision++; return true;
    }
    public synchronized Entry jump(int index, long expectedRevision) {
        if (revision != expectedRevision || index < 0 || index >= upcoming.size()) return null;
        Entry selected = null;
        for (int i = 0; i <= index; i++) selected = advance(false);
        return selected;
    }
    public synchronized void clear() { upcoming.clear(); cycle.clear(); if (current != null) cycle.add(current); revision++; }
    private void changed() { revision++; generation++; }
}
