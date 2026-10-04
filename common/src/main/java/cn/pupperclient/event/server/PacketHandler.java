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
