/**
 * @Author: oneachina
 * @link: github.com/oneachina
 */
package cn.pupperclient.utils.render;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import java.util.List;

public class PupperGuiRenderer extends GuiRenderer {
    public PupperGuiRenderer(GuiRenderState renderState, FeatureRenderDispatcher featureRenderDispatcher, List<PictureInPictureRenderer<?>> pictureInPictureRenderers) {
        super(renderState, featureRenderDispatcher, pictureInPictureRenderers);
    }
}
