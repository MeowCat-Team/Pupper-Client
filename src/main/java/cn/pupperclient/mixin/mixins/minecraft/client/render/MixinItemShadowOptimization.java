package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.utils.render.ItemRenderBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinItemShadowOptimization {
    @Inject(method = "extractShadow", at = @At("HEAD"), cancellable = true)
    private void pupper$skipDistantItemShadow(EntityRenderState state, Minecraft minecraft,
                                              Level level, CallbackInfo ci) {
        if (state instanceof ItemEntityRenderState && ItemRenderBudget.skipDistantShadow(state.distanceToCameraSq)) {
            state.shadowPieces.clear();
            state.shadowRadius = 0;
            ci.cancel();
        }
    }
}
