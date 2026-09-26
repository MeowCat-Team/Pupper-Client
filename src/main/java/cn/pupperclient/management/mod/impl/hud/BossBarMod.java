package cn.pupperclient.management.mod.impl.hud;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.gui.edithud.api.HUDCore;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.api.hud.design.HUDMotion;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.mixin.interfaces.IMixinBossHealthOverlay;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;

public class BossBarMod extends HUDMod {
    private static BossBarMod instance;
    private static final float WIDTH = 200;
    private static final float ROW_HEIGHT = 36;
    private final Map<UUID, BossRow> rows = new LinkedHashMap<>();
    private final HUDMotion motion = new HUDMotion();
    private final BooleanSetting vanillaPosition = new BooleanSetting("setting.vanillaposition",
        "setting.vanillaposition.description", Icon.PICTURE_IN_PICTURE_CENTER, this, false);
    public BossBarMod() {
        super("mod.bossbar.name", "mod.bossbar.description", Icon.BATTERY_LOW);
        instance = this;
    }
    public static BossBarMod getInstance() { return instance; }
    public boolean isVanillaPosition() { return vanillaPosition.isEnabled(); }
    @Override public void onDisable() { super.onDisable(); rows.clear(); }

    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        if (isVanillaPosition()) { rows.clear(); position.setSize(0, 0); return; }
        float dt = motion.deltaSeconds();
        boolean reduced = reducedMotion();
        rows.values().forEach(row -> row.active = false);
        int count = 0;
        for (LerpingBossEvent boss : ((IMixinBossHealthOverlay) client.gui.hud.getBossOverlay()).pupper$getBossEvents()) {
            if (count++ >= 5) break;
            BossRow row = rows.computeIfAbsent(boss.getId(), id -> new BossRow(boss));
            row.name = boss.getName().getString();
            row.overlay = boss.getOverlay();
            row.targetProgress = boss.getProgress();
            row.active = true;
        }
        for (BossRow row : rows.values()) {
            row.visibility = HUDMotion.approach(row.visibility, row.active ? 1 : 0, dt, reduced);
            row.displayedProgress = row.progress.update(row.targetProgress, dt, reduced);
        }
        rows.values().removeIf(row -> !row.active && row.visibility < 0.01f);
        if (rows.isEmpty() && !HUDCore.isEditing) { position.setSize(0, 0); return; }

        float contentHeight = 0;
        for (BossRow row : rows.values()) contentHeight += ROW_HEIGHT * row.visibility;
        float height = rows.isEmpty() ? 8 + ROW_HEIGHT : 8 + Math.max(1, contentHeight);
        position.setSize(WIDTH, height);
        begin();
        try {
            drawBackground(getX(), getY(), WIDTH, height);
            Skia.clip(getX(), getY(), WIDTH, height, Math.min(getRadius(), height / 2));
            if (rows.isEmpty()) drawBoss(getName(), 0.65f, BossEvent.BossBarOverlay.PROGRESS, getY() + 8);
            float y = getY() + 8;
            int index = 0;
            for (BossRow row : rows.values()) {
                float rowHeight = ROW_HEIGHT * row.visibility;
                Skia.save();
                try {
                    Skia.clip(getX(), y, WIDTH, rowHeight, 0);
                    if (index++ > 0) Skia.drawRoundedRect(getX() + 8, y,
                        WIDTH - 16, 0.75f, 0.375f, colors().outline());
                    drawBoss(row.name, row.displayedProgress,
                        row.overlay, y + 6 * (1 - row.visibility));
                } finally { Skia.restore(); }
                y += rowHeight;
            }
        } finally { finish(); }
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

    private static final class BossRow {
        String name;
        BossEvent.BossBarOverlay overlay;
        float targetProgress;
        float displayedProgress;
        final HUDMotion.Spring progress;
        float visibility;
        boolean active;

        BossRow(LerpingBossEvent boss) {
            name = boss.getName().getString();
            overlay = boss.getOverlay();
            targetProgress = boss.getProgress();
            displayedProgress = targetProgress;
            progress = new HUDMotion.Spring(targetProgress);
        }
    }
}
