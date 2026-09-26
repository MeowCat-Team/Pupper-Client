package cn.pupperclient.management.mod.api.hud;

import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import io.github.humbleui.skija.Font;

/** Compact metric with a quiet surface, paired icon badge, and consistent baseline. */
public abstract class SimpleHUDMod extends HUDMod {
    protected BooleanSetting iconSetting = new BooleanSetting("setting.icon", "setting.icon.description",
        Icon.NEW_RELEASES, this, true);

    public SimpleHUDMod(String name, String description, String icon) { super(name, description, icon); }

    protected void draw() {
        Font font = getTextFont(HUDTokens.BODY_SIZE);
        String text = getText();
        if (text == null) text = "";
        String unit = getUnit();
        if (!unit.isBlank() && text.endsWith(" " + unit)) text = text.substring(0, text.length() - unit.length() - 1);
        String icon = getIcon();
        boolean hasIcon = icon != null && !icon.isBlank() && iconSetting.isEnabled();
        float badge = 18;
        float maxWidth = Math.max(64, Math.min(280,
            client.getWindow().getGuiScaledWidth() / Math.max(0.5f, position.getScale()) - 40));
        float unitWidth = unit.isBlank() ? 0 : Skia.getTextBounds(unit, HUDTokens.label()).getWidth() + HUDTokens.GAP;
        float textLimit = Math.max(8, maxWidth - HUDTokens.PADDING * 2
            - (hasIcon ? badge + HUDTokens.GAP : 0) - unitWidth);

        // A single "Label: value" pair gets separate M3 label/value emphasis.
        // Multiple colons (coordinates, addresses) remain one intact value.
        int separator = text.indexOf(": ");
        boolean hasLabel = separator > 0 && separator < 24 && text.indexOf(": ", separator + 2) < 0;
        String label = hasLabel ? Skia.getLimitText(text.substring(0, separator), HUDTokens.label(), textLimit * 0.44f) : "";
        String value = hasLabel ? text.substring(separator + 2) : text;
        float labelWidth = label.isBlank() ? 0 : Skia.getTextBounds(label, HUDTokens.label()).getWidth() + HUDTokens.GAP;
        value = Skia.getLimitText(value, font, Math.max(8, textLimit - labelWidth));
        float valueWidth = Skia.getTextBounds(value, font).getWidth();
        float contentWidth = labelWidth + valueWidth + unitWidth;
        float width = HUDTokens.PADDING * 2 + contentWidth + (hasIcon ? badge + HUDTokens.GAP : 0);
        float height = HUDTokens.CHIP_HEIGHT;
        begin();
        try {
            drawBackground(getX(), getY(), width, height);
            float x = getX() + HUDTokens.PADDING;
            if (hasIcon) {
                Skia.drawRoundedRect(x - 3, getY() + 3, badge, badge, 6, colors().accentContainer());
                Skia.drawFullCenteredText(icon, x - 3 + badge / 2, getY() + height / 2,
                    colors().onAccentContainer(), getIconFont(HUDTokens.ICON_SIZE));
                x += badge + HUDTokens.GAP;
            }
            if (!label.isBlank()) {
                Skia.drawHeightCenteredText(label, x, getY() + height / 2, colors().secondaryText(), HUDTokens.label());
                x += labelWidth;
            }
            Skia.drawHeightCenteredText(value, x, getY() + height / 2,
                isUrgent() ? colors().danger() : colors().text(), font);
            if (!unit.isBlank()) Skia.drawHeightCenteredText(unit, x + valueWidth + HUDTokens.GAP,
                getY() + height / 2, colors().secondaryText(), HUDTokens.label());
        } finally { finish(); }
        position.setSize(width, height);
    }

    @Override public float getRadius() { return HUDTokens.CHIP_HEIGHT / 2; }
    public abstract String getText();
    public abstract String getIcon();
    protected String getUnit() { return ""; }
    protected boolean isUrgent() { return false; }
    protected Font getTextFont(float size) { return HUDTokens.value(); }
    protected Font getIconFont(float size) { return HUDTokens.icon(); }
}
