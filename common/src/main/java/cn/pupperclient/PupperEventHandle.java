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

package cn.pupperclient;

import java.util.List;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.*;
import cn.pupperclient.event.server.impl.GameJoinEvent;
import cn.pupperclient.management.profile.Profile;

public class PupperEventHandle {
	public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> {
		PupperClient.getInstance().getColorManager().onTick();
		PupperClient.getInstance().getHypixelManager().update();
	};

	public final EventBus.EventListener<GameJoinEvent> onGameJoin = event -> {
		PupperClient.getInstance().getHypixelManager().clear();
	};

	public final EventBus.EventListener<ServerJoinEvent> onServerJoin = event -> {

		List<Profile> profiles = PupperClient.getInstance().getProfileManager().getProfiles();

		for (Profile p : profiles) {

			String address = p.getServerIp();

			if (event.getAddress().contains(address)) {
				PupperClient.getInstance().getProfileManager().load(p);
				break;
			}
		}
	};
}
