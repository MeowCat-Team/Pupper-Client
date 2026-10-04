package cn.pupperclient.smoke.mixin;

import cn.pupperclient.smoke.StartupSmoke;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exits only after Main completed the client's normal shutdown and resource cleanup. */
@Mixin(Main.class)
public abstract class MixinSmokeMain {
    @Inject(method = "main", at = @At("TAIL"), require = 1)
    private static void pupperSmoke$shutdownCompleted(String[] arguments, CallbackInfo ci) {
        StartupSmoke.shutdownCompleted();
    }
}
