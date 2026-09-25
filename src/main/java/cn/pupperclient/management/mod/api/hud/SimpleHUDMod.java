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
        float maxWidth = Math.max(40, Math.min(260, client.getWindow().getGuiScaledWidth() / position.getScale() - 40));
        String unit = getUnit();
        if (!unit.isBlank() && text.endsWith(" " + unit)) text = text.substring(0, text.length() - unit.length() - 1);
        float unitWidth = unit.isBlank() ? 0 : Skia.getTextBounds(unit, HUDTokens.label()).getWidth() + HUDTokens.GAP;
        text = Skia.getLimitText(text, font, Math.max(8, maxWidth - unitWidth));
        String icon = getIcon();
        boolean hasIcon = icon != null && !icon.isBlank() && iconSetting.isEnabled();
        float badge = 18;
        float valueWidth = Skia.getTextBounds(text, font).getWidth();
        float contentWidth = valueWidth + unitWidth;
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
            Skia.drawHeightCenteredText(text, x, getY() + height / 2, isUrgent() ? colors().danger() : colors().text(), font);
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