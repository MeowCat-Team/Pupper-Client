package cn.pupperclient.management.music.media;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import static java.lang.foreign.ValueLayout.*;

/** Minimal WinRT ABI bindings using the final Java FFM API; initialized only on Windows. */
public final class WinRt {
    static final MemoryLayout TOKEN = MemoryLayout.structLayout(JAVA_LONG);
    static final MemoryLayout TIME_SPAN = MemoryLayout.structLayout(JAVA_LONG);
    private static final Map<Signature, MethodHandle> CALLS = new ConcurrentHashMap<>();
    private record Signature(long address, FunctionDescriptor descriptor) { }
    private final SymbolLookup combase = SymbolLookup.libraryLookup("combase.dll", Arena.global());
    private boolean initialized;

    public WinRt() {
        int result = integer("RoInitialize", new MemoryLayout[] { JAVA_INT }, 1); // RO_INIT_MULTITHREADED
        check(result);
        initialized = true;
    }

    static MethodHandle function(MemorySegment pointer, FunctionDescriptor descriptor) {
        return CALLS.computeIfAbsent(new Signature(pointer.address(), descriptor),
            _ -> Linker.nativeLinker().downcallHandle(pointer, descriptor));
    }

    static int invoke(MemorySegment function, FunctionDescriptor descriptor, Object... args) {
        try { return (int) function(function, descriptor).invokeWithArguments(args); }
        catch (Throwable error) { throw new IllegalStateException("WinRT call failed", error); }
    }

    private int integer(String name, MemoryLayout[] arguments, Object... args) {
        return invoke(combase.findOrThrow(name), FunctionDescriptor.of(JAVA_INT, arguments), args);
    }

    public static void check(int result) {
        if (result < 0) throw new IllegalStateException("WinRT HRESULT 0x" + Integer.toHexString(result));
    }

    public static MemorySegment guid(Arena arena, String text) {
        UUID id = UUID.fromString(text);
        MemorySegment value = arena.allocate(16, 4);
        long most = id.getMostSignificantBits(), least = id.getLeastSignificantBits();
        value.set(JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), 0, (int) (most >>> 32));
        value.set(JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), 4, (short) (most >>> 16));
        value.set(JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), 6, (short) most);
        value.set(JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 8, least);
        return value;
    }

    public HString string(String text) {
        try (Arena arena = Arena.ofConfined()) {
            byte[] bytes = (text + "\0").getBytes(StandardCharsets.UTF_16LE);
            MemorySegment buffer = arena.allocate(bytes.length, 2);
            buffer.copyFrom(MemorySegment.ofArray(bytes));
            MemorySegment out = arena.allocate(ADDRESS);
            check(integer("WindowsCreateString", new MemoryLayout[] { ADDRESS, JAVA_INT, ADDRESS },
                buffer, text.length(), out));
            return new HString(out.get(ADDRESS, 0));
        }
    }

    public final class HString implements AutoCloseable {
        final MemorySegment pointer;
        HString(MemorySegment pointer) { this.pointer = pointer; }
        @Override public void close() {
            check(integer("WindowsDeleteString", new MemoryLayout[] { ADDRESS }, pointer));
        }
    }

    public Com factory(String name, String iid) {
        try (Arena arena = Arena.ofConfined(); HString type = string(name)) {
            MemorySegment out = arena.allocate(ADDRESS);
            check(integer("RoGetActivationFactory", new MemoryLayout[] { ADDRESS, ADDRESS, ADDRESS },
                type.pointer, guid(arena, iid), out));
            return new Com(out.get(ADDRESS, 0));
        }
    }

    public Com activate(String name) {
        try (Arena arena = Arena.ofConfined(); HString type = string(name)) {
            MemorySegment out = arena.allocate(ADDRESS);
            check(integer("RoActivateInstance", new MemoryLayout[] { ADDRESS, ADDRESS }, type.pointer, out));
            return new Com(out.get(ADDRESS, 0));
        }
    }

    public Com thumbnail(String uri) {
        // CreateFromUri does not support file: URLs. A desktop file stream exposes cached/embedded covers directly.
        try (Arena arena = Arena.ofConfined();
             Com streams = factory("Windows.Storage.Streams.RandomAccessStreamReference", "857309dc-3fbf-4e7d-986f-ef3b1a07a964")) {
            String path = java.nio.file.Path.of(java.net.URI.create(uri)).toAbsolutePath().toString();
            MemorySegment name = arena.allocateFrom(path, StandardCharsets.UTF_16LE), out = arena.allocate(ADDRESS);
            var shcore = SymbolLookup.libraryLookup("shcore.dll", Arena.global());
            check(invoke(shcore.findOrThrow("CreateRandomAccessStreamOnFile"),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS), name, 0,
                guid(arena, "905a0fe1-bc53-11df-8c49-001e4fc686da"), out));
            try (Com file = new Com(out.get(ADDRESS, 0))) {
                return streams.output(8, new MemoryLayout[] { ADDRESS }, file.pointer);
            }
        }
    }

    public final class Com implements AutoCloseable {
        final MemorySegment pointer;
        private boolean released;
        Com(MemorySegment pointer) {
            if (pointer.address() == 0) throw new IllegalStateException("Null WinRT interface");
            this.pointer = pointer;
        }
        public int call(int slot, MemoryLayout[] argumentLayouts, Object... args) {
            MemoryLayout[] layouts = new MemoryLayout[argumentLayouts.length + 1];
            layouts[0] = ADDRESS;
            System.arraycopy(argumentLayouts, 0, layouts, 1, argumentLayouts.length);
            Object[] values = new Object[args.length + 1];
            values[0] = pointer;
            System.arraycopy(args, 0, values, 1, args.length);
            MemorySegment table = pointer.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0)
                .reinterpret((slot + 1L) * ADDRESS.byteSize());
            return invoke(table.getAtIndex(ADDRESS, slot), FunctionDescriptor.of(JAVA_INT, layouts), values);
        }
        public void integer(int slot, int value) { check(call(slot, new MemoryLayout[] { JAVA_INT }, value)); }
        public void bool(int slot, boolean value) { check(call(slot, new MemoryLayout[] { JAVA_BYTE }, (byte) (value ? 1 : 0))); }
        public void text(int slot, String value) {
            try (HString text = string(value)) { check(call(slot, new MemoryLayout[] { ADDRESS }, text.pointer)); }
        }
        public void time(int slot, long ticks) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment span = arena.allocate(TIME_SPAN);
                span.set(JAVA_LONG, 0, ticks);
                check(call(slot, new MemoryLayout[] { TIME_SPAN }, span));
            }
        }
        public Com output(int slot, MemoryLayout[] layouts, Object... args) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment out = arena.allocate(ADDRESS);
                MemoryLayout[] arguments = Arrays.copyOf(layouts, layouts.length + 1);
                arguments[layouts.length] = ADDRESS;
                Object[] values = Arrays.copyOf(args, args.length + 1);
                values[args.length] = out;
                check(call(slot, arguments, values));
                return new Com(out.get(ADDRESS, 0));
            }
        }
        public Com query(String iid) {
            try (Arena arena = Arena.ofConfined()) { return output(0, new MemoryLayout[] { ADDRESS }, guid(arena, iid)); }
        }
        @Override public void close() { if (!released) { released = true; call(2, new MemoryLayout[0]); } }
    }

    public void close() {
        if (!initialized) return;
        initialized = false;
        try { function(combase.findOrThrow("RoUninitialize"), FunctionDescriptor.ofVoid()).invokeExact(); }
        catch (Throwable error) { throw new IllegalStateException("WinRT uninitialization failed", error); }
    }
}
