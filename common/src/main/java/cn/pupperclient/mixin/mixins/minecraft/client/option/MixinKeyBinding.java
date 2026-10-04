package cn.pupperclient.mixin.mixins.minecraft.client.option;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.pupperclient.management.mod.impl.player.SnapTapMod;
import cn.pupperclient.mixin.interfaces.IMixinKeyBinding;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

@Mixin(KeyMapping.class)
public class MixinKeyBinding implements IMixinKeyBinding {

	@Shadow
	@Final
	private InputConstants.Key defaultKey;

	@Shadow
	private boolean isDown;

	@Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
	public void onGetPressed(CallbackInfoReturnable<Boolean> cir) {

		SnapTapMod mod = SnapTapMod.getInstance();

		if (mod == null || !mod.isEnabled()) {
			return;
		}

		if (this.defaultKey.getValue() == InputConstants.KEY_A) {
			if (this.isDown) {
				if (mod.getRightPressTime() == 0) {
					cir.setReturnValue(true);
					cir.cancel();
					return;
				}

				cir.setReturnValue(mod.getRightPressTime() <= mod.getLeftPressTime());
				cir.cancel();
			}
		} else if (this.defaultKey.getValue() == InputConstants.KEY_D) {
			if (this.isDown) {
				if (mod.getLeftPressTime() == 0) {
					cir.setReturnValue(true);
					cir.cancel();
					return;
				}

				cir.setReturnValue(mod.getLeftPressTime() <= mod.getRightPressTime());
				cir.cancel();
			}
		} else if (this.defaultKey.getValue() == InputConstants.KEY_W) {
			if (this.isDown) {
				if (mod.getForwardPressTime() == 0) {
					cir.setReturnValue(true);
					cir.cancel();
					return;
				}

				cir.setReturnValue(mod.getBackPressTime() <= mod.getForwardPressTime());
				cir.cancel();
			}
		} else if (this.defaultKey.getValue() == InputConstants.KEY_S) {
			if (this.isDown) {
				if (mod.getBackPressTime() == 0) {
					cir.setReturnValue(true);
					cir.cancel();
					return;
				}

				cir.setReturnValue(mod.getForwardPressTime() <= mod.getBackPressTime());
				cir.cancel();
			}
		}
	}

	@Inject(method = "setDown", at = @At("HEAD"))
	public void setPressed(boolean pressed, CallbackInfo ci) {

		SnapTapMod mod = SnapTapMod.getInstance();

		if (mod == null || !mod.isEnabled()) {
			return;
		}

		if (this.defaultKey.getValue() == InputConstants.KEY_A) {
			if (pressed) {
				mod.setLeftPressTime(System.currentTimeMillis());
			} else {
				mod.setLeftPressTime(0);
			}
		} else if (this.defaultKey.getValue() == InputConstants.KEY_D) {
			if (pressed) {
				mod.setRightPressTime(System.currentTimeMillis());
			} else {
				mod.setRightPressTime(0);
			}
		} else if (this.defaultKey.getValue() == InputConstants.KEY_W) {
			if (pressed) {
				mod.setForwardPressTime(System.currentTimeMillis());
			} else {
				mod.setForwardPressTime(0);
			}
		} else if (this.defaultKey.getValue() == InputConstants.KEY_S) {
			if (pressed) {
				mod.setBackPressTime(System.currentTimeMillis());
			} else {
				mod.setBackPressTime(0);
			}
		}
	}

	@Override
	public boolean getRealIsPressed() {
		return this.isDown;
	}
}
