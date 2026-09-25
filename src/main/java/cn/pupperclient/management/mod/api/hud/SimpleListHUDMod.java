package cn.pupperclient.management.mod.api.hud;

import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.types.Rect;

import java.util.List;

public abstract class SimpleListHUDMod extends HUDMod{
    public SimpleListHUDMod (String name, String description, String icon) {
        super(name, description, icon);
    }

    protected void draw() {
        float fontSize = 9;
        float padding = 5;
        float maxWidth = 0;
        float contentHeight = 0;
        float lineSpacing = 4;
        List<String> lines = getText();

        for (int i = 0; i < lines.size(); i++) {
            Rect textBounds = Skia.getTextBounds(lines.get(i), Fonts.getRegular(fontSize));
            maxWidth = Math.max(maxWidth, textBounds.getWidth());
            contentHeight += textBounds.getHeight();
            if (i < lines.size() - 1) {
                contentHeight += lineSpacing;
            }
        }

        float width = maxWidth + (padding * 2);
        float height = contentHeight + padding * 2;

        this.begin();
        this.drawBackground(getX(), getY(), width, height);

        float y = getY() + padding;

        for (String line : lines) {
            this.drawText(line, getX() + padding,
                y, Fonts.getRegular(fontSize));
            y += Skia.getTextBounds(line, Fonts.getRegular(fontSize)).getHeight() + lineSpacing;
        }

        this.finish();
        position.setSize(width, height);
    }

    public abstract List<String> getText();

    public abstract String getIcon();

    @Override
	public float getRadius() {
		return 6;
	}
}
