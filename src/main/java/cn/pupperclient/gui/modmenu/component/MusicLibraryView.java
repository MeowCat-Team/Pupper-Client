package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.Music;
import cn.pupperclient.management.music.MusicError;
import cn.pupperclient.management.music.MusicManager;
import cn.pupperclient.management.music.MusicService;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.management.music.MusicTrack;
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
import java.util.Map;
import java.util.HashMap;
import org.lwjgl.glfw.GLFW;

/** A fixed toolbar, independently scrolling rows and asynchronous, paginated cloud results. */
public final class MusicLibraryView extends Component {
    public enum Tab { LIBRARY, SEARCH, LIKED }
    private record Row(MusicTrack track, Music local, String filename) { }
    private record Press(float x, float y, float width, float height, Runnable action) {
        boolean contains(double mx, double my) { return MusicUi.inside(mx, my, x, y, width, height); }
    }
    private static final float ROW_HEIGHT = 76;
    private final MusicManager manager = PupperClient.getInstance().getMusicManager();
    private final MusicService service = manager.getService();
    private final SearchBar search;
    private final ScrollHelper scroll = new ScrollHelper();
    private final List<MusicTrack> results = new ArrayList<>();
    private Tab tab;
    private Press pressed;
    private int requestGeneration, total;
    private boolean searching, refreshing, disposed;
    private String submittedQuery = "", previousFilter = "";
    private final String[] queries = { "", "", "" };
    private String statusKey = "music.status.ready";
    private Object[] statusArguments = new Object[0];
    private String quality = "exhigh";
    private String hoveredHint;

    public MusicLibraryView(float x, float y, float width, float height, Tab initial) {
        super(x, y);
        this.width = width;
        this.height = height;
        tab = initial;
        search = new SearchBar(x, y + 64, width - 116, "", scroll::reset);
        refresh();
    }

