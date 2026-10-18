package cn.pupperclient.gui.modmenu.pages;

import cn.pupperclient.PupperClient;
import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.gui.api.PupperClientGui;
import cn.pupperclient.gui.api.page.Page;
import cn.pupperclient.gui.api.page.impl.RightLeftTransition;
import cn.pupperclient.management.cape.CapeManager;
import cn.pupperclient.management.cape.CapeRenderer;
import cn.pupperclient.management.skin.SkinManager;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.handler.impl.ButtonHandler;
import cn.pupperclient.ui.component.impl.IconButton;
import cn.pupperclient.utils.thread.Multithreading;
import cn.pupperclient.utils.file.FileLocation;
import cn.pupperclient.utils.file.FileDialog;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.mouse.MouseUtils;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import io.github.humbleui.skija.Font;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class CosmeticsPage extends Page {
    private Category selectedCategory = Category.ALL;
    private final List<CosmeticItem> capeItems = new ArrayList<>();
    private final List<CosmeticItem> skinItems = new ArrayList<>();
    private Component addButton;
    private String selectedCapeId = null;
    private String selectedSkinId = null;

    public enum Category {
        ALL("text.all"),
        CAPE("text.cape"),
        SKIN("text.skin");

        private final String name;

        Category(String name) {
            this.name = name;
        }

        public String getName() {
            return I18n.get(name);
        }
    }

    private enum CosmeticType {
        CAPE,
        SKIN
    }

    public CosmeticsPage(PupperClientGui parent) {
        super(parent, "text.modmenu.cosmetics", Icon.SHOP, new RightLeftTransition(true));
    }

    @Override
    public void init() {
        super.init();

        addButton = new IconButton(Icon.ADD, x + width - 80, y + height - 80, IconButton.Size.LARGE, IconButton.Style.SECONDARY);
        addButton.setHandler(new ButtonHandler() {
            @Override
            public void onAction() {
                uploadCosmetic();
            }
        });

        loadExistingCosmetics();
        selectedCapeId = PupperClient.getInstance().getCapeManager().getSelectedCapeId();
        selectedSkinId = PupperClient.getInstance().getSkinManager().getSelectedSkinId();
    }

    private void loadExistingCosmetics() {
        capeItems.clear();
        skinItems.clear();
        loadCapeItems();
        loadSkinItems();
    }

    private void loadCapeItems() {
        File capesDir = FileLocation.CAPES_DIR;
        if (!capesDir.isDirectory()) return;
        File[] capeFiles = capesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (capeFiles == null) return;

        for (File capeFile : capeFiles) {
            String capeId = stripPng(capeFile.getName());
            capeItems.add(new CosmeticItem(CosmeticType.CAPE, capeId, capeFile));
            try {
                PupperClient.getInstance().getCapeManager().loadCape(capeId, Files.readAllBytes(capeFile.toPath()));
            } catch (IOException e) {
                cn.pupperclient.PupperLogger.error("CosmeticsPage", "Failed to load existing cape", e);
            }
        }
    }

    private void loadSkinItems() {
        File skinsDir = FileLocation.SKINS_DIR;
        if (!skinsDir.isDirectory()) return;
        File[] skinFiles = skinsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (skinFiles == null) return;

        for (File skinFile : skinFiles) {
            String skinId = stripPng(skinFile.getName());
            skinItems.add(new CosmeticItem(CosmeticType.SKIN, skinId, skinFile));
            try {
                PupperClient.getInstance().getSkinManager().loadSkin(skinId, Files.readAllBytes(skinFile.toPath()));
            } catch (IOException e) {
                cn.pupperclient.PupperLogger.error("CosmeticsPage", "Failed to load existing skin", e);
            }
        }
    }

    private static String stripPng(String name) {
        return name.substring(0, name.length() - 4);
    }

    private List<CosmeticItem> visibleItems() {
        if (selectedCategory == Category.CAPE) return capeItems;
        if (selectedCategory == Category.SKIN) return skinItems;

        List<CosmeticItem> all = new ArrayList<>(skinItems.size() + capeItems.size());
        all.addAll(skinItems);
        all.addAll(capeItems);
        return all;
    }

    private void uploadCosmetic() {
        Multithreading.runAsync(() -> {
            boolean uploadSkin = selectedCategory == Category.SKIN;
            var result = FileDialog.chooseFile(uploadSkin ? "Select Skin" : "Select Cape", "png");

            if (result.left()) {
                copyCosmetic(result.right(), uploadSkin ? FileLocation.SKINS_DIR : FileLocation.CAPES_DIR, uploadSkin);
            }
        });
    }

    private void copyCosmetic(File selectedFile, File directory, boolean skin) {
        if (!(skin ? validateSkin(selectedFile) : validateCape(selectedFile))) {
            PupperClient.LOGGER.warn("Invalid {} format: {}", skin ? "skin" : "cape", selectedFile.getAbsolutePath());
            return;
        }

        String processedName = selectedFile.getName().replace(" ", "_");
        File targetFile = new File(directory, processedName);
        if (targetFile.exists()) {
            PupperClient.LOGGER.warn("{} already exists: {}", skin ? "Skin" : "Cape", processedName);
            return;
        }

        try {
            Files.copy(selectedFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Minecraft.getInstance().execute(this::loadExistingCosmetics);
        } catch (IOException e) {
            cn.pupperclient.PupperLogger.error("CosmeticsPage", "Failed to copy uploaded " + (skin ? "skin" : "cape"), e);
        }
    }

    private boolean validateCape(File file) {
        return validateImage(file, false);
    }

    private boolean validateSkin(File file) {
        return validateImage(file, true);
    }

    private boolean validateImage(File file, boolean skin) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) return false;
            int width = image.getWidth();
            int height = image.getHeight();
            if (skin) return width == 64 && (height == 64 || height == 32);
            return (width == 64 && height == 32) || (width == 128 && height == 64);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void draw(double mouseX, double mouseY) {
        super.draw(mouseX, mouseY);

        double relativeMouseY = mouseY - scrollHelper.getValue();

        drawCategoryBar(mouseX, mouseY);

        Skia.save();
        Skia.clip(x, y + 88, width, height - 88, 0);
        Skia.translate(0, scrollHelper.getValue());
        drawMd3Style(mouseX, relativeMouseY);
        Skia.restore();
        addButton.draw(mouseX, mouseY);
    }

    private void drawCategoryBar(double mouseX, double mouseY) {
        ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
        float categoryBarY = y + 56;
        float categoryBarHeight = 24;
        float categoryX = x + 26;

        for (Category category : Category.values()) {
            String categoryName = category.getName();
            float textWidth = getTextWidth(categoryName, Fonts.getRegular(16));
            float padding = 20.0f;
            float buttonWidth = textWidth + padding;
            boolean isSelected = category == selectedCategory;
            boolean isHovered = MouseUtils.isInside(mouseX, mouseY, categoryX, categoryBarY, buttonWidth, categoryBarHeight);

            Skia.drawRoundedRect(categoryX, categoryBarY, buttonWidth, categoryBarHeight, 12,
                MaterialTheme.surface(isSelected ? palette.getSecondaryContainer() :
                    isHovered ? palette.getSurfaceContainerHigh() : palette.getSurfaceContainerLow()));
            Skia.drawFullCenteredText(categoryName, categoryX + buttonWidth / 2, categoryBarY + categoryBarHeight / 2,
                isSelected ? palette.getOnSecondaryContainer() : palette.getOnSurfaceVariant(), Fonts.getMedium(14));
            categoryX += buttonWidth + 10;
        }
    }

    private float getTextWidth(String text, Font font) {
        return Skia.getTextBounds(text, font).getWidth();
    }

    private void drawMd3Style(double mouseX, double mouseY) {
        ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
        float startX = x + 32;
        float startY = y + 120;
        float itemWidth = 140;
        float itemHeight = 220;
        float spacing = 20;
        int itemsPerRow = 5;
        List<CosmeticItem> items = visibleItems();

        for (int i = 0; i < items.size(); i++) {
            CosmeticItem item = items.get(i);
            int row = i / itemsPerRow;
            int col = i % itemsPerRow;
            float itemX = startX + col * (itemWidth + spacing);
            float itemY = startY + row * (itemHeight + spacing);

            item.xAnimation.onTick(itemX, 14);
            item.yAnimation.onTick(itemY, 14);
            itemX = item.xAnimation.getValue();
            itemY = item.yAnimation.getValue();

            boolean isSelected = item.isSelected(selectedCapeId, selectedSkinId);
            boolean isHovered = MouseUtils.isInside(mouseX, mouseY, itemX, itemY, itemWidth, itemHeight);
            item.focusAnimation.onTick(isHovered ? 1 : 0, 10);

            MaterialTheme.card(itemX + 5, itemY + 5, itemWidth - 10, itemHeight - 10, MaterialTheme.CARD_RADIUS, palette);
            Skia.drawRoundedRect(itemX + 5, itemY + 5, itemWidth - 10, itemHeight - 10, MaterialTheme.CARD_RADIUS,
                MaterialTheme.alpha(palette.getPrimary(), (isSelected ? 0.12F : 0) + item.focusAnimation.getValue() * 0.06F));

            if (isSelected) {
                Skia.drawOutline(itemX + 3, itemY + 3, itemWidth - 6, itemHeight - 6, MaterialTheme.CARD_RADIUS + 2, 1.5F, palette.getPrimary());
            }

            if (item.type == CosmeticType.CAPE) {
                drawCape(item, itemX, itemY, itemWidth, itemHeight, palette);
            } else {
                drawSkin(item, itemX, itemY, itemWidth, itemHeight, palette);
            }

            String label = limitLabel(item.id);
            Skia.drawFullCenteredText(label, itemX + itemWidth / 2, itemY + itemHeight - 20,
                isSelected ? palette.getPrimary() : palette.getOnSurfaceVariant(), Fonts.getRegular(11));
        }

        int totalRows = (int) Math.ceil((double) items.size() / itemsPerRow);
        float totalHeight = totalRows * (itemHeight + spacing);
        scrollHelper.setMaxScroll(totalHeight, height - 200);
    }

    private void drawCape(CosmeticItem item, float itemX, float itemY, float itemWidth, float itemHeight,
                          ColorPalette palette) {
        Identifier capeTexture = PupperClient.getInstance().getCapeManager().getLoadedCape(item.id);
        if (capeTexture != null) {
            CapeRenderer.renderRoundedCapePreview(capeTexture,
                itemX + 5, itemY + 5, itemWidth - 10, itemHeight - 10, MaterialTheme.CARD_RADIUS);
        } else {
            Skia.drawRoundedRect(itemX + 5, itemY + 5, itemWidth - 10, itemHeight - 10, MaterialTheme.CARD_RADIUS,
                MaterialTheme.alpha(palette.getSurfaceVariant(), MaterialTheme.opacity() * 0.2F));
            Skia.drawFullCenteredText("Loading...", itemX + itemWidth / 2, itemY + itemHeight / 2,
                palette.getOnSurfaceVariant(), Fonts.getRegular(12));
        }
    }

    private void drawSkin(CosmeticItem item, float itemX, float itemY, float itemWidth, float itemHeight,
                          ColorPalette palette) {
        if (!item.file.exists()) {
            Skia.drawFullCenteredText(Icon.PERSON, itemX + itemWidth / 2, itemY + itemHeight / 2,
                palette.getOnSurfaceVariant(), Fonts.getRegular(40));
            return;
        }

        float scale = 3.0f;
        float modelWidth = 16 * scale;
        float modelHeight = 32 * scale;
        Skia.drawSkin(item.file, itemX + (itemWidth - modelWidth) / 2, itemY + 48, scale);
    }

    private String limitLabel(String label) {
        return label.length() > 12 ? label.substring(0, 12) + "..." : label;
    }

    @Override
    public void mousePressed(double mouseX, double mouseY, int button) {
        super.mousePressed(mouseX, mouseY, button);
        addButton.mousePressed(mouseX, mouseY, button);

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            float categoryBarY = y + 56;
            float categoryBarHeight = 24;
            float categoryX = x + 26;

            for (Category category : Category.values()) {
                String categoryName = category.getName();
                float textWidth = getTextWidth(categoryName, Fonts.getRegular(16));
                float padding = 20.0f;
                float buttonWidth = textWidth + padding;
                if (MouseUtils.isInside(mouseX, mouseY, categoryX, categoryBarY, buttonWidth, categoryBarHeight)) {
                    this.selectedCategory = category;
                    return;
                }
                categoryX += buttonWidth + 10;
            }
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
        addButton.mouseReleased(mouseX, mouseY, button);

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT
            || MouseUtils.isInside(mouseX, mouseY, addButton.getX(), addButton.getY(), addButton.getWidth(), addButton.getHeight())
            || !MouseUtils.isInside(mouseX, mouseY, x, y + 88, width, height - 88)) {
            return;
        }

        double relativeMouseY = mouseY - scrollHelper.getValue();

        handleMd3MouseRelease(mouseX, relativeMouseY);
    }

    private void handleMd3MouseRelease(double mouseX, double mouseY) {
        float startX = x + 32;
        float startY = y + 120;
        float itemWidth = 140;
        float itemHeight = 220;
        float spacing = 20;
        int itemsPerRow = 5;
        List<CosmeticItem> items = visibleItems();

        for (int i = 0; i < items.size(); i++) {
            int row = i / itemsPerRow;
            int col = i % itemsPerRow;
            float itemX = startX + col * (itemWidth + spacing);
            float itemY = startY + row * (itemHeight + spacing);

            if (MouseUtils.isInside(mouseX, mouseY, itemX, itemY, itemWidth, itemHeight)) {
                handleCosmeticItemClick(items.get(i));
                return;
            }
        }
    }

    private void handleCosmeticItemClick(CosmeticItem item) {
        if (item.type == CosmeticType.CAPE) {
            CapeManager capeManager = PupperClient.getInstance().getCapeManager();
            if (item.id.equals(selectedCapeId)) {
                selectedCapeId = null;
                capeManager.clearSelectedCape();
            } else {
                selectedCapeId = item.id;
                capeManager.selectCape(item.id);
            }
            return;
        }

        SkinManager skinManager = PupperClient.getInstance().getSkinManager();
        if (item.id.equals(selectedSkinId)) {
            selectedSkinId = null;
            skinManager.clearSelectedSkin();
        } else {
            selectedSkinId = item.id;
            skinManager.selectSkin(item.id);
        }
    }

    private static class CosmeticItem {
        public final CosmeticType type;
        public final String id;
        public final File file;
        public final SimpleAnimation xAnimation = new SimpleAnimation();
        public final SimpleAnimation yAnimation = new SimpleAnimation();
        public final SimpleAnimation focusAnimation = new SimpleAnimation();

        public CosmeticItem(CosmeticType type, String id, File file) {
            this.type = type;
            this.id = id;
            this.file = file;
        }

        public boolean isSelected(String capeId, String skinId) {
            return type == CosmeticType.CAPE ? id.equals(capeId) : id.equals(skinId);
        }
    }
}
