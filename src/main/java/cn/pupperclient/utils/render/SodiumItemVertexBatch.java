package cn.pupperclient.utils.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.math.MatrixHelper;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.util.ColorMixer;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import net.caffeinemc.mods.sodium.client.model.quad.BakedQuadView;
import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.lwjgl.system.MemoryStack;

/** Optional Sodium bridge: the existing writer still owns format conversion and upload. */
public final class SodiumItemVertexBatch {
    public static final int QUADS_PER_BATCH = 64;
    public static final int QUAD_BYTES = 4 * EntityVertex.STRIDE;
    private static final boolean MULTIPLY_COLORS =
            PlatformRuntimeInformation.getInstance().usesBakedQuadColorMultiplication();
    private static final ThreadLocal<Encoder> ENCODER = ThreadLocal.withInitial(Encoder::new);

    private SodiumItemVertexBatch() { }

    static boolean write(VertexConsumer consumer, PoseStack.Pose pose, List<BakedQuad> quads,
                         int start, int end, int[] tints, QuadInstance instance) {
        VertexBufferWriter writer = VertexBufferWriter.tryOf(consumer);
        // Unsupported decorators retain their own putBakedQuad semantics. BakedQuad is final,
        // so Sodium's mixin either supplies this interface to every quad or to none of them.
        if (writer == null || !((Object) quads.get(start) instanceof BakedQuadView)) return false;
        Encoder encoder = ENCODER.get();
        encoder.reset(pose);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(4, Math.min(QUADS_PER_BATCH, end - start) * QUAD_BYTES);
            for (int base = start; base < end; base += QUADS_PER_BATCH) {
                int limit = Math.min(end, base + QUADS_PER_BATCH);
                for (int index = base; index < limit; index++) {
                    BakedQuad quad = quads.get(index);
                    instance.setColor(ItemQuadBatcher.tint(tints, quad.materialInfo()));
                    encoder.write(buffer + (long) (index - base) * QUAD_BYTES, (BakedQuadView) (Object) quad, instance);
                }
                // Same EntityVertex format as Sodium's single-quad path. push copies the data
                // before returning; the bounded scratch region is reused for the next chunk.
                writer.push(stack, buffer, (limit - base) * 4, EntityVertex.FORMAT);
            }
        }
        return true;
    }

    /** Reuses transformed packed normals only within one immutable pose, never between items. */
    public static final class Encoder {
        private final int[] sourceNormals = new int[16];
        private final int[] transformedNormals = new int[16];
        private int normalCount;
        private PoseStack.Pose pose;

        public void reset(PoseStack.Pose pose) {
            this.pose = pose;
            normalCount = 0;
        }

        private int normal(int packed) {
            for (int index = 0; index < normalCount; index++) {
                if (sourceNormals[index] == packed) return transformedNormals[index];
            }
            int result = MatrixHelper.transformNormal(pose.normal(), pose.trustedNormals, packed);
            if (normalCount < sourceNormals.length) {
                sourceNormals[normalCount] = packed;
                transformedNormals[normalCount++] = result;
            }
            return result;
        }

        public void write(long address, BakedQuadView quad, QuadInstance instance) {
            var matrix = pose.pose();
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = quad.getX(vertex), y = quad.getY(vertex), z = quad.getZ(vertex);
                int color = instance.getColor(vertex);
                if (MULTIPLY_COLORS) color = ColorMixer.mulComponentWise(color, quad.getColor(vertex));
                EntityVertex.write(address + (long) vertex * EntityVertex.STRIDE,
                        MatrixHelper.transformPositionX(matrix, x, y, z),
                        MatrixHelper.transformPositionY(matrix, x, y, z),
                        MatrixHelper.transformPositionZ(matrix, x, y, z),
                        ColorARGB.toABGR(color), quad.getTexU(vertex), quad.getTexV(vertex),
                        instance.overlayCoords(), instance.getLightCoordsWithEmission(vertex, quad.getLightEmission()),
                        normal(quad.getAccurateNormal(vertex)));
            }
        }
    }
}
