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
import org.jetbrains.annotations.NotNull;

@NotNull
public class NoHurtFov extends Mod {
    private static NoHurtFov instance;
    public BooleanSetting nohurtFov = new BooleanSetting("mod.nofov.nohurtFov.name", "mod.nofov.nohurtFov.description", Icon.DISPLAY_SETTINGS, this, true);

    public NoHurtFov() {
        super("mod.nohurtfov.name", "mod.nohurtfov.description", Icon.DISPLAY_SETTINGS, ModCategory.RENDER);

        instance = this;
    }

    public static NoHurtFov getInstance() {
        return instance;
    }
}
