package cn.pupperclient.ui.render;

import com.mojang.blaze3d.*;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.humbleui.types.Rect;
import java.nio.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

/** All UI GPU allocations, uploads, copies and draws go through Blaze3D's public API. */
public final class BlazeUiRenderer implements AutoCloseable {
    private static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("DevicePosition", GpuFormat.RG32_FLOAT).addAttribute("LocalPosition", GpuFormat.RG32_FLOAT)
            .addAttribute("UV", GpuFormat.RG32_FLOAT).addAttribute("Color", GpuFormat.RGBA32_FLOAT)
            .addAttribute("UvBounds", GpuFormat.RGBA32_FLOAT).build();
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/ui")).withVertexShader(id("ui")).withFragmentShader(id("ui"))
            .withVertexBinding(0, FORMAT).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("UiData", UniformType.UNIFORM_BUFFER).withSampler("UiImage").withSampler("Scene").build())
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withDepthStencilState(Optional.empty()).withCull(false).build();
    private static final Map<GpuFormat, RenderPipeline> COMPOSITE = new HashMap<>();
    private final UiAssets assets = new UiAssets();
    private final List<UiTexture> layerPool = new ArrayList<>();
    private final Set<UiTexture> usedLayers = Collections.newSetFromMap(new IdentityHashMap<>());
    private UiTexture ui, scene, white;
    private int width, height;
    private boolean sceneReady;
    private record Prepared(BlazeUiCanvas.Draw draw, GpuBufferSlice vertices, GpuBufferSlice uniforms) {}

    public void draw(GpuTextureView destination, Consumer<BlazeUiCanvas> callback, boolean glass) {
        width = destination.getWidth(0); height = destination.getHeight(0);
        assets.beginFrame(); usedLayers.clear();
        long layerBytes = layerPool.stream().mapToLong(t -> (long) t.texture.getWidth(0) * t.texture.getHeight(0) * 4).sum();
        if (layerPool.size() > 48 || layerBytes > 128L * 1024 * 1024) { layerPool.forEach(UiTexture::close); layerPool.clear(); }
        if (ui == null || ui.texture.getWidth(0) != width || ui.texture.getHeight(0) != height) {
            if (ui != null) ui.close(); ui = new UiTexture("Pupper Client UI", width, height, GpuFormat.RGBA8_UNORM);
        }
        if (white == null) {
            white = new UiTexture("Pupper Client UI white", 1, 1, GpuFormat.RGBA8_UNORM);
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(white.texture, new Vector4f(1));
        }
        sceneReady = glass;
        if (glass) {
            GpuFormat format = destination.texture().getFormat();
            if (scene == null || scene.texture.getFormat() != format || scene.texture.getWidth(0) != width || scene.texture.getHeight(0) != height) {
                if (scene != null) scene.close(); scene = new UiTexture("Pupper Client UI scene", width, height, format);
            }
            // MainRenderTarget has COPY_SRC; no native texture handles or framebuffer wrappers.
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(destination.texture(), scene.texture,
                    destination.baseMipLevel(), 0, 0, 0, 0, width, height);
        }
        var canvas = new BlazeUiCanvas(assets, width, height, glass);
        // Recording fails before touching the destination, so a broken UI preserves the game frame.
        callback.accept(canvas);
        canvas.restoreToCount(1);
        render(canvas.root, ui, canvas.clearColor());
        composite(destination, ui.view);
    }

    private void render(BlazeUiCanvas.Group group, UiTexture target, int clearColor) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        Rect viewport = group.bounds;
        int left = Math.max(0, (int) Math.floor(viewport.getLeft())), top = Math.max(0, (int) Math.floor(viewport.getTop()));
        var prepared = new ArrayList<Prepared>();
        for (Object command : group.commands) {
            BlazeUiCanvas.Draw draw;
            if (command instanceof BlazeUiCanvas.Group child) {
                Rect b = child.bounds;
                int x = Math.max(0, (int) Math.floor(b.getLeft())), y = Math.max(0, (int) Math.floor(b.getTop()));
                int w = Math.min(width, (int) Math.ceil(b.getRight())) - x, h = Math.min(height, (int) Math.ceil(b.getBottom())) - y;
                if (w <= 0 || h <= 0 || child.alpha <= 0) continue;
                UiTexture layer = acquireLayer(w, h);
                render(child, layer, 0);
                draw = new BlazeUiCanvas.Draw(BlazeUiCanvas.IMAGE, layer.view, List.of(), Rect.makeWH(0, 0), new float[4], new float[4], new float[4], new int[]{-1, -1, -1}, new float[]{0, 1, 1});
                int color = (Math.round(child.alpha * 255) << 24) | 0xFFFFFF;
                float u = (float) w / layer.texture.getWidth(0), v = 1 - (float) h / layer.texture.getHeight(0);
                Rect uv = Rect.makeLTRB(0, v, u, 1);
                float[] identity = {1, 0, 0, 1, 0, 0}; int[] corners = {0, 1, 2, 0, 2, 3};
                for (int c : corners) draw.vertex(identity, c == 0 || c == 3 ? x : x + w, c < 2 ? y : y + h,
                        c == 0 || c == 3 ? 0 : u, c < 2 ? 1 : v, color, uv);
            } else draw = (BlazeUiCanvas.Draw) command;
            if (draw.size == 0) continue;
            ByteBuffer vertices = MemoryUtil.memAlloc(draw.size * Float.BYTES).order(ByteOrder.nativeOrder());
            ByteBuffer uniforms = MemoryUtil.memAlloc((9 + BlazeUiCanvas.MAX_CLIPS * 4) * 16).order(ByteOrder.nativeOrder());
            try {
                for (int i = 0; i < draw.size; i++) vertices.putFloat(draw.vertices[i]); vertices.flip();
                uniforms(uniforms, draw, target, left, top); uniforms.flip();
                var memory = encoder.transientMemory();
                var vertexSlice = memory.uploadGpu(vertices, 4, GpuBuffer.USAGE_VERTEX);
                var uniformSlice = memory.uploadGpu(uniforms, RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment(), GpuBuffer.USAGE_UNIFORM);
                prepared.add(new Prepared(draw, vertexSlice, uniformSlice));
            } finally { MemoryUtil.memFree(vertices); MemoryUtil.memFree(uniforms); }
        }
        try (var pass = encoder.createRenderPass(() -> "Pupper Client UI geometry", target.view, Optional.of(color(clearColor)))) {
            pass.setPipeline(PIPELINE);
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            pass.bindTexture("Scene", sceneReady ? scene.view : white.view, sampler);
            for (var draw : prepared) {
                pass.bindTexture("UiImage", draw.draw.texture == null ? white.view : draw.draw.texture, sampler);
                pass.setUniform("UiData", draw.uniforms); pass.setVertexBuffer(0, draw.vertices);
                pass.draw(draw.draw.size / BlazeUiCanvas.VERTEX_FLOATS, 1, 0, 0);
            }
        }
    }

    private void uniforms(ByteBuffer buffer, BlazeUiCanvas.Draw d, UiTexture target, int left, int top) {
        vec(buffer, target.texture.getWidth(0), target.texture.getHeight(0), left, top);
        vec(buffer, d.bounds.getLeft(), d.bounds.getTop(), d.bounds.getWidth(), d.bounds.getHeight());
        vec(buffer, d.radii[0], d.radii[1], d.radii[2], d.radii[3]);
        vec(buffer, d.style[0], d.style[1], d.style[2], d.mode);
        vec(buffer, d.axis[0], d.axis[1], d.axis[2], d.axis[3]);
        for (int i = 0; i < 3; i++) { var color = color(d.colors[Math.min(i, d.colors.length - 1)]); vec(buffer, color.x, color.y, color.z, color.w); }
        vec(buffer, d.stops[0], d.stops[Math.min(1, d.stops.length - 1)], d.stops[Math.min(2, d.stops.length - 1)], d.clips.size());
        for (int i = 0; i < BlazeUiCanvas.MAX_CLIPS; i++) {
            if (i < d.clips.size()) {
                var clip = d.clips.get(i); var m = clip.inverse(); var b = clip.bounds(); var r = clip.radii();
                vec(buffer, m[0], m[1], m[4], clip.difference() ? 1 : 0); vec(buffer, m[2], m[3], m[5], 0);
                vec(buffer, b.getLeft(), b.getTop(), b.getWidth(), b.getHeight()); vec(buffer, r[0], r[1], r[2], r[3]);
            } else for (int k = 0; k < 4; k++) vec(buffer, 0, 0, 0, 0);
        }
    }
    private UiTexture acquireLayer(int w, int h) {
        // Animation changes device bounds every frame. Buckets reuse textures while the
        // composite samples only the original bounds, preserving group clipping/origin.
        w = ((w + 63) / 64) * 64; h = ((h + 63) / 64) * 64;
        for (var layer : layerPool) if (!usedLayers.contains(layer) && layer.texture.getWidth(0) == w && layer.texture.getHeight(0) == h) { usedLayers.add(layer); return layer; }
        var layer = new UiTexture("Pupper Client UI opacity group", w, h, GpuFormat.RGBA8_UNORM); layerPool.add(layer); usedLayers.add(layer); return layer;
    }
    private static void composite(GpuTextureView destination, GpuTextureView ui) {
        var pipeline = COMPOSITE.computeIfAbsent(destination.texture().getFormat(), format -> RenderPipeline.builder()
                .withLocation(id("pipeline/ui_composite_" + format.name().toLowerCase(Locale.ROOT)))
                .withVertexShader(id("skia_ui")).withFragmentShader(id("skia_ui"))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withBindGroupLayout(BindGroupLayout.builder().withSampler("UiTexture").build())
                .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA), format, ColorTargetState.WRITE_ALL))
                .withDepthStencilState(Optional.empty()).withCull(false).build());
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Pupper Client UI composite", destination, Optional.empty())) {
            pass.setPipeline(pipeline); pass.bindTexture("UiTexture", ui, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)); pass.draw(3, 1, 0, 0);
        }
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("pupper", path); }
    private static Vector4f color(int argb) {
        float alpha = (argb >>> 24) / 255f; return new Vector4f(((argb >>> 16) & 255) / 255f * alpha, ((argb >>> 8) & 255) / 255f * alpha, (argb & 255) / 255f * alpha, alpha);
    }
    private static void vec(ByteBuffer b, float x, float y, float z, float w) { b.putFloat(x).putFloat(y).putFloat(z).putFloat(w); }
    public int assetRasterizations() { return assets.rasterizations(); }
    @Override public void close() {
        assets.close(); layerPool.forEach(UiTexture::close); layerPool.clear(); usedLayers.clear();
        if (ui != null) { ui.close(); ui = null; } if (scene != null) { scene.close(); scene = null; } if (white != null) { white.close(); white = null; }
    }
}
