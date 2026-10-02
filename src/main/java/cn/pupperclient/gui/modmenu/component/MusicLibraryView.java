package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.*;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.impl.text.SearchBar;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.utils.mouse.ScrollHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.lwjgl.glfw.GLFW;

/** Desktop browsing: select a row, play its artwork/double-click, and use explicit contextual actions. */
public final class MusicLibraryView extends Component {
    public enum Tab { LIBRARY, SEARCH, LIKED }
    private record Row(MusicTrack track, Music local, String filename) {
        MusicQueue.Entry entry() { return new MusicQueue.Entry(track, filename); }
    }
    private record Press(float x, float y, float width, float height, Runnable action) {
        boolean contains(double mx, double my) { return MusicUi.inside(mx, my, x, y, width, height); }
    }
    private static final float ROW_HEIGHT = 64;
    private final MusicManager manager = PupperClient.getInstance().getMusicManager();
    private final MusicService service = manager.getService();
    private final MusicPopupMenu menu;
    private final SearchBar search;
    private final ScrollHelper scroll = new ScrollHelper();
    private final List<MusicTrack> results = new ArrayList<>();
    private final String[] queries = { "", "", "" };
    private Tab tab;
    private Press pressed;
    private int requestGeneration, total, nextOffset;
    private boolean searching, refreshing, disposed;
    private long queryChanged;
    private String submittedQuery = "", observedQuery = "", selected = "";
    private String statusKey = "music.status.ready";
    private Object[] statusArguments = new Object[0];
    private String quality = service.provider().defaultQuality(), previousProvider = service.provider().id();

