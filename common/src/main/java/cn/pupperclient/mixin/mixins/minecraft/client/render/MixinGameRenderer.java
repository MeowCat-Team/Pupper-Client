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

import cn.pupperclient.management.mod.impl.render.NoHurtFov;
import cn.pupperclient.utils.render.StartupRenderUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GlobalSettingsUniform globalSettingsUniform;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void pupper$initializeStartupUniforms(CallbackInfo ci) {
        var window = minecraft.getWindow();
        StartupRenderUniforms.initialize(globalSettingsUniform, window.getWidth(), window.getHeight(),
                minecraft.options.glintStrength().get(), minecraft.options.getMenuBackgroundBlurriness());
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void tiltViewWhenHurt(CallbackInfo ci) {
        if (NoHurtFov.getInstance().isEnabled() && NoHurtFov.getInstance().nohurtFov.isEnabled()) {
            ci.cancel();
        }
    }
}
