package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.utils.render.StaticItemModelCache;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelManager.class)
public class MixinModelManagerItemCache {
    @Inject(method = "apply", at = @At("TAIL"))
    private void pupper$invalidateGroundModels(CallbackInfo ci) {
        StaticItemModelCache.clear();
    }
}
