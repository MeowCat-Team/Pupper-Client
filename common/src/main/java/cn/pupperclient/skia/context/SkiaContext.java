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

package cn.pupperclient.skia.context;

import cn.pupperclient.PupperLogger;
import cn.pupperclient.ui.render.BlazeUiRenderer;
import cn.pupperclient.ui.render.RasterUiCanvas;
import cn.pupperclient.ui.render.UiCanvas;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.humbleui.skija.Surface;
import java.util.function.Consumer;

/** Legacy drawing entry point backed exclusively by Blaze3D GPU resources. */
public final class SkiaContext {
    // CPU specimen rendering only; production frames never allocate a Skia surface.
    private static Surface surface;
    private static BlazeUiRenderer renderer;
    private static UiCanvas canvas;
    private static boolean reportedFailure;

    private SkiaContext() {}

    /** Called before presentation, while Minecraft's command encoder owns the frame. */
    public static void drawOffscreen(GpuTextureView destination, Consumer<UiCanvas> drawing, boolean captureGlass) {
        if (destination == null || destination.isClosed() || destination.getWidth(0) <= 0 || destination.getHeight(0) <= 0) return;
        try {
            if (renderer == null) renderer = new BlazeUiRenderer();
            renderer.draw(destination, frame -> {
                canvas = frame;
                try { drawing.accept(frame); }
                finally { canvas = null; }
            }, captureGlass);
            reportedFailure = false;
        } catch (RuntimeException failure) {
            if (!reportedFailure) PupperLogger.error("UI", "Failed to render the Blaze3D UI", failure);
            reportedFailure = true;
        }
    }

    public static UiCanvas getCanvas() {
        if (surface != null) return new RasterUiCanvas(surface.getCanvas());
        if (canvas == null) throw new IllegalStateException("UI drawing requires an active frame");
        return canvas;
    }

    /** Release owned textures before Minecraft shuts down its rendering device. */
    public static void close() {
        if (renderer != null) { renderer.close(); renderer = null; }
        canvas = null;
        reportedFailure = false;
    }
}
