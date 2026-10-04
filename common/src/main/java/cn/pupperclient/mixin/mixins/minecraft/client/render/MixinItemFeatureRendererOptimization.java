package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.management.mod.impl.render.EntityRenderOptimizerMod;
import cn.pupperclient.utils.render.ItemQuadBatcher;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public abstract class MixinItemFeatureRendererOptimization extends RenderTypeFeatureRenderer<ItemFeatureRenderer.Submit> {
    @Shadow @Final private QuadInstance quadInstance;

    @Inject(method = "prepareMainSubmit", at = @At("HEAD"), cancellable = true)
    private void pupper$batchGroundItemQuads(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
        EntityRenderOptimizerMod mod = EntityRenderOptimizerMod.getInstance();
        if (mod == null || !mod.optimizeItems() || submit.displayContext() != ItemDisplayContext.GROUND) return;
        ItemQuadBatcher.submit(submit, quadInstance, this::getVertexBuilder);
        ci.cancel();
    }
}
