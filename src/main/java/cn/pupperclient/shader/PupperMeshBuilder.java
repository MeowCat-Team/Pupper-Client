package cn.pupperclient.shader;

import cn.pupperclient.utils.color.Color;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryUtil.*;

public class PupperMeshBuilder {
    private static final boolean DEBUG = FabricLoader.getInstance().isDevelopmentEnvironment();

    public double alpha = 1;

    private final VertexFormat format;
    private final int primitiveVerticesSize;
    private final int primitiveIndicesCount;

    private ByteBuffer vertices = null;
    private long verticesPointerStart, verticesPointer;

    private ByteBuffer indices = null;
    private long indicesPointer;

    private int vertexI, indicesCount;

    private boolean building;

    public PupperMeshBuilder(RenderPipeline pipeline) {
        this(pipeline.getVertexFormatBinding(0), pipeline.getPrimitiveTopology());
    }

    public PupperMeshBuilder(VertexFormat format, PrimitiveTopology mode) {
        this.format = format;
        primitiveVerticesSize = format.getVertexSize();
        primitiveIndicesCount = mode.connectedPrimitives ? mode.primitiveStride : mode.primitiveLength;
    }

    public PupperMeshBuilder(VertexFormat format, PrimitiveTopology drawMode, int vertexCount, int indexCount) {
        this(format, drawMode);
        allocateBuffers(vertexCount, indexCount);
    }

    public void begin() {
        if (building) throw new IllegalStateException("Mesh.begin() called while already building.");

        verticesPointer = verticesPointerStart;
        vertexI = 0;
        indicesCount = 0;

        building = true;
    }

    public PupperMeshBuilder vec2(double x, double y) {
        debugVertexBufferCapacity();

        long p = verticesPointer;

        memPutFloat(p, (float) x);
        memPutFloat(p + 4, (float) y);

        verticesPointer += 8;
        return this;
    }

    public PupperMeshBuilder color(Color c) {
        debugVertexBufferCapacity();

        long p = verticesPointer;

        memPutByte(p, (byte) c.r);
        memPutByte(p + 1, (byte) c.g);
        memPutByte(p + 2, (byte) c.b);
        memPutByte(p + 3, (byte) (c.a * (float) alpha));

        verticesPointer += 4;
        return this;
    }

    public int next() {
        return vertexI++;
    }

    public void line(int i1, int i2) {
        debugIndexBufferCapacity();
        memPutInt(indicesPointer, i1);
        memPutInt(indicesPointer + 4, i2);
        indicesPointer += 8;
        indicesCount += 2;
    }

    public void quad(int i1, int i2, int i3, int i4) {
        debugIndexBufferCapacity();
        memPutInt(indicesPointer, i1);
        memPutInt(indicesPointer + 4, i2);
        memPutInt(indicesPointer + 8, i3);
        memPutInt(indicesPointer + 12, i1);
        memPutInt(indicesPointer + 16, i3);
        memPutInt(indicesPointer + 20, i4);
        indicesPointer += 24;
        indicesCount += 6;
    }

    public void ensureQuadCapacity() {
        ensureCapacity(4, 6);
    }

    public void ensureTriCapacity() {
        ensureCapacity(3, 3);
    }

    public void ensureLineCapacity() {
        ensureCapacity(2, 2);
    }

    public void ensureCapacity(int vertexCount, int indexCount) {
        if (DEBUG && (indexCount % primitiveIndicesCount != 0)) {
            throw new IllegalArgumentException("Unexpected amount of indices written to MeshBuilder.");
        }

        if (vertices == null || indices == null) {
            allocateBuffers(256 * 4, 512 * 4);
            return;
        }

        if ((vertexI + vertexCount) * primitiveVerticesSize >= vertices.capacity()) {
            int offset = getVerticesOffset();
            int newSize = Math.max(vertices.capacity() * 2, vertices.capacity() + vertexCount * primitiveVerticesSize);
            ByteBuffer newVertices = BufferUtils.createByteBuffer(newSize);
            memCopy(memAddress0(vertices), memAddress0(newVertices), offset);

            vertices = newVertices;
            verticesPointerStart = memAddress0(vertices);
            verticesPointer = verticesPointerStart + offset;
        }

        if ((indicesCount + indexCount) * Integer.BYTES >= indices.capacity()) {
            int newSize = Math.max(indices.capacity() * 2, indices.capacity() + indexCount * Integer.BYTES);

            ByteBuffer newIndices = BufferUtils.createByteBuffer(newSize);
            memCopy(memAddress0(indices), memAddress0(newIndices), indicesCount * 4L);

            indices = newIndices;
            indicesPointer = memAddress0(indices);
        }
    }

    public void end() {
        if (!building) throw new IllegalStateException("Mesh.end() called while not building.");

        building = false;
    }

    public GpuBuffer getVertexBuffer() {
        vertices.limit(getVerticesOffset());
        return RenderSystem.getDevice().createBuffer(() -> "Pupper mesh vertices", GpuBuffer.USAGE_VERTEX, vertices);
    }

    public GpuBuffer getIndexBuffer() {
        indices.limit(indicesCount * 4);
        return RenderSystem.getDevice().createBuffer(() -> "Pupper mesh indices", GpuBuffer.USAGE_INDEX, indices);
    }

    public PupperMeshBuilder tex2(float u, float v) {
        memPutFloat(verticesPointer, u);
        memPutFloat(verticesPointer + 4, v);
        verticesPointer += 8;
        return this;
    }

    public int getIndicesCount() { return indicesCount; }
    private int getVerticesOffset() { return (int) (verticesPointer - verticesPointerStart); }

    private void allocateBuffers(int vertexCount, int indexCount) {
        vertices = BufferUtils.createByteBuffer(primitiveVerticesSize * vertexCount);
        verticesPointer = verticesPointerStart = memAddress0(vertices);

        indices = BufferUtils.createByteBuffer(indexCount * Integer.BYTES);
        indicesPointer = memAddress0(indices);
    }

    private void debugVertexBufferCapacity() {
        if (DEBUG && (vertices == null || vertexI * primitiveVerticesSize >= vertices.capacity())) {
            throw new IndexOutOfBoundsException("Vertices written to MeshBuilder without calling 'ensureCapacity()' first!");
        }
    }

    private void debugIndexBufferCapacity() {
        if (DEBUG && (indices == null || indicesCount * Integer.BYTES >= indices.capacity())) {
            throw new IndexOutOfBoundsException("Indices written to MeshBuilder without calling 'ensureCapacity()' first!");
        }
    }

    public boolean isBuilding() {
        return building;
    }
}
