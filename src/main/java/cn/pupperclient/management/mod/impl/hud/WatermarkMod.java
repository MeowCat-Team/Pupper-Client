package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.StringSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;

public class WatermarkMod extends HUDMod {
    private static WatermarkMod instance;
    private final StringSetting textSetting = new StringSetting("setting.text",
        "setting.text.description", Icon.TEXT_FIELDS, this, "Pupper Client");
    private final BooleanSetting showLogoSetting = new BooleanSetting("setting.showLogo",
        "setting.showLogo.description", Icon.IMAGE, this, true);
    public WatermarkMod() {
        super("mod.watermark.name", "mod.watermark.description", Icon.BRANDING_WATERMARK);
        instance = this;
    }
    public static WatermarkMod getInstance() { return instance; }
    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        String text = textSetting.getValue() == null ? "" : textSetting.getValue();
        text = Skia.getLimitText(text, HUDTokens.title(), 220);
        boolean logo = showLogoSetting.isEnabled();
        if (text.isBlank() && !logo) { position.setSize(0, 0); return; }
        float width = 16 + Skia.getTextBounds(text, HUDTokens.title()).getWidth() + (logo ? 24 : 0);
        begin();
        try {
            drawBackground(getX(), getY(), width, 32);
            float x = getX() + 8;
            if (logo) {
                Skia.drawRoundedRect(x - 2, getY() + 4, 24, 24, 12, colors().accentContainer());
                Skia.drawImage("logo.png", x, getY() + 6, 20, 20);
                x += 24;
            }
            Skia.drawHeightCenteredText(text, x, getY() + 16, colors().text(), HUDTokens.title());
        } finally { finish(); }
        position.setSize(width, 32);
    };

    @Override public float getRadius() { return 16; }
}
