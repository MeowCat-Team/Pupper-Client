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

package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.management.mod.impl.render.ClearViewMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEffectFogEnvironment.class)
public abstract class MixinMobEffectFogEnvironment {

    @Shadow
    public abstract Holder<MobEffect> getMobEffect();

    @Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
    private void pupper$clearViewFog(FogType fogType, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ClearViewMod mod = ClearViewMod.getInstance();
        if (mod == null || !mod.isEnabled() || entity == null || entity != Minecraft.getInstance().player) {
            return;
        }

        Holder<MobEffect> effect = this.getMobEffect();
        if ((effect.is(MobEffects.BLINDNESS) && mod.isAntiBlindness())
                || (effect.is(MobEffects.DARKNESS) && mod.isAntiDarkness())) {
            cir.setReturnValue(false);
        }
    }
}
