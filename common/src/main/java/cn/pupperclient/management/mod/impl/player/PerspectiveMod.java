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

package cn.pupperclient.management.mod.impl.player;

import java.util.Arrays;
import net.minecraft.client.CameraType;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.setting.BooleanSetting;
import cn.pupperclient.management.mod.setting.ComboSetting;
import cn.pupperclient.management.mod.setting.KeybindSetting;
import cn.pupperclient.skia.font.Icon;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * A held or toggled third-person view.
 *
 * <p>Freelook and Taplook differ only in their default camera side and their
 * default key, so the state machine lives here once.
 */
public abstract class PerspectiveMod extends Mod {

    private boolean active;
    private boolean toggled;
    private CameraType prevPerspective;

    private final ComboSetting perspectiveSetting = new ComboSetting("setting.perspective",
            "setting.perspective.description", Icon.CAMERASWITCH, this,
            Arrays.asList("setting.front", "setting.behind"), defaultPerspective());
    private final BooleanSetting toggleSetting = new BooleanSetting("setting.toggle",
            "setting.toggle.description", Icon.SWITCH, this, false);
    private final KeybindSetting keybindSetting = new KeybindSetting("setting.keybind",
            "setting.keybind.description", Icon.KEYBOARD, this,
            InputConstants.getKey(new KeyEvent(defaultKey(), 0, 0)));

    protected PerspectiveMod(String name, String description, String icon) {
        super(name, description, icon, ModCategory.PLAYER);
    }

    /** @return the {@code setting.front} / {@code setting.behind} default for this mode */
    protected abstract String defaultPerspective();

    /** @return the GLFW key that holds or toggles this mode */
    protected abstract int defaultKey();

    public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> {
        if (toggleSetting.isEnabled()) {
            if (keybindSetting.isPressed()) {
                toggled = !toggled;
                if (toggled && !active) start();
                else if (!toggled && active) stop();
            }
        } else if (keybindSetting.isKeyDown()) {
            if (!active) start();
        } else if (active) {
            stop();
        }
    };

    private void start() {
        CameraType perspective = perspectiveSetting.getOption().equals("setting.front")
                ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK;

        active = true;
        prevPerspective = client.options.getCameraType();
        client.options.setCameraType(perspective);
    }

    private void stop() {
        active = false;
        client.options.setCameraType(prevPerspective);
    }

    public boolean isActive() {
        return active;
    }
}
