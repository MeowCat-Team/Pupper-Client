package cn.pupperclient.gui.tooltip;

import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.List;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.api.hud.design.HUDColors;
import cn.pupperclient.management.mod.api.hud.design.HUDDesign;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.color.ColorUtils;
import cn.pupperclient.utils.language.I18n;
import io.github.humbleui.skija.ClipMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** Skia container card; Minecraft still renders item sprites and decorations. */
public final class ContainerPreview {
    private static final int COLUMNS = 9, ROWS = 3, SLOT_SIZE = 18;
    private static final int GRID_X = 12, GRID_Y = 32;
    private static final int GRID_WIDTH = COLUMNS * SLOT_SIZE, GRID_HEIGHT = ROWS * SLOT_SIZE;
    private static final int WIDTH = GRID_X * 2 + GRID_WIDTH;
    private static final int HEIGHT = GRID_Y + GRID_HEIGHT + 24;
    private static final NonNullList<ItemStack> ENDER_CHEST_CONTENTS =
        NonNullList.withSize(COLUMNS * ROWS, ItemStack.EMPTY);
    private static WeakReference<ClientPacketListener> cachedConnection = new WeakReference<>(null);
    private static boolean enderChestKnown;
    private static int enderChestRevision;
    private static ContainerPreview active;

    private enum Kind { SHULKER, ENDER_CHEST }

    private final NonNullList<ItemStack> contents = NonNullList.withSize(COLUMNS * ROWS, ItemStack.EMPTY);
    private ItemStack source = ItemStack.EMPTY;
    private Kind sourceKind;
    private int sourceRevision = -1;
    private int x, y, mouseX, mouseY, screenWidth, screenHeight, hoveredIndex = -1;
    private boolean locked, hasContents;

