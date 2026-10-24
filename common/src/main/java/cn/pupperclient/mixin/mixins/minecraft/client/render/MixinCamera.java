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

package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.management.mod.impl.player.ZoomMod;
import cn.pupperclient.management.mod.impl.render.ClearViewMod;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.pupperclient.management.mod.impl.player.FreelookMod;
import cn.pupperclient.mixin.interfaces.IMixinCameraEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class MixinCamera {

    @Unique
    boolean firstTime = true;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    private @Nullable Entity entity;

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void onAlignWithEntity(float partialTicks, CallbackInfo ci) {
        if (!FreelookMod.getInstance().isEnabled() || !FreelookMod.getInstance().isActive()) return;
        Entity entity = this.entity;
        if (!(entity instanceof LocalPlayer)) return;

        var cameraOverridden = (IMixinCameraEntity) entity;
        if (firstTime && Minecraft.getInstance().player != null) {
            cameraOverridden.pupperClient$setCameraYaw(Minecraft.getInstance().player.getYRot());
            cameraOverridden.pupperClient$setCameraPitch(Minecraft.getInstance().player.getXRot());
            firstTime = false;
        }

        this.setRotation(cameraOverridden.pupperClient$getCameraYaw(), cameraOverridden.pupperClient$getCameraPitch());
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void onCalculateFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (ZoomMod.getInstance().isEnabled()) {
            float original = cir.getReturnValue();
            float modified = ZoomMod.getInstance().getFov(original);
            cir.setReturnValue(modified);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void pupper$clearViewSky(CameraRenderState cameraState, float partialTicks, CallbackInfo ci) {
        ClearViewMod mod = ClearViewMod.getInstance();
        Minecraft minecraft = Minecraft.getInstance();
        if (mod == null || !mod.isEnabled() || this.entity == null || this.entity != minecraft.player
                || !cameraState.entityRenderState.doesMobEffectBlockSky) {
            return;
        }

        LocalPlayer player = minecraft.player;
        boolean blindnessBlocksSky = player.hasEffect(MobEffects.BLINDNESS) && !mod.isAntiBlindness();
        boolean darknessBlocksSky = player.hasEffect(MobEffects.DARKNESS) && !mod.isAntiDarkness();
        if (!blindnessBlocksSky && !darknessBlocksSky) {
            cameraState.entityRenderState.doesMobEffectBlockSky = false;
        }
    }
}
