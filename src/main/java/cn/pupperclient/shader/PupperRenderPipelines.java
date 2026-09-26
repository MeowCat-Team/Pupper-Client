package cn.pupperclient.shader;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public abstract class PupperRenderPipelines {
    private static final List<RenderPipeline> PIPELINES = new ArrayList<>();

    private static final RenderPipeline.Snippet MESH_UNIFORMS = RenderPipeline.builder()
        .withUniform("MeshData", UniformType.UNIFORM_BUFFER)
        .buildSnippet();


    // Blur
    public static final RenderPipeline BLUR_DOWN = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLocation(getLocation("pipeline/blur_down"))
        .withVertexFormat(PupperVertexFormats.POS2, VertexFormat.Mode.TRIANGLES)
        .withVertexShader(getLocation("blur"))
        .withFragmentShader(getLocation("blur_down"))
        .withSampler("u_Texture")
        .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .build()
    );

    public static final RenderPipeline BLUR_UP = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLocation(getLocation("pipeline/blur_up"))
        .withVertexFormat(PupperVertexFormats.POS2, VertexFormat.Mode.TRIANGLES)
        .withVertexShader(getLocation("blur"))
        .withFragmentShader(getLocation("blur_up"))
        .withSampler("u_Texture")
        .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .build()
    );

    public static final RenderPipeline BLUR_PASSTHROUGH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLocation(getLocation("pipeline/passthrough"))
        .withVertexFormat(PupperVertexFormats.POS2, VertexFormat.Mode.TRIANGLES)
        .withVertexShader(getLocation("passthrough"))
        .withFragmentShader(getLocation("passthrough"))
        .withSampler("u_Texture")
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .build()
    );

//
//    // UI
//    public static final RenderPipeline UI_COLORED = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
//        .withLocation(getLocation("pipeline/ui_colored"))
//        .withVertexFormat(PupperVertexFormats.POS2_COLOR, VertexFormat.Mode.TRIANGLES)
//        .withVertexShader(getLocation("ui_colored"))
//        .withFragmentShader(getLocation("ui_colored"))
//        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
//        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
//        .withCull(true)
//        .build()
//    );
//
//    public static final RenderPipeline UI_COLORED_LINES = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
//        .withLineSmooth()
//        .withLocation(getLocation("pipeline/ui_colored_lines"))
//        .withVertexFormat(PupperVertexFormats.POS2_COLOR, VertexFormat.Mode.LINES)
//        .withVertexShader(getLocation("ui_colored"))
//        .withFragmentShader(getLocation("ui_colored"))
//        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
//        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
//        .withCull(true)
//        .build()
//    );
//
//    public static final RenderPipeline UI_TEXTURED = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
//        .withLocation(getLocation("pipeline/ui_textured"))
//        .withVertexFormat(PupperVertexFormats.POS2_COLOR_TEX, VertexFormat.Mode.TRIANGLES)
//        .withVertexShader(getLocation("ui_textured"))
//        .withFragmentShader(getLocation("ui_textured"))
//        .withSampler("u_Texture")
//        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
//        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
//        .withCull(true)
//        .build()
//    );
//
//    public static final RenderPipeline UI_ROUNDED_TEXTURED = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
//        .withLocation(getLocation("pipeline/ui_rounded_textured"))
//        .withVertexFormat(PupperVertexFormats.POS2_COLOR_TEX, VertexFormat.Mode.TRIANGLES)
//        .withVertexShader(getLocation("ui_textured"))
//        .withFragmentShader(getLocation("ui_rounded_textured"))
//        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
//        .build()
//    );

    private static RenderPipeline add(RenderPipeline pipeline) {
        PIPELINES.add(pipeline);
        return pipeline;
    }

    private PupperRenderPipelines() {}

    public static void precompile() {
        GpuDevice device = RenderSystem.getDevice();
        ResourceManager resources = Minecraft.getInstance().getResourceManager();

        for (RenderPipeline pipeline : PIPELINES) {
            device.precompilePipeline(pipeline, (identifier, shaderType) -> {
                var resource = resources.getResource(identifier).get();

                try (var in = resource.open()) {
                    return IOUtils.toString(in, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static Identifier getLocation(String path) {
        return Identifier.fromNamespaceAndPath("pupper", path);
    }
}
// based on meteor
