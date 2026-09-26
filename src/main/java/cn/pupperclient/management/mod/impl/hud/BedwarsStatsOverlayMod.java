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
            Skia.drawRoundedRect(getX() + 8, getY() + 6, 18, 18, 6, colors().accentContainer());
            Skia.drawFullCenteredText(Icon.SINGLE_BED, getX() + 17, getY() + 15,
                colors().onAccentContainer(), HUDTokens.icon());
            Skia.drawHeightCenteredText(Skia.getLimitText(getName(), HUDTokens.title(), width - 48),
                getX() + 32, getY() + 15, colors().text(), HUDTokens.title());
            Skia.drawRoundedRect(getX() + 8, getY() + 30, width - 16, 18, 6, colors().raised());
            String[] headers = {I18n.get("hud.player"), I18n.get("hud.level"), "WLR", "FKDR", "BBLR"};
            float[] columns = {48, 126, 176, 226, 276};
            for (int i = 0; i < headers.length; i++)
                Skia.drawFullCenteredText(headers[i], getX() + columns[i], getY() + 39,
                    colors().secondaryText(), HUDTokens.label());
            float y = getY() + 52;
            for (Entry entry : entries) {
                File skin = SkinUtils.getSkin(entry.player.getSkin().body().texturePath());
                Skia.drawRoundedRect(getX() + 8, y + 3, 14, 14, 4, colors().accentContainer());
                if (skin != null && skin.exists()) Skia.drawPlayerHead(skin, getX() + 8, y + 3, 14, 14, 4);
                else Skia.drawFullCenteredText(Icon.PERSON, getX() + 15, y + 10,
                    colors().onAccentContainer(), HUDTokens.icon());
                Skia.drawHeightCenteredText(Skia.getLimitText(entry.player.getProfile().name(), HUDTokens.body(), 72),
                    getX() + 28, y + 10, colors().text(), HUDTokens.body());
                String[] values = {entry.stats.getBedwarsLevel(), entry.stats.getWinLoseRatio(),
                    entry.stats.getFinalKillDeathRatio(), entry.stats.getBedsBrokeLostRatio()};
                Skia.drawRoundedRect(getX() + columns[1] - 19, y + 3, 38, 14, 7,
                    colors().accentContainer());
                for (int i = 0; i < values.length; i++)
                    Skia.drawFullCenteredText(Skia.getLimitText(values[i], HUDTokens.label(), i == 0 ? 34 : 42),
                        getX() + columns[i + 1], y + 10,
                        i == 0 ? colors().onAccentContainer() : colors().text(), HUDTokens.label());
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
