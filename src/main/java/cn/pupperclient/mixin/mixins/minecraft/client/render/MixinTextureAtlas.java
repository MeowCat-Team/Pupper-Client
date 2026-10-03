package cn.pupperclient.mixin.mixins.minecraft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Resource reload can finish before GameRenderer uploads the first Globals uniform on 26.2. */
@Mixin(TextureAtlas.class)
public class MixinTextureAtlas {
    @Inject(method = "uploadAnimationFrames()V", at = @At("HEAD"), cancellable = true)
    private void pupper$waitForGlobalUniform(CallbackInfo ci) {
        // Leave animation states dirty: the next atlas tick uploads them once rendering is ready.
        if (RenderSystem.getGlobalSettingsUniform() == null) ci.cancel();
    }
}
