/**
 * @Author: oneachina
 * @link: github.com/oneachina
 */
package cn.pupperclient.mixin.mixins.minecraft.entity;

import cn.pupperclient.management.mod.impl.player.ForceMainHandMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Avatar.class)
public class MixinAvatar {
    @Inject(method = "getMainArm", at = @At("HEAD"), cancellable = true)
    private void forceLeftHand(CallbackInfoReturnable<HumanoidArm> cir) {
        var self = (Object) this;
        if (self != Minecraft.getInstance().player) {
            cir.setReturnValue(ForceMainHandMod.getInstance().isRightHand() ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
        }
    }
}
