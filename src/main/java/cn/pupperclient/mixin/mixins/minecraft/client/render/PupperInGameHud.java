package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.DrawItemHotbarEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class PupperInGameHud {
    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void HotbarRender(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        DrawItemHotbarEvent event = new DrawItemHotbarEvent();
        EventBus.getInstance().post(event);

        if (event.isCancelled()) ci.cancel();
    }
}
