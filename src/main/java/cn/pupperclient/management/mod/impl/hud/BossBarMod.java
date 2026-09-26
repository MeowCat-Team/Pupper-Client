package cn.pupperclient.management.mod.impl.hud;

import java.util.List;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.gui.edithud.api.HUDCore;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.mixin.interfaces.IMixinBossHealthOverlay;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;

public class BossBarMod extends HUDMod {
    private static BossBarMod instance;
    private final BooleanSetting vanillaPosition = new BooleanSetting("setting.vanillaposition",
        "setting.vanillaposition.description", Icon.PICTURE_IN_PICTURE_CENTER, this, false);
    public BossBarMod() {
        super("mod.bossbar.name", "mod.bossbar.description", Icon.BATTERY_LOW);
        instance = this;
    }
    public static BossBarMod getInstance() { return instance; }
    public boolean isVanillaPosition() { return vanillaPosition.isEnabled(); }

    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        if (isVanillaPosition()) { position.setSize(0, 0); return; }
        List<LerpingBossEvent> bosses = ((IMixinBossHealthOverlay) client.gui.getBossOverlay())
            .pupper$getBossEvents().stream().limit(5).toList();
        if (bosses.isEmpty() && !HUDCore.isEditing) { position.setSize(0, 0); return; }
        float width = 200, height = Math.max(1, bosses.size()) * 36 + 8;
        begin();
        try {
            drawBackground(getX(), getY(), width, height);
            if (bosses.isEmpty()) drawBoss(getName(), 0.65f, BossEvent.BossBarOverlay.PROGRESS, getY() + 8);
            for (int i = 0; i < bosses.size(); i++) {
                var boss = bosses.get(i);
                if (i > 0) Skia.drawRoundedRect(getX() + 8, getY() + 4 + i * 36,
                    width - 16, 0.75f, 0.375f, colors().outline());
                drawBoss(boss.getName().getString(), boss.getProgress(), boss.getOverlay(), getY() + 8 + i * 36);
            }
        } finally { finish(); }
        position.setSize(width, height);
    };

    private void drawBoss(String name, float progress, BossEvent.BossBarOverlay overlay, float y) {
        float value = Math.max(0, Math.min(1, progress));
        Skia.drawCircle(getX() + 13, y + 7, 3, colors().accent());
        Skia.drawHeightCenteredText(Skia.getLimitText(name, HUDTokens.title(), 132),
            getX() + 22, y + 7, colors().text(), HUDTokens.title());
        Skia.drawFullCenteredText(Math.round(value * 100) + "%", getX() + 176, y + 7,
            colors().secondaryText(), HUDTokens.label());
        Skia.drawRoundedRect(getX() + 8, y + 20, 184, 6, 3, colors().track());
        if (value > 0) Skia.drawRoundedRect(getX() + 8, y + 20, 184 * value, 6, 3, colors().accent());
        int segments = switch (overlay) {
            case NOTCHED_6 -> 6;
            case NOTCHED_10 -> 10;
            case NOTCHED_12 -> 12;
            case NOTCHED_20 -> 20;
            default -> 0;
        };
        for (int i = 1; i < segments; i++)
            Skia.drawRect(getX() + 8 + 184f * i / segments, y + 20, 1, 6, colors().surface());
    }
}
