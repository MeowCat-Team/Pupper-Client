package cn.pupperclient.event.client;

import cn.pupperclient.event.Event;
import net.minecraft.network.protocol.Packet;

public class SendPacketEvent extends Event {

	private Packet<?> packet;

	public SendPacketEvent(Packet<?> packet) {
		this.packet = packet;
	}

	public Packet<?> getPacket() {
		return packet;
	}

    public void setPacket(Packet<?> packet) {
        this.packet = packet;
    }
}
