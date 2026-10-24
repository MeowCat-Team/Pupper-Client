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

package cn.pupperclient.utils.minecraft.item;

import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.misc.RomanConverter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class ItemInfoText {
    private ItemInfoText() { }

    public record Selection(Item item, DataComponentPatch components) { }
    public static Selection selection(ItemStack stack) {
        return stack.isEmpty() ? null : new Selection(stack.getItem(),
            stack.getComponentsPatch().forget(type -> type == DataComponents.DAMAGE));
    }

    public static int inventoryCount(ItemStack held, Iterable<ItemStack> inventory, ItemStack offhand) {
        if (held.isEmpty()) return 0;
        int count = 0;
        for (var stack : inventory) if (ItemStack.isSameItemSameComponents(held, stack)) count += stack.getCount();
        if (ItemStack.isSameItemSameComponents(held, offhand)) count += offhand.getCount();
        return count;
    }
    public static List<String> enchantments(ItemStack stack) {
        var stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        var enchantments = stack.getEnchantments().isEmpty() ? stored : stack.getEnchantments();
        var entries = new ArrayList<>(enchantments.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().unwrapKey().map(key -> key.identifier().toString())
            .orElseGet(() -> entry.getKey().value().description().getString())));
        return entries.stream().map(entry -> {
            var holder = entry.getKey();
            String name = holder.value().description().getString();
            var key = holder.unwrapKey();
            if (key.isPresent() && key.get().identifier().getNamespace().equals("minecraft")) {
                String translation = "hud.enchantment." + key.get().identifier().getPath();
                String translated = I18n.get(translation);
                if (!translated.equals(translation)) name = translated;
            }
            return name + " " + level(entry.getIntValue());
        }).toList();
    }
    public static String level(int level) {
        return level > 0 && level <= 10 ? RomanConverter.intToRomanByPlace(level) : Integer.toString(level);
    }
}
