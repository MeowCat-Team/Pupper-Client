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
    private record Reservation(Slot current, long revision, long generation, boolean shuffle, boolean repeatAll, Slot next) { }
    private final List<Slot> upcoming = new ArrayList<>(), history = new ArrayList<>(), cycle = new ArrayList<>();
    private Slot current;
    private Reservation reservation;
    private Runnable changeListener;
    private long revision, generation, nextId, listRevision;
    public synchronized long listRevision() { return listRevision; }
    /** The listener should schedule work; it runs after a mutation releases the queue monitor. */
    public synchronized void setChangeListener(Runnable listener) { changeListener = listener; }
    public void start(List<Entry> entries, int index) {
        Runnable listener;
        synchronized (this) {
            if (index < 0 || index >= entries.size()) throw new IllegalArgumentException("Invalid playback selection");
            listRevision++;
            cycle.clear(); entries.forEach(entry -> cycle.add(new Slot(++nextId, entry)));
            history.clear(); history.addAll(cycle.subList(Math.max(0, index - 100), index));
            upcoming.clear(); upcoming.addAll(cycle.subList(index + 1, cycle.size()));
            current = cycle.get(index); changed(); listener = changeListener;
        }
        notifyChanged(listener);
    }
    public synchronized Snapshot snapshot() { return new Snapshot(current == null ? null : current.entry(), upcoming.stream().map(Slot::entry).toList(), revision, generation); }
    public synchronized boolean current(long token) { return generation == token; }
    public synchronized boolean canSwitch() { return !history.isEmpty() || !upcoming.isEmpty(); }
    public void cancelPending() {
        Runnable listener;
        synchronized (this) { generation++; reservation = null; listener = changeListener; }
        notifyChanged(listener);
    }
    public void enqueue(Entry entry, boolean next) {
        Runnable listener;
        synchronized (this) {
            listRevision++;
            Slot slot = new Slot(++nextId, entry);
            int position = next && !upcoming.isEmpty() ? cycle.indexOf(upcoming.getFirst())
                : next && current != null ? cycle.indexOf(current) + 1 : cycle.size();
            cycle.add(Math.clamp(position, 0, cycle.size()), slot);
            upcoming.add(next ? 0 : upcoming.size(), slot); edited(); listener = changeListener;
        }
        notifyChanged(listener);
    }
    /** Background pages may extend the chosen list until the user edits or replaces it. */
    public boolean appendLoaded(List<Entry> entries, long expectedListRevision) {
        Runnable listener;
        synchronized (this) {
            if (listRevision != expectedListRevision) return false;
            if (entries.isEmpty()) return true;
            for (Entry entry : entries) {
                Slot slot = new Slot(++nextId, entry); cycle.add(slot); upcoming.add(slot);
            }
            edited(); listener = changeListener;
        }
        notifyChanged(listener); return true;
    }
    /** Reserves the actual next slot without changing playback tokens, history or visible ordering. */
    public synchronized Entry peekNext(boolean shuffle, boolean repeatAll) {
        Slot next = plannedNext(shuffle, repeatAll);
        return next == null ? null : next.entry();
    }
    private Slot plannedNext(boolean shuffle, boolean repeatAll) {
        if (reservation != null && reservation.current() == current && reservation.revision() == revision
                && reservation.generation() == generation && reservation.shuffle() == shuffle && reservation.repeatAll() == repeatAll)
            return reservation.next();
        List<Slot> choices = upcoming.isEmpty() && repeatAll ? cycle : upcoming;
        Slot next = choices.isEmpty() ? null : choices.getFirst();
        if (shuffle && !choices.isEmpty()) {
            List<Slot> candidates = choices.stream()
                .filter(slot -> current == null || !slot.entry().key().equals(current.entry().key())).toList();
            if (!candidates.isEmpty()) next = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }
        reservation = new Reservation(current, revision, generation, shuffle, repeatAll, next);
        return next;
    }
    public Entry advance(boolean shuffle) {
        return advance(shuffle, false);
    }
    public Entry advance(boolean shuffle, boolean repeatAll) {
        Entry selected;
        Runnable listener;
        synchronized (this) { selected = advanceLocked(shuffle, repeatAll); listener = changeListener; }
        notifyChanged(listener);
        return selected;
    }
    /** Commits a prepared audio handoff exactly once, before any later queue edit can change its slot. */
    public Entry advancePrepared(long expectedGeneration, long expectedRevision, String nextKey,
            boolean shuffle, boolean repeatAll) {
        Entry selected;
        Runnable listener;
        synchronized (this) {
            if (generation != expectedGeneration || revision != expectedRevision) return null;
            Slot next = plannedNext(shuffle, repeatAll);
            if (next == null || !next.entry().key().equals(nextKey)) return null;
            selected = advanceLocked(shuffle, repeatAll); listener = changeListener;
        }
        notifyChanged(listener);
        return selected;
    }
    private Entry advanceLocked(boolean shuffle, boolean repeatAll) {
        Slot next = plannedNext(shuffle, repeatAll);
        if (current != null) { history.add(current); if (history.size() > 100) history.removeFirst(); }
        if (upcoming.isEmpty() && repeatAll) upcoming.addAll(cycle);
        if (next != null) {
            for (int i = 0; i < upcoming.size(); i++) {
                if (upcoming.get(i) == next) { upcoming.remove(i); break; }
            }
        }
        current = next;
        changed(); return current == null ? null : current.entry();
    }
    public Entry previous() {
        Entry selected;
        Runnable listener;
        synchronized (this) {
            if (history.isEmpty()) return null;
            if (current != null) upcoming.addFirst(current);
            current = history.removeLast(); changed(); selected = current.entry(); listener = changeListener;
        }
        notifyChanged(listener);
        return selected;
    }
    public boolean remove(int index, long expectedRevision) {
        Runnable listener;
        synchronized (this) {
            if (revision != expectedRevision || index < 0 || index >= upcoming.size()) return false;
            listRevision++;
            cycle.remove(upcoming.remove(index)); edited(); listener = changeListener;
        }
        notifyChanged(listener);
        return true;
    }
    public boolean move(int from, int to, long expectedRevision) {
        Runnable listener;
        synchronized (this) {
            if (revision != expectedRevision || from < 0 || to < 0 || from >= upcoming.size() || to >= upcoming.size()) return false;
            if (from == to) return true;
            listRevision++;
            upcoming.add(to, upcoming.remove(from));
            // Previous can revisit a slot across a list wrap; each slot still appears only once in the next cycle.
            var ordered = upcoming.stream().filter(cycle::contains).distinct().toList();
            var pending = new java.util.HashSet<>(ordered);
            int index = 0;
            for (int i = 0; i < cycle.size(); i++) if (pending.contains(cycle.get(i))) cycle.set(i, ordered.get(index++));
            edited(); listener = changeListener;
        }
        notifyChanged(listener);
        return true;
    }
    public Entry jump(int index, long expectedRevision) {
        Entry selected = null;
        Runnable listener;
        synchronized (this) {
            if (revision != expectedRevision || index < 0 || index >= upcoming.size()) return null;
            for (int i = 0; i <= index; i++) selected = advanceLocked(false, false);
            listener = changeListener;
        }
        notifyChanged(listener);
        return selected;
    }
    public void clear() {
        Runnable listener;
        synchronized (this) {
            if (upcoming.isEmpty() && (current == null ? cycle.isEmpty() : cycle.size() == 1 && cycle.getFirst() == current)) return;
            listRevision++;
            upcoming.clear(); cycle.clear(); if (current != null) cycle.add(current); edited(); listener = changeListener;
        }
        notifyChanged(listener);
    }
    private void edited() { revision++; reservation = null; }
    private void changed() { edited(); generation++; }
    private static void notifyChanged(Runnable listener) { if (listener != null) listener.run(); }
}
