package cn.pupperclient.mixin.mixins.accessors;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemModelResolver.class)
public interface ItemModelResolverAccessor {
    @Invoker("getItemModel")
    ItemModel pupper$getItemModel(Identifier id);
}
