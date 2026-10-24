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

package cn.pupperclient.mixin.mixins.minecraft.client.sound;

import cn.pupperclient.management.mod.impl.player.OldAnimationsMod;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;

@Mixin(SoundEngine.class)
public class MixinSoundSystem {

    @Unique
    private final List<Identifier> newPvPSounds = Arrays.asList(
        SoundEvents.PLAYER_ATTACK_KNOCKBACK.location(),
        SoundEvents.PLAYER_ATTACK_SWEEP.location(),
        SoundEvents.PLAYER_ATTACK_CRIT.location(),
        SoundEvents.PLAYER_ATTACK_STRONG.location(),
        SoundEvents.PLAYER_ATTACK_WEAK.location(),
        SoundEvents.PLAYER_ATTACK_NODAMAGE.location()
    );

    @Inject(
        method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
        at = @At("HEAD"),
        cancellable = true
    )
    public void oldAnimations$disableNewPvPSounds(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (OldAnimationsMod.getInstance().isEnabled() && OldAnimationsMod.getInstance().isOldPvPSounds()
            && newPvPSounds.contains(sound.getIdentifier())) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}
