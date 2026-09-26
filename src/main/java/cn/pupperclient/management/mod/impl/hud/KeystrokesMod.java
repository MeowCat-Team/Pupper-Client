package cn.pupperclient.management.mod.impl.hud;

import java.awt.Color;
import java.util.List;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.api.hud.design.HUDMotion;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.mixin.interfaces.IMixinKeyBinding;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import net.minecraft.client.KeyMapping;

public class KeystrokesMod extends HUDMod {
    private final BooleanSetting spaceKeySetting = new BooleanSetting("setting.spacekey", "setting.spacekey.description",
        Icon.KEYBOARD, this, true);
    private final BooleanSetting unmarkSetting = new BooleanSetting("setting.unmarked", "setting.unmarked.description",
        Icon.TITLE, this, false);
    private final BooleanSetting snapTapSetting = new BooleanSetting("setting.snaptapcompatibility",
        "setting.snaptapcompatibility.description", Icon.KEYBOARD_KEYS, this, false);
    private final HUDMotion motion = new HUDMotion();
    private final List<Panel> panels;

    public KeystrokesMod() {
        super("mod.keystrokes.name", "mod.keystrokes.description", Icon.KEYBOARD);
        panels = List.of(new Panel(client.options.keyUp, 32, 0, 28, 28, false),
            new Panel(client.options.keyLeft, 0, 32, 28, 28, false),
            new Panel(client.options.keyDown, 32, 32, 28, 28, false),
            new Panel(client.options.keyRight, 64, 32, 28, 28, false),
            new Panel(client.options.keyJump, 0, 64, 92, 22, true));
    }

    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> drawPanels();

    private void drawPanels() {
        float dt = motion.deltaSeconds();
        position.setSize(92, spaceKeySetting.isEnabled() ? 86 : 60);
        begin();
        try {
            for (Panel panel : panels)
                if (!panel.jump || spaceKeySetting.isEnabled()) panel.draw(dt);
        } finally { finish(); }
    }

    private final class Panel {
        private final KeyMapping key;
        private final float x, y, width, height;
        private final boolean jump;
        private final HUDMotion.Spring shape = new HUDMotion.Spring(0);
        Panel(KeyMapping key, float x, float y, float width, float height, boolean jump) {
            this.key = key; this.x = x; this.y = y; this.width = width; this.height = height; this.jump = jump;
        }
        void draw(float dt) {
            boolean pressed = client.screen == null && (snapTapSetting.isEnabled()
                ? key.isDown() : ((IMixinKeyBinding) key).getRealIsPressed());
            float state = shape.update(pressed ? 1 : 0, dt, reducedMotion());
            float inset = state;
            float px = getX() + x + inset, py = getY() + y + inset;
            float w = width - inset * 2, h = height - inset * 2;
            float radius = 8 + state * 4;
            // Keep every key on the selected HUD surface, including Glass.
            getDesign().drawBackground(px, py, w, h, radius);
            if (state > 0.01f) Skia.drawRoundedRect(px, py, w, h, radius,
                withOpacity(colors().accentContainer(), state));
            Color foreground = blend(colors().text(), colors().onAccentContainer(), state);
            if (!unmarkSetting.isEnabled()) {
                if (jump) Skia.drawRoundedRect(px + 18, py + h / 2 - 1, w - 36, 2, 1, foreground);
                else Skia.drawFullCenteredText(Skia.getLimitText(key.getTranslatedKeyMessage().getString(),
                    HUDTokens.title(), w - 6), px + w / 2, py + h / 2, foreground, HUDTokens.title());
            }
        }
    }

    private static Color withOpacity(Color color, float amount) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
            Math.round(color.getAlpha() * Math.max(0, Math.min(1, amount))));
    }

    private static Color blend(Color from, Color to, float amount) {
        float t = Math.max(0, Math.min(1, amount));
        return new Color(Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
            Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
            Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t));
    }
}
