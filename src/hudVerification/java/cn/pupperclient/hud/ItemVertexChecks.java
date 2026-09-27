package cn.pupperclient.hud;

import java.util.Arrays;
import java.util.Random;

import cn.pupperclient.utils.render.SodiumItemVertexBatch;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import net.caffeinemc.mods.sodium.client.model.quad.BakedQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.immediate.model.BakedModelEncoder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Compares packed vertices with the installed Sodium encoder; no game or graphics context. */
public final class ItemVertexChecks {
    private static final int BYTES = SodiumItemVertexBatch.QUAD_BYTES;
    private static volatile long checksum;

    public static void main(String[] args) throws ReflectiveOperationException {
        verify();
        verifyIris();
        if (args.length != 0 && args[0].equals("--benchmark")) benchmark();
    }

    private static void verifyIris() throws ReflectiveOperationException {
        // Iris is a runtime dependency, not an API used by the production batcher. Exercise
        // its real converter here: grouping must preserve tangents, mid-UV and item/entity IDs.
        Class<?> serializerType = Class.forName("net.irisshaders.iris.vertices.sodium.ModelToEntityVertexSerializer");
        Object serializer = serializerType.getConstructor().newInstance();
        var serialize = serializerType.getMethod("serialize", long.class, long.class, int.class);
        Class<?> stateType = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
        Object state = stateType.getField("INSTANCE").get(null);
        VertexFormat format = (VertexFormat) Class.forName("net.irisshaders.iris.vertices.IrisVertexFormats")
                .getField("ENTITY").get(null);
        int stride = format.getVertexSize();
        int totalBytes = 129 * 4 * stride;
        long reference = MemoryUtil.nmemCalloc(1, totalBytes);
        long candidate = MemoryUtil.nmemCalloc(1, totalBytes);
        try (CopyWriter source = new CopyWriter(129)) {
            encode(false, source, new SodiumItemVertexBatch.Encoder(), new PoseStack().last(),
                    quads(129, true), 129, new QuadInstance());
            for (int context = 0; context < 3; context++) {
                stateType.getMethod("setCurrentRenderedItem", int.class).invoke(state, context * 117);
                stateType.getMethod("setCurrentEntity", int.class).invoke(state, context * 913);
                stateType.getMethod("setCurrentBlockEntity", int.class).invoke(state, context * 23);
                for (int count : new int[]{1, 63, 64, 65, 129}) {
                    for (int quad = 0; quad < count; quad++) {
                        serialize.invoke(serializer, source.address + (long) quad * BYTES,
                                reference + (long) quad * 4 * stride, 4);
                    }
                    for (int base = 0; base < count; base += SodiumItemVertexBatch.QUADS_PER_BATCH) {
                        int length = Math.min(SodiumItemVertexBatch.QUADS_PER_BATCH, count - base);
                        serialize.invoke(serializer, source.address + (long) base * BYTES,
                                candidate + (long) base * 4 * stride, length * 4);
                    }
                    byte[] expected = new byte[count * 4 * stride];
                    byte[] actual = new byte[expected.length];
                    MemoryUtil.memByteBuffer(reference, expected.length).get(expected);
                    MemoryUtil.memByteBuffer(candidate, actual.length).get(actual);
                    if (!Arrays.equals(expected, actual)) throw new AssertionError("Iris batch conversion changed: "
                            + "count=" + count + ", context=" + context);
                }
            }
        } finally {
            MemoryUtil.nmemFree(reference);
            MemoryUtil.nmemFree(candidate);
        }
        System.out.println("Iris vertex checks passed: 15 batch/context cases preserve packed extended vertices.");
    }

