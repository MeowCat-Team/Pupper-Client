package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.MusicDownloadTasks;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.theme.MaterialTheme;
import java.util.List;
import java.util.Locale;

/** Stateless download task painters; compact visible controls retain 48-unit pointer targets. */
public final class MusicDownloadsUi {
    public enum Action { RETRY_ALL, CANCEL_ALL, CLEAR }
    public static final float ROW_HEIGHT = 88;
    private MusicDownloadsUi() { }
    public static MusicPlayerLayout.Box body(MusicPlayerLayout.Box box) {
        return new MusicPlayerLayout.Box(box.x(), box.y() + 104, box.width(), Math.max(0, box.height() - 104));
    }
    public static MusicPlayerLayout.Box action(MusicPlayerLayout.Box box, Action action) {
        return new MusicPlayerLayout.Box(box.x() + box.width() - 48 * (3 - action.ordinal()), box.y(), 48, 48);
    }
    public static MusicPlayerLayout.Box rowAction(MusicPlayerLayout.Box box, float rowY) {
        return new MusicPlayerLayout.Box(box.x() + box.width() - 60, rowY + 16, 48, 48);
    }
    public static boolean enabled(Action action, List<MusicDownloadTasks.Task> tasks) {
        return switch (action) {
            case RETRY_ALL -> tasks.stream().anyMatch(task -> task.state() == MusicDownloadTasks.State.FAILED);
            case CANCEL_ALL -> tasks.stream().anyMatch(MusicDownloadTasks.Task::active);
            case CLEAR -> tasks.stream().anyMatch(task -> task.state() == MusicDownloadTasks.State.COMPLETED
                || task.state() == MusicDownloadTasks.State.CANCELLED);
        };
    }
    public static void draw(MusicPlayerLayout.Box box, List<MusicDownloadTasks.Task> tasks, float offset,
            double mx, double my, ColorPalette palette) {
        Skia.drawText(MusicText.get("music.downloads.title"), box.x() + 8, box.y() + 8, palette.getOnSurface(), Fonts.getMedium(22));
        long queued = tasks.stream().filter(task -> task.state() == MusicDownloadTasks.State.QUEUED).count();
        long running = tasks.stream().filter(task -> task.state() == MusicDownloadTasks.State.RUNNING).count();
        long failed = tasks.stream().filter(task -> task.state() == MusicDownloadTasks.State.FAILED).count();
        Skia.drawText(Skia.getLimitText(MusicText.get("music.downloads.summary", queued, running, failed), Fonts.getRegular(14), box.width() - 16),
            box.x() + 8, box.y() + 60, palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        for (Action action : Action.values()) {
            var target = action(box, action); String icon = switch (action) {
                case RETRY_ALL -> Icon.REFRESH; case CANCEL_ALL -> Icon.CLOSE; case CLEAR -> Icon.CLEAR_ALL;
            };
            boolean available = enabled(action, tasks);
            compactIcon(target, icon, available, target.contains(mx, my), palette);
            if (available && target.contains(mx, my)) MusicUi.tooltip(MusicText.get("music.downloads.action." + switch (action) {
                case RETRY_ALL -> "retryall"; case CANCEL_ALL -> "cancelall"; case CLEAR -> "clear";
            }), mx, my + 84, box.x() + box.width(), palette);
        }
        var body = body(box);
        Skia.save();
        try {
            Skia.clip(body.x(), body.y(), body.width(), body.height(), 8);
            if (tasks.isEmpty()) Skia.drawFullCenteredText(MusicText.get("music.downloads.empty"), body.x() + body.width() / 2,
                body.y() + body.height() / 2, palette.getOnSurfaceVariant(), Fonts.getRegular(16));
            for (int i = Math.max(0, (int) (-offset / ROW_HEIGHT)); i < tasks.size(); i++) {
                float rowY = body.y() + i * ROW_HEIGHT + offset;
                if (rowY >= body.y() + body.height()) break;
                row(box, rowY, tasks.get(i), mx, my, palette);
            }
        } finally { Skia.restore(); }
    }
    private static void row(MusicPlayerLayout.Box box, float y, MusicDownloadTasks.Task task,
            double mx, double my, ColorPalette palette) {
        float x = box.x(), width = box.width(), textWidth = Math.max(40, width - 312);
        if (task.active()) Skia.drawRoundedRect(x, y + 2, width, ROW_HEIGHT - 4, 8, MaterialTheme.surface(palette.getSurfaceContainerLow()));
        MusicUi.artwork(null, x + 12, y + 12, 48, palette);
        Skia.drawText(Skia.getLimitText(task.track().title(), Fonts.getMedium(16), textWidth), x + 76, y + 10,
            palette.getOnSurface(), Fonts.getMedium(16));
        String subtitle = task.state() == MusicDownloadTasks.State.FAILED ? MusicText.get(task.errorKey())
            : MusicText.artist(task.track()) + " · " + MusicText.get("music.quality." + task.quality());
        Skia.drawText(Skia.getLimitText(subtitle, Fonts.getRegular(14), textWidth), x + 76, y + 36,
            task.state() == MusicDownloadTasks.State.FAILED ? palette.getError() : palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        String status = MusicText.get("music.downloads.state." + task.state().name().toLowerCase(Locale.ROOT));
        var statusFont = Fonts.getRegular(14);
        Skia.drawText(Skia.getLimitText(status, statusFont, 148), x + width - 220, y + 12,
            task.state() == MusicDownloadTasks.State.FAILED ? palette.getError() : palette.getOnSurfaceVariant(), statusFont);
        if (task.active() || task.state() == MusicDownloadTasks.State.COMPLETED) {
            float progressWidth = Math.max(32, width - 296);
            Skia.drawRoundedRect(x + 76, y + 70, progressWidth, 4, 2, palette.getSurfaceContainerHighest());
            if (task.progress() > 0) Skia.drawRoundedRect(x + 76, y + 70, progressWidth * task.progress() / 100f, 4, 2, palette.getPrimary());
            if (task.state() != MusicDownloadTasks.State.QUEUED) Skia.drawText(MusicText.get("music.downloads.progress", task.progress()),
                x + width - 220, y + 44, palette.getOnSurfaceVariant(), statusFont);
        }
        var target = rowAction(box, y);
        if (task.active() || task.retryable()) compactIcon(target, task.active() ? Icon.CLOSE : Icon.REFRESH, true, target.contains(mx, my), palette);
        else Skia.drawFullCenteredText(Icon.DOWNLOAD_DONE, target.x() + 24, target.y() + 24, palette.getPrimary(), Fonts.getIcon(20));
        if ((task.active() || task.retryable()) && target.contains(mx, my)) MusicUi.tooltip(MusicText.get(task.active()
            ? "music.downloads.action.cancel" : "music.downloads.action.retry"), mx, my, x + width, palette);
        Skia.drawLine(x + 76, y + ROW_HEIGHT - 1, x + width - 12, y + ROW_HEIGHT - 1, 1,
            MaterialTheme.alpha(palette.getOutlineVariant(), .25f));
    }
    private static void compactIcon(MusicPlayerLayout.Box target, String icon, boolean enabled, boolean hover, ColorPalette palette) {
        if (enabled && hover) Skia.drawRoundedRect(target.x() + 4, target.y() + 4, 40, 40, 12,
            MaterialTheme.alpha(palette.getOnSurface(), .08f));
        Skia.drawFullCenteredText(icon, target.x() + 24, target.y() + 24,
            MaterialTheme.alpha(palette.getOnSurfaceVariant(), enabled ? 1 : .38f), Fonts.getIcon(20));
    }
}
