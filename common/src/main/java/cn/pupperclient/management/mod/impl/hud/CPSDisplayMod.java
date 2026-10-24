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

import java.util.ArrayList;

import org.lwjgl.glfw.GLFW;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.skia.font.Icon;

public class CPSDisplayMod extends SimpleHUDMod {

    private final ArrayList<Long> leftPresses = new ArrayList<Long>();
    private final ArrayList<Long> rightPresses = new ArrayList<Long>();

    private final BooleanSetting rightClickSetting = new BooleanSetting("setting.rightclick",
        "setting.rightclick.description", Icon.MOUSE, this, true);

    public CPSDisplayMod() {
        super("mod.cpsdisplay.name", "mod.cpsdisplay.description", Icon.MOUSE);
    }

    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        this.draw();
    };

    public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> {
        leftPresses.removeIf(t -> System.currentTimeMillis() - t > 1000);
        rightPresses.removeIf(t -> System.currentTimeMillis() - t > 1000);
    };

    public void onMouseClick(int button, boolean pressed) {
        if (pressed) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                leftPresses.add(System.currentTimeMillis());
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                rightPresses.add(System.currentTimeMillis());
            }
        }
    }

    @Override
    public String getText() {
        return (rightClickSetting.isEnabled() ?
            leftPresses.size() + " | " + rightPresses.size() :
            leftPresses.size()) + " CPS";
    }

    @Override
    public String getIcon() {
        return Icon.MOUSE;
    }
    @Override protected String getUnit() { return "CPS"; }
}
