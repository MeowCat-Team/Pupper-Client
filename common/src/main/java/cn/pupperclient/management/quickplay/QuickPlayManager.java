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

import cn.pupperclient.management.quickplay.impl.*;

import java.util.ArrayList;

public class QuickPlayManager {

    private ArrayList<QuickPlay> quickPlays = new ArrayList<QuickPlay>();

    public QuickPlayManager() {
        quickPlays.add(new ArcadeQuickPlay());
//        quickPlays.add(new BedwarsQuickPlay());
//        quickPlays.add(new DuelsQuickPlay());
//        quickPlays.add(new MainLobbyQuickPlay());
//        quickPlays.add(new MurderMysteryQuickPlay());
//        quickPlays.add(new SkywarsQuickPlay());
//        quickPlays.add(new TNTQuickPlay());
//        quickPlays.add(new UHCQuickPlay());
    }

    public ArrayList<QuickPlay> getQuickPlays() {
        return quickPlays;
    }
}
