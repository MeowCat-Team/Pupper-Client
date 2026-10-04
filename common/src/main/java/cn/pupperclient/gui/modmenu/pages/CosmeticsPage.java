package cn.pupperclient.gui.modmenu.pages;

import cn.pupperclient.PupperClient;
import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.gui.api.PupperClientGui;
import cn.pupperclient.gui.api.page.Page;
import cn.pupperclient.gui.api.page.impl.RightLeftTransition;
import cn.pupperclient.management.cape.CapeManager;
import cn.pupperclient.management.cape.CapeRenderer;
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
    private final List<CapeItem> capeItems = new ArrayList<>();
    private Component addButton;
    private String selectedCapeId = null;

    public enum Category {
        ALL("text.all"),
        CAPE("text.cape");

        private final String name;

        Category(String name) {
            this.name = name;
        }

        public String getName() {
            return I18n.get(name);
        }
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
                uploadCape();
            }
        });

        loadExistingCapes();
        selectedCapeId = PupperClient.getInstance().getCapeManager().getSelectedCapeId();
    }

    private void loadExistingCapes() {
        capeItems.clear();
        File capesDir = FileLocation.CAPES_DIR;
        if (capesDir.exists() && capesDir.isDirectory()) {
            File[] capeFiles = capesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
            if (capeFiles != null) {
                for (File capeFile : capeFiles) {
                    String capeId = capeFile.getName().replace(".png", "");
                    capeItems.add(new CapeItem(capeId, capeFile));
                    try {
                        byte[] capeData = Files.readAllBytes(capeFile.toPath());
                        PupperClient.getInstance().getCapeManager().loadCape(capeId, capeData);
                    } catch (IOException e) {
                        cn.pupperclient.PupperLogger.error("CosmeticsPage", "Failed to load existing cape", e);
                    }
                }
            }
        }
    }

    private void uploadCape() {
        Multithreading.runAsync(() -> {
            var result = FileDialog.chooseFile("Select Cape", "png");

            if (result.left()) {
                File selectedFile = result.right();
                if (validateCape(selectedFile)) {
                    String originalName = selectedFile.getName();
                    String processedName = originalName.replace(" ", "_");
                    File targetFile = new File(FileLocation.CAPES_DIR, processedName);

                    if (targetFile.exists()) {
                        PupperClient.LOGGER.warn("Cape already exists: {}", processedName);
                        return;
                    }

                    try {
                        Files.copy(selectedFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        Minecraft.getInstance().execute(this::loadExistingCapes);
                    } catch (IOException e) {
                        cn.pupperclient.PupperLogger.error("CosmeticsPage", "Failed to copy uploaded cape", e);
                    }
                } else {
                    PupperClient.LOGGER.warn("Invalid cape format: {}", selectedFile.getAbsolutePath());
                }
            }
        });
    }

    private boolean validateCape(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) return false;
            int width = image.getWidth();
            int height = image.getHeight();
            return (width == 64 && height == 32) || (width == 128 && height == 64);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void draw(double mouseX, double mouseY) {
        super.draw(mouseX, mouseY);

        double relativeMouseY = mouseY - scrollHelper.getValue();

        // Draw category bar (similar to ModsPage)
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

        for (int i = 0; i < capeItems.size(); i++) {
            CapeItem item = capeItems.get(i);
            int row = i / itemsPerRow;
            int col = i % itemsPerRow;
            float itemX = startX + col * (itemWidth + spacing);
            float itemY = startY + row * (itemHeight + spacing);

            item.xAnimation.onTick(itemX, 14);
            item.yAnimation.onTick(itemY, 14);
            itemX = item.xAnimation.getValue();
            itemY = item.yAnimation.getValue();

            boolean isSelected = item.capeId.equals(selectedCapeId);
            boolean isHovered = MouseUtils.isInside(mouseX, mouseY, itemX, itemY, itemWidth, itemHeight);
            item.focusAnimation.onTick(isHovered ? 1 : 0, 10);

            MaterialTheme.card(itemX + 5, itemY + 5, itemWidth - 10, itemHeight - 10, MaterialTheme.CARD_RADIUS, palette);
            Skia.drawRoundedRect(itemX + 5, itemY + 5, itemWidth - 10, itemHeight - 10, MaterialTheme.CARD_RADIUS,
                MaterialTheme.alpha(palette.getPrimary(), (isSelected ? 0.12F : 0) + item.focusAnimation.getValue() * 0.06F));

            if (isSelected) {
                Skia.drawOutline(itemX + 3, itemY + 3, itemWidth - 6, itemHeight - 6, MaterialTheme.CARD_RADIUS + 2, 1.5F, palette.getPrimary());
            }

            if (item.capeFile.exists()) {
                Identifier capeTexture = PupperClient.getInstance().getCapeManager().getLoadedCape(item.capeId);
                if (capeTexture != null) {
                    CapeRenderer.renderRoundedCapePreview(capeTexture,
                        itemX + 5, itemY + 5, itemWidth - 10, itemHeight - 10, MaterialTheme.CARD_RADIUS);
                } else {
                    // 如果纹理尚未加载，显示加载状态或占位符
                    Skia.drawRoundedRect(itemX + 5, itemY + 5, itemWidth - 10, itemHeight - 10, MaterialTheme.CARD_RADIUS,
                        MaterialTheme.alpha(palette.getSurfaceVariant(), MaterialTheme.opacity() * 0.2F));
                    // 可以添加加载中的文字提示
                    String loadingText = "Loading...";
                    float textWidth = Skia.getTextBounds(loadingText, Fonts.getRegular(12)).getWidth();
                    Skia.drawText(loadingText, itemX + itemWidth / 2 - textWidth / 2, 
                        itemY + itemHeight / 2, palette.getOnSurfaceVariant(), Fonts.getRegular(12));
                }
            }
        }

        int totalRows = (int) Math.ceil((double) capeItems.size() / itemsPerRow);
        float totalHeight = totalRows * (itemHeight + spacing);
        scrollHelper.setMaxScroll(totalHeight, height - 200);
    }

    @Override
    public void mousePressed(double mouseX, double mouseY, int button) {
        super.mousePressed(mouseX, mouseY, button);
        addButton.mousePressed(mouseX, mouseY, button);

        // Handle category selection
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
                    if (this.selectedCategory != category) {
                        this.selectedCategory = category;
                    }
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

        for (int i = 0; i < capeItems.size(); i++) {
            int row = i / itemsPerRow;
            int col = i % itemsPerRow;
            float itemX = startX + col * (itemWidth + spacing);
            float itemY = startY + row * (itemHeight + spacing);

            if (MouseUtils.isInside(mouseX, mouseY, itemX, itemY, itemWidth, itemHeight)) {
                handleCapeItemClick(capeItems.get(i));
                return;
            }
        }
    }

    private void handleCapeItemClick(CapeItem item) {
        CapeManager capeManager = PupperClient.getInstance().getCapeManager();
        if (item.capeId.equals(selectedCapeId)) {
            selectedCapeId = null;
            capeManager.clearSelectedCape();
        } else {
            selectedCapeId = item.capeId;
            capeManager.selectCape(item.capeId);
        }
    }

    private static class CapeItem {
        public final String capeId;
        public final File capeFile;
        public final SimpleAnimation xAnimation = new SimpleAnimation();
        public final SimpleAnimation yAnimation = new SimpleAnimation();
        public final SimpleAnimation focusAnimation = new SimpleAnimation();

        public CapeItem(String capeId, File capeFile) {
            this.capeId = capeId;
            this.capeFile = capeFile;
        }
    }
}
