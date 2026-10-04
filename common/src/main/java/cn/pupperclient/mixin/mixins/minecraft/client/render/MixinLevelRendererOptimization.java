package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.management.mod.impl.render.EntityRenderOptimizerMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRendererOptimization {
    @Inject(method = "submitEntities", at = @At("HEAD"))
    private void pupper$reduceItemPileCopies(PoseStack poseStack, LevelRenderState levelRenderState,
                                              SubmitNodeCollector collector, CallbackInfo ci) {
        EntityRenderOptimizerMod mod = EntityRenderOptimizerMod.getInstance();
        if (mod == null || !mod.optimizeItems()) {
            return;
        }

        int itemCount = 0;
        for (EntityRenderState state : levelRenderState.entityRenderStates) {
            if (state instanceof ItemEntityRenderState) {
                itemCount++;
            }
        }

        int threshold = mod.getItemThreshold();
        if (itemCount < threshold) {
            return;
        }

        // Vanilla submits up to five models per dropped stack. Every entity
        // still renders; under load only decorative duplicate models go.
        int maxCopies = itemCount >= threshold * 2 ? 1 : 2;
        for (EntityRenderState state : levelRenderState.entityRenderStates) {
            if (state instanceof ItemEntityRenderState itemState) {
                itemState.count = Math.min(itemState.count, maxCopies);
            }
        }
    }
}
