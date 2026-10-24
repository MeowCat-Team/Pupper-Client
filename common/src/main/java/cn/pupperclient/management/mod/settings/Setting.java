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

package cn.pupperclient.management.mod.settings;

import cn.pupperclient.management.mod.Mod;

public abstract class Setting {

    private String name, description, icon;
    private Mod parent;

    public Setting(String name, String description, String icon, Mod parent) {
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.parent = parent;
    }

    public abstract void reset();

    public boolean isVisible() {
        return true;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public Mod getParent() {
        return parent;
    }
}
