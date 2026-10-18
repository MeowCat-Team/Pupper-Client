package cn.pupperclient.utils.render;

import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 矿车铁路姿态记忆化。
 *
 * <p>26.2 的 {@code AbstractMinecartRenderer#oldExtractState} 每帧对每辆矿车调用
 * {@code OldMinecartBehavior#getPos} 一次、{@code getPosOffs} 两次；这几个方法内部都会
 * {@code new BlockPos} 并查询世界方块状态，合计约 10 次分配 + 6 次方块查询 / 车 / 帧，
 * 而且与矿车是否运动无关 —— 静止矿车也会被完整重算。
 *
 * <p>轨道插值的唯一输入是渲染位置 {@code (x, y, z)}。矿车静止时
 * {@code Mth.lerp(partialTick, xOld, x)} 在 {@code xOld == x} 下逐位相等，因此可以安全复用
 * 上一帧结果。{@code xRot} / {@code yRot} 依赖 {@code partialTick}，由调用方自行重算，
 * 本类不缓存它们。
 *
 * <p>只在渲染线程访问（{@code LevelExtractor#extractVisibleEntities}）。
 */
public final class RailOrientationCache {
    /** 静止矿车最多复用多少帧，兜底"脚下铁轨被替换"等世界变化，把陈旧窗口限制在 ~0.5 秒内。 */
    private static final int MAX_REUSE_FRAMES = 10;

    private static final Map<AbstractMinecart, Entry> CACHE = new WeakHashMap<>();

    private RailOrientationCache() {
    }

    /**
     * 尝试复用上一帧的轨道插值结果。
     *
     * @return true 表示命中，且已写入 {@code posOnRail} / {@code frontPos} / {@code backPos}
     */
    public static boolean tryReuse(AbstractMinecart cart, MinecartRenderState state) {
        Entry entry = CACHE.get(cart);
        if (entry == null) return false;
        if (entry.x != state.x || entry.y != state.y || entry.z != state.z) return false;
        if (++entry.reuseCount > MAX_REUSE_FRAMES) {
            entry.reuseCount = 0;
            return false;
        }

        state.posOnRail = entry.posOnRail;
        state.frontPos = entry.frontPos;
        state.backPos = entry.backPos;
        return true;
    }

    /** 记录一帧的完整轨道插值结果（由 vanilla 计算后调用）。 */
    public static void remember(AbstractMinecart cart, MinecartRenderState state) {
        Entry entry = CACHE.get(cart);
        if (entry == null) {
            entry = new Entry();
            CACHE.put(cart, entry);
        }

        entry.x = state.x;
        entry.y = state.y;
        entry.z = state.z;
        entry.posOnRail = state.posOnRail;
        entry.frontPos = state.frontPos;
        entry.backPos = state.backPos;
        entry.reuseCount = 0;
    }

    /** 优化关闭时清理，避免下次开启读到过期数据。 */
    public static void invalidate(AbstractMinecart cart) {
        CACHE.remove(cart);
    }

    private static final class Entry {
        double x;
        double y;
        double z;
        Vec3 posOnRail;
        Vec3 frontPos;
        Vec3 backPos;
        int reuseCount;
    }
}
