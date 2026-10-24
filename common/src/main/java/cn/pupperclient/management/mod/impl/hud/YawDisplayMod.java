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

import java.text.DecimalFormat;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Icon;

public class YawDisplayMod extends SimpleHUDMod {

	private DecimalFormat df = new DecimalFormat("0.##");

	public YawDisplayMod() {
		super("mod.yawdisplay.name", "mod.yawdisplay.description", Icon.ARROW_RANGE);
	}

	public EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};

	@Override
	public String getText() {
		return "Yaw: " + (client.player == null ? "0" : df.format(Math.abs(client.player.getViewYRot(0.0f) % 90)));
	}

	@Override
	public String getIcon() {
		return Icon.ARROW_RANGE;
	}
}
