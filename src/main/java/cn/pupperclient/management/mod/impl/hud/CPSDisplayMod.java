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
}
