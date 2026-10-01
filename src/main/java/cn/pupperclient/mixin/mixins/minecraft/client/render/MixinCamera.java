package cn.pupperclient.mixin.mixins.minecraft.client.render;

import cn.pupperclient.management.mod.impl.player.ZoomMod;
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
}
