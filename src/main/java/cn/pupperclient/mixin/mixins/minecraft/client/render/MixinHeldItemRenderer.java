package cn.pupperclient.mixin.mixins.minecraft.client.render;

import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.pupperclient.management.mod.impl.player.OldAnimationsMod;
import cn.pupperclient.management.mod.impl.render.CustomHandMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinHeldItemRenderer {

	@Shadow
	protected abstract void applyItemArmAttackTransform(PoseStack matrices, HumanoidArm arm, float swingProgress);

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", ordinal = 1))
	private void renderFirstPersonItem(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack item, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		OldAnimationsMod mod = OldAnimationsMod.getInstance();

		if (item.getItem() instanceof BowItem && mod.isEnabled() && mod.isOldBow()) {
            poseStack.translate(0f, 0.05f, 0.04f);
            poseStack.scale(0.93f, 1f, 1f);
		} else if (item.getItem() instanceof FishingRodItem && mod.isEnabled() && mod.isOldRod()) {
            poseStack.translate(0.08f, -0.027f, -0.33f);
            poseStack.scale(0.93f, 1f, 1f);
		}
	}
	
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void applyCustomHand(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		CustomHandMod mod = CustomHandMod.getInstance();
		
		if(mod.isEnabled()) {
            poseStack.translate(mod.getX(), mod.getY(), mod.getZ());
            poseStack.scale(mod.getScale(), mod.getScale(), mod.getScale());
		}
	}
	
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", ordinal = 2, shift = At.Shift.AFTER))
	private void applyFoodSwingOffset(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		applyOldSwingOffset(player, hand, attack, poseStack);
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", ordinal = 3, shift = At.Shift.AFTER))
	private void applyBlockingSwingOffset(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		applyOldSwingOffset(player, hand, attack, poseStack);
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", ordinal = 4, shift = At.Shift.AFTER))
	private void applyBowSwingOffset(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		applyOldSwingOffset(player, hand, attack, poseStack);
	}

	@Unique
	private void applyOldSwingOffset(AbstractClientPlayer player, InteractionHand hand, float swingProgress,
			PoseStack matrices) {
		if (OldAnimationsMod.getInstance().isEnabled() && OldAnimationsMod.getInstance().isOldBreaking()) {
			final HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
			applyItemArmAttackTransform(matrices, arm, swingProgress);
		}
	}
}
