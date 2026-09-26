package cn.pupperclient.gui.modmenu.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import cn.pupperclient.PupperClient;
import org.lwjgl.glfw.GLFW;

import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.gui.api.SoarGui;
import cn.pupperclient.gui.api.page.Page;
import cn.pupperclient.gui.api.page.impl.LeftRightTransition;
import cn.pupperclient.gui.api.page.impl.RightLeftTransition;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.component.api.PressAnimation;
import cn.pupperclient.utils.color.ColorUtils;
import cn.pupperclient.utils.misc.SearchUtils;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.mouse.MouseUtils;

public class ModsPage extends Page {

    private final List<Item> items = new ArrayList<>();
    private Category selectedCategory = Category.ALL;
    private List<Item> filteredItems = new ArrayList<>();
    private String filteredQuery;
    private Category filteredCategory;
    private int filteredSourceSize = -1;

    public enum Category {
        ALL("text.all"),
        HUD("text.hud"),
        RENDER("text.render"),
        PLAYER("text.player"),
        MISC("text.misc"),
        FUN("text.fun");

        private final String name;

        Category(String name) {
            this.name = name;
        }

        public String getName() {
            return I18n.get(name);
        }
    }

    public ModsPage(SoarGui parent) {
        super(parent, "text.mods", Icon.INVENTORY_2, new RightLeftTransition(true));
    }

    @Override
    public void init() {
        super.init();
        items.clear();
        filteredQuery = null;
        filteredCategory = null;
        filteredSourceSize = -1;
        for (Mod m : PupperClient.getInstance().getModManager().getMods()) {
            Item i = new Item(m);
            if (m.isEnabled()) {
                i.pressAnimation.setPressed();
            }
            items.add(i);
        }
        for (Item i : items) {
            i.xAnimation.setFirstTick(true);
            i.yAnimation.setFirstTick(true);
        }
        updateFilteredItems();
    }

    private void updateFilteredItems() {
        String query = searchBar.getText();
        if (Objects.equals(filteredQuery, query) && filteredCategory == selectedCategory
            && filteredSourceSize == items.size()) return;
        filteredQuery = query;
        filteredCategory = selectedCategory;
        filteredSourceSize = items.size();
        filteredItems = items.stream()
            .filter(i -> {
                Mod m = i.mod;
                if (m.isHidden()) return false;
                boolean searchMatch = query.isEmpty() || SearchUtils.isSimilar(m.getName(), query);
                boolean categoryMatch = selectedCategory == Category.ALL ||
                    (m.getCategory() != null && m.getCategory().name().equalsIgnoreCase(selectedCategory.name()));
                return searchMatch && categoryMatch;
            })
            .collect(Collectors.toList());
    }

    private float getEstimatedTextWidth(String text) {
        float width = 0;
        float wideCharWidth = 16.0f;
        float narrowCharWidth = 8.5f;
        for (char c : text.toCharArray()) {
            if (c >= '一' && c <= '\u9FFF' || c >= '\u3000' && c <= '\u303F' ||
                c >= '\u3040' && c <= 'ゟ' || c >= '゠' && c <= 'ヿ' ||
                c >= '\uFF00' && c <= '\uFFEF') {
                width += wideCharWidth;
            } else {
                width += narrowCharWidth;
            }
        }
        return width;
    }

