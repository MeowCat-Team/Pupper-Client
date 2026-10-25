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

package cn.pupperclient.hud;

import cn.pupperclient.management.mod.api.hud.design.ContextInfoHudUi;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;
import cn.pupperclient.utils.minecraft.item.HeldItemTracker;
import cn.pupperclient.utils.minecraft.item.ItemInfoText;
import com.mojang.serialization.Lifecycle;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Real Minecraft stacks: expiry, component-aware totals, books and modded enchantments. */
public final class ItemInfoChecks {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // 26.2 binds default item components after the data-driven registries are ready.
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup()).forEach(pending -> pending.apply());
        totals();
        selectionLifetime();
        enchantments();
        System.out.println("Item info checks passed: timed selections, component-aware counts, enchantments and bilingual layouts.");
    }

    private static void totals() {
        var held = new ItemStack(Items.COBBLESTONE, 12);
        var renamed = new ItemStack(Items.COBBLESTONE, 40);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Different stack"));
        var inventory = List.of(held, new ItemStack(Items.COBBLESTONE, 64), renamed,
            new ItemStack(Items.STONE, 64), ItemStack.EMPTY);
        require(ItemInfoText.inventoryCount(held, inventory, new ItemStack(Items.COBBLESTONE, 3)) == 79,
            "Must count the held slot once, include offhand and exclude component/item variants");
        require(ItemInfoText.inventoryCount(held, inventory, renamed) == 76, "Different offhand components must not match");
        require(ItemInfoText.inventoryCount(ItemStack.EMPTY, inventory, held) == 0, "Empty selection has no total");
    }

    private static void selectionLifetime() {
        var tracker = new HeldItemTracker();
        var context = new Object();
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        tracker.update(context, 0, ItemInfoText.selection(sword));
        require(tracker.visible(), "Selecting an item must reveal the card");
        for (int tick = 1; tick < HeldItemTracker.DISPLAY_TICKS; tick++) {
            sword.setDamageValue(tick);
            tracker.update(context, 0, ItemInfoText.selection(sword));
            require(tracker.visible(), "Damage updates must not shorten the display");
        }
        tracker.update(context, 0, ItemInfoText.selection(sword));
        require(!tracker.visible(), "Damage updates must not keep the card visible indefinitely");
        tracker.update(context, 1, ItemInfoText.selection(sword));
        require(tracker.visible(), "Changing hotbar slots reveals even an identical item");
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Renamed"));
        tracker.update(context, 1, ItemInfoText.selection(sword));
        for (int tick = 1; tick < HeldItemTracker.DISPLAY_TICKS; tick++) tracker.update(context, 1, ItemInfoText.selection(sword));
        require(tracker.visible(), "Relevant component changes must restart the timer");
        tracker.update(context, 1, ItemInfoText.selection(sword));
        require(!tracker.visible(), "The refreshed card must also expire");
        tracker.update(new Object(), 1, ItemInfoText.selection(sword));
        require(tracker.visible(), "A new player context reveals the current item");
        tracker.update(context, 1, ItemInfoText.selection(ItemStack.EMPTY));
        require(!tracker.visible(), "Empty hand clears stale information");
        var stack = new ItemStack(Items.APPLE, 64);
        var identity = ItemInfoText.selection(stack);
        stack.setCount(1);
        require(identity.equals(ItemInfoText.selection(stack)), "Consuming stackable items must not restart the timer");
        tracker.reset();
        tracker.update(context, 1, identity);
        require(tracker.visible(), "Re-enabling a module must reveal the current item");
    }

    private static void enchantments() {
        var definition = Enchantment.definition(HolderSet.direct(Items.DIAMOND_SWORD.builtInRegistryHolder()),
            1, 10, Enchantment.constantCost(1), Enchantment.constantCost(1), 1, EquipmentSlotGroup.MAINHAND);
        var registry = new MappedRegistry<Enchantment>(Registries.ENCHANTMENT, Lifecycle.stable());
        var sharpness = registry.register(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("sharpness")),
            new Enchantment(Component.literal("Sharpness"), definition, HolderSet.direct(), DataComponentMap.EMPTY), RegistrationInfo.BUILT_IN);
        registry.freeze();
        Holder<Enchantment> custom = Holder.direct(new Enchantment(Component.literal("Custom Echo"), definition,
            HolderSet.direct(), DataComponentMap.EMPTY));
        var mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(sharpness, 5); mutable.set(custom, 255);
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        for (var language : List.of(Language.ENGLISH, Language.CHINESE)) {
            I18n.setLanguage(language);
            var details = ItemInfoText.enchantments(sword);
            require(details.contains(I18n.get("hud.enchantment.sharpness") + " V"), "Vanilla abbreviation must be localized");
            require(details.contains("Custom Echo 255"), "Modded enchantments and nonstandard levels must remain readable");
            require(details.equals(ItemInfoText.enchantments(book)), "Stored book enchantments must use the same formatting");
            require(ItemInfoText.enchantments(new ItemStack(Items.APPLE)).isEmpty(), "Unenchanted items have no enchantment line");
            var layout = ContextInfoHudUi.layout("A very long item name for clipping 与中文名称", List.of(
                String.join(" · ", details) + " · " + I18n.get("hud.enchantment.unbreaking") + " III",
                I18n.get("hud.iteminfo.total") + ": 2304"), 145);
            require(layout.width() <= 145 && layout.height() >= 40, "Card must respect its width budget");
            require(HUDTokens.title().measureText(layout.title()).getWidth() <= layout.width() - 50 + .1f, "Heading must fit");
            for (var line : layout.lines()) require(HUDTokens.body().measureText(line).getWidth() <= layout.width() - 50 + .1f,
                "Details must wrap or truncate without spilling outside the card");
            for (String key : List.of("mod.iteminfo.name", "mod.iteminfo.description", "mod.lookinfo.name", "mod.lookinfo.description",
                "hud.lookinfo.growth", "hud.lookinfo.health", "hud.lookinfo.count", "hud.lookinfo.unbreakable",
                "hud.lookinfo.harvestable", "hud.lookinfo.wrongtool", "hud.iteminfo.total"))
                require(!I18n.get(key).equals(key), "Missing translation: " + key);
        }
        require(ItemInfoText.level(1).equals("I") && ItemInfoText.level(10).equals("X")
            && ItemInfoText.level(11).equals("11"), "Standard levels use Roman numerals without unbounded conversion");
    }

    private static void require(boolean result, String reason) { if (!result) throw new AssertionError(reason); }
}
