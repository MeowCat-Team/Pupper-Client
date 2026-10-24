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

package cn.pupperclient.management.hypixel.api;

public class HypixelUser {

	private final String uuid;
	private final String networkLevel, bedwarsLevel;
	private final String winLoseRatio, finalKillDeathRatio, bedsBrokeLostRatio;
	
	public HypixelUser(String uuid, String networkLevel, String bedwarsLevel, String winLoseRatio,
			String finalKillDeathRatio, String bedsBrokeLostRatio) {
		this.uuid = uuid;
		this.networkLevel = networkLevel;
		this.bedwarsLevel = bedwarsLevel;
		this.winLoseRatio = winLoseRatio;
		this.finalKillDeathRatio = finalKillDeathRatio;
		this.bedsBrokeLostRatio = bedsBrokeLostRatio;
	}

	public String getUuid() {
		return uuid;
	}

	public String getNetworkLevel() {
		return networkLevel;
	}

	public String getBedwarsLevel() {
		return bedwarsLevel;
	}

	public String getWinLoseRatio() {
		return winLoseRatio;
	}

	public String getFinalKillDeathRatio() {
		return finalKillDeathRatio;
	}

	public String getBedsBrokeLostRatio() {
		return bedsBrokeLostRatio;
	}
}
