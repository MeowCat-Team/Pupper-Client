package cn.pupperclient.mixin.mixins.viafabricplus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import cn.pupperclient.management.mod.impl.player.OldAnimationsMod;

@Mixin(value = Minecraft.class, priority = 2000)
public class MixinMinecraftClient {

	@Shadow
	public LocalPlayer player;
	
	@Shadow
	public MultiPlayerGameMode gameMode;
	
    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"))
    private boolean injectOldAnimation(boolean original) {
    	
    	if(OldAnimationsMod.getInstance().isEnabled()) {
    		return false;
    	}
    	
        return original;
    }
}
