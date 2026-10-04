package cn.pupperclient.mixin.mixins.minecraft.network;

import cn.pupperclient.utils.network.ServerTextContents;
import cn.pupperclient.utils.network.ServerTextPrivacy;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeybindContents.class)
public abstract class MixinKeybindContents implements ServerTextContents {
    @Shadow @Final private String name;
    @Unique private boolean pupper$serverSupplied;

    @Override
    public void pupper$markServerSupplied() {
        pupper$serverSupplied = true;
    }

    @Inject(method = "getNestedComponent", at = @At("HEAD"), cancellable = true)
    private void pupper$hideRemoteModKeybind(CallbackInfoReturnable<Component> cir) {
        if (ServerTextPrivacy.protectKeybind(name, pupper$serverSupplied)) {
            cir.setReturnValue(Component.literal(name));
        }
    }
}
