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

package cn.pupperclient.management.mod.impl.settings;

import java.util.Arrays;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.skia.font.Icon;

public class SystemSettings extends Mod {

    private static SystemSettings instance;
    private ComboSetting blurTypeSetting = new ComboSetting("setting.blurtype", "setting.blurtype.description",
            Icon.BLUR_MEDIUM, this, Arrays.asList("setting.fastblur", "setting.normalblur"), "setting.normalblur");

    public SystemSettings() {
        super("mod.systemsettings.name", "mod.systemsettings.description", Icon.SETTINGS, ModCategory.MISC);
        instance = this;
        this.setHidden(true);
        this.setEnabled(true);
    }

    @Override
    public void onDisable() {
        this.setEnabled(true);
    }

    public static SystemSettings getInstance() {
        return instance;
    }

    public boolean isFastBlur() {
        return blurTypeSetting.getOption().contains("fastblur");
    }
}
