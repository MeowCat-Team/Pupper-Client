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

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.mod.impl.misc.HypixelMod;
import cn.pupperclient.utils.server.Server;
import cn.pupperclient.utils.server.ServerUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer<T extends Entity, S extends EntityRenderState> {
    @Unique
    private Entity entity;

    @Inject(method = "extractRenderState", at = @At(value = "TAIL"))
    private void getEntity(T entity, S state, float partialTicks, CallbackInfo ci) {
        this.entity = entity;
    }

    @Inject(
        method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            shift = At.Shift.AFTER
        )
    )
    private void onRenderLevelHead(S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, int offset, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        if (!ServerUtils.isJoin(Server.HYPIXEL)) return;
        if (!HypixelMod.getInstance().isEnabled() || !HypixelMod.getInstance().getLevelHeadSetting().isEnabled()) return;
        if (!(entity instanceof AbstractClientPlayer player)) return;
        if (state.nameTag == null) return;

        Component levelText = Component.literal("Level: ").withStyle(ChatFormatting.AQUA).append(
            Component.literal(String.valueOf(PupperClient.getInstance().getHypixelManager()
                .getByUuid(player.getUUID().toString().replace("-", ""))
                .getNetworkLevel())).withStyle(ChatFormatting.YELLOW));

        poseStack.pushPose();
        poseStack.translate(0, 0.25f, 0);
        submitNodeCollector.submitNameTag(poseStack, state.nameTagAttachment, offset,
            levelText, !state.isDiscrete, state.lightCoords, camera);
        poseStack.popPose();
    }
}
