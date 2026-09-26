package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.management.mod.impl.render.EntityRenderOptimizerMod;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;

@Mixin(ChestRenderer.class)
public class MixinChestRendererOptimization {
    @Unique
    private final Map<BlockEntity, ClosedChestState> pupper$closedChestStates = new WeakHashMap<>();

    @Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/client/renderer/blockentity/state/ChestRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
        at = @At("HEAD"), cancellable = true)
    private void pupper$reuseClosedChest(BlockEntity chest, ChestRenderState state, float partialTick,
                                          Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breaking,
                                          CallbackInfo ci) {
        EntityRenderOptimizerMod mod = EntityRenderOptimizerMod.getInstance();
        if (mod == null || !mod.optimizeChests() || !pupper$isClosedSingleChest(chest, partialTick)) {
            return;
        }

        BlockState blockState = chest.getBlockState();
        ClosedChestState cached = pupper$closedChestStates.get(chest);
        if (cached == null || cached.blockState != blockState) {
            return;
        }

        // Position, light and break overlay remain live.
        BlockEntityRenderState.extractBase(chest, state, breaking);
        state.type = cached.type;
        state.facing = cached.facing;
        state.material = cached.material;
        state.open = 0.0F;
        ci.cancel();
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/client/renderer/blockentity/state/ChestRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
        at = @At("TAIL"))
    private void pupper$rememberClosedChest(BlockEntity chest, ChestRenderState state, float partialTick,
                                             Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breaking,
                                             CallbackInfo ci) {
        EntityRenderOptimizerMod mod = EntityRenderOptimizerMod.getInstance();
        if (mod != null && mod.optimizeChests() && pupper$isClosedSingleChest(chest, partialTick)) {
            pupper$closedChestStates.put(chest,
                new ClosedChestState(chest.getBlockState(), state.type, state.facing, state.material));
        } else {
            pupper$closedChestStates.remove(chest);
        }
    }

    @Unique
    private static boolean pupper$isClosedSingleChest(BlockEntity chest, float partialTick) {
        if (!(chest instanceof LidBlockEntity lid) || !chest.hasLevel() || lid.getOpenNess(partialTick) != 0.0F) {
            return false;
        }
        BlockState state = chest.getBlockState();
        return !state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE;
    }

    @Unique
    private record ClosedChestState(BlockState blockState, ChestType type, Direction facing,
                                    ChestRenderState.ChestMaterialType material) { }
}
