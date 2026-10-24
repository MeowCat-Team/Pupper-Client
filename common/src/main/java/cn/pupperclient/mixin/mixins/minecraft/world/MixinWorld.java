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
import net.minecraft.world.level.Level;

@Mixin(Level.class)
public class MixinWorld {

	@Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
	public void getRainGradient(float delta, CallbackInfoReturnable<Float> cir) {
		
		WeatherChangerMod mod = WeatherChangerMod.getInstance();
		
		if(mod.isEnabled()) {
			if(mod.isRaining()) {
				cir.setReturnValue(1F);
			} else {
				cir.setReturnValue(0F);
			}
		}
	}
	
	@Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
	public void isRaining(CallbackInfoReturnable<Boolean> cir) {
		if(WeatherChangerMod.getInstance().isEnabled()) {
			cir.setReturnValue(WeatherChangerMod.getInstance().isRaining());
		}
	}
	
	@Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
	public void getThunderGradient(float delta, CallbackInfoReturnable<Float> cir) {
		
		WeatherChangerMod mod = WeatherChangerMod.getInstance();
		
		if(mod.isEnabled()) {
			if(mod.isThundering()) {
				cir.setReturnValue(1F);
			} else {
				cir.setReturnValue(0F);
			}
		}
	}
	
	@Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
	public void isThundering(CallbackInfoReturnable<Boolean> cir) {
		if(WeatherChangerMod.getInstance().isEnabled()) {
			cir.setReturnValue(WeatherChangerMod.getInstance().isThundering());
		}
	}
}