    @Override public void draw(double mouseX, double mouseY) {
        ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
        hoveredHint = null;
        float tabWidth = (width - 60) / 3;
        String[] keys = { "music.tab.library", "music.tab.search", "music.tab.liked" };
        String[] icons = { Icon.LIBRARY_MUSIC, Icon.SEARCH, Icon.FAVORITE };
        for (int i = 0; i < 3; i++) {
            boolean hover = inside(mouseX, mouseY, x + i * tabWidth, y, tabWidth - 4, 48);
            MusicUi.tab(x + i * tabWidth, y, tabWidth - 4, icons[i], MusicText.get(keys[i]),
                tab.ordinal() == i, hover, palette);
        }
        boolean sync = tab == Tab.LIKED && service.loggedIn();
        boolean toolbarBusy = sync ? service.favoritesBusy() : refreshing;
        MusicUi.iconButton(x + width - 48, y, sync ? Icon.CLOUD_SYNC : Icon.REFRESH, false, !toolbarBusy,
            inside(mouseX, mouseY, x + width - 48, y, 48, 48), palette);
        if (inside(mouseX, mouseY, x + width - 48, y, 48, 48))
            hoveredHint = MusicText.get(sync ? "music.action.sync" : "music.action.refresh");

        search.setHintText(tab == Tab.SEARCH ? "music.search.hint" : "music.filter.hint");
        search.draw(mouseX, mouseY);
        if (tab == Tab.SEARCH) {
            MusicUi.button(x + width - 104, y + 62, 104,
                MusicText.get(searching ? "music.action.searching" : "music.action.search"), true,
                inside(mouseX, mouseY, x + width - 104, y + 62, 104, 48), palette);
        } else {
            Skia.drawHeightCenteredText(MusicText.get("music.tracks", rows().size()), x + width - 104, y + 85,
                palette.getOnSurfaceVariant(), Fonts.getRegular(13));
        }
        if (!previousFilter.equals(search.getText()) && tab != Tab.SEARCH) {
            previousFilter = search.getText();
            scroll.reset();
        }
        List<Row> rows = rows();
        float listY = y + 150;
        float listHeight = height - 200;
        scroll.setMaxScroll(rows.size() * ROW_HEIGHT, listHeight);
        scroll.onUpdate();
        String heading = tab == Tab.SEARCH ? (submittedQuery.isBlank() ? MusicText.get("music.discover")
            : MusicText.get("music.results.for", submittedQuery, total))
            : MusicText.get(tab == Tab.LIKED ? "music.liked.heading" : "music.library.heading");
        Skia.drawText(Skia.getLimitText(heading, Fonts.getMedium(13), width), x + 12, y + 126,
            palette.getOnSurfaceVariant(), Fonts.getMedium(13));
        if (rows.isEmpty()) drawEmpty(listY, listHeight, palette);
        else {
            Skia.save();
            try {
                Skia.clip(x, listY, width, listHeight, 16);
                int first = Math.max(0, (int) (-scroll.getValue() / ROW_HEIGHT));
                int last = Math.min(rows.size(), first + (int) Math.ceil(listHeight / ROW_HEIGHT) + 2);
                for (int i = first; i < last; i++) {
                    Row row = rows.get(i);
                    float rowY = listY + i * ROW_HEIGHT + scroll.getValue();
                    Music playing = manager.getCurrentMusic();
                    boolean active = row.local() != null && playing != null
                        && row.local().getAudio().equals(playing.getAudio());
                    File cover = row.local() != null && row.local().getAlbum() != null
                        ? row.local().getAlbum() : service.cover(row.track());
                    boolean hover = inside(mouseX, mouseY, x, rowY, width, 72)
                        && inside(mouseX, mouseY, x, listY, width, listHeight);
                    String subtitle = row.track().artist().isBlank() ? MusicText.get("music.artist.unknown") : row.track().artist();
                    if (!row.track().album().isBlank()) subtitle += " · " + row.track().album();
                    MusicUi.row(x, rowY, width - 8, cover, row.track().title(), subtitle,
                        row.track().durationMillis() > 0 ? MusicText.time(row.track().durationMillis() / 1000f) : "—",
                        active, manager.isPlaying(), service.isLiked(row.track(), row.filename()), row.local() != null,
                        service.downloadProgress(row.track().id()), hover, !service.favoritesBusy(), mouseX, mouseY, palette);
                    if (hover) {
                        if (mouseX >= x + width - 64) hoveredHint = MusicText.get(service.isLiked(row.track(), row.filename())
                            ? "music.action.unlike" : "music.action.like");
                        else if (mouseX >= x + width - 116) hoveredHint = MusicText.get(row.local() == null
                            ? "music.action.download" : "music.action.saved");
                        else hoveredHint = MusicText.get(row.local() == null ? "music.action.downloadplay" : "music.action.play");
                    }
                }
                if (rows.size() * ROW_HEIGHT > listHeight) {
                    float thumb = Math.max(28, listHeight * listHeight / (rows.size() * ROW_HEIGHT));
                    float fraction = -scroll.getValue() / Math.max(1, rows.size() * ROW_HEIGHT - listHeight);
                    Skia.drawRoundedRect(x + width - 4, listY + fraction * (listHeight - thumb), 3, thumb, 2,
                        MaterialTheme.alpha(palette.getOutline(), .4f));
                }
            } finally { Skia.restore(); }
        }
        float footerY = y + height - 42;
        boolean hasMore = tab == Tab.SEARCH && results.size() < total && !results.isEmpty();
        if (hasMore) {
            MusicUi.button(x + width - 276, footerY, 128, MusicText.get(searching ? "music.action.searching" : "music.action.more"),
                false, inside(mouseX, mouseY, x + width - 276, footerY, 128, 48), palette);
        }
        String status = tab == Tab.LIKED && statusKey.equals("music.status.ready")
            ? MusicText.get(service.loggedIn() ? "music.favorite.cloud" : "music.favorite.guest")
            : MusicText.get(statusKey, statusArguments);
        Skia.drawHeightCenteredText(Skia.getLimitText(status, Fonts.getRegular(12), width - (hasMore ? 294 : 150)),
            x + 8, footerY + 24, statusKey.startsWith("music.error") ? palette.getError() : palette.getOnSurfaceVariant(),
            Fonts.getRegular(12));
        MusicUi.button(x + width - 140, footerY, 140, MusicText.get("music.quality.button", MusicText.get("music.quality." + quality)),
            false, inside(mouseX, mouseY, x + width - 140, footerY, 140, 48), palette);
        if (inside(mouseX, mouseY, x + width - 140, footerY, 140, 48)) hoveredHint = MusicText.get("music.quality.change");
        if (hoveredHint != null) MusicUi.tooltip(hoveredHint, mouseX, mouseY, x + width, palette);
    }

