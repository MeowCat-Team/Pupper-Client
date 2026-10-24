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

package cn.pupperclient.utils.network;

import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

/** Restricts remote text lookups to vanilla names; local UI contents are never marked. */
public final class ServerTextPrivacy {
    private static volatile boolean enabled;

    private ServerTextPrivacy() {}

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isRemoteConnection() {
        return Minecraft.getInstance().getCurrentServer() != null;
    }

    public static Component markNetworkText(Component component) {
        return isRemoteConnection() ? markServerText(component) : component;
    }

    public static Component markServerText(Component component) {
        visitContents(component, contents -> {
            if (contents instanceof ServerTextContents serverText) {
                serverText.pupper$markServerSupplied();
            }
        });
        return component;
    }

    public static String resolveTranslation(Language language, String key, String fallback, boolean serverSupplied) {
        // DEFAULT_INSTANCE reads the bundled vanilla dictionary, independently of mods/resource packs.
        return enabled && serverSupplied && !Language.DEFAULT_INSTANCE.has(key)
            ? fallback : language.getOrDefault(key, fallback);
    }

    public static boolean protectKeybind(String name, boolean serverSupplied) {
        return enabled && serverSupplied && !Language.DEFAULT_INSTANCE.has(name);
    }

    public static void visitContents(Component root, Consumer<ComponentContents> visitor) {
        Set<Component> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Component> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Component component = pending.removeLast();
            if (!visited.add(component)) continue;

            ComponentContents contents = component.getContents();
            visitor.accept(contents);
            pending.addAll(component.getSiblings());
            if (contents instanceof TranslatableContents translation) {
                for (Object argument : translation.getArgs()) {
                    if (argument instanceof Component nested) pending.add(nested);
                }
            } else if (contents instanceof NbtContents nbt) {
                nbt.separator().ifPresent(pending::add);
            } else if (contents instanceof SelectorContents selector) {
                selector.separator().ifPresent(pending::add);
            }
            if (component.getStyle().getHoverEvent() instanceof HoverEvent.ShowText hover) {
                pending.add(hover.value());
            } else if (component.getStyle().getHoverEvent() instanceof HoverEvent.ShowEntity hover) {
                hover.entity().name.ifPresent(pending::add);
            }
        }
    }
}
