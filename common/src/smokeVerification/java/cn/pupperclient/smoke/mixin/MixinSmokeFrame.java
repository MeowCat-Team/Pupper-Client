package cn.pupperclient.smoke.mixin;

import cn.pupperclient.smoke.StartupSmoke;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts completed real render frames only in the separate smoke test mod. */
@Mixin(Minecraft.class)
public abstract class MixinSmokeFrame {
    @Inject(method = "renderFrame", at = @At("TAIL"))
    private void pupperSmoke$frameCompleted(CallbackInfo ci) {
        StartupSmoke.framePresented();
    }
}
