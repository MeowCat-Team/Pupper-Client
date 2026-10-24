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

package cn.pupperclient.event.mod;

import cn.pupperclient.PupperClient;
import cn.pupperclient.event.Event;
import cn.pupperclient.management.mod.Mod;

public class ModStateChangeEvent extends Event {
    private final Mod mod;
    String modName;
    private final boolean enabled;
    private final long timestamp;

    public ModStateChangeEvent(Mod mod, boolean enabled) {
        this.mod = mod;
        this.enabled = enabled;
        this.timestamp = System.currentTimeMillis();
    }

    public ModStateChangeEvent(String modName, boolean enabled) {
        this.modName = modName;
        this.enabled = enabled;
        this.timestamp = System.currentTimeMillis();
        this.mod = PupperClient.getInstance().getModManager().getModByName(modName);
    }

    public Mod getMod() {
        return mod;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getModName() {
        return mod.getName();
    }
}
