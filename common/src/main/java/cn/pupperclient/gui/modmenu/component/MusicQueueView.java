package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.music.MusicQueue;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.utils.mouse.ScrollHelper;
import org.lwjgl.glfw.GLFW;

/** An independent Playing Next panel; revision checks keep drag/remove actions on the intended song. */
public final class MusicQueueView extends Component {
    private final ScrollHelper scroll = new ScrollHelper();
    private int pressed = -1, drop = -1;
    private long revision;
    private double startY;
    private boolean removing, doubled, clearPressed, dragging, addPressed, savePressed;
    private final Runnable add, save;
    public MusicQueueView(MusicPlayerLayout.Box box, Runnable add, Runnable save) {
        super(box.x(), box.y()); width = box.width(); height = box.height();
        this.add = add; this.save = save;
    }
    @Override public void draw(double mx, double my) {
        draw(mx, my, PupperClient.getInstance().getColorManager().getPalette());
    }
    public void layout(MusicPlayerLayout.Box box) {
        if (x != box.x() || y != box.y() || width != box.width() || height != box.height()) {
            pressed = drop = -1; clearPressed = dragging = addPressed = savePressed = false;
        }
        x = box.x(); y = box.y(); width = box.width(); height = box.height();
    }
    public void draw(double mx, double my, cn.pupperclient.management.color.api.ColorPalette palette) {
        var manager = PupperClient.getInstance().getMusicManager();
        var snapshot = manager.getQueue().snapshot();
        var current = snapshot.current();
        var playing = manager.getCurrentMusic();
        MusicUi.queueHeader(x, y, width, current == null ? null : playing != null && current.key().equals(MusicQueue.Entry.of(playing).key())
            ? playing.getTrack() : manager.getService().displayTrack(current.track()), current == null ? null
            : playing != null && current.key().equals(MusicQueue.Entry.of(playing).key()) ? playing.getAlbum()
            : manager.getService().cover(current.track()), snapshot.upcoming().size(), mx, my, palette);
        float top = y + 180, bodyHeight = height - 236;
        scroll.setMaxScroll(snapshot.upcoming().size() * 64, bodyHeight); scroll.onUpdate();
        dragging = pressed >= 0 && !removing && Math.abs(my - startY) > 6;
        drop = dragging && !snapshot.upcoming().isEmpty() && MusicUi.inside(mx, my, x, top, width, bodyHeight)
            ? Math.clamp((int) ((my - top - scroll.getValue()) / 64), 0, snapshot.upcoming().size() - 1) : -1;
        if (snapshot.upcoming().isEmpty()) {
            Skia.drawCenteredText(Skia.getLimitText(MusicText.get("music.queue.empty"), Fonts.getRegular(14), width - 24),
                x + width / 2, top + bodyHeight / 2, palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        } else {
            Skia.save();
            try {
                Skia.clip(x, top, width, bodyHeight, 12);
                for (int i = Math.max(0, (int) (-scroll.getValue() / 64)); i < snapshot.upcoming().size(); i++) {
                    float rowY = top + i * 64 + scroll.getValue(); if (rowY >= top + bodyHeight) break;
                    var entry = snapshot.upcoming().get(i);
                    MusicUi.queueRow(x, rowY, width, manager.getService().displayTrack(entry.track()), manager.getService().cover(entry.track()),
                        i == pressed && snapshot.revision() == revision, mx, my, palette);
                    if (i == drop && snapshot.revision() == revision) Skia.drawLine(x + 8, rowY, x + width - 8, rowY, 2, palette.getPrimary());
                }
            } finally { Skia.restore(); }
        }
        MusicUi.queueActions(x, y, width, height, mx, my, palette);
        if (MusicUi.inside(mx, my, x + width - 48, y, 48, 48))
            MusicUi.tooltip(MusicText.get("music.action.clearqueue"), mx, my, MusicPlayerLayout.WIDTH, palette);
    }
    public void mousePressed(double mx, double my, int button, boolean twice) {
        pressed = -1; dragging = false; doubled = twice;
        clearPressed = button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MusicUi.inside(mx, my, x + width - 48, y, 48, 48);
        addPressed = button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MusicUi.inside(mx, my, x, y + height - 48, (width - 8) / 2, 48);
        savePressed = button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MusicUi.inside(mx, my, x + (width + 8) / 2, y + height - 48, (width - 8) / 2, 48);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !MusicUi.inside(mx, my, x, y + 180, width, height - 236)) return;
        var snapshot = PupperClient.getInstance().getMusicManager().getQueue().snapshot();
        int index = (int) ((my - y - 180 - scroll.getValue()) / 64);
        if (index < 0 || index >= snapshot.upcoming().size()) return;
        pressed = index; revision = snapshot.revision(); startY = my; removing = mx >= x + width - 48;
    }
    @Override public void mouseReleased(double mx, double my, int button) {
        var manager = PupperClient.getInstance().getMusicManager();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (clearPressed && MusicUi.inside(mx, my, x + width - 48, y, 48, 48)) manager.getQueue().clear();
            if (addPressed && MusicUi.inside(mx, my, x, y + height - 48, (width - 8) / 2, 48)) add.run();
            if (savePressed && MusicUi.inside(mx, my, x + (width + 8) / 2, y + height - 48, (width - 8) / 2, 48)) save.run();
            if (pressed >= 0 && MusicUi.inside(mx, my, x, y + 180, width, height - 236)) {
                int index = (int) ((my - y - 180 - scroll.getValue()) / 64);
                if (removing && index == pressed && mx >= x + width - 48) manager.getQueue().remove(pressed, revision);
                else if (!removing && Math.abs(my - startY) > 6 && !manager.getQueue().snapshot().upcoming().isEmpty()) manager.getQueue().move(pressed,
                    Math.clamp(index, 0, manager.getQueue().snapshot().upcoming().size() - 1), revision);
                else if (doubled && index == pressed && mx < x + width - 48) manager.jumpQueue(pressed, revision);
            }
        }
        pressed = drop = -1; clearPressed = dragging = addPressed = savePressed = false;
    }
    @Override public void mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (MusicUi.inside(mx, my, x, y + 180, width, height - 236)) scroll.onScroll(vertical);
    }
}
