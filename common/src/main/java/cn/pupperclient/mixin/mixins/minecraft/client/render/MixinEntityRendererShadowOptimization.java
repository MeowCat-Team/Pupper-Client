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
import cn.pupperclient.utils.render.ShadowPieceCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 实体阴影扫描记忆化（P2-J）。
 *
 * <p>{@code EntityRenderer#finalizeRenderState} 每帧对每个 16 格内实体执行
 * {@code extractShadow}，逐列 {@code getChunk}、逐方块 {@code getBlockState} /
 * {@code getMaxLocalRawBrightness} / {@code getShape} 并 new {@code ShadowPiece}。
 * 实测占矿车渲染数据提取成本的 56%。
 *
 * <p>几何只取决于实体所在位置，alpha 随距离线性变化，因此可以缓存几何、每帧只重算 alpha
 * （见 {@link ShadowPieceCache}）。缓存命中时输出与 vanilla 一致。
 *
 * <p>关闭实体阴影、实体隐形、半径或强度为 0 等分支一律放行给 vanilla，保证语义不变。
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererShadowOptimization {

    @Shadow
    protected abstract float getShadowRadius(EntityRenderState state);

    @Shadow
    protected abstract float getShadowStrength(EntityRenderState state);

    @Inject(method = "finalizeRenderState", at = @At("HEAD"), cancellable = true)
    private void pupper$reuseShadowPieces(Entity entity, EntityRenderState state, CallbackInfo ci) {
        EntityRenderOptimizerMod mod = EntityRenderOptimizerMod.getInstance();
        if (mod == null || !mod.optimizeEntities()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.entityShadows().get() || state.isInvisible) return;

        float radius = Math.min(this.getShadowRadius(state), 32.0F);
        if (radius <= 0.0F) return;

        float pow = (float) ((1.0 - state.distanceToCameraSq / 256.0) * this.getShadowStrength(state));
        if (pow <= 0.0F) return;

        if (ShadowPieceCache.apply(entity, state, radius, pow)) ci.cancel();
    }

    @Inject(method = "finalizeRenderState", at = @At("TAIL"))
    private void pupper$captureShadowPieces(Entity entity, EntityRenderState state, CallbackInfo ci) {
        EntityRenderOptimizerMod mod = EntityRenderOptimizerMod.getInstance();
        if (mod == null || !mod.optimizeEntities()) {
            ShadowPieceCache.invalidate(entity);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.entityShadows().get() || state.isInvisible) {
            ShadowPieceCache.invalidate(entity);
            return;
        }

        float radius = Math.min(this.getShadowRadius(state), 32.0F);
        float pow = (float) ((1.0 - state.distanceToCameraSq / 256.0) * this.getShadowStrength(state));
        if (radius <= 0.0F || pow <= 0.0F) {
            ShadowPieceCache.invalidate(entity);
            return;
        }

        ShadowPieceCache.capture(entity, state, radius, pow);
    }
}
