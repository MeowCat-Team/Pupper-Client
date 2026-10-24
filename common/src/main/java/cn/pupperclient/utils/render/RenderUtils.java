/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
