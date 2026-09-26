package cn.pupperclient.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.PrimitiveTopology;

public class PupperFullScreenRenderer {
    public static GpuBuffer vbo;
    public static GpuBuffer ibo;

    /**
     * Deprecated for performance reasons, use {@link PupperMeshRenderer#fullscreen()} or the {@link PupperFullScreenRenderer#vbo}
     * and {@link PupperFullScreenRenderer#ibo} buffer objects instead.
     */
    @Deprecated(forRemoval = true)
    public static PupperMeshBuilder mesh;

    private PupperFullScreenRenderer() {}

    static {
        mesh = new PupperMeshBuilder(PupperVertexFormats.POS2, PrimitiveTopology.TRIANGLES);

        mesh.begin();

        mesh.quad(
            mesh.vec2(-1, -1).next(),
            mesh.vec2(-1, 1).next(),
            mesh.vec2(1, 1).next(),
            mesh.vec2(1, -1).next()
        );

        mesh.end();

        vbo = mesh.getVertexBuffer();
        ibo = mesh.getIndexBuffer();
    }
}
// based on meteor
