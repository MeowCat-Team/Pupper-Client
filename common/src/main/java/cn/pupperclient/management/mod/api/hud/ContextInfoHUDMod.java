package cn.pupperclient.management.mod.api.hud;

import cn.pupperclient.gui.edithud.api.HUDCore;
import cn.pupperclient.management.mod.api.AnchorPosition;
import cn.pupperclient.management.mod.api.hud.design.ContextInfoHudUi;
import cn.pupperclient.mixin.mixins.accessors.PlayerTabOverlayAccessor;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.item.ItemStack;

/** Uses Minecraft's item renderer alongside the shared Blaze3D HUD card, with no backend calls. */
public abstract class ContextInfoHUDMod extends HUDMod {
    protected record Info(ItemStack icon, String title, List<String> details, String fallbackIcon) {
        public Info { details = List.copyOf(details); icon = icon == null ? ItemStack.EMPTY : icon; }
    }
    private Info prepared;
    private ContextInfoHudUi.Layout layout;
    private boolean iconExtracted;
    private float iconX, iconY, iconScale;

    protected ContextInfoHUDMod(String name, String description, String icon, boolean aboveHotbar) {
        super(name, description, icon);
        position.setSize(180, 40);
        position.setAnchor(aboveHotbar ? AnchorPosition.BOTTOM_CENTER : AnchorPosition.TOP_CENTER);
        position.setRawPosition(0, aboveHotbar ? -60 : 24);
    }
    protected abstract Info currentInfo();
    protected abstract Info previewInfo();

    private boolean canShow() {
        if (HUDCore.isEditing) return true;
        return client.player != null && client.level != null && !client.gui.hud.isHidden()
            && !((PlayerTabOverlayAccessor) client.gui.hud.getTabList()).pupper$isVisible()
            && (client.gui.screen() == null || client.gui.screen() instanceof ChatScreen);
    }
    private void prepare() {
        prepared = canShow() ? HUDCore.isEditing ? previewInfo() : currentInfo() : null;
        iconExtracted = false;
        if (prepared == null) { layout = null; position.setSize(0, 0); return; }
        float available = Math.min(280, client.getWindow().getGuiScaledWidth() / Math.max(.2f, position.getScale()) - 24);
        layout = ContextInfoHudUi.layout(prepared.title(), prepared.details(), available);
        position.setSize(layout.width(), layout.height());
    }
    protected final void extractInfo(GuiGraphicsExtractor graphics) {
        prepare();
        if (prepared == null || prepared.icon().isEmpty()) return;
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            iconX = getX(); iconY = getY(); iconScale = position.getScale();
            pose.translate(iconX, iconY).scale(iconScale);
            graphics.fill(10, 10, 30, 30, colors().surface().getRGB());
            pose.translate(10, 10).scale(ContextInfoHudUi.ICON_SIZE / 16);
            graphics.item(prepared.icon(), 0, 0);
            iconExtracted = true;
        } finally { pose.popMatrix(); }
    }
    protected final void drawInfo() {
        if (!canShow()) { prepared = null; position.setSize(0, 0); return; }
        if (prepared == null && HUDCore.isEditing) prepare();
        if (prepared == null || layout == null) return;
        boolean extracted = iconExtracted && getX() == iconX && getY() == iconY && position.getScale() == iconScale;
        begin();
        try { ContextInfoHudUi.draw(getX(), getY(), layout, prepared.fallbackIcon(), extracted, getDesign(), colors()); }
        finally { finish(); iconExtracted = false; }
    }
    @Override public void onDisable() {
        super.onDisable(); prepared = null; layout = null; iconExtracted = false; position.setSize(0, 0);
    }
}
