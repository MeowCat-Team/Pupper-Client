package cn.pupperclient.music;

import cn.pupperclient.gui.modmenu.component.MusicPlayerLayout;
import cn.pupperclient.gui.modmenu.component.MusicPopupMenu;
import cn.pupperclient.management.music.MusicQueue;
import cn.pupperclient.management.music.MusicTrack;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.lwjgl.glfw.GLFW;

/** Reproduces playlist changes during loading and pointer/keyboard menu interactions without Minecraft. */
public final class MusicInteractionChecks {
    private static int checks;
    private static final MusicQueue.Entry A = entry("netease", "42"), B = entry("audius", "42"),
        C = entry("audius", "abc"), D = entry("netease", "43");
    private static MusicQueue.Entry entry(String provider, String id) {
        return new MusicQueue.Entry(new MusicTrack(0, id, "Artist", "", "", 1000, provider, id, true, true), "");
    }
    public static void run() {
        MusicQueue queue = new MusicQueue();
        queue.start(List.of(A, B, C), 1);
        var loading = queue.snapshot();
        require(loading.current().equals(B) && loading.upcoming().equals(List.of(C)), "Selection did not establish list playback order");
        require(!A.key().equals(B.key()), "Same numeric ID across music providers collided");
        queue.enqueue(D, true); queue.enqueue(A, false);
        require(queue.snapshot().upcoming().equals(List.of(D, C, A)), "Play Next/Last lost the chosen order");
        require(queue.current(loading.generation()), "Enqueue invalidated a currently loading song");
        require(loading.upcoming().equals(List.of(C)), "Snapshot changed after editing queue");
        require(!queue.remove(0, loading.revision()), "Old remove action deleted a newly inserted song");
        long revision = queue.snapshot().revision();
        require(queue.move(2, 0, revision), "Drag reorder rejected current revision");
        require(queue.snapshot().upcoming().equals(List.of(A, D, C)), "Drag moved the wrong entry");
        require(!queue.move(0, 1, revision), "Stale drag changed a newer queue");
        require(queue.remove(1, queue.snapshot().revision()), "Remove rejected intended song");
        require(queue.snapshot().upcoming().equals(List.of(A, C)), "Remove changed the wrong song");
        require(queue.advance(false).equals(A), "Next ignored the edited queue");
        require(!queue.current(loading.generation()), "Old asynchronous song completion can replace Next");
        require(queue.previous().equals(B) && queue.snapshot().upcoming().equals(List.of(A, C)), "Previous lost actual playback history");
        long pending = queue.snapshot().generation(); queue.cancelPending();
        require(!queue.current(pending) && queue.snapshot().current().equals(B), "Pause failed to cancel loading or lost its song");
        var beforeClear = queue.snapshot(); queue.clear();
        require(queue.snapshot().current().equals(B) && queue.snapshot().upcoming().isEmpty(), "Clear interrupted current playback");
        require(queue.canSwitch(), "SMTC lost Previous after the last upcoming song was removed");
        require(queue.current(beforeClear.generation()), "Clear cancelled the current song preparation");
        require(queue.advance(false) == null && queue.previous().equals(B), "Queue end lost replay history");
        queue.start(List.of(A, B, C, D), 0);
        require(queue.jump(2, queue.snapshot().revision()).equals(D) && queue.snapshot().upcoming().isEmpty(), "Double-click queued song failed to advance to its position");
        require(queue.jump(0, queue.snapshot().revision()) == null, "Queue jump accepted an invalid index");
        require(queue.previous().equals(C), "Jump lost skipped-song history");
        pending = queue.snapshot().generation(); queue.start(List.of(A), 0);
        require(!queue.current(pending), "Choosing a new list did not cancel previous pending load");
        require(!queue.canSwitch(), "A standalone song advertised nonexistent queue history");
        queue.start(List.of(A, B, C, D), 0);
        var random = queue.advance(true);
        require(List.of(B, C, D).contains(random) && queue.snapshot().upcoming().size() == 2, "Shuffle played outside the chosen context");
        require(queue.previous().equals(A), "Shuffle Previous failed to follow actual playback history");
        var local = new MusicTrack(0, "Local", "", "", "", 0);
        require(!new MusicQueue.Entry(local, "a.mp3").key().equals(new MusicQueue.Entry(local, "b.mp3").key()), "Local filenames lost their identity");

        var full = MusicPlayerLayout.content(MusicPlayerLayout.Panel.NONE);
        var compact = MusicPlayerLayout.content(MusicPlayerLayout.Panel.LYRICS);
        var side = MusicPlayerLayout.sidePanel();
        require(full.width() > compact.width() && compact.x() == full.x(), "Opening a side panel moved navigation or failed to reserve space");
        require(compact.x() + compact.width() < side.x() && side.x() + side.width() <= MusicPlayerLayout.WIDTH, "Side panel overlaps songs or exceeds window");
        require(!side.contains(side.x() + side.width(), side.y()), "Adjacent hit targets share their border");
        var popup = MusicPlayerLayout.popup(1119, 719, 288, 9);
        require(popup.x() >= 8 && popup.x() + popup.width() <= 1112 && popup.y() + popup.height() <= 608, "Quality menu crosses window or covers transport");

        AtomicInteger calls = new AtomicInteger();
        var items = List.of(new MusicPopupMenu.Item("Disabled", "", false, false, () -> calls.addAndGet(100)),
            new MusicPopupMenu.Item("Play", "", true, false, calls::incrementAndGet),
            new MusicPopupMenu.Item("Next", "", true, false, () -> calls.addAndGet(10)));
        MusicPopupMenu menu = new MusicPopupMenu(); menu.open(100, 100, items);
        menu.mousePressed(120, 130, GLFW.GLFW_MOUSE_BUTTON_LEFT); menu.mouseReleased(120, 130, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(calls.get() == 0 && menu.isOpen(), "Disabled menu action ran or closed the menu");
        menu.mousePressed(120, 175, GLFW.GLFW_MOUSE_BUTTON_LEFT); menu.mouseReleased(120, 175, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(calls.get() == 1 && !menu.isOpen(), "Click failed to activate the intended row exactly once");
        menu.open(100, 100, items);
        menu.mousePressed(120, 175, GLFW.GLFW_MOUSE_BUTTON_LEFT); menu.mouseReleased(120, 225, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(calls.get() == 1 && menu.isOpen(), "Dragging between menu rows activated an unintended action");
        menu.mousePressed(0, 0, GLFW.GLFW_MOUSE_BUTTON_LEFT); menu.mouseReleased(120, 175, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(calls.get() == 1 && !menu.isOpen(), "Outside press clicked through on release");
        menu.open(100, 100, items); menu.keyPressed(GLFW.GLFW_KEY_ENTER);
        require(calls.get() == 2, "Keyboard initial focus did not skip a disabled action");
        menu.open(100, 100, items); menu.keyPressed(GLFW.GLFW_KEY_UP); menu.keyPressed(GLFW.GLFW_KEY_ENTER);
        require(calls.get() == 12, "Keyboard navigation failed to wrap past disabled actions");
        menu.open(100, 100, items); require(menu.keyPressed(GLFW.GLFW_KEY_ESCAPE) && !menu.isOpen(), "Escape did not dismiss just the menu");
        require(!menu.keyPressed(GLFW.GLFW_KEY_ENTER) && calls.get() == 12, "Closed menu consumed or activated keyboard actions");
        menu.open(100, 100, items); menu.mousePressed(120, 175, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        require(!menu.isOpen() && calls.get() == 12, "Secondary press unexpectedly activated a menu action");
        System.out.println("Music interaction checks passed: " + checks + " assertions; list context, queue editing, stale requests, popup geometry and pointer/keyboard actions.");
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
