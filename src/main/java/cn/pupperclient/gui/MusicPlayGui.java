package cn.pupperclient.gui;

import cn.pupperclient.gui.api.SimpleSoarGui;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.mouse.MouseUtils;

import java.awt.*;

public class MusicPlayGui extends SimpleSoarGui {
    private boolean isfullscreen = false;

    private static final float UI_WIDTH = 1350;
    private static final float UI_HEIGHT = 900;
    private static final float SCREEN_MARGIN = 20;

    public MusicPlayGui() {
        super();
    }

    @Override
    public void draw(double mouseX, double mouseY) {
        float scale = getUiScale();
        float offsetX = getUiOffsetX(scale);
        float offsetY = getUiOffsetY(scale);

        Skia.save();
        Skia.translate(offsetX, offsetY);
        Skia.scale(scale);
        Skia.drawRect(0, 0, 340, UI_HEIGHT, new Color(20, 20, 20, 240));
        Skia.drawRect(339, 0, 1015, UI_HEIGHT, new Color(10, 10, 10, 240));

        Skia.drawText("Minecraft Jagget MusicPlay", 20, 26, Color.WHITE, Fonts.getRegular(23));

        Skia.drawText(Icon.CLOSE, 1320, 20, Color.WHITE, Fonts.getIconFill(21));

        if (!isfullscreen) {
            Skia.drawText(Icon.FULLSCREEN, 1285, 19, Color.WHITE, Fonts.getIconFill(21));
        } else {
            Skia.drawText(Icon.FULLSCREEN_EXIT, 1285, 19, Color.WHITE, Fonts.getIconFill(21));
        }
        Skia.drawText(Icon.MINIMIZE, 1251, 25, Color.WHITE, Fonts.getIconFill(21));
        Skia.restore();
    }

    @Override
    public boolean onMousePressed(double mouseX, double mouseY, int button, boolean doubled) {
        float scale = getUiScale();
        double localMouseX = (mouseX - getUiOffsetX(scale)) / scale;
        double localMouseY = (mouseY - getUiOffsetY(scale)) / scale;

        var icon_CLOSE_rect = Skia.getTextBounds(Icon.CLOSE, Fonts.getIconFill(21));
        var icon_FULLSCREEN_rect = Skia.getTextBounds(Icon.FULLSCREEN, Fonts.getIconFill(21));
        var icon_MINIMIZE_rect = Skia.getTextBounds(Icon.MINIMIZE, Fonts.getIconFill(21));

        if (MouseUtils.isInside(localMouseX, localMouseY, 1320, 20, icon_CLOSE_rect.getWidth(), icon_CLOSE_rect.getHeight()) ||
            MouseUtils.isInside(localMouseX, localMouseY, 1251, 25, icon_MINIMIZE_rect.getWidth(), icon_MINIMIZE_rect.getHeight())
        ) {
            client.setScreen(null);
            return true;
        }
        if (MouseUtils.isInside(localMouseX, localMouseY, 1285, 19, icon_FULLSCREEN_rect.getWidth() + 1, icon_FULLSCREEN_rect.getHeight() + 1)) {
            isfullscreen = !isfullscreen;
            return true;
        }
        return false;
    }

    private float getUiScale() {
        float availableWidth = Math.max(1, width - SCREEN_MARGIN * 2);
        float availableHeight = Math.max(1, height - SCREEN_MARGIN * 2);
        return Math.min(1, Math.min(availableWidth / UI_WIDTH, availableHeight / UI_HEIGHT));
    }

    private float getUiOffsetX(float scale) {
        return (width - UI_WIDTH * scale) / 2;
    }

    private float getUiOffsetY(float scale) {
        return (height - UI_HEIGHT * scale) / 2;
    }
}
