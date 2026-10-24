/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.music.MusicDownloadTasks;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.utils.mouse.ScrollHelper;
import java.util.List;
import java.util.function.LongConsumer;
import org.lwjgl.glfw.GLFW;

/** Task identity, rather than row indices, survives concurrent progress and completion updates. */
public final class MusicDownloadsView extends Component {
    private final MusicDownloadTasks tasks;
    private final LongConsumer retry;
    private final Runnable retryAll;
    private final ScrollHelper scroll = new ScrollHelper();
    private MusicDownloadsUi.Action pressedHeader;
    private long pressedTask = -1;
    private boolean pressedCancel;
    private MusicPlayerLayout.Box pressedBox;
    public MusicDownloadsView(MusicPlayerLayout.Box box, MusicDownloadTasks tasks) {
        this(box, tasks, tasks::retry, tasks::retryFailed);
    }
    public MusicDownloadsView(MusicPlayerLayout.Box box, MusicDownloadTasks tasks, LongConsumer retry, Runnable retryAll) {
        super(box.x(), box.y()); this.tasks = tasks; this.retry = retry; this.retryAll = retryAll; layout(box);
    }
    public void layout(MusicPlayerLayout.Box box) {
        if (x != box.x() || y != box.y() || width != box.width() || height != box.height()) cancelPointer();
        x = box.x(); y = box.y(); width = box.width(); height = box.height();
    }
    private MusicPlayerLayout.Box box() { return new MusicPlayerLayout.Box(x, y, width, height); }
    public void cancelPointer() { pressedHeader = null; pressedTask = -1; pressedBox = null; }
    @Override public void draw(double mx, double my) {
        List<MusicDownloadTasks.Task> snapshot = tasks.list();
        scroll.setMaxScroll(snapshot.size() * MusicDownloadsUi.ROW_HEIGHT, MusicDownloadsUi.body(box()).height()); scroll.onUpdate();
        MusicDownloadsUi.draw(box(), snapshot, scroll.getValue(), mx, my, PupperClient.getInstance().getColorManager().getPalette());
    }
    @Override public void mousePressed(double mx, double my, int button) {
        cancelPointer(); if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        var snapshot = tasks.list(); var bounds = box();
        for (var action : MusicDownloadsUi.Action.values()) {
            var target = MusicDownloadsUi.action(bounds, action);
            if (target.contains(mx, my) && MusicDownloadsUi.enabled(action, snapshot)) {
                pressedHeader = action; pressedBox = target; return;
            }
        }
        var body = MusicDownloadsUi.body(bounds); if (!body.contains(mx, my)) return;
        int index = (int) ((my - body.y() - scroll.getValue()) / MusicDownloadsUi.ROW_HEIGHT);
        if (index < 0 || index >= snapshot.size()) return;
        var task = snapshot.get(index);
        var target = MusicDownloadsUi.rowAction(bounds, body.y() + index * MusicDownloadsUi.ROW_HEIGHT + scroll.getValue());
        if (target.contains(mx, my) && (task.active() || task.retryable())) {
            pressedTask = task.id(); pressedCancel = task.active(); pressedBox = target;
        }
    }
    @Override public void mouseReleased(double mx, double my, int button) {
        var header = pressedHeader; long id = pressedTask; boolean cancel = pressedCancel; var target = pressedBox;
        cancelPointer();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || target == null || !target.contains(mx, my)) return;
        if (header != null) switch (header) {
            case RETRY_ALL -> retryAll.run(); case CANCEL_ALL -> tasks.cancelAll(); case CLEAR -> tasks.clearFinished();
        } else if (id >= 0) {
            // A task that completed after press must not have a newly available Retry activated on release.
            var task = tasks.list().stream().filter(entry -> entry.id() == id).findFirst().orElse(null);
            if (task == null) return;
            if (cancel && task.active()) tasks.cancel(id);
            else if (!cancel && task.retryable()) retry.accept(id);
        }
    }
    @Override public void mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (Double.isFinite(vertical) && MusicDownloadsUi.body(box()).contains(mx, my)) { cancelPointer(); scroll.onScroll(vertical); }
    }
}
