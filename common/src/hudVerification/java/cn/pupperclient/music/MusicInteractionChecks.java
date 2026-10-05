package cn.pupperclient.music;

import cn.pupperclient.gui.modmenu.component.MusicPlayerLayout;
import cn.pupperclient.gui.modmenu.component.MusicPopupMenu;
import cn.pupperclient.gui.modmenu.component.MusicSettingsUi;
import cn.pupperclient.gui.modmenu.component.MusicCloudUi;
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
        var popup = MusicPlayerLayout.popup(MusicPlayerLayout.WIDTH - 1, MusicPlayerLayout.HEIGHT - 1, 288, 9);
        require(popup.x() >= 8 && popup.x() + popup.width() <= MusicPlayerLayout.WIDTH - 8
            && popup.y() + popup.height() <= MusicPlayerLayout.transport().y() - 8, "Quality menu crosses window or covers transport");
        layoutChecks();

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
        AtomicInteger choice = new AtomicInteger(-1);
        var many = java.util.stream.IntStream.range(0, 24).mapToObj(i -> new MusicPopupMenu.Item("Playlist " + i, "", true, false, () -> choice.set(i))).toList();
        var longPopup = MusicPlayerLayout.popup(100, 100, 288, 10);
        double firstRowY = longPopup.y() + 32, lastRowY = firstRowY + 9 * 48;
        menu.open(100, 100, many);
        for (int i = 0; i < 18; i++) menu.mouseScrolled(120, firstRowY, -1);
        menu.mousePressed(120, firstRowY, GLFW.GLFW_MOUSE_BUTTON_LEFT); menu.mouseReleased(120, firstRowY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(choice.get() == 14, "Scrolling a long playlist picker activated the wrong song list");
        menu.open(100, 100, many); menu.keyPressed(GLFW.GLFW_KEY_UP); menu.keyPressed(GLFW.GLFW_KEY_ENTER);
        require(choice.get() == 23, "Long-menu keyboard wrap lost the last playlist");
        var disabledPrefix = java.util.stream.IntStream.range(0, 16).mapToObj(i -> new MusicPopupMenu.Item("Playlist " + i, "", i == 15, false, () -> choice.set(i))).toList();
        menu.open(100, 100, disabledPrefix);
        menu.mousePressed(120, lastRowY, GLFW.GLFW_MOUSE_BUTTON_LEFT); menu.mouseReleased(120, lastRowY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(choice.get() == 15 && !menu.isOpen(), "Initial menu focus hid the first enabled action beyond ten rows");
        menu.open(100, 100, many); menu.mousePressed(120, firstRowY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        menu.mouseScrolled(120, firstRowY, -1); menu.mouseReleased(120, firstRowY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(choice.get() == 15 && menu.isOpen(), "Scrolling between press and release activated another playlist");
        System.out.println("Music interaction checks passed: " + checks + " assertions; list context, queue editing, stale requests, popup geometry and pointer/keyboard actions.");
    }
    private static void layoutChecks() {
        var transport = MusicPlayerLayout.transport();
        var cover = MusicPlayerLayout.coverArtwork(transport);
        require(contained(transport, cover) && cover.width() == 48 && cover.height() == 48,
            "Now Playing artwork lost its touch target or extends outside transport");
        var seek = MusicPlayerLayout.seekTrack(transport); var seekHit = MusicPlayerLayout.seekHit(transport);
        require(contained(transport, seekHit) && contained(seekHit, seek)
            && close(seek.x() + seek.width() / 2, transport.x() + transport.width() / 2), "Playback seek line does not match its pointer target");
        var content = MusicPlayerLayout.content(MusicPlayerLayout.Panel.NONE);
        require(content.y() + content.height() < transport.y(), "Song list covers playback controls");
        var account = MusicPlayerLayout.accountButton();
        require(account.y() > MusicPlayerLayout.navBox("audius").y() + 48 && account.y() + account.height() < transport.y(),
            "Account launcher overlaps providers or playback controls");
        List<MusicPlayerLayout.Box> navigation = java.util.stream.Stream.of("search", "library", "liked", "playlists", "cloud", "recent", "downloads", "netease", "audius", "settings", "account")
            .map(MusicPlayerLayout::navBox).toList();
        for (int i = 0; i < navigation.size(); i++) {
            var target = navigation.get(i);
            require(target.height() == 48 && target.x() + target.width() < content.x()
                && target.y() + target.height() < transport.y(), "Compact navigation crosses the browser or playback controls");
            for (int j = i + 1; j < navigation.size(); j++) require(!overlap(target, navigation.get(j)), "Two sidebar actions share a click region");
        }
        for (boolean connection : new boolean[] { false, true }) {
            var controls = java.util.Arrays.stream(MusicSettingsUi.Control.values())
                .filter(control -> MusicSettingsUi.visible(control, connection))
                .map(control -> MusicSettingsUi.control(content, control, connection)).toList();
            for (int i = 0; i < controls.size(); i++) {
                require(contained(content, controls.get(i)), "Settings control crosses its page boundary");
                if (connection) require(!overlap(MusicSettingsUi.endpoint(content), controls.get(i)), "Service input overlaps an action");
                for (int j = i + 1; j < controls.size(); j++) require(!overlap(controls.get(i), controls.get(j)), "Settings actions overlap");
            }
        }
        var preferences = new cn.pupperclient.management.music.MusicExperienceStore.Settings(38, false, -.5f, true, 0, true);
        var cycling = preferences;
        for (int i = 0; i < 3; i++) cycling = MusicSettingsUi.nextTransition(cycling);
        require(cycling.equals(preferences), "Transition presets changed unrelated preferences or failed to cycle");
        require(contained(content, MusicCloudUi.login(content)) && contained(content, MusicCloudUi.more(content))
            && !overlap(MusicCloudUi.tab(content, MusicCloudUi.Tab.CREATED), MusicCloudUi.tab(content, MusicCloudUi.Tab.SUBSCRIBED)),
            "Cloud filters, login or page controls overlap or leave the page");
        var lyrics = MusicPlayerLayout.lyricsButton(); var queue = MusicPlayerLayout.queueButton();
        require(lyrics.width() >= 48 && queue.width() >= 48 && lyrics.x() + lyrics.width() < queue.x()
            && contained(MusicPlayerLayout.transport(), lyrics) && contained(MusicPlayerLayout.transport(), queue),
            "Playback-strip utilities overlap or exceed the transport");
        var dialog = MusicPlayerLayout.nameDialog();
        require(close(dialog.x() + dialog.width() / 2, MusicPlayerLayout.WIDTH / 2)
            && close(dialog.y() + dialog.height() / 2, MusicPlayerLayout.HEIGHT / 2), "Playlist dialog is not centered");
        require(contained(dialog, MusicPlayerLayout.nameInput()) && contained(dialog, MusicPlayerLayout.nameCancel())
            && contained(dialog, MusicPlayerLayout.nameSave()) && MusicPlayerLayout.nameCancel().x() + MusicPlayerLayout.nameCancel().width()
                < MusicPlayerLayout.nameSave().x(), "Playlist input or actions overlap the modal boundary");
        for (int[] size : new int[][] { {800, 600}, {1366, 768}, {1920, 1080}, {3840, 2160}, {3440, 1440}, {1200, 1920} }) {
            for (boolean fullscreen : new boolean[] {false, true}) {
                var viewport = MusicPlayerLayout.fit(size[0], size[1], fullscreen);
                require(viewport.scale() > 0 && viewport.offsetX() >= 0 && viewport.offsetY() >= 0,
                    "Music viewport uses a negative scale or origin");
                require(viewport.screenX(viewport.width()) <= size[0] + .001
                    && viewport.screenY(viewport.height()) <= size[1] + .001, "Music viewport extends outside framebuffer");
                if (fullscreen) {
                    require(viewport.offsetX() == 0 && viewport.offsetY() == 0
                        && close(viewport.screenX(viewport.width()), size[0]) && close(viewport.screenY(viewport.height()), size[1]),
                        "Fullscreen lyrics leave an unused framebuffer margin");
                    var playing = MusicPlayerLayout.nowPlaying(viewport.width(), viewport.height());
                    var frame = new MusicPlayerLayout.Box(0, 0, viewport.width(), viewport.height());
                    for (var box : List.of(playing.artwork(), playing.metadata(), playing.lyrics(), playing.back(), playing.transport()))
                        require(contained(frame, box), "Fullscreen artwork, lyrics or controls exceed framebuffer");
                    require(playing.artwork().x() + playing.artwork().width() < playing.lyrics().x()
                        && !overlap(playing.lyrics(), playing.transport()) && !overlap(playing.artwork(), playing.metadata())
                        && !overlap(playing.metadata(), playing.transport()),
                        "Fullscreen lyrics overlap artwork or transport");
                    for (int action : new int[] {0, 1, 2, 3, 4, 5, 6, 9}) {
                        var hit = MusicPlayerLayout.playbackAction(playing.transport(), action, true);
                        require(contained(playing.transport(), hit), "Immersive action exceeds its control column");
                        require(!overlap(hit, MusicPlayerLayout.seekHit(playing.transport(), true))
                            && !overlap(hit, MusicPlayerLayout.volumeHit(playing.transport(), true)),
                            "Immersive buttons steal slider input");
                    }
                } else {
                    require(viewport.scale() <= 1.35f && viewport.width() == MusicPlayerLayout.WIDTH
                        && viewport.height() == MusicPlayerLayout.HEIGHT, "Main viewport changed logical hit targets or exceeded scale cap");
                    if (size[0] >= 1920 && size[1] >= 1080) require(viewport.scale() > 1, "Large displays keep the player unnecessarily small");
                }
                for (var box : List.of(cover, account, lyrics, queue, MusicPlayerLayout.nameInput(), MusicPlayerLayout.nameSave())) {
                    double centerX = box.x() + box.width() / 2, centerY = box.y() + box.height() / 2;
                    require(close(viewport.localX(viewport.screenX(centerX)), centerX)
                        && close(viewport.localY(viewport.screenY(centerY)), centerY)
                        && box.contains(viewport.localX(viewport.screenX(centerX)), viewport.localY(viewport.screenY(centerY))),
                        "Scaled pointer cannot reach the painted cover, header, account or modal action");
                }
            }
        }
    }
    private static boolean contained(MusicPlayerLayout.Box outer, MusicPlayerLayout.Box inner) {
        return inner.width() > 0 && inner.height() > 0 && inner.x() >= outer.x() && inner.y() >= outer.y()
            && inner.x() + inner.width() <= outer.x() + outer.width() + .001
            && inner.y() + inner.height() <= outer.y() + outer.height() + .001;
    }
    private static boolean close(double first, double second) { return Math.abs(first - second) < .001; }
    private static boolean overlap(MusicPlayerLayout.Box first, MusicPlayerLayout.Box second) {
        return first.x() < second.x() + second.width() && first.x() + first.width() > second.x()
            && first.y() < second.y() + second.height() && first.y() + first.height() > second.y();
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
