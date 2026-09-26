package cn.pupperclient.mixin.mixins.minecraft.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.RenderGameOverlayEvent;
import cn.pupperclient.management.mod.impl.player.OldAnimationsMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;

@Mixin(Hud.class)
public class MixinInGameHud {
//    /**
//	 * @author EldoDebug
//	 * @reason drawHeart
//	 */
//	@Overwrite
//    private void extractHeart(final GuiGraphicsExtractor graphics, final Gui.HeartType type, final int xo, final int yo, final boolean isHardcore, final boolean blinks, final boolean half) {
//    	// OldAnimationsMod mod = OldAnimationsMod.getInstance();
//		// graphics.blitSprite(RenderPipelines.GUI, type.getSprite(isHardcore, half, (!mod.isEnabled() || !mod.isDisableHeartFlash()) && blinks), xo, yo, 9, 9);
//	}
    
	@Inject(method = "extractHotbarAndDecorations", at = @At("TAIL"))
	private void renderMainHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
		EventBus.getInstance().post(new RenderGameOverlayEvent(context));
	}
}