    public boolean extract(GuiGraphicsExtractor graphics, ItemStack hovered, int mouseX, int mouseY,
                           int screenWidth, int screenHeight, boolean controlDown) {
        syncConnection();
        if (!controlDown) locked = false;
        Kind hoveredKind = kindOf(hovered);
        if (!locked && hoveredKind != null) {
            updateSource(hovered, hoveredKind);
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
        hoveredIndex = controlDown && hasContents ? slotAt(mouseX, mouseY) : -1;
        active = this;

        if (hasContents) {
            HUDColors colors = design().colors();
            Color hoveredSlot = ColorUtils.applyAlpha(
                ColorUtils.blend(colors.accentContainer(), colors.raised(), 0.24),
                colors.raised().getAlpha());
            graphics.nextStratum();
            for (int index = 0; index < contents.size(); index++) {
                int cellX = x + GRID_X + index % COLUMNS * SLOT_SIZE;
                int cellY = y + GRID_Y + index / COLUMNS * SLOT_SIZE;
                graphics.fill(cellX, cellY, cellX + SLOT_SIZE, cellY + SLOT_SIZE,
                    (index == hoveredIndex ? hoveredSlot : colors.raised()).getRGB());
                ItemStack stack = contents.get(index);
                if (!stack.isEmpty()) {
                    graphics.item(stack, cellX + 1, cellY + 1);
                    graphics.itemDecorations(Minecraft.getInstance().font, stack, cellX + 1, cellY + 1);
                }
            }
        }
        return true;
    }

    /** The server only sends ender chest slots while its menu is open. Keep the last observed view. */
    public static void captureEnderChest(Screen screen) {
        if (!(screen instanceof ContainerScreen containerScreen)
            || containerScreen.getMenu().getRowCount() != ROWS
            || !(screen.getTitle().getContents() instanceof TranslatableContents title)
            || !"container.enderchest".equals(title.getKey())) return;
        syncConnection();
        if (cachedConnection.get() == null) return;
        Container inventory = containerScreen.getMenu().getContainer();
        for (int index = 0; index < ENDER_CHEST_CONTENTS.size(); index++)
            ENDER_CHEST_CONTENTS.set(index, inventory.getItem(index).copy());
        enderChestKnown = true;
        enderChestRevision++;
    }

    public static void renderActive(Screen screen) {
        if (active != null && screen != null) active.renderSkia();
    }

    public static boolean hasActive() { return active != null; }

    private void renderSkia() {
        HUDDesign design = design();
        HUDColors colors = design.colors();
        Skia.save();
        try {
            if (hasContents)
                Skia.clip(x + GRID_X, y + GRID_Y, GRID_WIDTH, GRID_HEIGHT, 0, ClipMode.DIFFERENCE);
            design.drawBackground(x, y, WIDTH, HEIGHT, 14);
        } finally {
            Skia.restore();
        }

        Skia.drawFullCenteredText(sourceKind == Kind.ENDER_CHEST ? Icon.INVENTORY_2 : Icon.BOX,
            x + 20, y + 16, colors.secondaryText(), HUDTokens.icon());
        Skia.drawHeightCenteredText(Skia.getLimitText(source.getHoverName().getString(), HUDTokens.title(),
            WIDTH - 46), x + 32, y + 16, colors.text(), HUDTokens.title());

        if (hasContents) {
            Color slotOutline = ColorUtils.applyAlpha(colors.outline(), 0.28F);
            for (int index = 0; index < contents.size(); index++) {
                int cellX = x + GRID_X + index % COLUMNS * SLOT_SIZE;
                int cellY = y + GRID_Y + index / COLUMNS * SLOT_SIZE;
                Skia.drawOutline(cellX, cellY, SLOT_SIZE, SLOT_SIZE, 3,
                    index == hoveredIndex ? 1 : 0.5F,
                    index == hoveredIndex ? colors.accent() : slotOutline);
            }
        } else {
            Skia.drawFullCenteredText(Icon.LOCK, x + WIDTH / 2F, y + GRID_Y + 16,
                colors.secondaryText(), HUDTokens.icon());
            Skia.drawFullCenteredText(I18n.get("hud.enderchest.openfirst"),
                x + WIDTH / 2F, y + GRID_Y + 34, colors.secondaryText(), HUDTokens.body());
        }

        String footer = sourceKind == Kind.ENDER_CHEST
            ? hasContents ? I18n.get(locked ? "hud.enderchest.locked" : "hud.enderchest.cached") : ""
            : I18n.get(locked ? "hud.shulker.locked" : "hud.shulker.hint");
        if (!footer.isEmpty())
            Skia.drawHeightCenteredText(Skia.getLimitText(footer, HUDTokens.label(), WIDTH - 24),
                x + GRID_X, y + HEIGHT - 12, colors.secondaryText(), HUDTokens.label());
        if (hoveredIndex >= 0 && !contents.get(hoveredIndex).isEmpty())
            renderItemTooltip(contents.get(hoveredIndex), design, colors);
    }

    private void renderItemTooltip(ItemStack stack, HUDDesign design, HUDColors colors) {
        List<Component> lines = Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
        int count = Math.min(lines.size(), Math.max(1, (screenHeight - 20) / 15));
        if (count == 0) return;
        float textWidth = 0;
        for (int index = 0; index < count; index++)
            textWidth = Math.max(textWidth,
                Skia.getTextBounds(lines.get(index).getString(), Fonts.getRegular(11)).getWidth());
        float width = Math.min(Math.min(300, screenWidth - 8), textWidth + 16);
        float height = count * 15 + 12;
        float tipX = clamp(mouseX + 12, 4, Math.max(4, screenWidth - (int) width - 4));
        float tipY = clamp(mouseY + 8, 4, Math.max(4, screenHeight - (int) height - 4));
        design.drawBackground(tipX, tipY, width, height, 10);
        for (int index = 0; index < count; index++) {
            String line = Skia.getLimitText(lines.get(index).getString(), Fonts.getRegular(11), width - 16);
            Color color = index == 0 ? colors.text() : colors.secondaryText();
            Skia.drawHeightCenteredText(line, tipX + 8, tipY + 12 + index * 15, color, Fonts.getRegular(11));
        }
    }

    public boolean contains(double mouseX, double mouseY) {
        return locked && inside(mouseX, mouseY, x, y, WIDTH, HEIGHT);
    }

    public void clear() {
        if (active == this) active = null;
        source = ItemStack.EMPTY;
        sourceKind = null;
        sourceRevision = -1;
        hasContents = false;
        locked = false;
        hoveredIndex = -1;
        for (int i = 0; i < contents.size(); i++) contents.set(i, ItemStack.EMPTY);
    }

    private int slotAt(double px, double py) {
        if (!inside(px, py, x + GRID_X, y + GRID_Y, GRID_WIDTH, GRID_HEIGHT)) return -1;
        int column = (int) (px - x - GRID_X) / SLOT_SIZE;
        int row = (int) (py - y - GRID_Y) / SLOT_SIZE;
        return row * COLUMNS + column;
    }

    private void updateSource(ItemStack stack, Kind kind) {
        int revision = kind == Kind.ENDER_CHEST ? enderChestRevision : -1;
        if (kind == sourceKind && revision == sourceRevision
            && ItemStack.isSameItemSameComponents(source, stack)) return;
        source = stack.copy();
        sourceKind = kind;
        sourceRevision = revision;
        for (int i = 0; i < contents.size(); i++) contents.set(i, ItemStack.EMPTY);
        if (kind == Kind.ENDER_CHEST) {
            hasContents = enderChestKnown;
            if (hasContents)
                for (int i = 0; i < contents.size(); i++) contents.set(i, ENDER_CHEST_CONTENTS.get(i));
        } else {
            hasContents = true;
            ItemContainerContents container = stack.get(DataComponents.CONTAINER);
            if (container != null) container.copyInto(contents);
        }
    }

    private static Kind kindOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() == Items.ENDER_CHEST) return Kind.ENDER_CHEST;
        return stack.getItem() instanceof BlockItem blockItem
            && blockItem.getBlock() instanceof ShulkerBoxBlock ? Kind.SHULKER : null;
    }

    private static void syncConnection() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (cachedConnection.get() == connection) return;
        cachedConnection = new WeakReference<>(connection);
        enderChestKnown = false;
        enderChestRevision++;
        for (int i = 0; i < ENDER_CHEST_CONTENTS.size(); i++) ENDER_CHEST_CONTENTS.set(i, ItemStack.EMPTY);
    }

    private static HUDDesign design() {
        return PupperClient.getInstance().getModManager().getCurrentDesign();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, Math.max(minimum, maximum)));
    }

    private static boolean inside(double px, double py, int left, int top, int width, int height) {
        return px >= left && px < left + width && py >= top && py < top + height;
    }
}
