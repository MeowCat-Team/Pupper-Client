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
