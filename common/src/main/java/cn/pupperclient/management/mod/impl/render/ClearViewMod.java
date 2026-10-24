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

package cn.pupperclient.management.mod.impl.render;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.skia.font.Icon;

/**
 * Removes the screen effects of blindness, darkness and nausea on the local player.
 * The effects themselves stay untouched, so gameplay calculations and the server
 * remain authoritative.
 */
public class ClearViewMod extends Mod {

    private static ClearViewMod instance;

    private final BooleanSetting antiBlindness = new BooleanSetting(
            "mod.clearview.blindness.name", "mod.clearview.blindness.description",
            Icon.VISIBILITY, this, true);
    private final BooleanSetting antiDarkness = new BooleanSetting(
            "mod.clearview.darkness.name", "mod.clearview.darkness.description",
            Icon.VISIBILITY, this, true);
    private final BooleanSetting antiNausea = new BooleanSetting(
            "mod.clearview.nausea.name", "mod.clearview.nausea.description",
            Icon.VISIBILITY, this, true);

    public ClearViewMod() {
        super("mod.clearview.name", "mod.clearview.description", Icon.VISIBILITY, ModCategory.RENDER);
        instance = this;
    }

    public static ClearViewMod getInstance() {
        return instance;
    }

    public boolean isAntiBlindness() {
        return antiBlindness.isEnabled();
    }

    public boolean isAntiDarkness() {
        return antiDarkness.isEnabled();
    }

    public boolean isAntiNausea() {
        return antiNausea.isEnabled();
    }
}