    private static void verify() {
        TestQuad[] quads = quads(129, true);
        SodiumItemVertexBatch.Encoder encoder = new SodiumItemVertexBatch.Encoder();
        QuadInstance instance = new QuadInstance();
        int checks = 0;
        try (CopyWriter reference = new CopyWriter(quads.length); CopyWriter candidate = new CopyWriter(quads.length)) {
            for (int poseIndex = 0; poseIndex < 6; poseIndex++) {
                PoseStack poses = new PoseStack();
                if (poseIndex != 0) {
                    poses.translate(12.25F, -3.5F, 0.125F);
                    poses.mulPose(new Quaternionf().rotationXYZ(0.32F * poseIndex, -0.4F, 1.1F));
                }
                if (poseIndex == 2) poses.scale(2, 2, 2);
                if (poseIndex == 3) poses.scale(0.5F, 3, 1.25F);
                if (poseIndex == 4) poses.scale(-1, 2, 0.5F);
                if (poseIndex == 5) poses.scale(-2, -2, -2);
                for (int count : new int[]{1, 6, 63, 64, 65, 129}) {
                    reference.reset();
                    candidate.reset();
                    encode(false, reference, encoder, poses.last(), quads, count, instance);
                    encode(true, candidate, encoder, poses.last(), quads, count, instance);
                    if (reference.bytes != count * BYTES || candidate.bytes != reference.bytes) {
                        throw new AssertionError("Missing vertices at batch boundary " + count);
                    }
                    int expectedPushes = (count + SodiumItemVertexBatch.QUADS_PER_BATCH - 1)
                            / SodiumItemVertexBatch.QUADS_PER_BATCH;
                    if (candidate.pushes != expectedPushes || reference.pushes != count) {
                        throw new AssertionError("Unexpected push count at batch boundary " + count);
                    }
                    byte[] expected = reference.copy();
                    byte[] actual = candidate.copy();
                    int mismatch = Arrays.mismatch(expected, actual);
                    if (mismatch != -1) throw new AssertionError("Vertex mismatch: pose=" + poseIndex
                            + ", count=" + count + ", quad=" + mismatch / BYTES + ", byte=" + mismatch % BYTES);
                    checks += count * 4;
                }
            }
        }
        System.out.printf("Item vertex checks passed: %,d vertices match Sodium byte-for-byte across "
                + "six poses, lights, colors, emissive faces, normal-cache overflow/reset and 64-quad boundaries.%n", checks);
    }

