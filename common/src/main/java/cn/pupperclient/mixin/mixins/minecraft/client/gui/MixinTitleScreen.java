package cn.pupperclient.mixin.mixins.minecraft.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TitleScreen.class, priority = 1001)
public abstract class MixinTitleScreen extends Screen {

    protected MixinTitleScreen(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("HEAD"))
    public void onInit(CallbackInfo ci) {

    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    public void onRender(CallbackInfo ci) {

    }
}
