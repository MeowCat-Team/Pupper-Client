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

package cn.pupperclient.management.mod.impl.hud;

import java.util.Comparator;
import java.util.List;
import cn.pupperclient.PupperClient;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.api.hud.AnimatedListHUDMod;
import cn.pupperclient.management.mod.setting.BooleanSetting;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.language.I18n;

public class ArrayListMod extends AnimatedListHUDMod {
    private static ArrayListMod instance;
    private final BooleanSetting hudSetting = new BooleanSetting("setting.hud",
        "setting.hud.description", Icon.DASHBOARD, this, false);
    private final BooleanSetting renderSetting = new BooleanSetting("setting.render",
        "setting.render.description", Icon.VISIBILITY, this, false);
    private final BooleanSetting playerSetting = new BooleanSetting("setting.player",
        "setting.player.description", Icon.PERSON, this, false);
    private final BooleanSetting otherSetting = new BooleanSetting("setting.other",
        "setting.other.description", Icon.MORE_HORIZ, this, false);

    /** Rebuilt only when a flag or the language changes, not on every frame. */
    private List<Row> cachedRows = List.of();
    private long cachedState;

    public ArrayListMod() {
        super("mod.arraylist.name", "mod.arraylist.description", Icon.LIST);
        instance = this;
    }
    public static ArrayListMod getInstance() { return instance; }
    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> drawList();

    @Override protected float minimumListWidth() { return 100; }
    @Override protected float listRowHeight() { return 20; }

    @Override protected List<Row> rows() {
        long state = stateSignature();
        if (state != cachedState) {
            cachedRows = buildRows();
            cachedState = state;
        }
        return cachedRows;
    }

    private List<Row> buildRows() {
        return PupperClient.getInstance().getModManager().getMods().stream()
            .filter(mod -> mod != this && mod.isEnabled() && !mod.isHidden() && included(mod.getCategory()))
            .sorted(Comparator.comparing(Mod::getRawName))
            .map(mod -> new Row(mod.getRawName(), displayName(mod), "", "", false)).toList();
    }

    /**
     * Cheap fingerprint of everything the row list depends on: the per-mod flags,
     * the category filters and the active language (which drives the labels).
     */
    private long stateSignature() {
        long state = I18n.getCurrentLanguage() == null ? 0 : I18n.getCurrentLanguage().ordinal() + 1;
        state = state * 31 + (hudSetting.isEnabled() ? 1 : 0);
        state = state * 31 + (renderSetting.isEnabled() ? 1 : 0);
        state = state * 31 + (playerSetting.isEnabled() ? 1 : 0);
        state = state * 31 + (otherSetting.isEnabled() ? 1 : 0);
        for (Mod mod : PupperClient.getInstance().getModManager().getMods()) {
            state = state * 31 + (mod.isEnabled() ? 1 : 0);
            state = state * 31 + (mod.isHidden() ? 1 : 0);
        }
        return state;
    }

    private String displayName(Mod mod) {
        String name = mod.getName();
        return name == null || name.equals("null") ? mod.getRawName() : name;
    }

    private boolean included(ModCategory category) {
        return switch (category) {
            case HUD -> hudSetting.isEnabled();
            case RENDER -> renderSetting.isEnabled();
            case PLAYER -> playerSetting.isEnabled();
            case MISC -> otherSetting.isEnabled();
            default -> true;
        };
    }
}