    private void drawEmpty(float listY, float listHeight, ColorPalette palette) {
        String icon = tab == Tab.SEARCH ? Icon.SEARCH : tab == Tab.LIKED ? Icon.FAVORITE : Icon.LIBRARY_MUSIC;
        float centerY = listY + listHeight / 2;
        Skia.drawCircle(x + width / 2, centerY - 38, 32, MaterialTheme.surface(palette.getSecondaryContainer()));
        Skia.drawFullCenteredText(icon, x + width / 2, centerY - 38, palette.getPrimary(), Fonts.getIcon(28));
        String key = searching ? "music.status.searching" : tab == Tab.SEARCH
            ? (submittedQuery.isEmpty() ? "music.empty.search" : "music.empty.results")
            : tab == Tab.LIKED ? "music.empty.liked" : "music.empty.library";
        Skia.drawCenteredText(MusicText.get(key), x + width / 2, centerY + 16,
            palette.getOnSurface(), Fonts.getMedium(16));
        Skia.drawCenteredText(Skia.getLimitText(MusicText.get(tab == Tab.LIKED
            ? (service.loggedIn() ? "music.favorite.cloud" : "music.favorite.guest")
            : "music.empty.hint"), Fonts.getRegular(12), width - 32), x + width / 2, centerY + 46,
            palette.getOnSurfaceVariant(), Fonts.getRegular(12));
    }

    private List<Row> rows() {
        Map<Long, Music> downloaded = new HashMap<>();
        Map<String, Music> localFiles = new HashMap<>();
        for (Music music : manager.getMusics()) {
            if (music.getTrack().id() > 0) downloaded.put(music.getTrack().id(), music);
            localFiles.put(music.getAudio().getName(), music);
        }
        List<Row> rows;
        if (tab == Tab.SEARCH) rows = results.stream().map(t -> {
            Music local = downloaded.get(t.id());
            return new Row(t, local, local == null ? "" : local.getAudio().getName());
        }).toList();
        else if (tab == Tab.LIKED) rows = service.favorites().stream().map(f -> {
            Music local = f.track().id() > 0 ? downloaded.get(f.track().id()) : localFiles.get(f.filename());
            return new Row(f.track(), local, local == null ? f.filename() : local.getAudio().getName());
        }).toList();
        else rows = manager.getMusics().stream().map(m -> new Row(m.getTrack(), m, m.getAudio().getName())).toList();
        if (tab == Tab.SEARCH || search.getText().isBlank()) return rows;
        String filter = search.getText().strip().toLowerCase(Locale.ROOT);
        return rows.stream().filter(r -> (r.track().title() + " " + r.track().artist() + " " + r.track().album())
            .toLowerCase(Locale.ROOT).contains(filter)).toList();
    }

    @Override public void mousePressed(double mouseX, double mouseY, int button) {
        search.mousePressed(mouseX, mouseY, button);
        pressed = button == GLFW.GLFW_MOUSE_BUTTON_LEFT ? actionAt(mouseX, mouseY) : null;
    }

