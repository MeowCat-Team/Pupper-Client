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

package cn.pupperclient.utils.server;

import java.util.Objects;
import net.minecraft.client.Minecraft;

public class ServerUtils {

	private static final Minecraft client = Minecraft.getInstance();

	public static boolean isJoin(Server server) {
		return isMultiplayer() && getAddress().contains(server.getAddress());
	}

	public static boolean isSingleplayer() {
		return client.hasSingleplayerServer();
	}

	public static boolean isMultiplayer() {
		return client.getCurrentServer() != null;
	}

	public static String getAddress() {
		return isMultiplayer() ? Objects.requireNonNull(client.getCurrentServer()).ip : "null";
	}
}
