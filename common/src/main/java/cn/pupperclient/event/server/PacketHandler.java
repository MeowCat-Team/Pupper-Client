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

package cn.pupperclient.event.server;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.EventListener;
import cn.pupperclient.event.client.ReceivePacketEvent;
import cn.pupperclient.event.client.SendPacketEvent;
import cn.pupperclient.event.server.impl.AttackEntityEvent;
import cn.pupperclient.event.server.impl.DamageEntityEvent;
import cn.pupperclient.event.server.impl.GameJoinEvent;
import cn.pupperclient.event.server.impl.ReceiveChatEvent;
import cn.pupperclient.event.server.impl.SendChatEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;

public class PacketHandler {

    @EventListener
	public void onSendPacket(SendPacketEvent packetEvent) {

		Packet<?> basePacket = packetEvent.getPacket();

		if (basePacket instanceof ServerboundInteractPacket packet) {

			if (!packet.usingSecondaryAction()) {
				EventBus.getInstance()
						.post(new AttackEntityEvent((packet.entityId())));
			}
		}

		if (basePacket instanceof ServerboundChatPacket packet) {

            SendChatEvent event = new SendChatEvent(packet.message());

			EventBus.getInstance().post(event);

			if (event.isCancelled()) {
				packetEvent.setCancelled(true);
			}
		}
	};

    @EventListener
	public void onReceivePacket(ReceivePacketEvent packetEvent) {

		Packet<?> basePacket = packetEvent.getPacket();

		if (basePacket instanceof ClientboundDamageEventPacket packet) {

            EventBus.getInstance().post(new DamageEntityEvent(packet.entityId()));
		}

		if (basePacket instanceof ClientboundPlayerChatPacket packet) {

            ReceiveChatEvent event = new ReceiveChatEvent(packet.body().content());

			EventBus.getInstance().post(event);

			if (event.isCancelled()) {
				packetEvent.setCancelled(true);
			}
		}

		if (basePacket instanceof ClientboundSystemChatPacket packet) {

            ReceiveChatEvent event = new ReceiveChatEvent(packet.content().getString());

			EventBus.getInstance().post(event);

			if (event.isCancelled()) {
				packetEvent.setCancelled(true);
			}
		}

		if (basePacket instanceof ClientboundLoginPacket) {
			EventBus.getInstance().post(new GameJoinEvent());
		}
	};
}
