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

package cn.pupperclient.management.quickplay;

import java.util.ArrayList;
import net.minecraft.resources.Identifier;

public class QuickPlay {

    private ArrayList<QuickPlayCommand> commands = new ArrayList<QuickPlayCommand>();

    private final String name;
    private final Identifier icon;

    public QuickPlay(String name, Identifier icon) {
        this.name = name;
        this.icon = icon;
        this.addCommands();
    }

    public void addCommands() {}

    public String getName() {
        return name;
    }

    public ArrayList<QuickPlayCommand> getCommands() {
        return commands;
    }

    public Identifier getIcon() {
        return icon;
    }

    public void setCommands(ArrayList<QuickPlayCommand> commands) {
        this.commands = commands;
    }
}
