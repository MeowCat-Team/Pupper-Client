package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.MusicCollection;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.theme.MaterialTheme;
import java.io.File;
import java.util.List;
import java.util.function.Function;

/** Read-only cloud playlist rows can be opened in the ordinary collection browser. */
public final class MusicCloudUi {
    public enum Tab { CREATED, SUBSCRIBED }
    public record State(Tab tab, List<MusicCollection> collections, boolean loading, boolean more, boolean guest, String errorKey) {
        public State { collections = List.copyOf(collections); errorKey = errorKey == null ? "" : errorKey; }
    }
    private MusicCloudUi() { }
    public static MusicPlayerLayout.Box tab(MusicPlayerLayout.Box box, Tab tab) { return new MusicPlayerLayout.Box(box.x() + tab.ordinal() * 184, box.y(), 176, 48); }
    public static MusicPlayerLayout.Box retry(MusicPlayerLayout.Box box) { return new MusicPlayerLayout.Box(box.x() + box.width() - 48, box.y(), 48, 48); }
    public static MusicPlayerLayout.Box body(MusicPlayerLayout.Box box) { return new MusicPlayerLayout.Box(box.x(), box.y() + 104, box.width(), Math.max(0, box.height() - 160)); }
    public static MusicPlayerLayout.Box more(MusicPlayerLayout.Box box) { return new MusicPlayerLayout.Box(box.x() + box.width() - 160, box.y() + box.height() - 48, 160, 48); }
    public static MusicPlayerLayout.Box login(MusicPlayerLayout.Box box) { return new MusicPlayerLayout.Box(box.x() + (box.width() - 240) / 2, box.y() + box.height() / 2 + 8, 240, 48); }
    public static void draw(MusicPlayerLayout.Box box, State state, float scroll, double mx, double my, ColorPalette palette) {
        draw(box, state, scroll, mx, my, palette, _ -> null);
    }
    public static void draw(MusicPlayerLayout.Box box, State state, float scroll, double mx, double my,
            ColorPalette palette, Function<MusicCollection, File> covers) {
        for (Tab tab : Tab.values()) {
            var target = tab(box, tab);
            MusicUi.button(target.x(), target.y(), target.width(), MusicText.get(tab == Tab.CREATED ? "music.cloud.created" : "music.cloud.subscribed"),
                state.tab() == tab, target.contains(mx, my), palette);
        }
        var refresh = retry(box);
        MusicUi.iconButton(refresh.x(), refresh.y(), Icon.REFRESH, false, !state.loading() && !state.guest(), refresh.contains(mx, my), palette);
        String caption = state.guest() ? "music.cloud.guest" : state.loading() ? "music.cloud.loading" : state.errorKey();
        if (!caption.isEmpty()) Skia.drawText(Skia.getLimitText(MusicText.get(caption), Fonts.getRegular(16), box.width() - 16),
            box.x() + 8, box.y() + 64, state.errorKey().isEmpty() ? palette.getOnSurfaceVariant() : palette.getError(), Fonts.getRegular(16));
        else Skia.drawText(MusicText.get("music.cloud.count", state.collections().size()), box.x() + 8, box.y() + 64, palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        var body = body(box);
        if (state.guest()) {
            var signIn = login(box);
            MusicUi.button(signIn.x(), signIn.y(), signIn.width(), MusicText.get("music.cloud.signin"), true, signIn.contains(mx, my), palette);
            return;
        }
        Skia.save();
        try {
            Skia.clip(body.x(), body.y(), body.width(), body.height(), 8);
            if (state.collections().isEmpty() && !state.loading() && state.errorKey().isEmpty())
                Skia.drawFullCenteredText(MusicText.get("music.cloud.empty"), body.x() + body.width() / 2, body.y() + body.height() / 2,
                    palette.getOnSurfaceVariant(), Fonts.getRegular(16));
            for (int i = Math.max(0, (int) (-scroll / 64)); i < state.collections().size(); i++) {
                float rowY = body.y() + i * 64 + scroll; if (rowY >= body.y() + body.height()) break;
                var collection = state.collections().get(i);
                MusicUi.collectionRow(body.x(), rowY, body.width(), collection, covers.apply(collection), false, mx, my, palette);
                Skia.drawLine(body.x() + 76, rowY + 63, body.x() + body.width() - 12, rowY + 63, 1,
                    MaterialTheme.alpha(palette.getOutlineVariant(), .2f));
            }
        } finally { Skia.restore(); }
        if (state.more() && !state.loading()) {
            var more = more(box); MusicUi.button(more.x(), more.y(), more.width(), MusicText.get("music.cloud.more"), false, more.contains(mx, my), palette);
        }
        if (!state.errorKey().isEmpty() && !state.loading()) {
            var retry = new MusicPlayerLayout.Box(box.x() + 8, box.y() + box.height() - 48, 160, 48);
            MusicUi.button(retry.x(), retry.y(), retry.width(), MusicText.get("music.cloud.retry"), false, retry.contains(mx, my), palette);
        }
    }
    public static MusicPlayerLayout.Box retryFooter(MusicPlayerLayout.Box box) { return new MusicPlayerLayout.Box(box.x() + 8, box.y() + box.height() - 48, 160, 48); }
}
