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
