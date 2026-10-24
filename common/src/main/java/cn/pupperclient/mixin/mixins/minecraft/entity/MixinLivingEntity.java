/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.pupperclient.mixin.mixins.minecraft.entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.pupperclient.management.mod.impl.hud.JumpResetIndicatorMod;
import cn.pupperclient.management.mod.impl.player.NoJumpDelayMod;
import cn.pupperclient.management.mod.impl.render.ClearViewMod;
import cn.pupperclient.mixin.interfaces.IMixinLivingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

	@Inject(method = "getEffectBlendFactor", at = @At("HEAD"), cancellable = true)
	private void pupper$clearViewEffectBlend(Holder<MobEffect> effect, float partialTicks,
			CallbackInfoReturnable<Float> cir) {
		ClearViewMod mod = ClearViewMod.getInstance();
		if (mod == null || !mod.isEnabled() || (Object) this != Minecraft.getInstance().player) {
			return;
		}

		if ((effect.is(MobEffects.NAUSEA) && mod.isAntiNausea())
				|| (effect.is(MobEffects.DARKNESS) && mod.isAntiDarkness())) {
			cir.setReturnValue(0.0F);
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
