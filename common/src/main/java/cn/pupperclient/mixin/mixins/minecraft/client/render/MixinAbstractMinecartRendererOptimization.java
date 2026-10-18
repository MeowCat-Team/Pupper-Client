package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.management.mod.impl.render.EntityRenderOptimizerMod;
import cn.pupperclient.utils.render.RailOrientationCache;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 矿车渲染优化：复用静止矿车的铁路姿态。
 *
 * <p>目标方法（唯一重载，故按名字匹配）：
 * <pre>
 * private static &lt;T extends AbstractMinecart, S extends MinecartRenderState&gt;
 * void oldExtractState(T, OldMinecartBehavior, S, float)
 * </pre>
 * 擦除后描述符：
 * {@code (Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;Lnet/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior;Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;F)V}
 *
 * <p>它每帧调用 {@code getPos} 与两次 {@code getPosOffs}，每个都会 {@code new BlockPos}
 * 并查询附近的方块状态，合计约 10 次分配 + 6 次方块查询。矿车静止时输入
 * {@code (x, y, z)} 逐位不变，结果必然相同，因此可以直接复用。
 */
@Mixin(AbstractMinecartRenderer.class)
public abstract class MixinAbstractMinecartRendererOptimization {

    @Inject(method = "oldExtractState", at = @At("HEAD"), cancellable = true)
    private static void pupper$reuseRailOrientation(AbstractMinecart entity, OldMinecartBehavior behavior,
                                                    MinecartRenderState state, float partialTicks,
                                                    CallbackInfo ci) {
        EntityRenderOptimizerMod mod = EntityRenderOptimizerMod.getInstance();
        if (mod == null || !mod.optimizeMinecarts()) return;
        if (!RailOrientationCache.tryReuse(entity, state)) return;

        // 取消后 vanilla 不会再写这两个插值字段，按原始语义补上（本身零成本）。
        state.xRot = entity.getXRot(partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        ci.cancel();
    }

    @Inject(method = "oldExtractState", at = @At("TAIL"))
    private static void pupper$rememberRailOrientation(AbstractMinecart entity, OldMinecartBehavior behavior,
                                                       MinecartRenderState state, float partialTicks,
                                                       CallbackInfo ci) {
        EntityRenderOptimizerMod mod = EntityRenderOptimizerMod.getInstance();
        if (mod == null || !mod.optimizeMinecarts()) {
            RailOrientationCache.invalidate(entity);
            return;
        }

        RailOrientationCache.remember(entity, state);
    }
}
