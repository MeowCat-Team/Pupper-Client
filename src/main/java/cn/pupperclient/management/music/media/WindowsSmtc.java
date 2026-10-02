package cn.pupperclient.management.music.media;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import static java.lang.foreign.ValueLayout.*;

/** SMTC belongs to our top-level GLFW window. All COM operations live on one platform/MTA thread. */
public final class WindowsSmtc implements AutoCloseable {
    public record Snapshot(String identity, String title, String artist, String album, String artwork,
            boolean playing, boolean hasTrack, boolean canSwitch, float position, float duration) {
        public static final Snapshot EMPTY = new Snapshot("", "", "", "", "", false, false, false, 0, 0);
    }
    private final WinRt runtime;
    private WinRt.Com controls, controls2, updater, music, music2, timeline;
    private ButtonHandler buttons;
    private long token;
    private boolean registered;
    private Snapshot previous;

    public WindowsSmtc(long hwnd, IntConsumer onButton) {
        runtime = new WinRt();
        try {
            try (Arena arena = Arena.ofConfined(); WinRt.Com factory = runtime.factory(
                    "Windows.Media.SystemMediaTransportControls", "ddb0472d-c911-4a1f-86d9-dc3d71a95f5a")) {
                controls = factory.output(6, new MemoryLayout[] { ADDRESS, ADDRESS }, MemorySegment.ofAddress(hwnd),
                    WinRt.guid(arena, "99fa3ff4-1742-42a6-902e-087d41f965ec"));
            }
            updater = controls.output(8, new MemoryLayout[0]);
            updater.integer(7, 1);
            music = updater.output(12, new MemoryLayout[0]);
            music2 = music.query("00368462-97d3-44b9-b00f-008afcefaf18");
            controls2 = controls.query("ea98d2f6-7f3c-4af2-a586-72889808efb1");
            try (WinRt.Com instance = runtime.activate("Windows.Media.SystemMediaTransportControlsTimelineProperties")) {
                timeline = instance.query("5125316a-c3a2-475b-8507-93534dc88f15");
            }
            buttons = new ButtonHandler(runtime, onButton);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment out = arena.allocate(JAVA_LONG);
                WinRt.check(controls.call(32, new MemoryLayout[] { ADDRESS, ADDRESS }, buttons.pointer, out));
                token = out.get(JAVA_LONG, 0);
                registered = true;
            }
            publish(Snapshot.EMPTY);
        } catch (RuntimeException failed) { close(); throw failed; }
    }

    public void publish(Snapshot state) {
        controls.bool(11, state.hasTrack());
        controls.bool(13, state.hasTrack());
        controls.bool(17, state.hasTrack());
        controls.bool(15, state.hasTrack());
        controls.bool(25, state.canSwitch());
        controls.bool(27, state.canSwitch());
        controls.integer(7, !state.hasTrack() ? 2 : state.playing() ? 3 : 4);
        if (previous == null || !previous.identity().equals(state.identity()) || !previous.title().equals(state.title())
                || !previous.artist().equals(state.artist()) || !previous.album().equals(state.album())
                || !previous.artwork().equals(state.artwork())) {
            WinRt.check(updater.call(16, new MemoryLayout[0])); // ClearAll also removes the previous cover.
            if (state.hasTrack()) {
                updater.integer(7, 1); // MediaPlaybackType.Music
                updater.text(9, state.identity());
                music.text(7, state.title());
                music.text(9, state.artist());
                music.text(11, state.artist());
                music2.text(7, state.album());
                if (!state.artwork().isBlank()) try (WinRt.Com image = runtime.thumbnail(state.artwork())) {
                    WinRt.check(updater.call(11, new MemoryLayout[] { ADDRESS }, image.pointer));
                } catch (RuntimeException noArtwork) { /* Metadata and transport still work without a cover. */ }
            }
            WinRt.check(updater.call(17, new MemoryLayout[0]));
        }
        long end = ticks(state.duration()), position = Math.min(ticks(state.position()), end);
        timeline.time(7, 0);
        timeline.time(9, end);
        // Seeking is not advertised: these decoders currently support transport, not arbitrary seek.
        timeline.time(11, position);
        timeline.time(13, position);
        timeline.time(15, position);
        WinRt.check(controls2.call(12, new MemoryLayout[] { ADDRESS }, timeline.pointer));
        previous = state;
    }

    public static long ticks(float seconds) { return Float.isFinite(seconds) ? (long) (Math.max(0, seconds) * 10_000_000d) : 0; }

    @Override public void close() {
        if (buttons != null) buttons.active = false;
        if (controls != null) {
            try { controls.bool(11, false); } catch (RuntimeException ignored) { }
            if (registered) try (Arena arena = Arena.ofConfined()) {
                MemorySegment value = arena.allocate(WinRt.TOKEN);
                value.set(JAVA_LONG, 0, token);
                WinRt.check(controls.call(33, new MemoryLayout[] { WinRt.TOKEN }, value));
            } catch (RuntimeException ignored) { }
            registered = false;
        }
        for (WinRt.Com value : new WinRt.Com[] { timeline, music2, music, updater, controls2, controls })
            if (value != null) value.close();
        timeline = music2 = music = updater = controls2 = controls = null;
        if (buttons != null) { buttons.release(buttons.pointer); buttons = null; }
        runtime.close();
    }

    /** The native server owns COM references, so retain delegates until its final Release.
     * An automatic shared arena keeps an in-flight upcall alive through its return to native code. */
    static final class ButtonHandler {
        private static final Map<Long, ButtonHandler> LIVE = new ConcurrentHashMap<>();
        private final Arena arena = Arena.ofAuto();
        private final AtomicInteger references = new AtomicInteger(1);
        private final WinRt runtime;
        private final IntConsumer listener;
        private final MemorySegment unknown, agile, event;
        final MemorySegment pointer;
        volatile boolean active = true;

        ButtonHandler(WinRt runtime, IntConsumer listener) {
            this.runtime = runtime; this.listener = listener;
            unknown = WinRt.guid(arena, "00000000-0000-0000-c000-000000000046");
            agile = WinRt.guid(arena, "94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90");
            event = WinRt.guid(arena, "0557e996-7b23-5bae-aa81-ea0d671143a4");
            pointer = arena.allocate(ADDRESS);
            MemorySegment table = arena.allocate(4 * ADDRESS.byteSize(), ADDRESS.byteAlignment());
            pointer.set(ADDRESS, 0, table);
            bind(table, 0, "query", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS),
                MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            bind(table, 1, "addRef", FunctionDescriptor.of(JAVA_INT, ADDRESS), MethodType.methodType(int.class, MemorySegment.class));
            bind(table, 2, "release", FunctionDescriptor.of(JAVA_INT, ADDRESS), MethodType.methodType(int.class, MemorySegment.class));
            bind(table, 3, "invoke", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS),
                MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            LIVE.put(pointer.address(), this);
        }

        private void bind(MemorySegment table, int slot, String name, FunctionDescriptor descriptor, MethodType type) {
            try {
                var handle = MethodHandles.lookup().findVirtual(ButtonHandler.class, name, type).bindTo(this);
                table.setAtIndex(ADDRESS, slot, Linker.nativeLinker().upcallStub(handle, descriptor, arena));
            } catch (ReflectiveOperationException failed) { throw new IllegalStateException(failed); }
        }
        int query(MemorySegment self, MemorySegment iid, MemorySegment out) {
            try {
                MemorySegment id = iid.reinterpret(16), result = out.reinterpret(ADDRESS.byteSize());
                if (id.mismatch(unknown) == -1 || id.mismatch(agile) == -1 || id.mismatch(event) == -1) {
                    result.set(ADDRESS, 0, pointer); addRef(self); return 0;
                }
                result.set(ADDRESS, 0, MemorySegment.NULL); return 0x80004002;
            } catch (Throwable failed) { return 0x80004005; }
        }
        int addRef(MemorySegment self) { return references.incrementAndGet(); }
        int release(MemorySegment self) {
            int remaining = references.decrementAndGet();
            if (remaining == 0) LIVE.remove(pointer.address(), this);
            return remaining;
        }
        int invoke(MemorySegment self, MemorySegment sender, MemorySegment args) {
            if (!active) return 0;
            try (Arena scratch = Arena.ofConfined()) {
                MemorySegment out = scratch.allocate(JAVA_INT);
                // Event arguments are borrowed for Invoke's duration; never Release the caller's reference.
                WinRt.Com borrowed = runtime.new Com(args);
                WinRt.check(borrowed.call(6, new MemoryLayout[] { ADDRESS }, out));
                listener.accept(out.get(JAVA_INT, 0));
                return 0;
            } catch (Throwable failed) { return 0x80004005; }
        }
    }
}
