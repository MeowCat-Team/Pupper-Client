package cn.pupperclient.shader;

import cn.pupperclient.mixin.interfaces.IRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;

public class ExtendedRenderPipelineBuilder extends RenderPipeline.Builder {
    private boolean lineSmooth;

    public ExtendedRenderPipelineBuilder(RenderPipeline.Snippet... snippets) {
        for (RenderPipeline.Snippet snippet : snippets) {
            withSnippet(snippet);
        }
    }

    public ExtendedRenderPipelineBuilder withLineSmooth() {
        lineSmooth = true;
        return this;
    }

    @Override
    public RenderPipeline build() {
        RenderPipeline pipeline = super.build();
        ((IRenderPipeline) pipeline).pupper$setLineSmooth(lineSmooth);

        return pipeline;
    }
}
// based on meteor
