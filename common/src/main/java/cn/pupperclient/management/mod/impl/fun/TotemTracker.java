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

package cn.pupperclient.management.mod.impl.fun;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.EventListener;
import cn.pupperclient.event.client.ReceivePacketEvent;
import cn.pupperclient.event.client.TotemEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.chat.ChatUtils;
import java.util.HashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;

public class TotemTracker extends Mod {
    private static TotemTracker instance;
    public final HashMap<String, Integer> popContainer = new HashMap<>();

    public TotemTracker() {
        super("mod.totemtracker.name", "mod.totemtracker.description", Icon.SECURITY, ModCategory.FUN);
        instance = this;
    }

    public static TotemTracker getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventListener
    public void onTotem(TotemEvent event) {
        Player player = event.getPlayer();
        char ch = 0;
        int l_Count = 1;
        if (popContainer.containsKey(player.getName().getString())) {
            l_Count = popContainer.get(player.getName().getString());
        }
        if (l_Count == 1) {
            if (player.equals(client.player)) {
                ChatUtils.addChatMessage(Component.nullToEmpty(String.format(ChatFormatting.WHITE + "You(%s)" + ChatFormatting.RESET + " popped " + ChatFormatting.GRAY + "%d" + ChatFormatting.RESET + " totem.", player.getName().getString(), l_Count)));
            } else {
                ChatUtils.addChatMessage(Component.nullToEmpty(String.format(ChatFormatting.WHITE + "%s" + ChatFormatting.RESET + " popped " + ChatFormatting.GRAY + "%d" + ChatFormatting.RESET + " totem.", player.getName().getString().replace(String.valueOf(ChatFormatting.PREFIX_CODE), ""), l_Count)));
            }
        } else {
            if (player.equals(client.player)) {
                ChatUtils.addChatMessage(Component.nullToEmpty(String.format(ChatFormatting.WHITE + "You(%s)" + ChatFormatting.RESET + " popped " + ChatFormatting.GRAY + "%d" + ChatFormatting.RESET + " totem.", player.getName().getString().replace(String.valueOf(ChatFormatting.PREFIX_CODE), ""), l_Count)));
            } else {
                ChatUtils.addChatMessage(Component.nullToEmpty(String.format(ChatFormatting.WHITE + "%s" + ChatFormatting.RESET + " has popped " + ChatFormatting.GRAY + "%d" + ChatFormatting.RESET + " totems.", player.getName().getString(), l_Count)));
            }
        }
    }

    @EventListener(priority = EventListener.Priority.HIGHEST)
    public void onPacketReceive(ReceivePacketEvent event) {
        if (event.getPacket() instanceof ClientboundEntityEventPacket packet) {
            if (packet.getEventId() == EntityEvent.PROTECTED_FROM_DEATH) {
                assert client.level != null;
                Entity entity = packet.getEntity(client.level);
                if(entity instanceof Player player) {
                    onTotemPop(player);
                }
            }
        }
    }

    public void onTotemPop(Player player) {
        int l_Count = 1;
        if (popContainer.containsKey(player.getName().getString())) {
            l_Count = popContainer.get(player.getName().getString());
            popContainer.put(player.getName().getString(), ++l_Count);
        } else {
            popContainer.put(player.getName().getString(), l_Count);
        }
        EventBus.getInstance().post(new TotemEvent(player));
    }
}
