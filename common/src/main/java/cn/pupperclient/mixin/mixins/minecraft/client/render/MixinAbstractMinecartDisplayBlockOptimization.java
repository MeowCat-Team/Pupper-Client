package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.management.mod.impl.render.EntityRenderOptimizerMod;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 26.2 矿车展示方块模型的脏标记（P2-H）。
 *
 * <p>{@code AbstractMinecartRenderer#extractRenderState} 每帧无条件调用
 * {@code BlockModelResolver#update}，它会 {@code clear()} 渲染状态、对 BlockState 做一次
 * 哈希查表重建 parts 列表；展示方块没变也照做。
 *
 * <p>{@code MinecartRenderState#displayBlockModel} 不是 final 字段，所以这里缓存上一次
 * 解析结果并在 BlockState 未变时直接换引用即可，无需复制任何私有字段。
 */
@Mixin(AbstractMinecartRenderer.class)
public abstract class MixinAbstractMinecartDisplayBlockOptimization {

    @Unique
    private final Map<AbstractMinecart, CachedDisplayBlock> pupper$displayBlocks = new WeakHashMap<>();

    @Redirect(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockModelResolver;update("
                   + "Lnet/minecraft/client/renderer/block/BlockModelRenderState;"
                   + "Lnet/minecraft/world/level/block/state/BlockState;"
                   + "Lnet/minecraft/client/renderer/block/model/BlockDisplayContext;)V"
        )
    )
    private void pupper$updateDisplayBlockIfChanged(BlockModelResolver resolver, BlockModelRenderState target,
                                                    BlockState blockState, BlockDisplayContext context,
                                                    AbstractMinecart entity, MinecartRenderState state) {
        EntityRenderOptimizerMod mod = EntityRenderOptimizerMod.getInstance();
        if (mod == null || !mod.optimizeEntities()) {
            this.pupper$displayBlocks.remove(entity);
            resolver.update(target, blockState, context);
            return;
        }

        CachedDisplayBlock cached = this.pupper$displayBlocks.get(entity);
        if (cached != null && cached.blockState == blockState) {
            state.displayBlockModel = cached.renderState;
            return;
        }

        resolver.update(target, blockState, context);
        this.pupper$displayBlocks.put(entity, new CachedDisplayBlock(blockState, target));
    }

    @Unique
    private record CachedDisplayBlock(BlockState blockState, BlockModelRenderState renderState) { }
}
