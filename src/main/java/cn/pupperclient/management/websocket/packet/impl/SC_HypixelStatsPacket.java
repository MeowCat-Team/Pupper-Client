package cn.pupperclient.management.websocket.packet.impl;

import com.google.gson.JsonObject;
import cn.pupperclient.management.websocket.packet.PupperClientPacket;

public class SC_HypixelStatsPacket extends PupperClientPacket {

	private final String uuid;
	
	public SC_HypixelStatsPacket(String uuid) {
		super("sc-hypixel-stats");
		this.uuid = uuid;
	}

	@Override
	public JsonObject toJson() {
		jsonObject.addProperty("uuid", uuid);
		return jsonObject;
	}
}
