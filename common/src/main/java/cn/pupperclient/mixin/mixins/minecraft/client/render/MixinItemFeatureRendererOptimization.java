/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
