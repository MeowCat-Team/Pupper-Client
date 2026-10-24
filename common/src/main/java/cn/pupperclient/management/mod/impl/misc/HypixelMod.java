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

package cn.pupperclient.management.mod.impl.misc;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.time.TimerUtils;
import cn.pupperclient.utils.server.Server;
import cn.pupperclient.utils.server.ServerUtils;

public class HypixelMod extends Mod {

	private static HypixelMod instance;
	private BooleanSetting levelHeadSetting = new BooleanSetting("setting.levelhead", "setting.levelhead.description",
			Icon._123, this, false);
	private BooleanSetting autoTipSetting = new BooleanSetting("setting.autotip", "setting.autotip.description",
			Icon.MONEY, this, false);

	private TimerUtils tipTimer = new TimerUtils();

	public HypixelMod() {
		super("mod.hypixel.name", "mod.hypixel.description", Icon.CONSTRUCTION, ModCategory.MISC);

		instance = this;
	}

	public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> {
		if (autoTipSetting.isEnabled() && client.player != null && ServerUtils.isJoin(Server.HYPIXEL)) {
			if (tipTimer.delay(1200000)) {
				client.player.connection.sendCommand("tip all");
				tipTimer.reset();
			}
		} else {
			tipTimer.reset();
		}
	};

	public static HypixelMod getInstance() {
		return instance;
	}

	public BooleanSetting getLevelHeadSetting() {
		return levelHeadSetting;
	}
}
