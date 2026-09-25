package cn.pupperclient.management.mod.impl.hud;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import cn.pupperclient.PupperClient;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.hypixel.api.HypixelUser;
import cn.pupperclient.management.mod.api.hud.HUDMod;
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
    private final NumberSetting maxSetting = new NumberSetting("setting.max", "setting.max.description",
        Icon.MAXIMIZE, this, 16, 1, 30, 1);
    public BedwarsStatsOverlayMod() {
        super("mod.bedwarsstatsoverlay.name", "mod.bedwarsstatsoverlay.description", Icon.SINGLE_BED);
    }
    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        List<Entry> entries = new ArrayList<>();
        if (ServerUtils.isJoin(Server.HYPIXEL) && client.getConnection() != null) {
            for (PlayerInfo player : client.getConnection().getOnlinePlayers()) {
                HypixelUser stats = PupperClient.getInstance().getHypixelManager()
                    .getByUuid(player.getProfile().id().toString().replace("-", ""));
                if (stats != null) entries.add(new Entry(player, stats));
                if (entries.size() >= (int) maxSetting.getValue()) break;
            }
        }
        float width = 304;
        float height = 52 + Math.max(1, entries.size()) * HUDTokens.ROW_HEIGHT;
        begin();
        try {
            drawBackground(getX(), getY(), width, height);
            Skia.drawText(getName(), getX() + 8, getY() + 8, colors().text(), HUDTokens.title());
            Skia.drawRoundedRect(getX() + 4, getY() + 28, width - 8, 20, 6, colors().raised());
            String[] headers = {I18n.get("hud.player"), I18n.get("hud.level"), "WLR", "FKDR", "BBLR"};
            float[] columns = {48, 126, 176, 226, 276};
            for (int i = 0; i < headers.length; i++)
                Skia.drawFullCenteredText(headers[i], getX() + columns[i], getY() + 38,
                    colors().secondaryText(), HUDTokens.label());
            float y = getY() + 52;
            for (Entry entry : entries) {
                File skin = SkinUtils.getSkin(entry.player.getSkin().body().texturePath());
                if (skin != null && skin.exists()) Skia.drawPlayerHead(skin, getX() + 8, y + 3, 14, 14, 4);
                Skia.drawHeightCenteredText(Skia.getLimitText(entry.player.getProfile().name(), HUDTokens.body(), 72),
                    getX() + 28, y + 10, colors().text(), HUDTokens.body());
                String[] values = {entry.stats.getBedwarsLevel(), entry.stats.getWinLoseRatio(),
                    entry.stats.getFinalKillDeathRatio(), entry.stats.getBedsBrokeLostRatio()};
                for (int i = 0; i < values.length; i++)
                    Skia.drawFullCenteredText(Skia.getLimitText(values[i], HUDTokens.label(), 42),
                        getX() + columns[i + 1], y + 10, colors().text(), HUDTokens.label());
                y += HUDTokens.ROW_HEIGHT;
            }
            if (entries.isEmpty())
                Skia.drawHeightCenteredText(I18n.get("hud.empty"), getX() + 8, y + 10,
                    colors().secondaryText(), HUDTokens.label());
        } finally { finish(); }
        position.setSize(width, height);
    };
    private record Entry(PlayerInfo player, HypixelUser stats) {}
}