    private static void encode(boolean batch, CopyWriter writer, SodiumItemVertexBatch.Encoder encoder,
                               PoseStack.Pose pose, TestQuad[] quads, int count, QuadInstance instance) {
        if (!batch) {
            for (int index = 0; index < count; index++) {
                attributes(instance, index);
                BakedModelEncoder.writeQuadVertices(writer, pose, quads[index], instance);
            }
            return;
        }
        encoder.reset(pose);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int capacity = SodiumItemVertexBatch.QUADS_PER_BATCH;
            long buffer = stack.nmalloc(4, Math.min(capacity, count) * BYTES);
            for (int base = 0; base < count; base += capacity) {
                int limit = Math.min(count, base + capacity);
                for (int index = base; index < limit; index++) {
                    attributes(instance, index);
                    encoder.write(buffer + (long) (index - base) * BYTES, quads[index], instance);
                }
                writer.push(stack, buffer, (limit - base) * 4, EntityVertex.FORMAT);
            }
        }
    }

    private static void attributes(QuadInstance instance, int index) {
        instance.setOverlayCoords((index % 16) << 16 | (15 - index % 16));
        for (int vertex = 0; vertex < 4; vertex++) {
            instance.setColor(vertex, 0x80553217 + index * 701 + vertex * 37115);
            instance.setLightCoords(vertex, ((index + vertex) % 16) << 20 | ((index * 3 + vertex) % 16) << 4);
        }
    }

    private static void benchmark() {
        final int items = 512, faces = 64, rounds = 31, warmup = 15;
        TestQuad[] quads = quads(faces, false);
        PoseStack.Pose[] poses = new PoseStack.Pose[items];
        for (int index = 0; index < items; index++) {
            PoseStack stack = new PoseStack();
            stack.translate(index % 32, 0.2F, index / 32);
            stack.mulPose(new Quaternionf().rotationY(index * 0.17F));
            stack.scale(0.5F, 0.5F, 0.5F);
            poses[index] = stack.last();
        }
        long[][] nanos = new long[2][rounds];
        SodiumItemVertexBatch.Encoder encoder = new SodiumItemVertexBatch.Encoder();
        QuadInstance instance = new QuadInstance();
        try (CopyWriter writer = new CopyWriter(items * faces)) {
            for (int round = -warmup; round < rounds; round++) {
                // Alternate order so one implementation does not always benefit from warm caches.
                for (int step = 0; step < 2; step++) {
                    int mode = (round + step) & 1;
                    writer.reset();
                    long start = System.nanoTime();
                    for (PoseStack.Pose pose : poses) encode(mode == 1, writer, encoder, pose, quads, faces, instance);
                    long elapsed = System.nanoTime() - start;
                    checksum += MemoryUtil.memGetInt(writer.address + writer.bytes - BYTES)
                            + writer.bytes + writer.pushes;
                    if (round >= 0) nanos[mode][round] = elapsed;
                }
            }
        }
        Arrays.sort(nanos[0]);
        Arrays.sort(nanos[1]);
        double oldMs = nanos[0][rounds / 2] / 1e6;
        double newMs = nanos[1][rounds / 2] / 1e6;
        System.out.printf("Item encoder CPU benchmark: %d items x %d quads; median Sodium %.3f ms, batch %.3f ms "
                + "(%.1f%% reduction); checksum=%d. Includes native staging copies, excludes GPU and game FPS.%n",
                items, faces, oldMs, newMs, (1 - newMs / oldMs) * 100, checksum);
    }

    private static TestQuad[] quads(int count, boolean variedNormals) {
        Random random = new Random(923721L);
        TestQuad[] quads = new TestQuad[count];
        for (int index = 0; index < count; index++) quads[index] = new TestQuad(random, index, variedNormals);
        return quads;
    }

    private static final class CopyWriter implements VertexBufferWriter, AutoCloseable {
        private final long address;
        private final int capacity;
        private int bytes;
        private int pushes;
        private CopyWriter(int quads) {
            capacity = quads * BYTES;
            address = MemoryUtil.nmemCalloc(1, capacity);
            if (address == 0) throw new OutOfMemoryError("Vertex test allocation");
        }
        void reset() { bytes = 0; pushes = 0; }
        byte[] copy() {
            byte[] result = new byte[bytes];
            MemoryUtil.memByteBuffer(address, bytes).get(result);
            return result;
        }
        @Override public void push(MemoryStack stack, long source, int count, VertexFormat format) {
            if (format != EntityVertex.FORMAT) throw new AssertionError("Changed vertex format");
            int length = count * EntityVertex.STRIDE;
            if (length > capacity - bytes) throw new AssertionError("Vertex staging overflow");
            MemoryUtil.memCopy(source, address + bytes, length);
            bytes += length;
            pushes++;
        }
        @Override public void close() { MemoryUtil.nmemFree(address); }
    }

    private static final class TestQuad implements BakedQuadView {
        private final float[] coordinates = new float[12];
        private final int[] normals = new int[4];
        private final int index;
        TestQuad(Random random, int index, boolean variedNormals) {
            this.index = index;
            for (int vertex = 0; vertex < 4; vertex++) {
                for (int axis = 0; axis < 3; axis++) coordinates[vertex * 3 + axis] = random.nextFloat() * 2 - 1;
                Vector3f normal = variedNormals
                        ? new Vector3f(random.nextFloat() - 0.5F, random.nextFloat() - 0.5F, random.nextFloat() - 0.5F).normalize()
                        : switch (index % 6) {
                            case 0 -> new Vector3f(1, 0, 0);
                            case 1 -> new Vector3f(-1, 0, 0);
                            case 2 -> new Vector3f(0, 1, 0);
                            case 3 -> new Vector3f(0, -1, 0);
                            case 4 -> new Vector3f(0, 0, 1);
                            default -> new Vector3f(0, 0, -1);
                        };
                normals[vertex] = variedNormals && vertex == 0 ? 0 : NormI8.pack(normal);
            }
        }
        @Override public float getX(int vertex) { return coordinates[vertex * 3]; }
        @Override public float getY(int vertex) { return coordinates[vertex * 3 + 1]; }
        @Override public float getZ(int vertex) { return coordinates[vertex * 3 + 2]; }
        @Override public int getColor(int vertex) { return 0x80241742 + index * 397 + vertex * 199; }
        @Override public float getTexU(int vertex) { return vertex / 4.0F; }
        @Override public float getTexV(int vertex) { return (vertex + 1) / 4.0F; }
        @Override public int getVertexNormal(int vertex) { return normals[vertex]; }
        @Override public int getFaceNormal() { return NormI8.pack(0, 1, 0); }
        @Override public int getLight(int vertex) { return 0; }
        @Override public int getFlags() { return 0; }
        @Override public int getTintIndex() { return -1; }
        @Override public TextureAtlasSprite getSprite() { return null; }
        @Override public Direction getLightFace() { return Direction.UP; }
        @Override public int getMaxLightQuad(int vertex) { return 0; }
        @Override public ModelQuadFacing getNormalFace() { return ModelQuadFacing.POS_Y; }
        @Override public boolean hasShade() { return true; }
        @Override public boolean hasAO() { return true; }
        @Override public int getLightEmission() { return index % 16; }
    }
}
