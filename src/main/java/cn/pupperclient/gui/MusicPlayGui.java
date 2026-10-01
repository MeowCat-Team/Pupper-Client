package cn.pupperclient.gui;

import cn.pupperclient.PupperClient;
import cn.pupperclient.gui.api.SimplePupperClientGui;
import cn.pupperclient.gui.modmenu.component.MusicControlBar;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.Music;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;
import cn.pupperclient.utils.mouse.MouseUtils;
import org.lwjgl.glfw.GLFW;

public class MusicPlayGui extends SimplePupperClientGui {
    private static final float UI_WIDTH = 1100;
    private static final float UI_HEIGHT = 720;
    private static final float SCREEN_MARGIN = 24;
    private static final float HEADER_BUTTON_Y = 24;
    private static final float HEADER_BUTTON_SIZE = 40;

    private boolean fullscreen;
    private MusicControlBar controlBar;

    @Override
    public void init() {
        super.init();
        controlBar = new MusicControlBar(28, UI_HEIGHT - 92, UI_WIDTH - 56);
    }

    @Override
    public void draw(double mouseX, double mouseY) {
        float scale = getUiScale();
        double localMouseX = (mouseX - getUiOffsetX(scale)) / scale;
        double localMouseY = (mouseY - getUiOffsetY(scale)) / scale;
        ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
        Music music = PupperClient.getInstance().getMusicManager().getCurrentMusic();

        Skia.save();
        Skia.translate(getUiOffsetX(scale), getUiOffsetY(scale));
        Skia.scale(scale);
        MaterialTheme.glassPanel(0, 0, UI_WIDTH, UI_HEIGHT, MaterialTheme.SURFACE_RADIUS, palette);

        Skia.drawRoundedRect(28, 24, 40, 40, MaterialTheme.CONTROL_RADIUS,
            MaterialTheme.surface(palette.getPrimaryContainer()));
        Skia.drawFullCenteredText(Icon.MUSIC_NOTE, 48, 44, palette.getPrimary(), Fonts.getIconFill(24));
        Skia.drawText(localized("Music", "音乐"), 82, 28, palette.getOnSurface(), Fonts.getMedium(26));
        Skia.drawText(localized("Your local soundtrack", "随时播放你的本地音乐"), 82, 61,
            palette.getOnSurfaceVariant(), Fonts.getRegular(13));

        drawWindowButton(Icon.MINIMIZE, UI_WIDTH - 164, localMouseX, localMouseY, palette);
        drawWindowButton(fullscreen ? Icon.FULLSCREEN_EXIT : Icon.FULLSCREEN, UI_WIDTH - 116,
            localMouseX, localMouseY, palette);
        drawWindowButton(Icon.CLOSE, UI_WIDTH - 68, localMouseX, localMouseY, palette);

        float albumSize = 320;
        float albumX = (UI_WIDTH - albumSize) / 2;
        if (music != null && music.getAlbum() != null) {
            Skia.drawRoundedImage(music.getAlbum(), albumX, 130, albumSize, albumSize, MaterialTheme.SURFACE_RADIUS);
            MaterialTheme.outline(albumX, 130, albumSize, albumSize, MaterialTheme.SURFACE_RADIUS, palette);
        } else {
            MaterialTheme.card(albumX, 130, albumSize, albumSize, MaterialTheme.SURFACE_RADIUS, palette);
            Skia.drawFullCenteredText(Icon.MUSIC_NOTE, UI_WIDTH / 2, 290, palette.getPrimary(), Fonts.getIcon(100));
        }

        String title = music == null ? localized("Ready when you are", "音乐随时待命") : music.getTitle();
        String artist = music == null ? localized("Choose a track or add music below", "选择歌曲，或使用下方按钮添加音乐") : music.getArtist();
        Skia.drawCenteredText(Skia.getLimitText(title, Fonts.getMedium(28), UI_WIDTH - 100),
            UI_WIDTH / 2, 482, palette.getOnSurface(), Fonts.getMedium(28));
        Skia.drawCenteredText(Skia.getLimitText(artist, Fonts.getRegular(16), UI_WIDTH - 100),
            UI_WIDTH / 2, 522, palette.getOnSurfaceVariant(), Fonts.getRegular(16));
        controlBar.draw(localMouseX, localMouseY);
        Skia.restore();
    }

    private void drawWindowButton(String icon, float x, double mouseX, double mouseY, ColorPalette palette) {
        boolean hovered = MouseUtils.isInside(mouseX, mouseY, x, HEADER_BUTTON_Y, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);
        if (hovered) {
            Skia.drawRoundedRect(x, HEADER_BUTTON_Y, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE, MaterialTheme.CONTROL_RADIUS,
                MaterialTheme.alpha(palette.getOnSurface(), 0.08F));
        }
        Skia.drawFullCenteredText(icon, x + HEADER_BUTTON_SIZE / 2, HEADER_BUTTON_Y + HEADER_BUTTON_SIZE / 2,
            palette.getOnSurfaceVariant(), Fonts.getIcon(22));
    }

    @Override
    public boolean onMousePressed(double mouseX, double mouseY, int button, boolean doubled) {
        float scale = getUiScale();
        double localX = (mouseX - getUiOffsetX(scale)) / scale;
        double localY = (mouseY - getUiOffsetY(scale)) / scale;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (overButton(localX, localY, UI_WIDTH - 68) || overButton(localX, localY, UI_WIDTH - 164)) {
                client.gui.setScreen(null);
                return true;
            }
            if (overButton(localX, localY, UI_WIDTH - 116)) {
                fullscreen = !fullscreen;
                return true;
            }
        }
        controlBar.mousePressed(localX, localY, button);
        return true;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        float scale = getUiScale();
        controlBar.mouseReleased((mouseX - getUiOffsetX(scale)) / scale, (mouseY - getUiOffsetY(scale)) / scale, button);
        return true;
    }

    @Override
    public boolean onCharTyped(int chr) {
        controlBar.charTyped(chr);
        return true;
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return super.onKeyPressed(keyCode, scanCode, modifiers);
        controlBar.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    private boolean overButton(double mouseX, double mouseY, float x) {
        return MouseUtils.isInside(mouseX, mouseY, x, HEADER_BUTTON_Y, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);
    }

    private float getUiScale() {
        float margin = fullscreen ? 0 : SCREEN_MARGIN;
        float availableWidth = Math.max(1, client.getWindow().getWidth() - margin * 2);
        float availableHeight = Math.max(1, client.getWindow().getHeight() - margin * 2);
        float scale = Math.min(availableWidth / UI_WIDTH, availableHeight / UI_HEIGHT);
        return fullscreen ? scale : Math.min(1, scale);
    }

    private float getUiOffsetX(float scale) {
        return (client.getWindow().getWidth() - UI_WIDTH * scale) / 2;
    }

    private float getUiOffsetY(float scale) {
        return (client.getWindow().getHeight() - UI_HEIGHT * scale) / 2;
    }

    private static String localized(String english, String chinese) {
        return I18n.getCurrentLanguage() == Language.CHINESE ? chinese : english;
    }
}
