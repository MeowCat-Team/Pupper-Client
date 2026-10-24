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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Icon;

public class SpeedometerMod extends SimpleHUDMod {

	private DecimalFormat speedFormat = new DecimalFormat("0.00");

	public SpeedometerMod() {
		super("mod.speedometer.name", "mod.speedometer.description", Icon.SPEED);
	}

	public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};
	
	@Override
	public String getText() {

		String suffix = " m/s";
		
		if (client.player != null) {
			
			Entity entity = client.player.getVehicle() == null ? client.player : client.player.getVehicle();
			Vec3 vec = entity.getKnownMovement();
			
			if (entity.onGround() && vec.y < 0) {
				vec = new Vec3(vec.x, 0, vec.z);
			}
			
			return speedFormat.format(vec.length() * 20) + suffix;
		}

		return speedFormat.format(0) + suffix;
	}

	@Override
	public String getIcon() {
		return Icon.SPEED;
	}
    @Override protected String getUnit() { return "m/s"; }
}
