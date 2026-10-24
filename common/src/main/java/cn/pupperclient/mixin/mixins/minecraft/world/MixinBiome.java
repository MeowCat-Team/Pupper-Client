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

package cn.pupperclient.mixin.mixins.minecraft.world;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.pupperclient.management.mod.impl.misc.WeatherChangerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

@Mixin(Biome.class)
public class MixinBiome {

	@Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
	public void getPrecipitation(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
		
		if(WeatherChangerMod.getInstance().isEnabled() && WeatherChangerMod.getInstance().isSnowing()) {
			cir.setReturnValue(Biome.Precipitation.SNOW);
		}
	}
}
