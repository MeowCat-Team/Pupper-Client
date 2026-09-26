package cn.pupperclient.management.mod.impl.hud;

import java.awt.Color;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.api.hud.design.HUDMotion;
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
    private final HUDMotion motion = new HUDMotion();
    private HUDMotion.Spring widthMotion = new HUDMotion.Spring(0);
    private String displayedText;
    private String previousText;
    private float textReveal = 1;
    public WatermarkMod() {
        super("mod.watermark.name", "mod.watermark.description", Icon.BRANDING_WATERMARK);
        instance = this;
    }
    public static WatermarkMod getInstance() { return instance; }
    @Override public void onDisable() {
        super.onDisable();
        widthMotion = new HUDMotion.Spring(0);
        displayedText = null;
        previousText = null;
    }

    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        String text = textSetting.getValue() == null ? "" : textSetting.getValue();
        text = Skia.getLimitText(text, HUDTokens.title(), 220);
        float dt = motion.deltaSeconds();
        boolean reduced = reducedMotion();
        if (displayedText == null) displayedText = text;
        else if (!displayedText.equals(text)) {
            previousText = displayedText;
            displayedText = text;
            textReveal = reduced ? 1 : 0;
        }
        textReveal = HUDMotion.approach(textReveal, 1, dt, reduced);
        if (textReveal >= 0.99f) previousText = null;
        boolean logo = showLogoSetting.isEnabled();
        float desiredWidth = text.isBlank() && !logo ? 0
            : 16 + Skia.getTextBounds(text, HUDTokens.title()).getWidth() + (logo ? 24 : 0);
        float width = widthMotion.update(desiredWidth, dt, reduced);
        if (width < 0.5f) { position.setSize(0, 0); return; }
        position.setSize(width, 32);
        begin();
        try {
            drawBackground(getX(), getY(), width, 32);
            Skia.clip(getX(), getY(), width, 32, Math.min(getRadius(), width / 2));
            float x = getX() + 8;
            if (logo) {
                Skia.drawRoundedRect(x - 2, getY() + 4, 24, 24, 12, colors().accentContainer());
                Skia.drawImage("logo.png", x, getY() + 6, 20, 20);
                x += 24;
            }
            if (previousText != null && !previousText.isBlank())
                Skia.drawHeightCenteredText(previousText, x, getY() + 16 - 3 * textReveal,
                    withOpacity(colors().text(), 1 - textReveal), HUDTokens.title());
            if (!displayedText.isBlank())
                Skia.drawHeightCenteredText(displayedText, x, getY() + 16 + 3 * (1 - textReveal),
                    withOpacity(colors().text(), textReveal), HUDTokens.title());
        } finally { finish(); }
    };

    private static Color withOpacity(Color color, float opacity) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
            Math.round(color.getAlpha() * Math.max(0, Math.min(1, opacity))));
    }

    @Override public float getRadius() { return 16; }
}
