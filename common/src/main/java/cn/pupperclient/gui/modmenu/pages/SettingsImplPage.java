package cn.pupperclient.gui.modmenu.pages;

import cn.pupperclient.PupperClient;
import cn.pupperclient.gui.api.PupperClientGui;
import cn.pupperclient.gui.api.page.Page;
import cn.pupperclient.gui.api.page.impl.RightTransition;
import cn.pupperclient.gui.modmenu.component.SettingBar;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.settings.Setting;
import cn.pupperclient.management.mod.settings.SettingPresentation;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.misc.SearchUtils;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.mouse.MouseUtils;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

public class SettingsImplPage extends Page {

    private final List<SettingBar> bars = new ArrayList<>();
    private final List<Setting> lastVisibleSettings = new ArrayList<>();
    private final Map<Setting, SettingBar> cachedBars = new IdentityHashMap<>();
    private boolean showMore, morePressed;
    private float moreY;

    private final Class<? extends Page> prevPage;
    private final Mod mod;

    public SettingsImplPage(PupperClientGui parent, Class<? extends Page> prevPage, Mod mod) {
        super(parent, mod.getRawName(), Icon.SETTINGS, new RightTransition(true));
        this.prevPage = prevPage;
        this.mod = mod;
    }

    @Override
    public void init() {
        super.init();
        cachedBars.clear();
        rebuildSettingBars();
        parent.setClosable(false);
    }

    private void rebuildSettingBars() {
        bars.clear();
        lastVisibleSettings.clear();

        for (Setting s : PupperClient.getInstance().getModManager().getSettingsByMod(mod)) {
            if (isShown(s)) {
                SettingBar bar = cachedBars.computeIfAbsent(s, setting -> new SettingBar(setting, x + 32, y + 32, width - 64));
                bars.add(bar);
                lastVisibleSettings.add(s);
            }
        }
    }

    private boolean isShown(Setting setting) {
        return SettingPresentation.shown(setting, showMore, !searchBar.getText().isBlank());
    }

    private boolean hasMore() {
        return searchBar.getText().isBlank() && PupperClient.getInstance().getModManager().getSettingsByMod(mod).stream()
            .anyMatch(setting -> setting.isVisible() && SettingPresentation.level(setting.getName()) == SettingPresentation.Level.MORE);
    }

    private boolean overMore(double mouseX, double mouseY) {
        return hasMore() && MouseUtils.isInside(mouseX, mouseY, x + 32, moreY, width - 64, 48);
    }

    private boolean hasVisibilityChanged() {
        List<Setting> currentVisibleSettings = new ArrayList<>();
        for (Setting s : PupperClient.getInstance().getModManager().getSettingsByMod(mod)) {
            if (isShown(s)) {
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

        moreY = y + offsetY;
        if (hasMore()) {
            var palette = PupperClient.getInstance().getColorManager().getPalette();
            if (overMore(mouseX, mouseY)) MaterialTheme.card(x + 32, moreY, width - 64, 48, MaterialTheme.CARD_RADIUS, palette);
            Skia.drawHeightCenteredText(I18n.get(showMore ? "setting.options.less" : "setting.options.more"),
                x + 52, moreY + 24, palette.getPrimary(), Fonts.getMedium(16));
            Skia.drawFullCenteredText(showMore ? Icon.EXPAND_LESS : Icon.EXPAND_MORE,
                x + width - 58, moreY + 24, palette.getPrimary(), Fonts.getIcon(22));
            offsetY += 66;
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
        morePressed = button == GLFW.GLFW_MOUSE_BUTTON_LEFT && overMore(mouseX, mouseY);

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

        boolean toggleMore = morePressed && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && overMore(mouseX, mouseY);
        morePressed = false;
        if (toggleMore) {
            for (SettingBar bar : bars) bar.mousePressed(-Double.MAX_VALUE, -Double.MAX_VALUE, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            showMore = !showMore;
            rebuildSettingBars();
            return;
        }

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
