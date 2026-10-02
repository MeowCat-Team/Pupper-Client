package cn.pupperclient.management.music.media;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static java.lang.foreign.ValueLayout.*;

/** Real WinRT round trips against a hidden test window; no Minecraft, hardware audio or visible helper. */
public final class WindowsSmtcChecks {
    private static int checks;
    private static int button;

    public static void main(String[] args) throws Throwable {
        if (!MediaSession.supported()) { System.out.println("Windows SMTC native checks skipped on this host."); return; }
        var user32 = SymbolLookup.libraryLookup("user32.dll", Arena.global());
        var create = Linker.nativeLinker().downcallHandle(user32.findOrThrow("CreateWindowExW"), FunctionDescriptor.of(ADDRESS,
            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        var destroy = Linker.nativeLinker().downcallHandle(user32.findOrThrow("DestroyWindow"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = arena.allocateFrom("STATIC", StandardCharsets.UTF_16LE);
            MemorySegment title = arena.allocateFrom("Pupper Client SMTC verification", StandardCharsets.UTF_16LE);
            MemorySegment window = (MemorySegment) create.invokeWithArguments(0, name, title, 0, 0, 0, 64, 64,
                MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
            require(window.address() != 0, "Hidden top-level window not created");
            List<Integer> actions = new ArrayList<>();
            Path cover = Files.createTempFile("pupper-smtc-cover-", ".png");
            try (var resource = WindowsSmtcChecks.class.getResourceAsStream("/assets/pupper/logo.png")) {
                Files.copy(resource, cover, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try (WindowsSmtc smtc = new WindowsSmtc(window.address(), actions::add)) {
                WinRt.Com controls = field(smtc, "controls"), music = field(smtc, "music"), album = field(smtc, "music2");
                smtc.publish(new WindowsSmtc.Snapshot("fixture:1", "原生测试 ♪", "Pupper Client", "FFM fixture", cover.toUri().toString(),
                    true, true, true, 12.5f, 120));
                require(readInt(controls, 6) == 3, "Playing status did not reach WinRT");
                require(readText(music, 6).equals("原生测试 ♪"), "Unicode title did not reach WinRT");
                require(readText(music, 10).equals("Pupper Client"), "Artist did not reach WinRT");
                require(readText(album, 6).equals("FFM fixture"), "Album did not reach WinRT");
                try (WinRt.Com thumbnail = field(smtc, "updater").output(10, new MemoryLayout[0]);
                     WinRt.Com read = thumbnail.output(6, new MemoryLayout[0]);
                     WinRt.Com info = read.query("00000036-0000-0000-c000-000000000046")) {
                    long deadline = System.nanoTime() + 2_000_000_000L;
                    while (readInt(info, 7) == 0 && System.nanoTime() < deadline) Thread.sleep(5);
                    require(readInt(info, 7) == 1, "System thumbnail could not open the cached local image");
                    try (WinRt.Com stream = read.output(8, new MemoryLayout[0]);
                         WinRt.Com random = stream.query("905a0fe1-bc53-11df-8c49-001e4fc686da")) {
                        require(readTime(random, 6) == Files.size(cover), "Thumbnail stream lost cached cover bytes");
                    }
                }
                require(readTime(field(smtc, "timeline"), 14) == 125_000_000L, "TimeSpan struct ABI is wrong");
                require(readTime(field(smtc, "timeline"), 8) == 1_200_000_000L, "Duration not published");
                require(readTime(field(smtc, "timeline"), 10) == readTime(field(smtc, "timeline"), 12), "Unsupported seeking was advertised");
                smtc.publish(new WindowsSmtc.Snapshot("fixture:2", "Second track", "Another artist", "", "",
                    false, true, false, 0, 60));
                require(readInt(controls, 6) == 4 && readText(music, 6).equals("Second track"), "Pause/track change lost");
                require(readText(album, 6).isEmpty(), "Old album survives a track switch");

                Field callbackField = WindowsSmtc.class.getDeclaredField("buttons");
                callbackField.setAccessible(true);
                var callback = (WindowsSmtc.ButtonHandler) callbackField.get(smtc);
                MemorySegment fakeArgs = arena.allocate(ADDRESS), table = arena.allocate(7 * ADDRESS.byteSize(), ADDRESS.byteAlignment());
                fakeArgs.set(ADDRESS, 0, table);
                var getter = MethodHandles.lookup().findStatic(WindowsSmtcChecks.class, "getButton",
                    MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class));
                table.setAtIndex(ADDRESS, 6, Linker.nativeLinker().upcallStub(getter, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS), arena));
                var delegate = callback.pointer.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0).reinterpret(4 * ADDRESS.byteSize());
                var invoke = Linker.nativeLinker().downcallHandle(delegate.getAtIndex(ADDRESS, 3),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
                for (int value : new int[] {0, 1, 2, 6, 7}) {
                    button = value;
                    int result = (int) invoke.invokeExact(callback.pointer, MemorySegment.NULL, fakeArgs);
                    require(result == 0 && actions.getLast() == value, "Native media-button upcall lost " + value);
                }
                smtc.publish(WindowsSmtc.Snapshot.EMPTY);
                require(readInt(controls, 6) == 2, "Empty player did not stop the system session");
            } finally {
                int ignored = (int) destroy.invokeExact(window);
                Files.deleteIfExists(cover);
            }
        }
        System.out.println("Windows SMTC FFM checks passed: " + checks + " assertions; hidden HWND, metadata, timeline structs and native button upcalls.");
    }

    private static int getButton(MemorySegment self, MemorySegment out) { out.reinterpret(4).set(JAVA_INT, 0, button); return 0; }
    private static WinRt.Com field(WindowsSmtc smtc, String name) throws Exception {
        Field field = WindowsSmtc.class.getDeclaredField(name); field.setAccessible(true); return (WinRt.Com) field.get(smtc);
    }
    private static int readInt(WinRt.Com com, int slot) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(JAVA_INT);
            WinRt.check(com.call(slot, new MemoryLayout[] { ADDRESS }, out)); return out.get(JAVA_INT, 0);
        }
    }
    private static long readTime(WinRt.Com com, int slot) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(JAVA_LONG);
            WinRt.check(com.call(slot, new MemoryLayout[] { ADDRESS }, out)); return out.get(JAVA_LONG, 0);
        }
    }
    private static String readText(WinRt.Com com, int slot) throws Throwable {
        var library = SymbolLookup.libraryLookup("combase.dll", Arena.global());
        var buffer = Linker.nativeLinker().downcallHandle(library.findOrThrow("WindowsGetStringRawBuffer"),
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
        var delete = Linker.nativeLinker().downcallHandle(library.findOrThrow("WindowsDeleteString"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS), length = arena.allocate(JAVA_INT);
            WinRt.check(com.call(slot, new MemoryLayout[] { ADDRESS }, out));
            MemorySegment value = out.get(ADDRESS, 0);
            try {
                MemorySegment raw = (MemorySegment) buffer.invokeExact(value, length);
                int count = length.get(JAVA_INT, 0);
                return count == 0 ? "" : new String(raw.reinterpret(count * 2L).toArray(JAVA_BYTE), StandardCharsets.UTF_16LE);
            } finally { int ignored = (int) delete.invokeExact(value); }
        }
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
