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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

public class WeatherDisplayMod extends SimpleHUDMod {

	public WeatherDisplayMod() {
		super("mod.weatherdisplay.name", "mod.weatherdisplay.description", Icon.SUNNY);
	}

	public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};

	@Override
	public String getText() {

		String prefix = "Weather: ";
		ClientLevel world = client.level;
		LocalPlayer player = client.player;
		if (world == null || player == null) {
			return prefix + "Unknown";
		}
		BlockPos playerPos = player.blockPosition();
		Holder<Biome> biomeEntry = world.getBiome(playerPos);

		if (world.isThundering()) {
			return prefix + "Thundering";
		}
		
		if (world.isRaining()) {
			if (biomeEntry.value().getPrecipitationAt(playerPos, world.getSeaLevel()).equals(Biome.Precipitation.SNOW)) {
				return prefix + "Snowing";
			} else {
				return prefix + "Raining";
			}
		}

		return prefix + "Clear";
	}

	@Override
	public String getIcon() {
		ClientLevel world = client.level;
		LocalPlayer player = client.player;
		if (world == null || player == null) {
			return Icon.SUNNY;
		}
		BlockPos playerPos = player.blockPosition();
		Holder<Biome> biomeEntry = world.getBiome(playerPos);

		String iconFont = Icon.SUNNY;

		if (world.isThundering()) {
			iconFont = Icon.THUNDERSTORM;
		}
		
		if (world.isRaining()) {
			if (biomeEntry.value().getPrecipitationAt(playerPos, world.getSeaLevel()).equals(Biome.Precipitation.SNOW)) {
				iconFont = Icon.WEATHER_SNOWY;
			} else {
				iconFont = Icon.RAINY;
			}
		}

		return iconFont;
	}
}
