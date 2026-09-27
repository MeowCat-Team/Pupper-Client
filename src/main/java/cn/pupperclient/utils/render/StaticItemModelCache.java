package cn.pupperclient.utils.render;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.pupperclient.mixin.mixins.accessors.CuboidItemModelWrapperAccessor;
import cn.pupperclient.mixin.mixins.accessors.ItemModelResolverAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Reuses only proven static, unmodified ground block models; no entity state is cached. */
public final class StaticItemModelCache {
    private static final int MAX_MODELS = 256;
    private static final Map<Identifier, Entry> MODELS = new LinkedHashMap<>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Identifier, StaticItemModelCache.Entry> eldest) {
            return size() > MAX_MODELS;
        }
    };
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);

    private StaticItemModelCache() {}

    /**
     * Returned states are read-only: never pass them to a resolver or call clear/newLayer on
     * them. In 26.2 submit reads layer data and transforms the supplied PoseStack only. Resolve
     * the lazy bounding box before publishing so rendering no longer changes the shared state.
     */
    public static synchronized ItemStackRenderState get(Entity entity, ItemStack stack,
                                                        ItemModelResolver resolver) {
        if (!(entity instanceof ItemEntity) || !(entity.level() instanceof ClientLevel currentWorld)) return null;
        if (world.get() != currentWorld) {
            MODELS.clear();
            world = new WeakReference<>(currentWorld);
        }
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)
                || !stack.getComponentsPatch().isEmpty() || stack.hasFoil()) return null;

        Identifier id = stack.get(DataComponents.ITEM_MODEL);
        if (id == null) return null;
        ItemModelResolverAccessor access = (ItemModelResolverAccessor) resolver;
        ItemModel model = access.pupper$getItemModel(id);
        Entry previous = MODELS.get(id);
        // A reload replaces the baked model objects. Identity is checked even if a reload
        // listener runs after this call; an old model is never selected by identifier alone.
        if (previous != null && previous.model == model) return previous.state;

        // Do not infer staticness from BlockItem or isAnimated alone: resource packs may use
        // time/entity/count/component conditions, custom renderers, or world-dependent tints.
        // Vanilla's exact Cuboid wrapper with no tints/foil reads no other stack/world/seed data.
        if (model == null || model.getClass() != CuboidItemModelWrapper.class
                || !((CuboidItemModelWrapperAccessor) model).pupper$getTints().isEmpty()) {
            MODELS.put(id, new Entry(model, null));
            return null;
        }

        ItemStackRenderState resolved = new ItemStackRenderState();
        resolver.updateForNonLiving(resolved, stack, ItemDisplayContext.GROUND, entity);
        if (resolved.isEmpty() || resolved.isAnimated()) {
            MODELS.put(id, new Entry(model, null));
            return null;
        }
        if (model != access.pupper$getItemModel(id)) return null;
        resolved.getModelBoundingBox();
        MODELS.put(id, new Entry(model, resolved));
        return resolved;
    }

    /** Invoked after ModelManager applies new models/materials during resource reload. */
    public static synchronized void clear() {
        MODELS.clear();
        world.clear();
    }

    private record Entry(ItemModel model, ItemStackRenderState state) {}
}
