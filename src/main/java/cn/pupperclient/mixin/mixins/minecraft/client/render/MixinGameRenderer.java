package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.management.mod.impl.render.NoHurtFov;
import cn.pupperclient.utils.render.StartupRenderUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GlobalSettingsUniform globalSettingsUniform;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void pupper$initializeStartupUniforms(CallbackInfo ci) {
        var window = minecraft.getWindow();
        StartupRenderUniforms.initialize(globalSettingsUniform, window.getWidth(), window.getHeight(),
                minecraft.options.glintStrength().get(), minecraft.options.getMenuBackgroundBlurriness());
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void tiltViewWhenHurt(CallbackInfo ci) {
        if (NoHurtFov.getInstance().isEnabled() && NoHurtFov.getInstance().nohurtFov.isEnabled()) {
            ci.cancel();
        }
    }
}
