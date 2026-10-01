package cn.pupperclient.gui.modmenu.pages;

import cn.pupperclient.PupperClient;
import cn.pupperclient.gui.api.PupperClientGui;
import cn.pupperclient.gui.api.page.Page;
import cn.pupperclient.gui.api.page.impl.RightTransition;
import cn.pupperclient.gui.modmenu.component.SettingBar;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.settings.Setting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.misc.SearchUtils;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.mouse.MouseUtils;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class SettingsImplPage extends Page {

    private final List<SettingBar> bars = new ArrayList<>();
    private final List<Setting> lastVisibleSettings = new ArrayList<>();

    private final Class<? extends Page> prevPage;
    private final Mod mod;

    public SettingsImplPage(PupperClientGui parent, Class<? extends Page> prevPage, Mod mod) {
        super(parent, "text.mods", Icon.SETTINGS, new RightTransition(true));
        this.prevPage = prevPage;
        this.mod = mod;
    }

    @Override
    public void init() {
        super.init();
        rebuildSettingBars();
        parent.setClosable(false);
    }

    private void rebuildSettingBars() {
        bars.clear();
        lastVisibleSettings.clear();

        for (Setting s : PupperClient.getInstance().getModManager().getSettingsByMod(mod)) {
            if (s.isVisible()) {
                SettingBar bar = new SettingBar(s, x + 32, y + 32, width - 64);
                bars.add(bar);
                lastVisibleSettings.add(s);
            }
        }
    }

    private boolean hasVisibilityChanged() {
        List<Setting> currentVisibleSettings = new ArrayList<>();
        for (Setting s : PupperClient.getInstance().getModManager().getSettingsByMod(mod)) {
            if (s.isVisible()) {
                currentVisibleSettings.add(s);
            }
        }

        if (currentVisibleSettings.size() != lastVisibleSettings.size()) {
            return true;
        }

        for (int i = 0; i < currentVisibleSettings.size(); i++) {
            if (!currentVisibleSettings.get(i).equals(lastVisibleSettings.get(i))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void draw(double mouseX, double mouseY) {
        super.draw(mouseX, mouseY);

        if (hasVisibilityChanged()) {
            rebuildSettingBars();
        }

        float offsetY = 96;

        mouseY = mouseY - scrollHelper.getValue();

        Skia.save();
        Skia.clip(x, y + 88, width, height - 88, 0);
        Skia.translate(0, scrollHelper.getValue());

        for (SettingBar b : bars) {

            if (!searchBar.getText().isEmpty() && !SearchUtils.isSimilar(I18n.get(b.getTitle()), searchBar.getText())) {
                continue;
            }

            b.setY(y + offsetY);
            b.draw(mouseX, mouseY);

            offsetY += b.getHeight() + 18;
        }

        scrollHelper.setMaxScroll(offsetY, height);
        Skia.restore();
    }

    @Override
    public void mousePressed(double mouseX, double mouseY, int button) {
        super.mousePressed(mouseX, mouseY, button);
        boolean overContent = MouseUtils.isInside(mouseX, mouseY, x, y + 88, width, height - 88);

        mouseY = mouseY - scrollHelper.getValue();
        if (!overContent) mouseX = -Double.MAX_VALUE;

        for (SettingBar b : bars) {

            if (!searchBar.getText().isEmpty() && !SearchUtils.isSimilar(I18n.get(b.getTitle()), searchBar.getText())) {
                continue;
            }

            b.mousePressed(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
        boolean overContent = MouseUtils.isInside(mouseX, mouseY, x, y + 88, width, height - 88);

        mouseY = mouseY - scrollHelper.getValue();
        if (!overContent) mouseX = -Double.MAX_VALUE;

        for (SettingBar b : bars) {

            if (!searchBar.getText().isEmpty() && !SearchUtils.isSimilar(I18n.get(b.getTitle()), searchBar.getText())) {
                continue;
            }

            b.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public void charTyped(int chr) {
        super.charTyped(chr);

        for (SettingBar b : bars) {

            if (!searchBar.getText().isEmpty() && !SearchUtils.isSimilar(I18n.get(b.getTitle()), searchBar.getText())) {
                continue;
            }

            b.charTyped(chr);
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        super.keyPressed(keyCode, scanCode, modifiers);

        for (SettingBar b : bars) {

            if (!searchBar.getText().isEmpty() && !SearchUtils.isSimilar(I18n.get(b.getTitle()), searchBar.getText())) {
                continue;
            }

            b.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            parent.setClosable(true);
            parent.setCurrentPage(prevPage);
        }
    }
}
