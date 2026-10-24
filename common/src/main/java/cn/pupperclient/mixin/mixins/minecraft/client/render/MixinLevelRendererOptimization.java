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
