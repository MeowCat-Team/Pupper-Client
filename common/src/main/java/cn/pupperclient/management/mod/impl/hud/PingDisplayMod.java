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

package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.thread.Multithreading;
import cn.pupperclient.utils.time.TimerUtils;
import cn.pupperclient.utils.server.ServerUtils;

import net.lenni0451.mcping.MCPing;

public class PingDisplayMod extends SimpleHUDMod {

	private TimerUtils timer = new TimerUtils();
	private NumberSetting refreshTimeSetting = new NumberSetting("setting.refreshtime",
			"setting.refreshtime.description", Icon.REFRESH, this, 4, 1, 20, 1);
	private long ping;
	private boolean pinging;

	public PingDisplayMod() {
		super("mod.pingdisplay.name", "mod.pingdisplay.description", Icon.WIFI);
		pinging = false;
	}

	public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};
	public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> updatePing();

	private void updatePing() {

		if (timer.delay((long) (1000 * refreshTimeSetting.getValue()))) {

			if (ServerUtils.isMultiplayer() && client.getCurrentServer() != null) {
				var server = client.getCurrentServer();
				if (server.ping <= 1 && !pinging) {
					String address = server.ip;
					pinging = true;
					Multithreading.runAsync(() -> {
						try {
							ping = MCPing.pingModern().address(address).getSync().getPing();
						} finally {
							pinging = false;
						}
					});
				} else {
					ping = server.ping;
				}
			} else if (client.hasSingleplayerServer()) {
				ping = 0;
			}

			timer.reset();
		}
	}

	@Override
	public String getText() {
		return ping + " ms";
	}

	@Override
	public String getIcon() {
		return Icon.WIFI;
	}
    @Override protected String getUnit() { return "ms"; }
}