    public MusicLibraryView(MusicPlayerLayout.Box bounds, Tab initial, MusicPopupMenu menu) {
        super(bounds.x(), bounds.y());
        this.menu = menu; tab = initial;
        search = new SearchBar(x, y, bounds.width() - 56, "", scroll::reset);
        layout(bounds); refresh();
    }
    public Tab tab() { return tab; }
    public String quality() { return quality; }
    public void layout(MusicPlayerLayout.Box bounds) {
        x = bounds.x(); y = bounds.y(); width = bounds.width(); height = bounds.height();
        search.setX(x); search.setY(y); search.setWidth(width - 56);
    }
    public void navigate(Tab next) {
        if (tab != next) {
            queries[tab.ordinal()] = search.getText(); tab = next; search.setText(queries[next.ordinal()]);
            observedQuery = search.getText(); selected = ""; scroll.reset(); status("music.status.ready");
            if (next == Tab.LIKED && service.loggedIn("netease")) syncLikes();
        }
        if (next == Tab.SEARCH) search.keyPressed(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL);
    }
    public void selectProvider(String id) {
        if (service.provider().id().equals(id)) return;
        try { service.selectProvider(id); providerChanged(); }
        catch (MusicError failure) { error(failure); }
        catch (java.io.IOException failure) { error(new MusicError("music.error.file")); }
    }
    public void openQuality(double mx, double my) {
        menu.open(mx, my, service.qualities().stream().map(level -> new MusicPopupMenu.Item(
            MusicText.get("music.quality." + level), Icon.GRAPHIC_EQ, true, quality.equals(level), () -> quality = level)).toList());
    }
    private void updateQuery() {
        if (observedQuery.equals(search.getText())) return;
        observedQuery = search.getText(); selected = ""; scroll.reset(); queryChanged = System.nanoTime();
        if (tab == Tab.SEARCH) {
            requestGeneration++; searching = false; results.clear(); total = nextOffset = 0; submittedQuery = "";
            status(search.getText().isBlank() ? "music.status.ready" : "music.search.typing");
        }
    }
    @Override public void draw(double mx, double my) {
        if (!previousProvider.equals(service.provider().id())) providerChanged();
        updateQuery();
        if (tab == Tab.SEARCH && !search.getText().isBlank() && !searching && !search.getText().strip().equals(submittedQuery)
                && System.nanoTime() - queryChanged >= 350_000_000L) search(false);
        ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
        search.setHintText(tab == Tab.SEARCH ? "music.search.hint" : "music.filter.hint"); search.draw(mx, my);
        MusicUi.iconButton(x + width - 48, y - 3, tab == Tab.SEARCH ? Icon.SEARCH : Icon.REFRESH, false,
            !(searching || refreshing), MusicUi.inside(mx, my, x + width - 48, y - 3, 48, 48), palette);
        List<Row> rows = rows();
        String heading = tab == Tab.SEARCH ? submittedQuery.isBlank() ? MusicText.get("music.discover")
            : MusicText.get(total < 0 ? "music.results.query" : "music.results.for", submittedQuery, total)
            : MusicText.get("music.tracks", rows.size());
        Skia.drawText(Skia.getLimitText(heading, Fonts.getRegular(13), width - 16), x + 8, y + 54,
            palette.getOnSurfaceVariant(), Fonts.getRegular(13));
        float top = y + 80, listHeight = height - 132;
        scroll.setMaxScroll(rows.size() * ROW_HEIGHT, listHeight); scroll.onUpdate();
        if (rows.isEmpty()) {
            String key = searching ? "music.status.searching" : tab == Tab.SEARCH ? submittedQuery.isBlank()
                ? "music.empty.search" : "music.empty.results" : tab == Tab.LIKED ? "music.empty.liked" : "music.empty.library";
            Skia.drawFullCenteredText(tab == Tab.SEARCH ? Icon.SEARCH : Icon.LIBRARY_MUSIC, x + width / 2,
                top + listHeight / 2 - 28, palette.getPrimary(), Fonts.getIcon(32));
            Skia.drawCenteredText(MusicText.get(key), x + width / 2, top + listHeight / 2 + 24,
                palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        } else {
            Skia.save();
            try {
                Skia.clip(x, top, width, listHeight, 12);
                for (int i = Math.max(0, (int) (-scroll.getValue() / ROW_HEIGHT)); i < rows.size(); i++) {
                    float rowY = top + i * ROW_HEIGHT + scroll.getValue(); if (rowY >= top + listHeight) break;
                    Row row = rows.get(i); Music playing = manager.getCurrentMusic();
                    boolean active = playing != null && row.entry().key().equals(MusicQueue.Entry.of(playing).key());
                    File cover = row.local() != null && row.local().getAlbum() != null ? row.local().getAlbum() : service.cover(row.track());
                    String subtitle = row.track().artist().isBlank() ? MusicText.get("music.artist.unknown") : row.track().artist();
                    if (!row.track().album().isBlank()) subtitle += " · " + row.track().album();
                    MusicUi.songRow(x, rowY, width, cover, row.track().title(), subtitle,
                        row.track().durationMillis() > 0 ? MusicText.time(row.track().durationMillis() / 1000f) : "—",
                        selected.equals(row.entry().key()), active, manager.isPlaying(), service.isLiked(row.track(), row.filename()),
                        row.entry().playable(), !service.favoritesBusy(row.track()), service.downloadProgress(row.track()), mx, my, palette);
                }
            } finally { Skia.restore(); }
        }
        String status = tab == Tab.LIKED && statusKey.equals("music.status.ready") ? MusicText.get("music.favorite.mixed")
            : MusicText.get(statusKey, statusArguments);
        Skia.drawHeightCenteredText(Skia.getLimitText(status, Fonts.getRegular(12), width - (hasMore() ? 148 : 16)),
            x + 8, y + height - 24, statusKey.startsWith("music.error") ? palette.getError() : palette.getOnSurfaceVariant(), Fonts.getRegular(12));
        if (hasMore()) MusicUi.button(x + width - 136, y + height - 48, 136, MusicText.get("music.action.more"), false,
            MusicUi.inside(mx, my, x + width - 136, y + height - 48, 136, 48), palette);
    }
    private List<Row> rows() {
        List<Row> rows;
        if (tab == Tab.SEARCH) rows = results.stream().map(t -> row(t, "")).toList();
        else if (tab == Tab.LIKED) rows = service.favorites().stream().map(f -> row(f.track(), f.filename())).toList();
        else rows = manager.getMusics().stream().map(m -> new Row(m.getTrack(), m, m.getAudio().getName())).toList();
        if (tab == Tab.SEARCH || search.getText().isBlank()) return rows;
        String filter = search.getText().strip().toLowerCase(Locale.ROOT);
        return rows.stream().filter(r -> (r.track().title() + " " + r.track().artist() + " " + r.track().album())
            .toLowerCase(Locale.ROOT).contains(filter)).toList();
    }
    private Row row(MusicTrack track, String filename) {
        Music local = track.remote() ? service.local(track) : manager.getMusics().stream()
            .filter(m -> m.getAudio().getName().equals(filename)).findFirst().orElse(null);
        return new Row(track, local, local == null ? filename : local.getAudio().getName());
    }
    @Override public void mousePressed(double mx, double my, int button) { mousePressed(mx, my, button, false); }
    public void mousePressed(double mx, double my, int button, boolean doubled) {
        search.mousePressed(mx, my, button); pressed = null;
        if (MusicUi.inside(mx, my, x + width - 48, y - 3, 48, 48) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressed = new Press(x + width - 48, y - 3, 48, 48, () -> { if (tab == Tab.SEARCH) search(false); else refresh(); }); return;
        }
        if (hasMore() && MusicUi.inside(mx, my, x + width - 136, y + height - 48, 136, 48)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) pressed = new Press(x + width - 136, y + height - 48, 136, 48, () -> search(true));
            return;
        }
        if (!MusicUi.inside(mx, my, x, y + 80, width, height - 132)) return;
        List<Row> rows = rows();
        int index = (int) ((my - y - 80 - scroll.getValue()) / ROW_HEIGHT);
        if (index < 0 || index >= rows.size()) return;
        Row row = rows.get(index); selected = row.entry().key();
        float rowY = y + 80 + index * ROW_HEIGHT + scroll.getValue();
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { openRowMenu(row, mx, my); return; }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (mx >= x + width - 56) pressed = new Press(x + width - 56, rowY + 8, 48, 48, () -> openRowMenu(row, x + width - 288, rowY + 48));
        else if (mx >= x + width - 108) pressed = new Press(x + width - 108, rowY + 8, 48, 48, () -> like(row));
        else if (mx < x + 64) pressed = new Press(x + 8, rowY + 8, 48, 48, () -> play(row, true));
        else if (doubled) pressed = new Press(x + 64, rowY, width - 172, 64, () -> play(row, false));
    }
    @Override public void mouseReleased(double mx, double my, int button) {
        Press press = pressed; pressed = null;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && press != null && press.contains(mx, my)) press.action().run();
    }
    private void openRowMenu(Row row, double mx, double my) {
        boolean liked = service.isLiked(row.track(), row.filename());
        menu.open(mx, my, List.of(
            item("music.action.play", Icon.PLAY_ARROW, row.entry().playable(), () -> play(row, false)),
            item("music.action.playnext", Icon.PLAYLIST_PLAY, row.entry().playable(), () -> enqueue(row, true)),
            item("music.action.playlast", Icon.PLAYLIST_ADD, row.entry().playable(), () -> enqueue(row, false)),
            item(liked ? "music.action.unlike" : "music.action.like", Icon.FAVORITE, !service.favoritesBusy(row.track()), () -> like(row)),
            item(row.local() != null ? "music.action.saved" : "music.action.download", Icon.DOWNLOAD,
                row.local() == null && row.track().remote() && row.track().downloadable() && service.downloadProgress(row.track()) < 0, () -> download(row))));
    }
    private MusicPopupMenu.Item item(String key, String icon, boolean enabled, Runnable action) {
        return new MusicPopupMenu.Item(MusicText.get(key), icon, enabled, false, action);
    }
    private void enqueue(Row row, boolean next) {
        manager.getQueue().enqueue(row.entry(), next); status(next ? "music.status.queuednext" : "music.status.queuedlast", row.track().title());
    }
    private void play(Row row, boolean toggle) {
        if (!row.entry().playable()) { error(new MusicError("music.error.playrestricted")); return; }
        if (toggle && manager.getCurrentMusic() != null && row.entry().key().equals(MusicQueue.Entry.of(manager.getCurrentMusic()).key())) {
            manager.switchPlayBack(); return;
        }
        List<MusicQueue.Entry> entries = rows().stream().map(Row::entry).filter(MusicQueue.Entry::playable).toList();
        int index = entries.indexOf(row.entry()); if (index < 0) return;
        status("music.status.loadingtrack", row.track().title());
        manager.playFrom(entries, index, quality(row), music -> { if (!disposed) status("music.status.playing", music.getTitle()); }, this::error);
    }
    private void like(Row row) {
        service.toggleLike(row.track(), row.filename(), liked -> { if (!disposed) status(liked ? "music.status.liked" : "music.status.unliked", row.track().title()); }, this::error);
    }
    private void download(Row row) {
        status("music.status.downloadtrack", row.track().title());
        service.download(row.track(), quality(row), false, music -> { if (!disposed) status("music.status.downloaded", music.getTitle()); }, this::error);
    }
    private String quality(Row row) {
        return row.track().provider().equals(service.provider().id()) ? quality : service.defaultQuality(row.track());
    }
    private void search(boolean more) {
        updateQuery();
        String query = more ? submittedQuery : search.getText().strip(); if (query.isBlank() || searching && more) return;
        int generation = ++requestGeneration, offset = more ? nextOffset : 0;
        if (!more) { results.clear(); total = nextOffset = 0; selected = ""; submittedQuery = query; scroll.reset(); }
        searching = true; status("music.status.searching");
        service.search(query, offset, result -> {
            if (disposed || generation != requestGeneration) return;
            searching = false; results.addAll(result.tracks()); nextOffset = result.nextOffset();
            total = result.tracks().isEmpty() ? results.size() : result.total();
            if (tab == Tab.SEARCH) status(total < 0 ? "music.results.loaded" : "music.results.count", total < 0 ? results.size() : total);
        }, failure -> { if (!disposed && generation == requestGeneration) { searching = false; error(failure); } });
    }
    private void refresh() {
        if (refreshing) return; refreshing = true; status("music.status.refreshing");
        service.refresh(repaired -> { if (!disposed) { refreshing = false; status("music.status.refreshed"); } }, failure -> { refreshing = false; error(failure); });
    }
    private void syncLikes() { status("music.status.syncing"); service.syncLikes("netease", count -> { if (!disposed) status("music.status.synced", count); }, this::error); }
    private boolean hasMore() { return tab == Tab.SEARCH && !results.isEmpty() && (total < 0 || nextOffset < total); }
    private void providerChanged() {
        previousProvider = service.provider().id(); requestGeneration++; searching = false; results.clear(); total = nextOffset = 0;
        submittedQuery = selected = ""; scroll.reset(); quality = service.provider().defaultQuality(); queryChanged = System.nanoTime();
        status("music.provider.selected", MusicText.get(service.provider().nameKey()));
        if (tab == Tab.SEARCH && !search.getText().isBlank()) search(false);
    }
    public void dispose() { disposed = true; requestGeneration++; pressed = null; menu.close(); }
    public boolean isInputFocused() { return search.isFocused(); }
    private void error(MusicError error) { if (!disposed) status(error.key()); }
    private void status(String key, Object... arguments) { statusKey = key; statusArguments = arguments; }
    @Override public void mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (MusicUi.inside(mx, my, x, y + 80, width, height - 132)) scroll.onScroll(vertical);
    }
    @Override public void charTyped(int chr) { search.charTyped(chr); updateQuery(); }
    @Override public void keyPressed(int key, int scancode, int modifiers) {
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { if (tab == Tab.SEARCH) search(false); }
            else search.keyPressed(key, scancode, modifiers);
            updateQuery(); return;
        }
        List<Row> rows = rows();
        int index = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).entry().key().equals(selected)) { index = i; break; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            if (rows.isEmpty()) return;
            index = Math.clamp(index + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, rows.size() - 1);
            selected = rows.get(index).entry().key();
            float top = index * ROW_HEIGHT + scroll.getValue();
            if (top < 0) scroll.onScroll(-top / 60); else if (top + ROW_HEIGHT > height - 132) scroll.onScroll(-(top + ROW_HEIGHT - height + 132) / 60);
        } else if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && index >= 0) play(rows.get(index), false);
        else search.keyPressed(key, scancode, modifiers);
    }
}
