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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Reuses proven static ground geometry, including tools and glint variants; no entity state is cached. */
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
        if (stack.isEmpty()) return null;

        Identifier id = stack.get(DataComponents.ITEM_MODEL);
        if (id == null) return null;
        ItemModelResolverAccessor access = (ItemModelResolverAccessor) resolver;
        ItemModel model = access.pupper$getItemModel(id);
        Entry previous = MODELS.get(id);
        // A reload replaces the baked model objects. Identity is checked even if a reload
        // listener runs after this call; an old model is never selected by identifier alone.
        // Do not infer staticness from the item type or isAnimated alone: resource packs may use
        // time/entity/count/component conditions, custom renderers, or world-dependent tints.
        // An exact, untinted Cuboid only reads stack data to choose its foil type. Damage,
        // names and enchantment components cannot alter this model's geometry; conditional
        // resource-pack models are different classes and still use the original resolver.
        if (previous == null || previous.model != model) {
            boolean cacheable = model != null && model.getClass() == CuboidItemModelWrapper.class
                    && ((CuboidItemModelWrapperAccessor) model).pupper$getTints().isEmpty();
            previous = new Entry(model, cacheable);
            MODELS.put(id, previous);
        }
        if (!previous.cacheable) return null;

        int variant = !stack.hasFoil() ? 0 : (stack.is(ItemTags.COMPASSES) || stack.is(Items.CLOCK) ? 2 : 1);
        if (previous.resolved[variant]) return previous.states[variant];

        ItemStackRenderState resolved = new ItemStackRenderState();
        resolver.updateForNonLiving(resolved, stack, ItemDisplayContext.GROUND, entity);
        if (model != access.pupper$getItemModel(id)) return null;
        previous.resolved[variant] = true;
        if (resolved.isEmpty()) return null;
        // Cuboid's animated flag means shader glint or atlas animation, not changing geometry.
        // Keep that flag and foil type intact; their animations still advance when submitted.
        resolved.getModelBoundingBox();
        previous.states[variant] = resolved;
        return resolved;
    }

    /** Invoked after ModelManager applies new models/materials during resource reload. */
    public static synchronized void clear() {
        MODELS.clear();
        world.clear();
    }

    private static final class Entry {
        final ItemModel model;
        final boolean cacheable;
        final ItemStackRenderState[] states = new ItemStackRenderState[3];
        final boolean[] resolved = new boolean[3];

        Entry(ItemModel model, boolean cacheable) {
            this.model = model;
            this.cacheable = cacheable;
        }
    }
}
