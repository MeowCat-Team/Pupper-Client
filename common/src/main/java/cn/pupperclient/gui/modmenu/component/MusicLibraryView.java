package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.*;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.impl.text.SearchBar;
import cn.pupperclient.utils.mouse.ScrollHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.lwjgl.glfw.GLFW;

/** Desktop browsing: select a row, play its artwork/double-click, and use explicit contextual actions. */
public final class MusicLibraryView extends Component {
    public enum Tab { LIBRARY, SEARCH, LIKED, PLAYLISTS, PLAYLIST, BROWSE }
    private record Row(MusicTrack track, Music local, String filename, int occurrence) {
        Row(MusicTrack track, Music local, String filename) { this(track, local, filename, 0); }
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
    private final MusicSearchState browser = new MusicSearchState(service.provider().id());
    private final String[] queries = new String[Tab.values().length];
    private final MusicNameDialog dialog = new MusicNameDialog();
    private String playlistId = "";
    private long statusChanged;
    private Tab tab;
    private Press pressed;
    private boolean refreshing, disposed;
    private long queryChanged;
    private String observedQuery = "", selected = "";
    private String statusKey = "music.status.ready";
    private Object[] statusArguments = new Object[0];
    private String quality = service.provider().defaultQuality(), previousProvider = service.provider().id();

