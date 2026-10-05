package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.music.MusicCloudLibrary;
import cn.pupperclient.management.music.MusicCollection;
import cn.pupperclient.management.music.MusicPreparation;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.utils.mouse.ScrollHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import org.lwjgl.glfw.GLFW;

/** Raw page offsets survive Created/Collected filtering; late or cancelled pages cannot enter the view. */
public final class MusicCloudView extends Component implements AutoCloseable {
    private final Consumer<MusicCollection> openCollection;
    private final Runnable openLogin;
    private final ScrollHelper scroll = new ScrollHelper();
    private final LinkedHashMap<String, MusicCollection> created = new LinkedHashMap<>(), subscribed = new LinkedHashMap<>();
    private MusicCloudUi.Tab tab = MusicCloudUi.Tab.CREATED;
    private MusicPreparation.Registration pending;
    private boolean visible, loading, more, guest;
    private int nextOffset;
    private long generation, accountVersion = -1, endpointRevision = -1;
    private String error = "";
    private MusicPlayerLayout.Box pressed;
    private Runnable action;
    public MusicCloudView(MusicPlayerLayout.Box box, Consumer<MusicCollection> openCollection, Runnable openLogin) {
        super(box.x(), box.y()); this.openCollection = openCollection; this.openLogin = openLogin; layout(box);
    }
    private MusicPlayerLayout.Box box() { return new MusicPlayerLayout.Box(x, y, width, height); }
    public void layout(MusicPlayerLayout.Box box) {
        if (x != box.x() || y != box.y() || width != box.width() || height != box.height()) cancelPointer();
        x = box.x(); y = box.y(); width = box.width(); height = box.height();
    }
    public void show() { visible = true; accountChanged(); }
    public void open() { show(); }
    public void accountChanged() {
        cancelRequest(); created.clear(); subscribed.clear(); nextOffset = 0; more = false; error = ""; scroll.reset();
        var service = PupperClient.getInstance().getMusicManager().getService();
        accountVersion = service.login().sessionVersion(); endpointRevision = service.apiConfiguration().snapshot().revision();
        guest = !service.login().account().authenticated();
        if (visible && !guest) load(false);
    }
    private void cancelRequest() { generation++; loading = false; cancelPointer(); var previous = pending; pending = null; if (previous != null) previous.close(); }
    public void cancelPointer() { pressed = null; action = null; }
    @Override public void close() { visible = false; cancelRequest(); }
    public void dispose() { close(); created.clear(); subscribed.clear(); }
    private List<MusicCollection> collections() { return List.copyOf((tab == MusicCloudUi.Tab.CREATED ? created : subscribed).values()); }
    private boolean current(long token) { return visible && generation == token; }
    private void load(boolean append) {
        if (!visible || loading || guest || append && !more) return;
        if (!append) { cancelRequest(); created.clear(); subscribed.clear(); nextOffset = 0; more = false; scroll.reset(); }
        int offset = append ? nextOffset : 0; long token = ++generation; loading = true; error = "";
        var service = PupperClient.getInstance().getMusicManager().getService();
        var request = service.cloud().playlists(30, offset, page -> {
            if (!current(token)) return;
            loading = false; pending = null;
            if (page.offset() != offset || page.nextOffset() < offset) { error = "music.error.network"; return; }
            page.created().forEach(item -> created.putIfAbsent(item.key(), item));
            page.subscribed().forEach(item -> subscribed.putIfAbsent(item.key(), item));
            nextOffset = page.nextOffset(); more = page.more() && nextOffset > offset;
            if (more) load(true);
        }, failure -> {
            if (!current(token)) return;
            loading = false; pending = null; error = failure.key();
            guest = !service.login().account().authenticated();
        });
        if (current(token) && loading) pending = request; else request.close();
    }
    @Override public void draw(double mx, double my) {
        if (!visible) return;
        synchronizeSession();
        var service = PupperClient.getInstance().getMusicManager().getService();
        var snapshot = collections(); scroll.setMaxScroll(snapshot.size() * 64, MusicCloudUi.body(box()).height()); scroll.onUpdate();
        MusicCloudUi.draw(box(), new MusicCloudUi.State(tab, snapshot, loading, false, guest, error), scroll.getValue(), mx, my,
            PupperClient.getInstance().getColorManager().getPalette(), service::cover);
    }
    @Override public void mousePressed(double mx, double my, int button) {
        synchronizeSession();
        cancelPointer(); if (!visible || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        var bounds = box();
        for (var next : MusicCloudUi.Tab.values()) if (MusicCloudUi.tab(bounds, next).contains(mx, my)) {
            arm(MusicCloudUi.tab(bounds, next), () -> { tab = next; scroll.reset(); }); return;
        }
        if (guest) { if (MusicCloudUi.login(bounds).contains(mx, my)) arm(MusicCloudUi.login(bounds), openLogin); return; }
        if (!loading && MusicCloudUi.retry(bounds).contains(mx, my)) { arm(MusicCloudUi.retry(bounds), () -> load(false)); return; }
        if (!loading && !error.isEmpty() && MusicCloudUi.retryFooter(bounds).contains(mx, my)) {
            arm(MusicCloudUi.retryFooter(bounds), () -> load(nextOffset > 0)); return;
        }
        var body = MusicCloudUi.body(bounds); if (!body.contains(mx, my)) return;
        var snapshot = collections(); int index = (int) ((my - body.y() - scroll.getValue()) / 64);
        if (index < 0 || index >= snapshot.size()) return;
        var collection = snapshot.get(index);
        var row = new MusicPlayerLayout.Box(body.x(), body.y() + index * 64 + scroll.getValue(), body.width(), 64);
        arm(row, () -> openCollection.accept(collection));
    }
    private void arm(MusicPlayerLayout.Box box, Runnable action) { pressed = box; this.action = action; }
    @Override public void mouseReleased(double mx, double my, int button) {
        synchronizeSession();
        var box = pressed; var run = action; cancelPointer();
        if (visible && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && box != null && box.contains(mx, my)) run.run();
    }
    @Override public void mouseScrolled(double mx, double my, double horizontal, double vertical) {
        synchronizeSession();
        if (visible && Double.isFinite(vertical) && MusicCloudUi.body(box()).contains(mx, my)) { cancelPointer(); scroll.onScroll(vertical); }
    }
    private void synchronizeSession() {
        if (!visible) return;
        var service = PupperClient.getInstance().getMusicManager().getService();
        if (accountVersion != service.login().sessionVersion() || endpointRevision != service.apiConfiguration().snapshot().revision()) accountChanged();
    }
}
