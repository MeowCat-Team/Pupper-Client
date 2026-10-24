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

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Resource reload can finish before GameRenderer uploads the first Globals uniform on 26.2. */
@Mixin(TextureAtlas.class)
public class MixinTextureAtlas {
    @Inject(method = "uploadAnimationFrames()V", at = @At("HEAD"), cancellable = true)
    private void pupper$waitForGlobalUniform(CallbackInfo ci) {
        // Leave animation states dirty: the next atlas tick uploads them once rendering is ready.
        if (RenderSystem.getGlobalSettingsUniform() == null) ci.cancel();
    }
}