    public MusicLibraryView(MusicPlayerLayout.Box bounds, Tab initial, MusicPopupMenu menu) {
        super(bounds.x(), bounds.y());
        this.menu = menu; tab = initial; java.util.Arrays.fill(queries, "");
        search = new SearchBar(x, y, bounds.width() - 56, "", scroll::reset);
        layout(bounds); refresh();
    }
    public Tab tab() { return tab; }
    public String heading() { return tab == Tab.BROWSE && browser.collection() != null ? MusicText.get(browser.collection().type().nameKey())
        : tab == Tab.PLAYLIST && manager.getLibrary().playlist(playlistId) != null
        ? manager.getLibrary().playlist(playlistId).name() : MusicText.get("music.tab." + tab.name().toLowerCase(Locale.ROOT)); }
    public boolean dialogOpen() { return dialog.isOpen(); }
    public void drawDialog(double mx, double my) { dialog.draw(mx, my); }
    public String quality() { return quality; }
    public void layout(MusicPlayerLayout.Box bounds) {
        x = bounds.x(); y = bounds.y(); width = bounds.width(); height = bounds.height();
        search.setX(x); search.setY(y); search.setWidth(width - 56);
    }
    public void navigate(Tab next) {
        if (tab != next) {
            browser.cancel(); if (next == Tab.SEARCH) browser.back();
            queries[tab.ordinal()] = search.getText(); tab = next; search.setText(queries[next.ordinal()]);
            observedQuery = search.getText(); selected = ""; scroll.reset(); status("music.status.ready");
            if (next == Tab.LIKED && service.loggedIn("netease")) syncLikes();
        }
        if (next == Tab.SEARCH) search.keyPressed(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL);
    }
    public boolean back() { if (tab != Tab.BROWSE) return false; navigate(Tab.SEARCH); return true; }
    private void selectSearchType(MusicSearchType type) {
        if (!browser.reset(service.provider().id(), type, search.getText())) return;
        selected = ""; scroll.reset(); status("music.status.ready");
        if (!search.getText().isBlank()) search(false);
    }
    private void openCollection(MusicCollection collection) {
        navigate(Tab.BROWSE); browser.open(collection);
        search.setText(""); observedQuery = ""; selected = ""; scroll.reset(); search(false);
    }
    private void openPlaylist(String id) {
        playlistId = id; navigate(Tab.PLAYLIST); search.setText(""); observedQuery = ""; scroll.reset();
    }
    public void createPlaylist() { createPlaylist(List.of()); }
    private void createPlaylist(Row song) {
        createPlaylist(List.of(song.entry()));
    }
    public void saveQueue() {
        var snapshot = manager.getQueue().snapshot();
        var entries = new ArrayList<MusicQueue.Entry>();
        if (snapshot.current() != null) entries.add(snapshot.current()); entries.addAll(snapshot.upcoming());
        entries.replaceAll(entry -> new MusicQueue.Entry(service.displayTrack(entry.track()), entry.filename()));
        createPlaylist(entries);
    }
    private void createPlaylist(List<MusicQueue.Entry> entries) {
        createPlaylist(entries, MusicText.get("music.playlist.default"));
    }
    private void createPlaylist(List<MusicQueue.Entry> entries, String suggestedName) {
        dialog.open("music.playlist.create", suggestedName, name -> {
            try {
                var playlist = manager.getLibrary().createPlaylist(name, entries);
                openPlaylist(playlist.id()); return "";
            } catch (java.io.IOException failure) { return "music.error.file"; }
        });
    }
    private List<MusicLibraryStore.Playlist> playlists() {
        String filter = search.getText().strip().toLowerCase(Locale.ROOT);
        return manager.getLibrary().playlists().stream().filter(p -> p.name().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }
    private void drawPlaylists(double mx, double my, ColorPalette palette) {
        var playlists = playlists(); float top = y + 80, bodyHeight = height - 132;
        scroll.setMaxScroll(playlists.size() * ROW_HEIGHT, bodyHeight); scroll.onUpdate();
        if (playlists.isEmpty()) Skia.drawCenteredText(MusicText.get("music.playlist.none"), x + width / 2, top + bodyHeight / 2,
            palette.getOnSurfaceVariant(), Fonts.getRegular(16));
        Skia.save();
        try {
            Skia.clip(x, top, width, bodyHeight, 12);
            for (int i = Math.max(0, (int) (-scroll.getValue() / ROW_HEIGHT)); i < playlists.size(); i++) {
                float rowY = top + i * ROW_HEIGHT + scroll.getValue(); if (rowY >= top + bodyHeight) break;
                var playlist = playlists.get(i);
                MusicUi.playlistRow(x, rowY, width, playlist.name(), playlist.entries().size(), selected.equals(playlist.id()), mx, my, palette);
            }
        } finally { Skia.restore(); }
    }
    private void choosePlaylist(Row row, double mx, double my) {
        var choices = new ArrayList<MusicPopupMenu.Item>();
        choices.add(item("music.playlist.create", Icon.ADD, true, () -> createPlaylist(row)));
        manager.getLibrary().playlists().forEach(playlist -> choices.add(new MusicPopupMenu.Item(playlist.name(), Icon.QUEUE_MUSIC, true, false,
            () -> editPlaylist(() -> { manager.getLibrary().addToPlaylist(playlist.id(), List.of(row.entry())); status("music.playlist.added", playlist.name()); }))));
        menu.open(mx, my, choices);
    }
    private void openPlaylistMenu(String id, double mx, double my) {
        var playlist = manager.getLibrary().playlist(id); if (playlist == null) return;
        List<MusicQueue.Entry> playable = playlist.entries().stream().filter(MusicQueue.Entry::playable).toList();
        menu.open(mx, my, List.of(
            item("music.action.play", Icon.PLAY_ARROW, !playable.isEmpty(), () -> manager.playFrom(playable, 0, null, _ -> {}, this::error)),
            item("music.action.playlast", Icon.PLAYLIST_ADD, !playable.isEmpty(), () -> playable.forEach(entry -> manager.getQueue().enqueue(entry, false))),
            item("music.playlist.rename", Icon.EDIT, true, () -> dialog.open("music.playlist.rename", playlist.name(), name -> {
                try { manager.getLibrary().renamePlaylist(id, name); return ""; }
                catch (java.io.IOException | IllegalArgumentException failure) { return "music.error.file"; }
            })),
            item("music.playlist.delete", Icon.DELETE, true, () -> menu.open(mx, my, List.of(
                item("music.action.cancel", Icon.CLOSE, true, () -> {}),
                item("music.playlist.deleteconfirm", Icon.DELETE, true, () -> editPlaylist(() -> manager.getLibrary().deletePlaylist(id))))))));
    }
    @FunctionalInterface private interface PlaylistEdit { void run() throws java.io.IOException; }
    private void editPlaylist(PlaylistEdit edit) {
        try { edit.run(); }
        catch (java.io.IOException | IllegalArgumentException failure) { error(new MusicError("music.error.file")); }
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
            browser.reset(service.provider().id(), browser.type(), search.getText());
            status(search.getText().isBlank() ? "music.status.ready" : "music.search.typing");
        }
    }
    @Override public void draw(double mx, double my) {
        if (tab == Tab.PLAYLIST && manager.getLibrary().playlist(playlistId) == null) navigate(Tab.PLAYLISTS);
        if (!previousProvider.equals(service.provider().id())) providerChanged();
        updateQuery();
        if (tab == Tab.SEARCH && !search.getText().isBlank() && !browser.loading() && !browser.searched()
                && System.nanoTime() - queryChanged >= 350_000_000L) search(false);
        ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
        search.setX(tab == Tab.BROWSE ? x + 56 : x);
        search.setWidth(width - (tab == Tab.PLAYLISTS ? 176 : tab == Tab.PLAYLIST ? 112 : 56));
        search.setHintText(tab == Tab.SEARCH ? "music.search.hint." + browser.type().name().toLowerCase(Locale.ROOT)
            : tab == Tab.PLAYLISTS ? "music.playlist.filter" : "music.filter.hint"); search.draw(mx, my);
        if (tab == Tab.PLAYLISTS) MusicUi.button(x + width - 160, y - 3, 160, MusicText.get("music.playlist.create"), true,
            MusicUi.inside(mx, my, x + width - 160, y - 3, 160, 48), palette);
        else if (tab == Tab.BROWSE) {
            MusicUi.iconButton(x, y - 3, Icon.ARROW_BACK, false, true, MusicUi.inside(mx, my, x, y - 3, 48, 48), palette);
            var collection = browser.collection();
            if (collection != null) MusicUi.collectionHeader(x, y, width, collection, service.cover(collection), browser.tracks().size(),
                rows().stream().anyMatch(row -> row.entry().playable()), mx, my, palette);
        } else {
            MusicUi.iconButton(x + width - 48, y - 3, tab == Tab.SEARCH ? Icon.SEARCH : tab == Tab.PLAYLIST ? Icon.MORE_HORIZ : Icon.REFRESH, false,
                !(browser.loading() || refreshing), MusicUi.inside(mx, my, x + width - 48, y - 3, 48, 48), palette);
            if (tab == Tab.PLAYLIST) MusicUi.iconButton(x + width - 104, y - 3, Icon.PLAY_ARROW, false, rows().stream().anyMatch(row -> row.entry().playable()),
                MusicUi.inside(mx, my, x + width - 104, y - 3, 48, 48), palette);
        }
        if (tab == Tab.PLAYLISTS) { drawPlaylists(mx, my, palette); drawStatus(palette); return; }
        List<Row> rows = rows();
        if (tab == Tab.SEARCH) MusicUi.searchTypes(x, y, browser.type(), mx, my, palette);
        String heading = tab == Tab.SEARCH ? !browser.searched() ? ""
            : MusicText.get(browser.total() < 0 ? "music.search.results.query" : "music.search.results.for", browser.query(),
                MusicText.get(browser.type().nameKey()), browser.total())
            : MusicText.get("music.tracks", rows.size());
        if (tab != Tab.BROWSE) Skia.drawText(Skia.getLimitText(heading, Fonts.getRegular(14), width - 16), x + 8, y + listOffset() - 26,
            palette.getOnSurfaceVariant(), Fonts.getRegular(14));
        if (tab == Tab.SEARCH && browser.type() != MusicSearchType.SONGS) { drawCollections(mx, my, palette); drawStatus(palette); drawMore(mx, my, palette); return; }
        float top = y + listOffset(), listHeight = listHeight();
        scroll.setMaxScroll(rows.size() * ROW_HEIGHT, listHeight); scroll.onUpdate();
        if (rows.isEmpty()) {
            String key = browser.loading() ? tab == Tab.BROWSE ? "music.browse.loading" : "music.status.searching"
                : tab == Tab.SEARCH ? !browser.searched() ? "music.empty.search" : "music.empty.results"
                : tab == Tab.BROWSE ? "music.browse.empty" : tab == Tab.LIKED ? "music.empty.liked" : tab == Tab.PLAYLIST ? "music.playlist.empty" : "music.empty.library";
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
                    MusicUi.songRow(x, rowY, width, cover, row.track().title(), subtitle, MusicText.access(row.track()),
                        row.track().durationMillis() > 0 ? MusicText.time(row.track().durationMillis() / 1000f) : "—",
                        selected.equals(row.entry().key()), active, manager.isPlaying(), service.isLiked(row.track(), row.filename()),
                        row.entry().playable(), !service.favoritesBusy(row.track()), service.downloadProgress(row.track()), mx, my, palette);
                }
            } finally { Skia.restore(); }
        }
        drawStatus(palette);
        drawMore(mx, my, palette);
    }
    private void drawMore(double mx, double my, ColorPalette palette) {
        if (hasMore()) MusicUi.button(x + width - 136, y + height - 48, 136, MusicText.get("music.action.more"), false,
            MusicUi.inside(mx, my, x + width - 136, y + height - 48, 136, 48), palette);
    }
    private float listOffset() { return MusicPlayerLayout.listOffset(tab == Tab.SEARCH, tab == Tab.BROWSE); }
    private float listHeight() { return height - listOffset() - 52; }
    private void drawCollections(double mx, double my, ColorPalette palette) {
        float top = y + listOffset(), bodyHeight = listHeight(); var collections = browser.collections();
        scroll.setMaxScroll(collections.size() * ROW_HEIGHT, bodyHeight); scroll.onUpdate();
        if (collections.isEmpty()) Skia.drawCenteredText(MusicText.get(browser.loading() ? "music.status.searching"
            : browser.searched() ? "music.empty.results" : "music.empty.search"), x + width / 2, top + bodyHeight / 2,
            palette.getOnSurfaceVariant(), Fonts.getRegular(16));
        Skia.save();
        try {
            Skia.clip(x, top, width, bodyHeight, 12);
            for (int i = Math.max(0, (int) (-scroll.getValue() / ROW_HEIGHT)); i < collections.size(); i++) {
                float rowY = top + i * ROW_HEIGHT + scroll.getValue(); if (rowY >= top + bodyHeight) break;
                var collection = collections.get(i);
                MusicUi.collectionRow(x, rowY, width, collection, service.cover(collection), selected.equals(collection.key()), mx, my, palette);
            }
        } finally { Skia.restore(); }
    }
    private void drawStatus(ColorPalette palette) {
        if (!statusKey.equals("music.status.ready") && (statusKey.startsWith("music.error") || browser.loading() || refreshing
                || System.nanoTime() - statusChanged < 4_000_000_000L))
            Skia.drawHeightCenteredText(Skia.getLimitText(MusicText.get(statusKey, statusArguments), Fonts.getRegular(14), width - (hasMore() ? 148 : 16)),
                x + 8, y + height - 24, statusKey.startsWith("music.error") ? palette.getError() : palette.getOnSurfaceVariant(), Fonts.getRegular(14));
    }
    private List<Row> rows() {
        List<Row> rows;
        if (tab == Tab.SEARCH || tab == Tab.BROWSE) rows = browser.tracks().stream().map(t -> row(t, "")).toList();
        else if (tab == Tab.LIKED) rows = service.favorites().stream().map(f -> row(f.track(), f.filename())).toList();
        else if (tab == Tab.PLAYLIST) {
            var playlist = manager.getLibrary().playlist(playlistId);
            rows = playlist == null ? List.of() : playlist.entries().stream().map(entry -> row(entry.track(), entry.filename())).toList();
        } else rows = manager.getMusics().stream().map(m -> new Row(m.getTrack(), m, m.getAudio().getName())).toList();
        if (tab != Tab.SEARCH && !search.getText().isBlank()) {
            String filter = search.getText().strip().toLowerCase(Locale.ROOT);
            rows = rows.stream().filter(r -> (r.track().title() + " " + r.track().artist() + " " + r.track().album())
                .toLowerCase(Locale.ROOT).contains(filter)).toList();
        }
        var numbered = new ArrayList<Row>();
        for (int i = 0; i < rows.size(); i++) { Row row = rows.get(i); numbered.add(new Row(row.track(), row.local(), row.filename(), i)); }
        return List.copyOf(numbered);
    }
    private Row row(MusicTrack track, String filename) {
        Music local = track.remote() ? service.local(track) : manager.getMusics().stream()
            .filter(m -> m.getAudio().getName().equals(filename)).findFirst().orElse(null);
        return new Row(service.displayTrack(track), local, local == null ? filename : local.getAudio().getName());
    }
    @Override public void mousePressed(double mx, double my, int button) { mousePressed(mx, my, button, false); }
    public void mousePressed(double mx, double my, int button, boolean doubled) {
        if (dialog.isOpen()) { dialog.mousePressed(mx, my, button); return; }
        search.mousePressed(mx, my, button); pressed = null;
        if (tab == Tab.SEARCH && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (MusicSearchType type : MusicSearchType.values()) {
                var box = MusicPlayerLayout.searchType(x, y, type.ordinal());
                if (box.contains(mx, my)) { pressed = new Press(box.x(), box.y(), box.width(), box.height(), () -> selectSearchType(type)); return; }
            }
        }
        if (tab == Tab.BROWSE) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MusicUi.inside(mx, my, x, y - 3, 48, 48)) {
                pressed = new Press(x, y - 3, 48, 48, this::back); return;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MusicUi.inside(mx, my, x + width - 104, y + 60, 48, 48)) {
                pressed = new Press(x + width - 104, y + 60, 48, 48, this::playCollection); return;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MusicUi.inside(mx, my, x + width - 48, y + 60, 48, 48)) {
                pressed = new Press(x + width - 48, y + 60, 48, 48, () -> openCollectionMenu(mx, my)); return;
            }
        }
        if (tab == Tab.PLAYLISTS && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MusicUi.inside(mx, my, x + width - 160, y - 3, 160, 48)) {
            pressed = new Press(x + width - 160, y - 3, 160, 48, this::createPlaylist); return;
        }
        if (tab != Tab.BROWSE && MusicUi.inside(mx, my, x + width - 48, y - 3, 48, 48) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressed = new Press(x + width - 48, y - 3, 48, 48, () -> { if (tab == Tab.SEARCH) search(false);
                else if (tab == Tab.PLAYLIST) openPlaylistMenu(playlistId, mx, my); else refresh(); }); return;
        }
        if (tab == Tab.PLAYLIST && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MusicUi.inside(mx, my, x + width - 104, y - 3, 48, 48)) {
            pressed = new Press(x + width - 104, y - 3, 48, 48, () -> rows().stream().filter(row -> row.entry().playable()).findFirst()
                .ifPresent(row -> play(row, false))); return;
        }
        if (hasMore() && MusicUi.inside(mx, my, x + width - 136, y + height - 48, 136, 48)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) pressed = new Press(x + width - 136, y + height - 48, 136, 48, () -> search(true));
            return;
        }
        if (!MusicUi.inside(mx, my, x, y + listOffset(), width, listHeight())) return;
        if (tab == Tab.SEARCH && browser.type() != MusicSearchType.SONGS) {
            var collections = browser.collections(); int index = (int) ((my - y - listOffset() - scroll.getValue()) / ROW_HEIGHT);
            if (index < 0 || index >= collections.size()) return;
            var collection = collections.get(index); selected = collection.key();
            float rowY = y + listOffset() + index * ROW_HEIGHT + scroll.getValue();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) pressed = new Press(x, rowY, width, ROW_HEIGHT, () -> openCollection(collection));
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) menu.open(mx, my, List.of(item("music.action.open", Icon.CHEVRON_RIGHT, true, () -> openCollection(collection))));
            return;
        }
        if (tab == Tab.PLAYLISTS) {
            var playlists = playlists(); int index = (int) ((my - y - 80 - scroll.getValue()) / ROW_HEIGHT);
            if (index < 0 || index >= playlists.size()) return;
            var playlist = playlists.get(index); float rowY = y + 80 + index * ROW_HEIGHT + scroll.getValue();
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) openPlaylistMenu(playlist.id(), mx, my);
            else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                selected = playlist.id();
                pressed = mx >= x + width - 56 ? new Press(x + width - 56, rowY + 8, 48, 48,
                    () -> openPlaylistMenu(playlist.id(), mx, rowY + 48)) : new Press(x, rowY, width - 56, 64, () -> openPlaylist(playlist.id()));
            }
            return;
        }
        List<Row> rows = rows();
        int index = (int) ((my - y - listOffset() - scroll.getValue()) / ROW_HEIGHT);
        if (index < 0 || index >= rows.size()) return;
        Row row = rows.get(index); selected = row.entry().key();
        float rowY = y + listOffset() + index * ROW_HEIGHT + scroll.getValue();
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { openRowMenu(row, mx, my); return; }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (mx >= x + width - 56) pressed = new Press(x + width - 56, rowY + 8, 48, 48, () -> openRowMenu(row, x + width - 288, rowY + 48));
        else if (mx >= x + width - 108) pressed = new Press(x + width - 108, rowY + 8, 48, 48, () -> like(row));
        else if (mx >= x + width - 160) pressed = new Press(x + width - 160, rowY + 8, 48, 48, () -> enqueue(row, false));
        else if (mx < x + 64) pressed = new Press(x + 8, rowY + 8, 48, 48, () -> play(row, true));
        else if (doubled) pressed = new Press(x + 64, rowY, width - 224, 64, () -> play(row, false));
    }
    private List<MusicQueue.Entry> collectionEntries() { return rows().stream().map(Row::entry).filter(MusicQueue.Entry::playable).toList(); }
    private void playCollection() {
        rows().stream().filter(row -> row.entry().playable()).findFirst().ifPresent(row -> play(row, false));
    }
    private void openCollectionMenu(double mx, double my) {
        var collection = browser.collection(); if (collection == null) return;
        var entries = collectionEntries(); boolean partial = hasMore() || !search.getText().isBlank();
        menu.open(mx, my, List.of(
            item(partial ? "music.browse.playloaded" : "music.action.play", Icon.PLAY_ARROW, !entries.isEmpty(), this::playCollection),
            item(partial ? "music.browse.queueloaded" : "music.action.playlast", Icon.PLAYLIST_ADD, !entries.isEmpty(), () -> {
                entries.forEach(entry -> manager.getQueue().enqueue(entry, false)); status("music.browse.queued", entries.size());
            }),
            item(partial ? "music.browse.saveloaded" : "music.browse.save", Icon.LIBRARY_ADD, !entries.isEmpty(),
                () -> createPlaylist(entries, collection.name()))));
    }
    @Override public void mouseReleased(double mx, double my, int button) {
        if (dialog.isOpen()) { dialog.mouseReleased(mx, my, button); return; }
        Press press = pressed; pressed = null;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && press != null && press.contains(mx, my)) press.action().run();
    }
    private void openRowMenu(Row row, double mx, double my) {
        boolean liked = service.isLiked(row.track(), row.filename());
        var actions = new ArrayList<>(List.of(
            item("music.action.play", Icon.PLAY_ARROW, row.entry().playable(), () -> play(row, false)),
            item("music.action.playnext", Icon.PLAYLIST_PLAY, row.entry().playable(), () -> enqueue(row, true)),
            item("music.action.playlast", Icon.PLAYLIST_ADD, row.entry().playable(), () -> enqueue(row, false)),
            item("music.playlist.add", Icon.LIBRARY_ADD, true, () -> choosePlaylist(row, mx, my)),
            item(liked ? "music.action.unlike" : "music.action.like", Icon.FAVORITE, !service.favoritesBusy(row.track()), () -> like(row)),
            new MusicPopupMenu.Item(row.local() == null ? MusicText.downloadAction(row.track()) : row.local().getTrack().preview()
                ? MusicText.get("music.action.downloadagain") : MusicText.get("music.action.saved"), Icon.DOWNLOAD,
                (row.local() == null || row.local().getTrack().preview()) && row.track().remote() && row.track().downloadable()
                && service.downloadProgress(row.track()) < 0, false, () -> download(row))));
        if (tab == Tab.PLAYLIST) {
            var playlist = manager.getLibrary().playlist(playlistId); String id = playlistId;
            if (playlist != null) {
                int index = -1;
                for (int i = 0; i < playlist.entries().size(); i++) if (playlist.entries().get(i).key().equals(row.entry().key())) { index = i; break; }
                int position = index;
                actions.add(item("music.playlist.moveup", Icon.ARROW_UPWARD, index > 0, () -> editPlaylist(() -> playlistChanged(manager.getLibrary().moveInPlaylist(id, position, position - 1, playlist.revision())))));
                actions.add(item("music.playlist.movedown", Icon.ARROW_DOWNWARD, index >= 0 && index + 1 < playlist.entries().size(), () -> editPlaylist(() -> playlistChanged(manager.getLibrary().moveInPlaylist(id, position, position + 1, playlist.revision())))));
                actions.add(item("music.playlist.remove", Icon.REMOVE, true, () -> editPlaylist(() -> playlistChanged(manager.getLibrary().removeFromPlaylist(id, row.entry().key(), playlist.revision())))));
            }
        }
        menu.open(mx, my, actions);
    }
    private void playlistChanged(boolean changed) { if (!changed) status("music.error.playlistchanged"); }
    private MusicPopupMenu.Item item(String key, String icon, boolean enabled, Runnable action) {
        return new MusicPopupMenu.Item(MusicText.get(key), icon, enabled, false, action);
    }
    private void enqueue(Row row, boolean next) {
        if (!row.entry().playable()) return;
        manager.getQueue().enqueue(row.entry(), next); status(next ? "music.status.queuednext" : "music.status.queuedlast", row.track().title());
    }
    private void play(Row row, boolean toggle) {
        if (!row.entry().playable()) { error(new MusicError("music.error.playrestricted")); return; }
        if (toggle && manager.getCurrentMusic() != null && row.entry().key().equals(MusicQueue.Entry.of(manager.getCurrentMusic()).key())) {
            manager.switchPlayBack(); return;
        }
        List<Row> playable = rows().stream().filter(candidate -> candidate.entry().playable()).toList();
        List<MusicQueue.Entry> entries = playable.stream().map(Row::entry).toList();
        int index = -1;
        for (int i = 0; i < playable.size(); i++) if (playable.get(i).occurrence() == row.occurrence() && entries.get(i).key().equals(row.entry().key())) { index = i; break; }
        if (index < 0) return;
        status("music.status.loadingtrack", row.track().title());
        manager.playFrom(entries, index, quality(row), music -> { if (!disposed) {
            if (music.getTrack().preview()) status("music.status.playingpreview", music.getTitle(), MusicText.access(music.getTrack()));
            else status("music.status.playing", music.getTitle());
        } }, this::error);
    }
    private void like(Row row) {
        service.toggleLike(row.track(), row.filename(), liked -> { if (!disposed) status(liked ? "music.status.liked" : "music.status.unliked", row.track().title()); }, this::error);
    }
    private void download(Row row) {
        status("music.status.downloadtrack", row.track().title());
        service.download(row.track(), quality(row), false, music -> { if (!disposed) {
            if (music.getTrack().preview()) status("music.status.downloadedpreview", music.getTitle(), MusicText.access(music.getTrack()));
            else status("music.status.downloaded", music.getTitle());
        } }, this::error);
    }
    private String quality(Row row) {
        return row.track().provider().equals(service.provider().id()) ? quality : service.defaultQuality(row.track());
    }
    private void search(boolean more) {
        updateQuery();
        var request = browser.begin(more); if (request == null) return;
        if (!more) { selected = ""; scroll.reset(); }
        status(tab == Tab.BROWSE ? "music.browse.loading" : "music.status.searching");
        java.util.function.Consumer<MusicError> failure = error -> { if (!disposed && browser.fail(request)) error(error); };
        if (request.collection() != null) service.collectionTracks(request.collection(), request.offset(), result -> {
            if (!disposed && browser.accept(request, result)) status("music.status.ready");
        }, failure);
        else service.search(request.query(), request.type(), request.offset(), result -> {
            if (disposed || !browser.accept(request, result)) return;
            status(browser.total() < 0 ? "music.search.loaded" : "music.search.count", browser.total() < 0
                ? browser.tracks().size() + browser.collections().size() : browser.total());
        }, failure);
    }
    private void refresh() {
        if (refreshing) return; refreshing = true; status("music.status.refreshing");
        service.refresh(repaired -> { if (!disposed) { refreshing = false; status("music.status.refreshed"); } }, failure -> { refreshing = false; error(failure); });
    }
    private void syncLikes() { status("music.status.syncing"); service.syncLikes("netease", count -> { if (!disposed) status("music.status.synced", count); }, this::error); }
    private boolean hasMore() { return (tab == Tab.SEARCH || tab == Tab.BROWSE) && browser.hasMore(); }
    private void providerChanged() {
        previousProvider = service.provider().id(); if (tab == Tab.BROWSE) navigate(Tab.SEARCH);
        browser.reset(previousProvider, browser.type(), queries[Tab.SEARCH.ordinal()]);
        if (tab == Tab.SEARCH) browser.reset(previousProvider, browser.type(), search.getText());
        selected = ""; scroll.reset(); quality = service.provider().defaultQuality(); queryChanged = System.nanoTime();
        status("music.provider.selected", MusicText.get(service.provider().nameKey()));
        if (tab == Tab.SEARCH && !search.getText().isBlank()) search(false);
    }
    public void dispose() { disposed = true; browser.cancel(); pressed = null; menu.close(); dialog.close(); }
    public boolean isInputFocused() { return dialog.isOpen() || search.isFocused(); }
    private void error(MusicError error) { if (!disposed) status(error.key()); }
    private void status(String key, Object... arguments) { statusKey = key; statusArguments = arguments; statusChanged = System.nanoTime(); }
    @Override public void mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (MusicUi.inside(mx, my, x, y + listOffset(), width, listHeight())) scroll.onScroll(vertical);
    }
    @Override public void charTyped(int chr) { if (dialog.isOpen()) dialog.charTyped(chr); else { search.charTyped(chr); updateQuery(); } }
    @Override public void keyPressed(int key, int scancode, int modifiers) {
        if (dialog.isOpen()) { dialog.keyPressed(key, scancode, modifiers); return; }
        if (tab == Tab.BROWSE && key == GLFW.GLFW_KEY_LEFT && (modifiers & GLFW.GLFW_MOD_ALT) != 0) { back(); return; }
        if (tab == Tab.SEARCH && key == GLFW.GLFW_KEY_TAB && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            var types = MusicSearchType.values(); selectSearchType(types[Math.floorMod(browser.type().ordinal() + step, types.length)]); return;
        }
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { if (tab == Tab.SEARCH) search(false); }
            else search.keyPressed(key, scancode, modifiers);
            updateQuery(); return;
        }
        if (tab == Tab.PLAYLISTS) {
            var playlists = playlists(); if (playlists.isEmpty()) return;
            int index = -1;
            for (int i = 0; i < playlists.size(); i++) if (playlists.get(i).id().equals(selected)) index = i;
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                index = Math.clamp(index + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, playlists.size() - 1);
                selected = playlists.get(index).id(); keepVisible(index);
            }
            else if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && index >= 0) openPlaylist(playlists.get(index).id());
            else search.keyPressed(key, scancode, modifiers);
            return;
        }
        if (tab == Tab.SEARCH && browser.type() != MusicSearchType.SONGS) {
            var collections = browser.collections(); int index = -1;
            for (int i = 0; i < collections.size(); i++) if (collections.get(i).key().equals(selected)) index = i;
            if ((key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) && !collections.isEmpty()) {
                index = Math.clamp(index + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, collections.size() - 1);
                selected = collections.get(index).key(); keepVisible(index);
            } else if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && index >= 0) openCollection(collections.get(index));
            else search.keyPressed(key, scancode, modifiers);
            return;
        }
        List<Row> rows = rows();
        int index = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).entry().key().equals(selected)) { index = i; break; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            if (rows.isEmpty()) return;
            index = Math.clamp(index + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, rows.size() - 1);
            selected = rows.get(index).entry().key();
            keepVisible(index);
        } else if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && index >= 0) play(rows.get(index), false);
        else search.keyPressed(key, scancode, modifiers);
    }
    private void keepVisible(int index) {
        float top = index * ROW_HEIGHT + scroll.getValue();
        if (top < 0) scroll.onScroll(-top / 60); else if (top + ROW_HEIGHT > listHeight()) scroll.onScroll(-(top + ROW_HEIGHT - listHeight()) / 60);
    }
}
