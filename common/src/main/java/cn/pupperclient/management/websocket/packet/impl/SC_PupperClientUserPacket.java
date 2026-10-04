package cn.pupperclient.management.websocket.packet.impl;

import com.google.gson.JsonObject;
import cn.pupperclient.management.websocket.packet.PupperClientPacket;

public class SC_PupperClientUserPacket extends PupperClientPacket {

	private final String uuid;
	
	public SC_PupperClientUserPacket(String uuid) {
		// Keep the server's existing wire protocol identifier for compatibility.
		super("sc-soar-user");
		this.uuid = uuid;
	}

	@Override
	public JsonObject toJson() {
		jsonObject.addProperty("uuid", uuid);
		return jsonObject;
	}
}
