package cn.pupperclient.shader;

import cn.pupperclient.utils.color.Color;
import cn.pupperclient.utils.render.RenderUtils;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4fc;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.OptionalDouble;
import java.util.Optional;

public class PupperMeshRenderer {
    private static final PupperMeshRenderer INSTANCE = new PupperMeshRenderer();

    private static boolean taken;

    private GpuTextureView colorAttachment;
    private GpuTextureView depthAttachment;
    private Color clearColor;
    private RenderPipeline pipeline;
    private @Nullable PupperMeshBuilder mesh;
    private @Nullable GpuBuffer vertexBuffer;
    private @Nullable GpuBuffer indexBuffer;
    private Matrix4f matrix;
    private final HashMap<String, GpuBufferSlice> uniforms = new HashMap<>();
    private final HashMap<String, SamplerBinding> samplers = new HashMap<>();

    private record SamplerBinding(GpuTextureView view, GpuSampler sampler) {}

    PupperMeshRenderer() {}

    public static PupperMeshRenderer begin() {
        if (taken) throw new IllegalStateException("MeshRenderer already taken.");
        taken = true;

        return INSTANCE;
    }

    public PupperMeshRenderer attachments(RenderTarget framebuffer) {
        colorAttachment = framebuffer.getColorTextureView();
        depthAttachment = framebuffer.getDepthTextureView();
        return this;
    }

    public PupperMeshRenderer attachments(GpuTextureView color, GpuTextureView depth) {
        colorAttachment = color;
        depthAttachment = depth;
        return this;
    }

    public PupperMeshRenderer pipeline(RenderPipeline pipeline) {
        this.pipeline = pipeline;
        return this;
    }

    public PupperMeshRenderer mesh(PupperMeshBuilder mesh) {
        this.mesh = mesh;
        return this;
    }

    public PupperMeshRenderer mesh(PupperMeshBuilder mesh, Matrix4f matrix) {
        this.mesh = mesh;
        return this.transform(matrix);
    }

    public PupperMeshRenderer mesh(PupperMeshBuilder mesh, PoseStack matrices) {
        this.mesh = mesh;
        return this.transform(matrices);
    }

    public PupperMeshRenderer mesh(GpuBuffer vertices, GpuBuffer indices) {
        this.vertexBuffer = vertices;
        this.indexBuffer = indices;
        return this;
    }

    public PupperMeshRenderer transform(Matrix4f matrix) {
        this.matrix = matrix;
        return this;
    }

    public PupperMeshRenderer transform(PoseStack matrices) {
        this.matrix = matrices.last().pose();
        return this;
    }

    public PupperMeshRenderer fullscreen() {
        return this.mesh(PupperFullScreenRenderer.vbo, PupperFullScreenRenderer.ibo);
    }

    public PupperMeshRenderer uniform(String name, GpuBufferSlice slice) {
        uniforms.put(name, slice);
        return this;
    }

    public PupperMeshRenderer sampler(String name, GpuTextureView view, GpuSampler sampler) {
        if (name != null && view != null && sampler != null) {
            samplers.put(name, new SamplerBinding(view, sampler));
        }

        return this;
    }

    public void end() {
        int indexCount = mesh != null ? mesh.getIndicesCount()
            : (int) (indexBuffer != null ? indexBuffer.size() / Integer.BYTES : -1);

        if (pipeline != null && indexCount > 0) {
            GpuBuffer vertexBuffer = mesh != null ? mesh.getVertexBuffer() : this.vertexBuffer;
            GpuBuffer indexBuffer = mesh != null ? mesh.getIndexBuffer() : this.indexBuffer;

            if (vertexBuffer != null && indexBuffer != null) {
                GpuBufferSlice meshData = MeshUniforms.write(RenderUtils.projection, RenderSystem.getModelViewStack());

                Optional<Vector4fc> clearColor = this.clearColor != null ?
                    Optional.of(new Vector4f(this.clearColor.r / 255f, this.clearColor.g / 255f,
                        this.clearColor.b / 255f, this.clearColor.a / 255f)) : Optional.empty();

                RenderPass pass = (depthAttachment != null && pipeline.wantsDepthTexture()) ?
                    RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Pupper MeshRenderer", colorAttachment, clearColor, depthAttachment, OptionalDouble.empty()) :
                    RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Pupper MeshRenderer", colorAttachment, clearColor);

                pass.setPipeline(pipeline);
                pass.setUniform("MeshData", meshData);

                for (var entry : uniforms.entrySet()) {
                    pass.setUniform(entry.getKey(), entry.getValue());
                }

                for (var entry : samplers.entrySet()) {
                    pass.bindTexture(entry.getKey(), entry.getValue().view(), entry.getValue().sampler());
                }

                pass.setVertexBuffer(0, vertexBuffer.slice());
                pass.setIndexBuffer(indexBuffer, IndexType.INT);
                pass.drawIndexed(indexCount, 1, 0, 0, 0);

                pass.close();
                if (mesh != null) {
                    vertexBuffer.close();
                    indexBuffer.close();
                }
            }
        }

        colorAttachment = null;
        depthAttachment = null;
        clearColor = null;
        pipeline = null;
        mesh = null;
        vertexBuffer = null;
        indexBuffer = null;
        matrix = null;

        taken = false;
    }
}
// based on meteor
