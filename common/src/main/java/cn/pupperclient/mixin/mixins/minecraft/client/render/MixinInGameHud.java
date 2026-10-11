package cn.pupperclient.mixin.mixins.minecraft.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.RenderGameOverlayEvent;
import cn.pupperclient.management.mod.impl.hud.ItemInfoMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;

@Mixin(Hud.class)
public class MixinInGameHud {
    // NeoForge also has a height-aware overload; omitting target arguments covers both.
    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true, require = 1)
    private void pupper$itemInfoName(CallbackInfo ci) {
        var itemInfo = ItemInfoMod.getInstance();
        if (itemInfo != null && itemInfo.isEnabled()) ci.cancel();
    }
	// Both loaders extract effects immediately after the hotbar and its decorations.
	// NeoForge splits the decorations into layers and removes the vanilla dispatcher.
	@Inject(method = "extractEffects", at = @At("HEAD"), require = 1)
	private void renderMainHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
		EventBus.getInstance().post(new RenderGameOverlayEvent(context));
	}
}
