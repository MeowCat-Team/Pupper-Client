package cn.pupperclient.management.mod.impl.hud;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import cn.pupperclient.PupperClient;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.hypixel.api.HypixelUser;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.api.hud.design.HUDMotion;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.minecraft.player.SkinUtils;
import cn.pupperclient.utils.server.Server;
import cn.pupperclient.utils.server.ServerUtils;
import cn.pupperclient.utils.language.I18n;
import net.minecraft.client.multiplayer.PlayerInfo;

public class BedwarsStatsOverlayMod extends HUDMod {
    private static final float WIDTH = 304;
    private final NumberSetting maxSetting = new NumberSetting("setting.max", "setting.max.description",
        Icon.MAXIMIZE, this, 16, 1, 30, 1);
    private final Map<String, StatRow> rows = new LinkedHashMap<>();
    private final HUDMotion motion = new HUDMotion();
    public BedwarsStatsOverlayMod() {
        super("mod.bedwarsstatsoverlay.name", "mod.bedwarsstatsoverlay.description", Icon.SINGLE_BED);
    }
    @Override public void onDisable() { super.onDisable(); rows.clear(); }

    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        float dt = motion.deltaSeconds();
        boolean reduced = reducedMotion();
        rows.values().forEach(row -> row.active = false);
        if (ServerUtils.isJoin(Server.HYPIXEL) && client.getConnection() != null) {
            int count = 0;
            for (PlayerInfo player : client.getConnection().getOnlinePlayers()) {
                String id = player.getProfile().id().toString().replace("-", "");
                HypixelUser stats = PupperClient.getInstance().getHypixelManager()
                    .getByUuid(id);
                if (stats == null) continue;
                StatRow row = rows.computeIfAbsent(id, key -> new StatRow(player, stats));
                row.player = player;
                row.stats = stats;
                row.active = true;
                if (++count >= (int) maxSetting.getValue()) break;
            }
        }
        for (StatRow row : rows.values())
            row.visibility = HUDMotion.approach(row.visibility, row.active ? 1 : 0, dt, reduced);
        rows.values().removeIf(row -> !row.active && row.visibility < 0.01f);

        float contentHeight = 0;
        for (StatRow row : rows.values()) contentHeight += HUDTokens.ROW_HEIGHT * row.visibility;
        float height = 52 + Math.max(HUDTokens.ROW_HEIGHT, contentHeight);
        position.setSize(WIDTH, height);
        begin();
        try {
            drawBackground(getX(), getY(), WIDTH, height);
            Skia.clip(getX(), getY(), WIDTH, height, getRadius());
            Skia.drawRoundedRect(getX() + 8, getY() + 6, 18, 18, 6, colors().accentContainer());
            Skia.drawFullCenteredText(Icon.SINGLE_BED, getX() + 17, getY() + 15,
                colors().onAccentContainer(), HUDTokens.icon());
            Skia.drawHeightCenteredText(Skia.getLimitText(getName(), HUDTokens.title(), WIDTH - 48),
                getX() + 32, getY() + 15, colors().text(), HUDTokens.title());
            Skia.drawRoundedRect(getX() + 8, getY() + 30, WIDTH - 16, 18, 6, colors().raised());
            String[] headers = {I18n.get("hud.player"), I18n.get("hud.level"), "WLR", "FKDR", "BBLR"};
            float[] columns = {48, 126, 176, 226, 276};
            for (int i = 0; i < headers.length; i++)
                Skia.drawFullCenteredText(headers[i], getX() + columns[i], getY() + 39,
                    colors().secondaryText(), HUDTokens.label());
            float y = getY() + 52;
            for (StatRow row : rows.values()) {
                float rowHeight = HUDTokens.ROW_HEIGHT * row.visibility;
                Skia.save();
                try {
                    Skia.clip(getX(), y, WIDTH, rowHeight, 0);
                    drawStatRow(row, y + 6 * (1 - row.visibility), columns);
                } finally { Skia.restore(); }
                y += rowHeight;
            }
            if (rows.isEmpty())
                Skia.drawHeightCenteredText(I18n.get("hud.empty"), getX() + 8, y + 10,
                    colors().secondaryText(), HUDTokens.label());
        } finally { finish(); }
    };

    private void drawStatRow(StatRow row, float y, float[] columns) {
        File skin = SkinUtils.getSkin(row.player.getSkin().body().texturePath());
        Skia.drawRoundedRect(getX() + 8, y + 3, 14, 14, 4, colors().accentContainer());
        if (skin != null && skin.exists()) Skia.drawPlayerHead(skin, getX() + 8, y + 3, 14, 14, 4);
        else Skia.drawFullCenteredText(Icon.PERSON, getX() + 15, y + 10,
            colors().onAccentContainer(), HUDTokens.icon());
        Skia.drawHeightCenteredText(Skia.getLimitText(row.player.getProfile().name(), HUDTokens.body(), 72),
            getX() + 28, y + 10, colors().text(), HUDTokens.body());
        String[] values = {row.stats.getBedwarsLevel(), row.stats.getWinLoseRatio(),
            row.stats.getFinalKillDeathRatio(), row.stats.getBedsBrokeLostRatio()};
        Skia.drawRoundedRect(getX() + columns[1] - 19, y + 3, 38, 14, 7,
            colors().accentContainer());
        for (int i = 0; i < values.length; i++)
            Skia.drawFullCenteredText(Skia.getLimitText(values[i], HUDTokens.label(), i == 0 ? 34 : 42),
                getX() + columns[i + 1], y + 10,
                i == 0 ? colors().onAccentContainer() : colors().text(), HUDTokens.label());
    }

    private static final class StatRow {
        PlayerInfo player;
        HypixelUser stats;
        float visibility;
        boolean active;
        StatRow(PlayerInfo player, HypixelUser stats) { this.player = player; this.stats = stats; }
    }
}
