/**
 * @Author: oneachina
 * @link: github.com/oneachina
 */
package cn.pupperclient.utils.render;

import cn.pupperclient.utils.minecraft.interfaces.IMinecraft;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;

public class RenderUtils implements IMinecraft {
    public static final Matrix4f projection = new Matrix4f();
    private static final ProjectionMatrixBuffer matrixBuffer = new ProjectionMatrixBuffer("Pupper Client projection matrix");

    public static void unscaledProjection() {
        float width = client.getWindow().getWidth();
        float height = client.getWindow().getHeight();

        var proj = new Projection();
        proj.setupOrtho(-10, 100, width, height, true);
        var matrix = proj.getMatrix(new Matrix4f());

        RenderSystem.setProjectionMatrix(matrixBuffer.getBuffer(matrix), ProjectionType.ORTHOGRAPHIC);
        RenderUtils.projection.set(matrix);
    }

    public static void scaledProjection() {
        float width = (float) (client.getWindow().getWidth() / client.getWindow().getGuiScale());
        float height = (float) (client.getWindow().getHeight() / client.getWindow().getGuiScale());

        var proj = new Projection();
        proj.setupOrtho(-10, 100, width, height, true);
        var matrix = proj.getMatrix(new Matrix4f());

        RenderSystem.setProjectionMatrix(matrixBuffer.getBuffer(matrix), ProjectionType.PERSPECTIVE);
        RenderUtils.projection.set(matrix);
    }

    public static boolean canUpdate() {
        return client.level != null && client.player != null;
    }
}
