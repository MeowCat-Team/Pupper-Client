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

package cn.pupperclient.management.websocket.handler.impl;

import com.google.gson.JsonObject;
import cn.pupperclient.PupperClient;
import cn.pupperclient.management.hypixel.HypixelManager;
import cn.pupperclient.management.hypixel.api.HypixelUser;
import cn.pupperclient.management.websocket.handler.WebSocketHandler;
import cn.pupperclient.utils.misc.JsonUtils;

public class HypixelStatsHandler extends WebSocketHandler {

	@Override
	public void handle(JsonObject jsonObject) {
		
		HypixelManager hypixelManager = PupperClient.getInstance().getHypixelManager();

		String uuid = JsonUtils.getStringProperty(jsonObject, "uuid", "null");
		String networkLevel = JsonUtils.getStringProperty(jsonObject, "networkLevel", "-1");
		String bedwarsLevel = JsonUtils.getStringProperty(jsonObject, "bedwarsLevel", "-1");
		String winLoseRatio = JsonUtils.getStringProperty(jsonObject, "winLoseRatio", "-1");
		String finalKillDeathRatio = JsonUtils.getStringProperty(jsonObject, "finalKillDeathRatio", "-1");
		String bedsBrokeLostratio = JsonUtils.getStringProperty(jsonObject, "bedsBrokeLostRatio", "-1");
		
		hypixelManager.add(new HypixelUser(uuid, networkLevel, bedwarsLevel, winLoseRatio, finalKillDeathRatio,
				bedsBrokeLostratio));
	}
}
