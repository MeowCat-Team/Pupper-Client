package cn.pupperclient.mixin.mixins.minecraft.entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.pupperclient.management.mod.impl.hud.JumpResetIndicatorMod;
import cn.pupperclient.management.mod.impl.player.NoJumpDelayMod;
import cn.pupperclient.mixin.interfaces.IMixinLivingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity implements IMixinLivingEntity{

	@Shadow
	private int noJumpDelay;

	@Shadow
	public int swingTime;

	@Shadow
	public boolean swinging;

    @Shadow
	public InteractionHand swingingArm;

    public MixinLivingEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Shadow
    protected abstract int getCurrentSwingDuration();

    @Shadow
    public abstract void jumpFromGround();

    @Shadow
    public abstract float getViewYRot(float tickDelta);

    @Shadow
    protected abstract void defineSynchedData(SynchedEntityData.Builder builder);

    @Inject(method = "aiStep", at = @At("HEAD"))
	public void onNoJumpDelay(CallbackInfo ci) {
		if (NoJumpDelayMod.getInstance().isEnabled()) {
			noJumpDelay = 0;
		}
	}

	@Inject(method = "jumpFromGround", at = @At("HEAD"))
	private void onJump(CallbackInfo info) {

		JumpResetIndicatorMod mod = JumpResetIndicatorMod.getInstance();
		Minecraft client = Minecraft.getInstance();

		if ((Object) this == client.player) {
			mod.setJumpAge(client.player.tickCount);
			mod.setLastTime(System.currentTimeMillis());
		}
	}

	@Inject(method = "handleDamageEvent", at = @At("HEAD"))
	private void onDamage(CallbackInfo info) {

		JumpResetIndicatorMod mod = JumpResetIndicatorMod.getInstance();
		Minecraft client = Minecraft.getInstance();

		if ((Object) this == client.player) {
			mod.setHurtAge(client.player.tickCount);
		}
	}

	@Override
	public void pupperClient$fakeSwingHand(InteractionHand hand) {
		if (!this.swinging || this.swingTime >= this.getCurrentSwingDuration() / 2 || this.swingTime < 0) {
			this.swingTime = -1;
			this.swinging = true;
			this.swingingArm = hand;
		}
	}
}
