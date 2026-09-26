package cn.pupperclient.gui.tooltip;

import java.awt.Color;
import java.util.List;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.utils.color.ColorUtils;
import cn.pupperclient.utils.language.I18n;
import io.github.humbleui.skija.ClipMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** Skia preview for shulker boxes. Minecraft supplies only the actual item sprites. */
public final class ShulkerPreview {
    private static final int COLUMNS = 9, ROWS = 3, SLOT_SIZE = 18;
    private static final int GRID_X = 8, GRID_Y = 26;
    private static final int WIDTH = GRID_X * 2 + COLUMNS * SLOT_SIZE;
    private static final int HEIGHT = GRID_Y + ROWS * SLOT_SIZE + 20;
    private static ShulkerPreview active;

    private final NonNullList<ItemStack> contents = NonNullList.withSize(COLUMNS * ROWS, ItemStack.EMPTY);
    private ItemStack source = ItemStack.EMPTY;
    private int x, y, mouseX, mouseY, screenWidth, screenHeight, hoveredIndex = -1;
    private boolean locked;

    public boolean extract(GuiGraphicsExtractor graphics, ItemStack hovered, int mouseX, int mouseY,
                           int screenWidth, int screenHeight, boolean controlDown) {
        if (!controlDown) locked = false;
        if (!locked && isShulkerBox(hovered)) {
            updateSource(hovered);
            x = clamp(mouseX + 14, 4, screenWidth - WIDTH - 4);
            if (x < mouseX && mouseX - WIDTH - 14 >= 4) x = mouseX - WIDTH - 14;
            y = clamp(mouseY - 12, 4, screenHeight - HEIGHT - 4);
            locked = controlDown;
        } else if (!locked) {
            clear();
            return false;
        }
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        hoveredIndex = controlDown ? slotAt(mouseX, mouseY) : -1;
        active = this;

        // Minecraft renders item models, count, durability and glint. Skia paints the card around them.
        ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
        graphics.nextStratum();
        for (int index = 0; index < contents.size(); index++) {
            int cellX = x + GRID_X + index % COLUMNS * SLOT_SIZE;
            int cellY = y + GRID_Y + index / COLUMNS * SLOT_SIZE;
            graphics.fill(cellX, cellY, cellX + SLOT_SIZE, cellY + SLOT_SIZE,
                (index == hoveredIndex ? palette.getSecondaryContainer() : palette.getSurfaceContainerLow()).getRGB());
            ItemStack stack = contents.get(index);
            if (!stack.isEmpty()) {
                graphics.item(stack, cellX + 1, cellY + 1);
                graphics.itemDecorations(Minecraft.getInstance().font, stack, cellX + 1, cellY + 1);
            }
        }
        return true;
    }

    public static void renderActive(Screen screen) {
        if (active != null && screen != null) active.renderSkia();
    }

    public static boolean hasActive() {
        return active != null;
    }

    private void renderSkia() {
        ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
        Skia.save();
        try {
            Skia.clip(x + GRID_X, y + GRID_Y, COLUMNS * SLOT_SIZE, ROWS * SLOT_SIZE, 0,
                ClipMode.DIFFERENCE);
            Skia.drawShadow(x, y, WIDTH, HEIGHT, 14);
            Skia.drawRoundedRect(x, y, WIDTH, HEIGHT, 14, palette.getSurfaceContainerHigh());
        } finally {
            Skia.restore();
        }
        Skia.drawOutline(x, y, WIDTH, HEIGHT, 14, 1,
            ColorUtils.applyAlpha(palette.getOutlineVariant(), 0.65F));
        Skia.drawHeightCenteredText(Skia.getLimitText(source.getHoverName().getString(), Fonts.getMedium(12),
            WIDTH - 16), x + GRID_X, y + 13, palette.getOnSurface(), Fonts.getMedium(12));
        for (int index = 0; index < contents.size(); index++) {
            int cellX = x + GRID_X + index % COLUMNS * SLOT_SIZE;
            int cellY = y + GRID_Y + index / COLUMNS * SLOT_SIZE;
            Skia.drawOutline(cellX, cellY, SLOT_SIZE, SLOT_SIZE, 2, 0.7F,
                ColorUtils.applyAlpha(palette.getOutlineVariant(), index == hoveredIndex ? 0.9F : 0.34F));
        }
        Skia.drawHeightCenteredText(I18n.get(locked ? "hud.shulker.locked" : "hud.shulker.hint"),
            x + GRID_X, y + HEIGHT - 10, palette.getOnSurfaceVariant(), Fonts.getRegular(10));
        if (hoveredIndex >= 0 && !contents.get(hoveredIndex).isEmpty())
            renderItemTooltip(contents.get(hoveredIndex), palette);
    }

    private void renderItemTooltip(ItemStack stack, ColorPalette palette) {
        List<Component> lines = Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
        int count = Math.min(lines.size(), Math.max(1, (screenHeight - 20) / 15));
        if (count == 0) return;
        float textWidth = 0;
        for (int index = 0; index < count; index++)
            textWidth = Math.max(textWidth, Skia.getTextBounds(lines.get(index).getString(), Fonts.getRegular(11)).getWidth());
        float width = Math.min(Math.min(300, screenWidth - 8), textWidth + 16);
        float height = count * 15 + 12;
        float tipX = clamp(mouseX + 12, 4, Math.max(4, screenWidth - (int) width - 4));
        float tipY = clamp(mouseY + 8, 4, Math.max(4, screenHeight - (int) height - 4));
        Skia.drawShadow(tipX, tipY, width, height, 10);
        Skia.drawRoundedRect(tipX, tipY, width, height, 10, palette.getSurfaceContainerHighest());
        Skia.drawOutline(tipX, tipY, width, height, 10, 1,
            ColorUtils.applyAlpha(palette.getOutlineVariant(), 0.7F));
        for (int index = 0; index < count; index++) {
            String line = Skia.getLimitText(lines.get(index).getString(), Fonts.getRegular(11), width - 16);
            Color color = index == 0 ? palette.getOnSurface() : palette.getOnSurfaceVariant();
            Skia.drawHeightCenteredText(line, tipX + 8, tipY + 12 + index * 15, color, Fonts.getRegular(11));
        }
    }

    public boolean contains(double mouseX, double mouseY) {
        return locked && inside(mouseX, mouseY, x, y, WIDTH, HEIGHT);
    }

    public void clear() {
        if (active == this) active = null;
        source = ItemStack.EMPTY;
        locked = false;
        hoveredIndex = -1;
        for (int i = 0; i < contents.size(); i++) contents.set(i, ItemStack.EMPTY);
    }

    private int slotAt(double px, double py) {
        if (!inside(px, py, x + GRID_X, y + GRID_Y, COLUMNS * SLOT_SIZE, ROWS * SLOT_SIZE)) return -1;
        int column = (int) (px - x - GRID_X) / SLOT_SIZE;
        int row = (int) (py - y - GRID_Y) / SLOT_SIZE;
        return row * COLUMNS + column;
    }

    private void updateSource(ItemStack stack) {
        if (ItemStack.isSameItemSameComponents(source, stack)) return;
        source = stack.copy();
        for (int i = 0; i < contents.size(); i++) contents.set(i, ItemStack.EMPTY);
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) container.copyInto(contents);
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
            && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, Math.max(minimum, maximum)));
    }

    private static boolean inside(double px, double py, int left, int top, int width, int height) {
        return px >= left && px < left + width && py >= top && py < top + height;
    }
}
