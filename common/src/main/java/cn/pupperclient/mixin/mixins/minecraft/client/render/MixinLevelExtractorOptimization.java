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
import cn.pupperclient.utils.render.ItemRenderBudget;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class MixinLevelExtractorOptimization {
    @Shadow private ClientLevel level;

    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void pupper$beginItemBudget(Camera camera, Frustum frustum, DeltaTracker delta,
                                        LevelRenderState state, CallbackInfo ci) {
        EntityRenderOptimizerMod mod = EntityRenderOptimizerMod.getInstance();
        ItemRenderBudget.beginFrame(level, mod != null && mod.optimizeItemShadows(),
                mod == null ? 64 : mod.getItemThreshold());
    }

    @Inject(method = "extractVisibleEntities", at = @At("RETURN"))
    private void pupper$finishItemBudget(Camera camera, Frustum frustum, DeltaTracker delta,
                                         LevelRenderState state, CallbackInfo ci) {
        if (!ItemRenderBudget.isActive()) {
            ItemRenderBudget.endFrame(0);
            return;
        }
        int items = 0;
        for (EntityRenderState entity : state.entityRenderStates) {
            if (entity instanceof ItemEntityRenderState) items++;
        }
        ItemRenderBudget.endFrame(items);
    }
}
