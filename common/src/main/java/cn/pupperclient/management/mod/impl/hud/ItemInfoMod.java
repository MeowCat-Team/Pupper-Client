/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventListener;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.event.client.RenderGameOverlayEvent;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.ContextInfoHUDMod;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.minecraft.item.HeldItemTracker;
import cn.pupperclient.utils.minecraft.item.ItemInfoText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ItemInfoMod extends ContextInfoHUDMod {
    private static ItemInfoMod instance;
    private final HeldItemTracker tracker = new HeldItemTracker();
    private Info info;

    public ItemInfoMod() {
        super("mod.iteminfo.name", "mod.iteminfo.description", Icon.INVENTORY_2, true);
        instance = this;
    }
    public static ItemInfoMod getInstance() { return instance; }

    @EventListener public void tick(ClientTickEvent event) {
        if (client.player == null || client.level == null) { tracker.reset(); info = null; return; }
        var inventory = client.player.getInventory();
        var held = client.player.getMainHandItem();
        tracker.update(client.player, inventory.getSelectedSlot(), ItemInfoText.selection(held));
        info = tracker.visible() ? describe(held) : null;
    }
    private Info describe(ItemStack held) {
        var details = new ArrayList<String>();
        var enchantments = ItemInfoText.enchantments(held);
        if (!enchantments.isEmpty()) details.add(String.join(" · ", enchantments));
        if (held.isStackable()) details.add(I18n.get("hud.iteminfo.total") + ": "
            + ItemInfoText.inventoryCount(held, client.player.getInventory().getNonEquipmentItems(), client.player.getOffhandItem()));
        return new Info(held.copy(), held.getHoverName().getString(), details, Icon.INVENTORY_2);
    }
    @Override protected Info currentInfo() { return info; }
    @Override protected Info previewInfo() {
        var held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandItem();
        return !held.isEmpty() ? describe(held) : new Info(new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.DIAMOND_SWORD).getHoverName().getString(), List.of(I18n.get("hud.enchantment.sharpness") + " V · "
                + I18n.get("hud.enchantment.unbreaking") + " III"), Icon.INVENTORY_2);
    }
    @EventListener public void extract(RenderGameOverlayEvent event) { extractInfo(event.getContext()); }
    @EventListener public void render(RenderSkiaEvent event) { drawInfo(); }
    @Override public void onDisable() { super.onDisable(); tracker.reset(); info = null; }
}
