package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventListener;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.event.client.RenderGameOverlayEvent;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.ContextInfoHUDMod;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.language.I18n;
import dev.architectury.platform.Platform;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Information available to the local client, without a server-side Jade dependency or inventory probes. */
public final class LookInfoMod extends ContextInfoHUDMod {
    private Info info;
    public LookInfoMod() { super("mod.lookinfo.name", "mod.lookinfo.description", Icon.SEARCH, false); }

    @EventListener public void tick(ClientTickEvent event) { info = lookInfo(); }

    private Info lookInfo() {
        if (client.player == null || client.level == null || client.hitResult == null) return null;
        if (client.hitResult.getType() == HitResult.Type.BLOCK && client.hitResult instanceof BlockHitResult hit) {
            var pos = hit.getBlockPos();
            if (!client.level.hasChunkAt(pos)) return null;
            var state = client.level.getBlockState(pos);
            if (state.isAir()) return null;
            var block = state.getBlock();
            var icon = state.getCloneItemStack(client.level, pos, false);
            var details = new ArrayList<String>();
            details.add(source(BuiltInRegistries.BLOCK.getKey(block)));
            if (block instanceof CropBlock crop) details.add(I18n.get("hud.lookinfo.growth") + ": "
                + Math.round(100f * crop.getAge(state) / Math.max(1, crop.getMaxAge())) + "%");
            if (state.getDestroySpeed(client.level, pos) < 0) details.add(I18n.get("hud.lookinfo.unbreakable"));
            else details.add(I18n.get(!state.requiresCorrectToolForDrops() || client.player.getMainHandItem().isCorrectToolForDrops(state)
                ? "hud.lookinfo.harvestable" : "hud.lookinfo.wrongtool"));
            return new Info(icon, net.minecraft.network.chat.Component.translatable(block.getDescriptionId()).getString(), details, Icon.BOX);
        }
        if (client.hitResult.getType() == HitResult.Type.ENTITY && client.hitResult instanceof EntityHitResult hit) {
            var entity = hit.getEntity();
            if (entity.isRemoved() || entity.level() != client.level) return null;
            if (entity instanceof ItemEntity item) {
                var stack = item.getItem();
                if (stack.isEmpty()) return null;
                return new Info(stack.copy(), stack.getHoverName().getString(),
                    List.of(source(BuiltInRegistries.ITEM.getKey(stack.getItem())), I18n.get("hud.lookinfo.count") + ": " + stack.getCount()), Icon.INVENTORY_2);
            }
            var details = new ArrayList<String>();
            details.add(source(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())));
            if (entity instanceof LivingEntity living) details.add(I18n.get("hud.lookinfo.health") + ": "
                + String.format(Locale.ROOT, "%.1f / %.1f", Math.max(0, living.getHealth()), living.getMaxHealth()));
            ItemStack icon = entity.getPickResult();
            return new Info(icon == null ? ItemStack.EMPTY : icon, entity.getName().getString(), details, Icon.PERSON);
        }
        return null;
    }
    private static String source(Identifier id) {
        String namespace = id.getNamespace();
        if (namespace.equals("minecraft")) return "Minecraft";
        return Platform.isModLoaded(namespace) ? Platform.getMod(namespace).getName() : namespace;
    }
    @Override protected Info currentInfo() { return info; }
    @Override protected Info previewInfo() {
        return info != null ? info : new Info(new ItemStack(Items.GRASS_BLOCK), new ItemStack(Items.GRASS_BLOCK).getHoverName().getString(),
            List.of("Minecraft", I18n.get("hud.lookinfo.harvestable")), Icon.BOX);
    }
    @EventListener public void extract(RenderGameOverlayEvent event) { extractInfo(event.getContext()); }
    @EventListener public void render(RenderSkiaEvent event) { drawInfo(); }
    @Override public void onDisable() { super.onDisable(); info = null; }
}
