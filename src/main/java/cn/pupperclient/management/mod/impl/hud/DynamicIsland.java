package cn.pupperclient.management.mod.impl.hud;

import java.util.ArrayList;
import java.util.List;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.EventListener;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.event.mod.AutoAgainEvent;
import cn.pupperclient.event.mod.ModStateChangeEvent;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.api.hud.design.HUDMotion;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.management.mod.settings.impl.StringSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.language.I18n;

/** A compact status card with bounded, readable notifications. */
public class DynamicIsland extends HUDMod {
    private static boolean configLoading;
    private static long nextGameUntil;
    private final StringSetting name = new StringSetting("mod.DynamicIsland.customname",
        "mod.DynamicIsland.customname.description", Icon.FLIGHT_LAND, this, "Pupper Client");
    private final List<Notice> notices = new ArrayList<>();
    private final HUDMotion motion = new HUDMotion();
    private final HUDMotion.Spring widthMotion = new HUDMotion.Spring(220);

    public DynamicIsland() {
        super("mod.DynamicIsland.name", "mod.DynamicIsland.description", Icon.BROWSE_ACTIVITY);
    }
    public static void setConfigLoading(boolean loading) { configLoading = loading; }
    @EventListener public void onHeypixelAgain(AutoAgainEvent event) {
        nextGameUntil = System.currentTimeMillis() + 3000;
    }

    @EventListener private void handleModStateChange(ModStateChangeEvent event) {
        if (configLoading || !isEnabled() || event.getMod() == this) return;
        String module = event.getMod().getName();
        if (module == null || module.equals("null")) module = event.getMod().getRawName();
        String id = event.getMod().getRawName();
        notices.removeIf(notice -> notice.id.equals(id));
        if (notices.size() >= 3) notices.removeFirst();
        notices.add(new Notice(id, module, event.isEnabled(), System.currentTimeMillis() + 2400));
    }

    @Override public void onDisable() { super.onDisable(); notices.clear(); }
    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        long now = System.currentTimeMillis();
        float dt = motion.deltaSeconds();
        for (Notice notice : notices)
            notice.visibility = HUDMotion.approach(notice.visibility, now < notice.until ? 1 : 0, dt, reducedMotion());
        notices.removeIf(notice -> now >= notice.until && notice.visibility < 0.01f);

        String player = client.player == null ? "Player" : client.player.getName().getString();
        String server = client.getCurrentServer() == null ? I18n.get("hud.singleplayer") : client.getCurrentServer().ip;
        String detail = player + " · " + server + " · " + client.getFps() + " FPS";
        String title = now < nextGameUntil ? I18n.get("hud.nextgame") : name.getValue();
        float desiredWidth = Math.max(200, Math.max(Skia.getTextBounds(title, HUDTokens.title()).getWidth() + 40,
            Skia.getTextBounds(detail, HUDTokens.label()).getWidth() + 16));
        for (Notice notice : notices)
            desiredWidth = Math.max(desiredWidth, Skia.getTextBounds(notice.title, HUDTokens.body()).getWidth() + 96);
        float maximum = Math.max(100, Math.min(320, client.getWindow().getGuiScaledWidth() / position.getScale() - 24));
        float width = widthMotion.update(Math.min(maximum, desiredWidth), dt, reducedMotion());
        float height = 44;
        for (Notice notice : notices) height += 28 * notice.visibility;
        begin();
        try {
            drawBackground(getX(), getY(), width, height);
            Skia.clip(getX(), getY(), width, height, getRadius());
            Skia.drawRoundedRect(getX() + 8, getY() + 8, 20, 20, 7, colors().accentContainer());
            Skia.drawFullCenteredText(now < nextGameUntil ? Icon.CHECK : Icon.BROWSE_ACTIVITY,
                getX() + 18, getY() + 18, colors().onAccentContainer(), HUDTokens.icon());
            Skia.drawHeightCenteredText(Skia.getLimitText(title, HUDTokens.title(), width - 44),
                getX() + 34, getY() + 18, colors().text(), HUDTokens.title());
            Skia.drawText(Skia.getLimitText(detail, HUDTokens.label(), width - 16),
                getX() + 8, getY() + 31, colors().secondaryText(), HUDTokens.label());
            float y = getY() + 44;
            for (Notice notice : notices) {
                float rowHeight = 28 * notice.visibility;
                Skia.save();
                try {
                    Skia.clip(getX(), y, width, rowHeight, 0);
                    Skia.drawRoundedRect(getX() + 4, y, width - 8, 24, 7, colors().raised());
                    Skia.drawFullCenteredText(notice.enabled ? Icon.CHECK : Icon.CLOSE,
                        getX() + 16, y + 12, colors().accent(), HUDTokens.icon());
                    String state = I18n.get(notice.enabled ? "hud.enabled" : "hud.disabled");
                    float stateWidth = Skia.getTextBounds(state, HUDTokens.label()).getWidth();
                    Skia.drawHeightCenteredText(Skia.getLimitText(notice.title, HUDTokens.body(),
                        Math.max(8, width - stateWidth - 52)), getX() + 30, y + 12, colors().text(), HUDTokens.body());
                    Skia.drawHeightCenteredText(state, getX() + width - 12 - stateWidth, y + 12,
                        colors().secondaryText(), HUDTokens.label());
                } finally { Skia.restore(); }
                y += rowHeight;
            }
        } finally { finish(); }
        position.setSize(width, height);
    };

    private static final class Notice {
        final String id, title;
        final boolean enabled;
        final long until;
        float visibility;
        Notice(String id, String title, boolean enabled, long until) {
            this.id = id; this.title = title; this.enabled = enabled; this.until = until;
        }
    }
}