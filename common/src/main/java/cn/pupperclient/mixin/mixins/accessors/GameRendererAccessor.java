/**
 * @Author: oneachina
 * @link: github.com/oneachina
 */
package cn.pupperclient.mixin.mixins.accessors;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("fogRenderer")
    FogRenderer pupper$fogRenderer();
}
