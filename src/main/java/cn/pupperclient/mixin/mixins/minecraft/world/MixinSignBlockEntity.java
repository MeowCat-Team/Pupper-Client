package cn.pupperclient.mixin.mixins.minecraft.world;

import cn.pupperclient.utils.network.ServerTextPrivacy;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SignBlockEntity.class)
public abstract class MixinSignBlockEntity {
    @Inject(method = "loadLine", at = @At("RETURN"))
    private void pupper$markServerSignText(Component original, CallbackInfoReturnable<Component> cir) {
        SignBlockEntity sign = (SignBlockEntity) (Object) this;
        if (sign.getLevel() != null && sign.getLevel().isClientSide() && ServerTextPrivacy.isRemoteConnection()) {
            // Signs arrive inside block-entity NBT, which bypasses Component stream codecs.
            ServerTextPrivacy.markServerText(cir.getReturnValue());
        }
    }
}
