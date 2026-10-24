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

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.world.phys.Vec3;

/** Publishes the renderer-owned Globals buffer before resource reload can upload texture atlases. */
public final class StartupRenderUniforms {
    private StartupRenderUniforms() {}

    public static void initialize(GlobalSettingsUniform uniform, int width, int height,
                                  double glintStrength, int menuBlurRadius) {
        if (RenderSystem.getGlobalSettingsUniform() != null) return;
        // The regular render update replaces these initial values on the first frame.
        uniform.update(Math.max(1, width), Math.max(1, height), glintStrength,
                0L, DeltaTracker.ZERO, menuBlurRadius, Vec3.ZERO, false);
    }
}
