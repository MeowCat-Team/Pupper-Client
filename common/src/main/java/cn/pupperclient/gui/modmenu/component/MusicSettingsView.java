package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.music.MusicApiConfiguration;
import cn.pupperclient.management.music.MusicExperienceStore.Settings;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.impl.text.TextField;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.function.Supplier;
import org.lwjgl.glfw.GLFW;

/** Preferences apply immediately; service credentials require an explicit successful Save. */
public final class MusicSettingsView extends Component {
    private final EndpointField endpoint = new EndpointField();
    private final Runnable qualityChanged, accountChanged;
    private final Supplier<String> quality;
    private Settings options = Settings.DEFAULT;
    private boolean trusted, connectionOpen;
    private String status = "", error = "";
    private MusicSettingsUi.Control pressed;
    public MusicSettingsView(MusicPlayerLayout.Box box, Runnable qualityChanged, Runnable accountChanged, Supplier<String> quality) {
        super(box.x(), box.y()); this.qualityChanged = Objects.requireNonNull(qualityChanged);
        this.accountChanged = Objects.requireNonNull(accountChanged); this.quality = Objects.requireNonNull(quality); layout(box); show();
    }
    private MusicPlayerLayout.Box box() { return new MusicPlayerLayout.Box(x, y, width, height); }
    public void layout(MusicPlayerLayout.Box box) {
        if (x != box.x() || y != box.y() || width != box.width() || height != box.height()) pressed = null;
        x = box.x(); y = box.y(); width = box.width(); height = box.height();
        endpoint.layout(MusicSettingsUi.endpoint(box));
    }
    public void show() {
        var manager = PupperClient.getInstance().getMusicManager(); options = manager.experience().settings();
        var api = manager.getService().apiConfiguration().snapshot(); trusted = api.trustedAccounts(); endpoint.setText(api.endpoint().toString());
        connectionOpen = false; status = error = ""; blurInput();
    }
    public void openConnection() { show(); connectionOpen = true; }
    public void blurInput() { endpoint.mousePressed(-1_000_000, -1_000_000, GLFW.GLFW_MOUSE_BUTTON_LEFT); pressed = null; }
    public boolean isInputFocused() { return connectionOpen && endpoint.isFocused(); }
    @Override public void draw(double mx, double my) {
        MusicSettingsUi.draw(box(), new MusicSettingsUi.State(options, endpoint.getText(), trusted, quality.get(), status, error, connectionOpen),
            mx, my, PupperClient.getInstance().getColorManager().getPalette());
        if (connectionOpen) endpoint.draw(mx, my);
    }
    @Override public void mousePressed(double mx, double my, int button) {
        pressed = null; if (connectionOpen) endpoint.mousePressed(mx, my, button);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        for (var control : MusicSettingsUi.Control.values()) if (MusicSettingsUi.visible(control, connectionOpen)
                && MusicSettingsUi.control(box(), control, connectionOpen).contains(mx, my)) { pressed = control; return; }
    }
    @Override public void mouseReleased(double mx, double my, int button) {
        var control = pressed; pressed = null;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && control != null && MusicSettingsUi.visible(control, connectionOpen)
                && MusicSettingsUi.control(box(), control, connectionOpen).contains(mx, my)) edit(control);
    }
    private void edit(MusicSettingsUi.Control control) {
        status = error = "";
        switch (control) {
            case CONNECTION -> { if (connectionOpen) show(); else openConnection(); }
            case TRUST -> trusted = !trusted;
            case QUALITY -> qualityChanged.run();
            case SAVE -> saveConnection();
            default -> {
                Settings next = switch (control) {
                    case SIZE -> MusicSettingsUi.nextSize(options);
                    case TRANSLATIONS -> options.lyrics(options.lyricSize(), !options.translations(), options.lyricOffset());
                    case TRANSITION -> MusicSettingsUi.nextTransition(options);
                    case NORMALIZE -> options.audio(options.gapless(), options.crossfadeSeconds(), !options.normalize());
                    default -> options;
                };
                try { PupperClient.getInstance().getMusicManager().settings(next); options = next; }
                catch (IOException failure) { error = "music.error.file"; }
            }
        }
    }
    private void saveConnection() {
        URI origin;
        try { origin = MusicApiConfiguration.validate(URI.create(endpoint.getText().strip())); }
        catch (IllegalArgumentException invalid) { error = "music.settings.api.invalid"; return; }
        var manager = PupperClient.getInstance().getMusicManager();
        try {
            manager.getService().configureApi(origin, trusted);
            endpoint.setText(origin.toString()); status = "music.settings.saved"; accountChanged.run();
        } catch (IOException unavailable) { error = "music.error.file"; }
        catch (IllegalArgumentException invalid) { error = "music.settings.api.invalid"; }
    }
    @Override public void keyPressed(int key, int scancode, int modifiers) {
        if (!connectionOpen) return;
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && endpoint.isFocused()) saveConnection();
        else endpoint.keyPressed(key, scancode, modifiers);
    }
    @Override public void charTyped(int chr) { if (connectionOpen) { endpoint.charTyped(chr); status = error = ""; } }

    private static final class EndpointField extends TextField {
        private EndpointField() { super(0, 0, 100, ""); }
        private void layout(MusicPlayerLayout.Box box) { x = box.x(); y = box.y(); width = box.width(); height = box.height(); }
    }
}
