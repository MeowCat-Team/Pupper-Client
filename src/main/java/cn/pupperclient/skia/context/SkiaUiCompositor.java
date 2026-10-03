package cn.pupperclient.skia.context;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/** A regular Blaze3D pass owns the final blend into Minecraft's render target. */
final class SkiaUiCompositor {
    private record Key(GpuFormat format, boolean raster) {}
    private static final Map<Key, RenderPipeline> PIPELINES = new HashMap<>();

    static void composite(GpuTextureView destination, GpuTextureView ui, boolean raster) {
        var pipeline = PIPELINES.computeIfAbsent(new Key(destination.texture().getFormat(), raster), key -> {
            var builder = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("pupper", "pipeline/skia_ui_" + key.format().name().toLowerCase(java.util.Locale.ROOT) + (raster ? "_raster" : "")))
                    .withVertexShader(Identifier.fromNamespaceAndPath("pupper", "skia_ui"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("pupper", "skia_ui"))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withBindGroupLayout(BindGroupLayout.builder().withSampler("UiTexture").build())
                    .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA), key.format(), ColorTargetState.WRITE_ALL))
                    .withDepthStencilState(Optional.empty())
                    .withCull(false);
            if (raster) builder.withShaderDefine("RASTER_TOP_LEFT");
            return builder.build();
        });
        try (var pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Pupper Client Skia UI", destination, Optional.empty())) {
            pass.setPipeline(pipeline);
            pass.bindTexture("UiTexture", ui, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
    }

    private SkiaUiCompositor() {}
}
