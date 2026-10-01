package cn.pupperclient.management.websocket.packet;

import com.google.gson.JsonObject;

public abstract class PupperClientPacket {

	protected final JsonObject jsonObject = new JsonObject();
	private final String type;

	public PupperClientPacket(String type) {
		this.type = type;
		this.jsonObject.addProperty("type", type);
	}

	public String getType() {
		return type;
	}

	public abstract JsonObject toJson();
}