    @Override
    public void draw(double mouseX, double mouseY) {
        updateFilteredItems();
        ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

        double relativeMouseY = mouseY - scrollHelper.getValue();
        Skia.save();
        Skia.translate(0, scrollHelper.getValue());

        super.draw(mouseX, relativeMouseY);

        float categoryBarY = y + 56;
        float categoryBarHeight = 24;
        float categoryBarMarginBottom = 16;
        float categoryX = x + 26;

        for (Category category : Category.values()) {
            String categoryName = category.getName();
            float textWidth = getEstimatedTextWidth(categoryName);
            float padding = 20.0f;
            float buttonWidth = textWidth + padding;
            boolean isSelected = category == selectedCategory;
            boolean isHovered = MouseUtils.isInside(mouseX, relativeMouseY, categoryX, categoryBarY, buttonWidth, categoryBarHeight);
            Skia.drawRoundedRect(categoryX, categoryBarY, buttonWidth, categoryBarHeight, 12,
                isSelected ? palette.getSurfaceContainerHighest() :
                    isHovered ? palette.getSurfaceContainerHigh() : palette.getSurfaceContainerLow());
            Skia.drawFullCenteredText(categoryName, categoryX + buttonWidth / 2, categoryBarY + categoryBarHeight / 2,
                isSelected ? palette.getOnSurface() : palette.getOnSurfaceVariant(), Fonts.getRegular(16));
            categoryX += buttonWidth + 10;
        }

        int index = 0;
        float offsetX = 26;
        float offsetY = categoryBarHeight + categoryBarMarginBottom;

        for (Item i : filteredItems) {
            Mod m = i.mod;
            float itemX = x + offsetX;
            float itemY = y + 96 + offsetY;

            i.focusAnimation.onTick(MouseUtils.isInside(mouseX, relativeMouseY, itemX, itemY, 244, 116 + 35) ? (i.pressed ? 0.12F : 0.08F) : 0, 8);
            i.xAnimation.onTick(itemX, 14);
            i.yAnimation.onTick(itemY, 14);
            itemX = i.xAnimation.getValue();
            itemY = i.yAnimation.getValue();

            float visibleY = itemY + scrollHelper.getValue();
            if (visibleY + 151 < y + 80 || visibleY > y + height) {
                index++;
                offsetX += 32 + 244;
                if (index % 3 == 0) {
                    offsetX = 26;
                    offsetY += 22 + 151;
                }
                continue;
            }

            Skia.drawRoundedRectVarying(itemX, itemY, 244, 116, 26, 26, 0, 0, palette.getSurfaceContainerLow());
            Skia.drawRoundedRectVarying(itemX, itemY + 116, 244, 35, 0, 0, 26, 26, palette.getSurfaceContainerLow());
            Skia.drawRoundedRectVarying(itemX, itemY + 116, 244, 35, 0, 0, 26, 26, ColorUtils.applyAlpha(palette.getSurfaceContainerLowest(), i.focusAnimation.getValue()));
            Skia.drawOutline(itemX, itemY, 244, 151, 26, 1,
                ColorUtils.applyAlpha(palette.getOutlineVariant(), 0.45F));

            Skia.save();
            Skia.clip(itemX, itemY + 116, 244, 35, 0, 0, 26, 26);
            i.pressAnimation.draw(itemX, itemY + 116, 224, 35, palette.getSurfaceContainerHighest(), 1);
            Skia.restore();

            String modname;
            if (m.getName() == null || Objects.equals(m.getName(), "null"))  modname = m.getRawName();
            else modname = m.getName();

            Skia.drawFullCenteredText(modname, itemX + ((float) 244 / 2), itemY + 116 + ((float) 35 / 2), palette.getOnSurfaceVariant(), Fonts.getRegular(16));
            Skia.drawFullCenteredText(m.getIcon(), itemX + ((float) 244 / 2), itemY + ((float) 116 / 2), palette.getOnSurfaceVariant(), Fonts.getIcon(68));

            index++;
            offsetX += 32 + 244;
            if (index % 3 == 0) {
                offsetX = 26;
                offsetY += 22 + 151;
            }
        }

        scrollHelper.setMaxScroll(151, 22, filteredItems.size() + 3, 3, height);

        Skia.restore();
    }

    @Override
    public void mousePressed(double mouseX, double mouseY, int button) {
        double relativeMouseY = mouseY - scrollHelper.getValue();

        super.mousePressed(mouseX, relativeMouseY, button);

        float categoryBarY = y + 56;
        float categoryBarHeight = 24;
        float categoryX = x + 26;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (Category category : Category.values()) {
                String categoryName = category.getName();
                float textWidth = getEstimatedTextWidth(categoryName);
                float padding = 20.0f;
                float buttonWidth = textWidth + padding;
                if (MouseUtils.isInside(mouseX, relativeMouseY, categoryX, categoryBarY, buttonWidth, categoryBarHeight)) {
                    if (this.selectedCategory != category) {
                        this.selectedCategory = category;
                    }
                    return;
                }
                categoryX += buttonWidth + 10;
            }
        }

        for (Item i : filteredItems) {
            float itemX = i.xAnimation.getValue();
            float itemY = i.yAnimation.getValue();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (MouseUtils.isInside(mouseX, relativeMouseY, itemX, itemY + 116, 244, 35)) {
                    i.pressed = true;
                }
            }
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        double relativeMouseY = mouseY - scrollHelper.getValue();
        for (Item i : filteredItems) {
            Mod m = i.mod;
            float itemX = i.xAnimation.getValue();
            float itemY = i.yAnimation.getValue();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (MouseUtils.isInside(mouseX, relativeMouseY, itemX, itemY + 116, 244, 35)) {
                    m.toggle();
                    if (m.isEnabled()) {
                        i.pressAnimation.onPressed(mouseX, relativeMouseY, itemX, itemY + 116);
                    } else {
                        i.pressAnimation.onReleased(mouseX, relativeMouseY, itemX, itemY + 116);
                    }
                }
                if (MouseUtils.isInside(mouseX, relativeMouseY, itemX, itemY, 244, 116)
                    && !PupperClient.getInstance().getModManager().getSettingsByMod(m).isEmpty()) {
                    parent.setCurrentPage(new SettingsImplPage(parent, this.getClass(), m));
                    this.setTransition(new LeftRightTransition(true));
                }
            }
            i.pressed = false;
        }
    }

    @Override
    public void onClosed() {
        this.setTransition(new RightLeftTransition(true));
    }

    private class Item {
        private final Mod mod;
        private SimpleAnimation focusAnimation = new SimpleAnimation();
        private SimpleAnimation xAnimation = new SimpleAnimation();
        private SimpleAnimation yAnimation = new SimpleAnimation();
        private PressAnimation pressAnimation = new PressAnimation();
        private boolean pressed;

        private Item(Mod mod) {
            this.mod = mod;
            this.pressed = false;
        }
    }
}
