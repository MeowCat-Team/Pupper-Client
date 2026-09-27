package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.management.mod.impl.render.EntityRenderOptimizerMod;
import cn.pupperclient.utils.render.StaticItemModelCache;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemClusterRenderState.class)
public class MixinItemClusterRenderStateOptimization {
    @Shadow @Final @Mutable public ItemStackRenderState item;
    @Shadow public int count;
    @Shadow public int seed;

    @Unique private ItemStackRenderState pupper$ownedItem;
    @Unique private boolean pupper$usingCachedItem;

    @Inject(method = "extractItemGroupRenderState", at = @At("HEAD"), cancellable = true)
    private void pupper$reuseStaticGroundModel(Entity entity, ItemStack stack, ItemModelResolver resolver,
                                               CallbackInfo ci) {
        EntityRenderOptimizerMod mod = EntityRenderOptimizerMod.getInstance();
        ItemStackRenderState cached = mod != null && mod.optimizeItems()
                ? StaticItemModelCache.get(entity, stack, resolver) : null;
        if (cached == null) {
            // Vanilla currently creates a new cluster every frame. Keep this guard for callers
            // that reuse a state: a fallback resolver must never clear a shared cached model.
            if (pupper$usingCachedItem) {
                item = pupper$ownedItem;
                pupper$usingCachedItem = false;
            }
            return;
        }
        if (!pupper$usingCachedItem) pupper$ownedItem = item;
        item = cached;
        pupper$usingCachedItem = true;
        // Stack size/damage can change independently of a static model. Preserve the original
        // cluster layout and let the existing overload policy cap decorative copies later.
        count = ItemClusterRenderState.getRenderedAmount(stack.getCount());
        seed = ItemClusterRenderState.getSeedForItemStack(stack);
        ci.cancel();
    }
}