    @Override public void mouseReleased(double mouseX, double mouseY, int button) {
        Press press = pressed;
        pressed = null;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && press != null && press.contains(mouseX, mouseY)) press.action().run();
    }

    private Press actionAt(double mx, double my) {
        float tabWidth = (width - 60) / 3;
        for (Tab next : Tab.values()) {
            Press action = new Press(x + next.ordinal() * tabWidth, y, tabWidth - 4, 48, () -> {
                if (tab == next) return;
                queries[tab.ordinal()] = search.getText();
                tab = next; search.setText(queries[next.ordinal()]); scroll.reset(); status("music.status.ready");
                if (next == Tab.LIKED && service.loggedIn()) syncLikes();
            });
            if (action.contains(mx, my)) return action;
        }
        Press toolbar = new Press(x + width - 48, y, 48, 48,
            () -> { if (tab == Tab.LIKED && service.loggedIn()) syncLikes(); else refresh(); });
        if (toolbar.contains(mx, my)) return toolbar;
        Press submit = new Press(x + width - 104, y + 62, 104, 48, () -> search(false));
        if (tab == Tab.SEARCH && submit.contains(mx, my)) return submit;
        Press qualityButton = new Press(x + width - 140, y + height - 42, 140, 48, () -> {
            int index = MusicService.QUALITIES.indexOf(quality);
            quality = MusicService.QUALITIES.get((index + 1) % MusicService.QUALITIES.size());
        });
        if (qualityButton.contains(mx, my)) return qualityButton;
        Press more = new Press(x + width - 276, y + height - 42, 128, 48, () -> search(true));
        if (tab == Tab.SEARCH && results.size() < total && !results.isEmpty() && more.contains(mx, my)) return more;
        if (!inside(mx, my, x, y + 150, width, height - 200)) return null;
        List<Row> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float rowY = y + 150 + i * ROW_HEIGHT + scroll.getValue();
            if (!inside(mx, my, x, rowY, width - 8, 72)) continue;
            if (mx >= x + width - 64) return new Press(x + width - 64, rowY + 12, 48, 48,
                () -> service.toggleLike(row.track(), row.filename(), liked -> {
                    if (!disposed) status(liked ? "music.status.liked" : "music.status.unliked", row.track().title());
                }, this::error));
            if (mx >= x + width - 116) return new Press(x + width - 116, rowY + 12, 48, 48,
                () -> { if (row.local() == null && row.track().id() > 0) download(row, false); });
            return new Press(x, rowY, width - 180, 72, () -> {
                if (row.local() == null) { if (row.track().id() > 0) download(row, true); }
                else if (manager.getCurrentMusic() != null && manager.getCurrentMusic().getAudio().equals(row.local().getAudio()))
                    manager.switchPlayBack();
                else manager.play(row.local());
            });
        }
        return null;
    }

    private void search(boolean more) {
        String query = more ? submittedQuery : search.getText().strip();
        if (query.isEmpty() || (more && searching)) return;
        int generation = ++requestGeneration;
        int offset = more ? results.size() : 0;
        if (!more) { results.clear(); total = 0; submittedQuery = query; scroll.reset(); }
        searching = true;
        status("music.status.searching");
        service.search(query, offset, result -> {
            if (disposed || generation != requestGeneration) return;
            searching = false;
            results.addAll(result.tracks());
            total = result.tracks().isEmpty() ? results.size() : result.total();
            if (tab == Tab.SEARCH) status("music.results.count", total);
        }, failure -> { if (!disposed && generation == requestGeneration) { searching = false; error(failure); } });
    }

    private void download(Row row, boolean play) {
        status("music.status.downloadtrack", row.track().title());
        service.download(row.track(), quality, play, music -> {
            if (!disposed) status("music.status.downloaded", music.getTitle());
        }, this::error);
    }

    private void refresh() {
        if (refreshing) return;
        refreshing = true;
        status("music.status.refreshing");
        service.refresh(repaired -> {
            if (disposed) return;
            refreshing = false;
            status(repaired > 0 ? "music.status.repaired" : "music.status.refreshed", repaired);
        }, failure -> { refreshing = false; error(failure); });
    }

    private void syncLikes() {
        status("music.status.syncing");
        service.syncLikes(count -> { if (!disposed) status("music.status.synced", count); }, this::error);
    }

    public void dispose() { disposed = true; requestGeneration++; pressed = null; }
    public boolean isInputFocused() { return search.isFocused(); }
    private void error(MusicError error) { if (!disposed) status(error.key()); }
    private void status(String key, Object... arguments) { statusKey = key; statusArguments = arguments; }
    private static boolean inside(double mx, double my, float x, float y, float width, float height) {
        return MusicUi.inside(mx, my, x, y, width, height);
    }
    @Override public void mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (inside(mx, my, x, y + 150, width, height - 200)) scroll.onScroll(vertical);
    }
    @Override public void charTyped(int chr) { search.charTyped(chr); }
    @Override public void keyPressed(int key, int scancode, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && search.isFocused() && tab == Tab.SEARCH)
            search(false);
        else search.keyPressed(key, scancode, modifiers);
    }
}
