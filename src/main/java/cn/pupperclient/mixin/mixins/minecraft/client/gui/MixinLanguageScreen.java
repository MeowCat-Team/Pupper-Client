package cn.pupperclient.mixin.mixins.minecraft.client.gui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.network.chat.Component;

@Mixin(value = LanguageSelectScreen.class, priority = 1001)
public class MixinLanguageScreen extends Screen {
    protected MixinLanguageScreen(Component title) {
        super(title);
    }

    @Inject(method = "onDone", at = @At("TAIL"))
    public void onDone(CallbackInfo ci) {
        minecraft.gui.hud.getChat().rescaleChat();
    }
}
