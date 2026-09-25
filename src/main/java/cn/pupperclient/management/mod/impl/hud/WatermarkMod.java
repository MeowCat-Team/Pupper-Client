package cn.pupperclient.management.mod.impl.hud;

import java.io.IOException;
import java.io.InputStream;

import cn.pupperclient.PupperLogger;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.StringSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;

import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Image;
import io.github.humbleui.types.Rect;

public class WatermarkMod extends HUDMod {

    private static WatermarkMod instance;
    private Image logoImage;

    // Settings
    private final StringSetting textSetting = new StringSetting("setting.text",
        "setting.text.description", Icon.TEXT_FIELDS, this, "Pupper Client");
    private final BooleanSetting showLogoSetting = new BooleanSetting("setting.showLogo",
        "setting.showLogo.description", Icon.IMAGE, this, true);

    public WatermarkMod() {
        super("mod.watermark.name", "mod.watermark.description", Icon.BRANDING_WATERMARK);
        instance = this;

        try (InputStream is = getClass().getResourceAsStream("/assets/pupper/logo.png")) {
            if (is != null) {
                byte[] imageData = is.readAllBytes();
                this.logoImage = Image.makeDeferredFromEncodedBytes(imageData);
            }
        } catch (IOException e) {
            PupperLogger.error("WatermarkMod", "Failed to load logo", e);
        }
    }

    public static WatermarkMod getInstance() {
        return instance;
    }

    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> this.draw();

    private void draw() {
        try {
            this.begin();
            drawContent();
        } catch (Exception e) {
            PupperLogger.error("WatermarkMod", "Error in draw(): ", e);
            position.setSize(100, 20);
        } finally {
            try {
                this.finish();
            } catch (Exception e) {
                PupperLogger.error("WatermarkMod", "Error in finish(): ", e);
            }
        }
    }

    private void drawContent() {
        float padding = 5f;
        float logoSize = 18f;
        float fontSize = 12f;
        boolean showLogo = showLogoSetting.isEnabled() && logoImage != null;
        String text = textSetting.getValue();
        boolean showText = text != null && !text.isBlank();
        Rect textBounds = showText
            ? Skia.getTextBounds(text, Fonts.getGoogleSansRegular(fontSize))
            : Rect.makeWH(0, 0);

        float contentWidth = (showLogo ? logoSize : 0)
            + (showLogo && showText ? padding : 0)
            + textBounds.getWidth();
        float contentHeight = Math.max(showLogo ? logoSize : 0, showText ? textBounds.getHeight() : 0);
        float panelWidth = contentWidth + padding * 2;
        float panelHeight = contentHeight + padding * 2;

        drawBackground(getX(), getY(), panelWidth, panelHeight);
        float currentX = getX() + padding;

		if (showLogo) {
			Skia.drawImage("logo.png", currentX, getY() + (panelHeight - logoSize) / 2, logoSize, logoSize);
			currentX += logoSize + (showText ? padding : 0);
        }

		if (showText) {
            FontMetrics metrics = Fonts.getGoogleSansRegular(fontSize).getMetrics();
            float textCenterY = (metrics.getAscent() - metrics.getDescent()) / 2 - metrics.getAscent();
			float textY = getY() + panelHeight / 2 - textCenterY;
			drawText(text, currentX, textY, Fonts.getGoogleSansRegular(fontSize));
        }

		position.setSize(panelWidth, panelHeight);
    }

    @Override
    public float getRadius() {
        return 4f;
    }
}
