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
import cn.pupperclient.utils.minecraft.interfaces.IMinecraft;
import cn.pupperclient.utils.render.PupperGuiRenderer;
import cn.pupperclient.utils.render.RenderUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.util.profiling.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin implements IMinecraft {
    @Unique
    private PupperGuiRenderer guiRenderer;

    @Shadow
    public abstract void endFrame();

    @Unique
    private GuiRenderState renderState;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void pupper$init(GuiRenderState renderState, FeatureRenderDispatcher featureRenderDispatcher, List<PictureInPictureRenderer<?>> pictureInPictureRenderers, CallbackInfo ci) {
        if ((GuiRenderer) (Object) this instanceof PupperGuiRenderer) return;
        this.renderState = new GuiRenderState();

        guiRenderer = new PupperGuiRenderer(
            this.renderState,
            featureRenderDispatcher,
            pictureInPictureRenderers
        );
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void draw(CallbackInfo ci) {
        if (guiRenderer == null) return;

        if (RenderUtils.canUpdate()) {
            Profiler.get().push(PupperClient.getModId() + "_render_2d");

            RenderUtils.unscaledProjection();

            guiRenderer.render();

            RenderUtils.scaledProjection();

            Profiler.get().pop();
        }

        assert client.gameRenderer.mainRenderTarget().getDepthTexture() != null;
        RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(client.gameRenderer.mainRenderTarget().getDepthTexture(), 1.0);
        endFrame();
    }
}
