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

package cn.pupperclient.skia;

import java.awt.Color;

import io.github.humbleui.skija.Paint;

/**
 * The single shared {@link Paint} used by the drawing helpers.
 *
 * <p>One instance is reused because a fresh Paint per call allocates a native
 * object for every shape a frame draws.
 */
final class SkiaPaint {

    private static final Paint SHARED = new Paint();

    private SkiaPaint() {}

    /** Applies {@code color} to the shared paint and returns it. */
    static Paint of(Color color) {
        SHARED.setARGB(color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue());
        return SHARED;
    }

    static void release() {
        SHARED.close();
    }
}
