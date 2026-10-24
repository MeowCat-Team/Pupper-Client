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

package cn.pupperclient.utils.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 静止实体的阴影扫描记忆化（P2-J）。
 *
 * <p>26.2 的 {@code EntityRenderer#extractShadow} 每帧对每个 16 格内的实体做
 * O(列 × 层) 的方块扫描：每列一次 {@code getChunk}，每个候选方块一次
 * {@code getBlockState} / {@code getMaxLocalRawBrightness} / {@code getCollisionShapeFullBlock}
 * / {@code getShape}，每个命中块再 new 一个 {@code ShadowPiece}。
 * 实测占矿车渲染数据提取成本的 56%（1221 ns / 2182 ns）。
 *
 * <p>几何信息（相对坐标 + 形状 + 亮度）只取决于实体所在的方块，与玩家距离无关；
 * 只有 alpha 随距离变化。所以这里缓存几何，每帧按当前 {@code pow} 重算 alpha：
 *
 * <pre>alpha = clamp((pow + relativeY * 0.5) * k, 0, 1)</pre>
 *
 * 其中 {@code k = 0.5 * Lightmap.getBrightness(...)} 在捕获时反解出来。
 * 与 vanilla 的 {@code powerAtDepth = pow - (state.y - posY) * 0.5} 是同一个式子
 * （{@code state.y - posY = -relativeY}），因此**输出逐位一致**。
 *
 * <p>只在渲染线程访问（{@code EntityRenderer#finalizeRenderState}）。
 */
public final class ShadowPieceCache {
    /** 静止实体最多复用多少帧，兜底"脚下方块被换掉"这类世界变化。 */
    private static final int MAX_REUSE_FRAMES = 10;

    private static final Map<Object, Entry> CACHE = new WeakHashMap<>();

    private ShadowPieceCache() {
    }

    /**
     * 尝试用缓存重建 {@code state.shadowPieces}。
     *
     * @param pow 当前帧的 {@code (1 - distSq / 256) * shadowStrength}
     * @return true 表示命中并已填充，调用方应跳过 vanilla 的扫描
     */
    public static boolean apply(Object entity, EntityRenderState state, float radius, float pow) {
        Entry entry = CACHE.get(entity);
        if (entry == null) return false;
        if (entry.x != state.x || entry.y != state.y || entry.z != state.z) return false;
        if (entry.radius != radius) return false;
        if (++entry.reuseCount > MAX_REUSE_FRAMES) {
            entry.reuseCount = 0;
            return false;
        }

        state.shadowRadius = radius;
        state.shadowPieces.clear();
        for (int i = 0; i < entry.count; i++) {
            float depth = pow + entry.relY[i] * 0.5F;
            float alpha = depth * entry.scale[i];
            if (alpha < 0.0F) alpha = 0.0F;
            else if (alpha > 1.0F) alpha = 1.0F;
            state.shadowPieces.add(new EntityRenderState.ShadowPiece(
                    entry.relX[i], entry.relY[i], entry.relZ[i], entry.shapes[i], alpha));
        }
        return true;
    }

    /**
     * 记录一帧的扫描结果。只保存 alpha 处于 (0, 1) 开区间的块 —— 完全透明的不必重画，
     * 完全遮蔽的（alpha 已被 clamp 到 1）反解不出 {@code k}，也不缓存。
     */
    public static void capture(Object entity, EntityRenderState state, float radius, float pow) {
        Entry entry = new Entry();
        entry.x = state.x;
        entry.y = state.y;
        entry.z = state.z;
        entry.radius = radius;

        int size = state.shadowPieces.size();
        entry.relX = new float[size];
        entry.relY = new float[size];
        entry.relZ = new float[size];
        entry.scale = new float[size];
        entry.shapes = new VoxelShape[size];

        int count = 0;
        for (int i = 0; i < size; i++) {
            EntityRenderState.ShadowPiece piece = state.shadowPieces.get(i);
            float depth = pow + piece.relativeY() * 0.5F;
            float alpha = piece.alpha();
            if (depth <= 1.0E-6F || alpha <= 0.0F || alpha >= 1.0F) continue;

            entry.relX[count] = piece.relativeX();
            entry.relY[count] = piece.relativeY();
            entry.relZ[count] = piece.relativeZ();
            entry.shapes[count] = piece.shapeBelow();
            entry.scale[count] = alpha / depth;
            count++;
        }
        entry.count = count;
        CACHE.put(entity, entry);
    }

    public static void invalidate(Object entity) {
        CACHE.remove(entity);
    }

    private static final class Entry {
        double x;
        double y;
        double z;
        float radius;
        float[] relX;
        float[] relY;
        float[] relZ;
        float[] scale;
        VoxelShape[] shapes;
        int count;
        int reuseCount;
    }
}
