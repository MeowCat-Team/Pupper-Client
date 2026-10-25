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

package cn.pupperclient.management.color.api;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import cn.pupperclient.libraries.material3.Material3;
import cn.pupperclient.libraries.material3.dynamiccolor.DynamicColor;
import cn.pupperclient.libraries.material3.dynamiccolor.DynamicScheme;
import cn.pupperclient.libraries.material3.dynamiccolor.MaterialDynamicColors;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.libraries.material3.utils.ColorUtils;

/**
 * The resolved Material 3 roles for one scheme.
 *
 * <p>A scheme is immutable, so every role is computed once and kept. Resolving a
 * role allocates a {@link DynamicColor} and walks the HCT palette; a GUI frame asks
 * for the same roles hundreds of times.
 */
public class ColorPalette {

    private final DynamicScheme scheme;
    private final MaterialDynamicColors colors;
    private final boolean dark;
    private final Hct hct;
    private final Map<String, Color> roles = new HashMap<>();

    public ColorPalette(Hct hct, boolean dark, float contrast) {
        this.hct = Hct.fromInt(hct.toInt());
        this.dark = dark;
        scheme = Material3.getDynamicScheme(this.hct, dark, contrast);
        colors = new MaterialDynamicColors();
    }

    public ColorPalette(Hct hct, boolean dark) {
        this(hct, dark, 0);
    }

    public Color getPrimary() {
        return role("primary", MaterialDynamicColors::primary);
    }

    public Color getSecondary() {
        return role("secondary", MaterialDynamicColors::secondary);
    }

    public Color getTertiary() {
        return role("tertiary", MaterialDynamicColors::tertiary);
    }

    public Color getPrimaryContainer() {
        return role("primary_container", MaterialDynamicColors::primaryContainer);
    }

    public Color getSecondaryContainer() {
        return role("secondary_container", MaterialDynamicColors::secondaryContainer);
    }

    public Color getTertiaryContainer() {
        return role("tertiary_container", MaterialDynamicColors::tertiaryContainer);
    }

    public Color getOnPrimary() {
        return role("on_primary", MaterialDynamicColors::onPrimary);
    }

    public Color getOnSecondary() {
        return role("on_secondary", MaterialDynamicColors::onSecondary);
    }

    public Color getOnTertiary() {
        return role("on_tertiary", MaterialDynamicColors::onTertiary);
    }

    public Color getOnPrimaryContainer() {
        return role("on_primary_container", MaterialDynamicColors::onPrimaryContainer);
    }

    public Color getOnSecondaryContainer() {
        return role("on_secondary_container", MaterialDynamicColors::onSecondaryContainer);
    }

    public Color getOnTertiaryContainer() {
        return role("on_tertiary_container", MaterialDynamicColors::onTertiaryContainer);
    }

    public Color getWarning() {
        return role("warning", MaterialDynamicColors::warning);
    }

    public Color getError() {
        return role("error", MaterialDynamicColors::error);
    }

    public Color getErrorContainer() {
        return role("error_container", MaterialDynamicColors::errorContainer);
    }

    public Color getOnError() {
        return role("on_error", MaterialDynamicColors::onError);
    }

    public Color getOnErrorContainer() {
        return role("on_error_container", MaterialDynamicColors::onErrorContainer);
    }

    public Color getBackground() {
        return role("background", MaterialDynamicColors::background);
    }

    public Color getOnBackground() {
        return role("on_background", MaterialDynamicColors::onBackground);
    }

    public Color getSurface() {
        return role("surface", MaterialDynamicColors::surface);
    }

    public Color getOnSurface() {
        return role("on_surface", MaterialDynamicColors::onSurface);
    }

    public Color getInverseSurface() {
        return role("inverse_surface", MaterialDynamicColors::inverseSurface);
    }

    public Color getInverseOnSurface() {
        return role("inverse_on_surface", MaterialDynamicColors::inverseOnSurface);
    }

    public Color getSurfaceContainer() {
        return role("surface_container", MaterialDynamicColors::surfaceContainer);
    }

    public Color getSurfaceVariant() {
        return role("surface_variant", MaterialDynamicColors::surfaceVariant);
    }

    public Color getOnSurfaceVariant() {
        return role("on_surface_variant", MaterialDynamicColors::onSurfaceVariant);
    }

    public Color getSurfaceContainerHigh() {
        return role("surface_container_high", MaterialDynamicColors::surfaceContainerHigh);
    }

    public Color getSurfaceContainerHighest() {
        return role("surface_container_highest", MaterialDynamicColors::surfaceContainerHighest);
    }

    public Color getSurfaceContainerLow() {
        return role("surface_container_low", MaterialDynamicColors::surfaceContainerLow);
    }

    public Color getSurfaceContainerLowest() {
        return role("surface_container_lowest", MaterialDynamicColors::surfaceContainerLowest);
    }

    public Color getSurfaceTint() {
        return role("surface_tint", MaterialDynamicColors::surfaceTint);
    }

    public Color getOutline() {
        return role("outline", MaterialDynamicColors::outline);
    }

    public Color getOutlineVariant() {
        return role("outline_variant", MaterialDynamicColors::outlineVariant);
    }

    private Color role(String name, Function<MaterialDynamicColors, DynamicColor> lookup) {
        return roles.computeIfAbsent(name, key -> argbToColor(lookup.apply(colors).getArgb(scheme)));
    }

    private Color argbToColor(int argb) {
        return new Color(ColorUtils.redFromArgb(argb), ColorUtils.greenFromArgb(argb), ColorUtils.blueFromArgb(argb));
    }

    public boolean isDarkMode() {
        return dark;
    }

    public Hct getHct() {
        return hct;
    }
}
