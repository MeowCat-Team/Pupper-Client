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
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Icon;

public class HealthDisplayMod extends SimpleHUDMod {

	public HealthDisplayMod() {
		super("mod.healthdisplay.name", "mod.healthdisplay.description", Icon.FAVORITE);
	}

	public EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};

	@Override
	public String getText() {
        if (client.player != null) {
            return (int) client.player.getHealth() + " Health";
        }
        return "";
    }

	@Override
	public String getIcon() {
		return Icon.FAVORITE;
	}
    @Override protected String getUnit() { return "Health"; }
    @Override protected boolean isUrgent() { return client.player != null && client.player.getHealth() <= client.player.getMaxHealth() * 0.25f; }
}
