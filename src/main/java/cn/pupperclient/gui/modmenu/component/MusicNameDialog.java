package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.ui.component.impl.text.TextField;
import java.util.function.Function;
import org.lwjgl.glfw.GLFW;

/** A modal name editor; typing, Escape and outside clicks never reach playback controls. */
public final class MusicNameDialog {
    private final TextField input = new TextField(344, 310, 432, "");
    private String title, error = "";
    private Function<String, String> save;
    private int pressed = -1;

    public boolean isOpen() { return save != null; }
    public void open(String titleKey, String name, Function<String, String> action) {
        title = MusicText.get(titleKey); error = ""; save = action; pressed = -1;
        input.setText(name);
        input.keyPressed(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL);
        input.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
    }
    public void close() { save = null; pressed = -1; }
    public void draw(double mx, double my) {
        if (!isOpen()) return;
        var palette = PupperClient.getInstance().getColorManager().getPalette();
        MusicUi.nameDialog(title, error, mx, my, palette);
        input.draw(mx, my);
    }
    public void mousePressed(double mx, double my, int button) {
        input.mousePressed(mx, my, button); pressed = -1;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (MusicUi.inside(mx, my, 492, 396, 136, 48)) pressed = 0;
            else if (MusicUi.inside(mx, my, 640, 396, 136, 48)) pressed = 1;
        }
    }
    public void mouseReleased(double mx, double my, int button) {
        int action = pressed; pressed = -1;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (action == 0 && MusicUi.inside(mx, my, 492, 396, 136, 48)) close();
        else if (action == 1 && MusicUi.inside(mx, my, 640, 396, 136, 48)) submit();
    }
    private void submit() {
        String name = input.getText().strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 80 || name.codePoints().anyMatch(Character::isISOControl)) {
            error = "music.error.playlistname"; return;
        }
        error = save.apply(name);
        if (error.isEmpty()) close();
    }
    public void charTyped(int chr) { input.charTyped(chr); }
    public void keyPressed(int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) close();
        else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) submit();
        else input.keyPressed(key, scancode, modifiers);
    }
}
