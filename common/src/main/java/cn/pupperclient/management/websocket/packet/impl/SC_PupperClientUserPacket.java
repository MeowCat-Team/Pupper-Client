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
