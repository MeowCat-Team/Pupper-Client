package cn.pupperclient.gui.modmenu.pages;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import cn.pupperclient.PupperClient;
import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.gui.api.SoarGui;
import cn.pupperclient.gui.api.page.SimplePage;
import cn.pupperclient.gui.api.page.impl.RightLeftTransition;
import cn.pupperclient.gui.modmenu.GuiModMenu;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.color.ColorUtils;
import cn.pupperclient.utils.mouse.MouseUtils;

public class HomePage extends SimplePage {

    private static final float PAGE_PADDING = 36;
    private static final float CARD_GAP = 18;
    private static final float CARD_HEIGHT = 196;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);

    private final List<QuickAction> quickActions = List.of(
        new QuickAction(Icon.INVENTORY_2, "Mod Manager", "Tune combat, HUD and visuals", ModsPage.class),
        new QuickAction(Icon.DESCRIPTION, "Profiles", "Save and switch your setup", ProfilePage.class),
        new QuickAction(Icon.MUSIC_NOTE, "Music Player", "Your local soundtrack", MusicPage.class)
    );

    private String currentTime;
    private String currentDate;
    private QuickAction pressedAction;

    public HomePage(SoarGui parent) {
        super(parent, "text.home", Icon.HOME, new RightLeftTransition(true));
    }

    @Override
    public void init() {
        setTransition(new RightLeftTransition(true));
        updateTime();
        pressedAction = null;
    }

    @Override
    public void draw(double mouseX, double mouseY) {
        updateTime();

        PupperClient client = PupperClient.getInstance();
        ColorPalette palette = client.getColorManager().getPalette();

        Skia.drawRect(x, y, width, height, palette.getSurfaceContainer());
        drawBackgroundDecoration(palette);
        drawHeader(palette);
        drawQuickActions(mouseX, mouseY, palette);
        drawOverview(palette);
    }

    private void drawBackgroundDecoration(ColorPalette palette) {
        Skia.drawCircle(x + width - 26, y + 10, 142,
            ColorUtils.applyAlpha(palette.getPrimary(), 0.035F));
        Skia.drawCircle(x + width - 26, y + 10, 92,
            ColorUtils.applyAlpha(palette.getTertiary(), 0.045F));
    }

    private void drawHeader(ColorPalette palette) {
        float badgeX = x + PAGE_PADDING;
        float badgeY = y + 30;

        Skia.drawRoundedRect(badgeX, badgeY, 132, 28, 14, palette.getSurfaceContainerHighest());
        Skia.drawHeightCenteredText(Icon.AUTO_AWESOME, badgeX + 12, badgeY + 14,
            palette.getOnSurfaceVariant(), Fonts.getIconFill(16));
        Skia.drawHeightCenteredText("PUPPER HOME", badgeX + 36, badgeY + 14,
            palette.getOnSurfaceVariant(), Fonts.getMedium(12));

        Skia.drawText("Welcome back.", x + PAGE_PADDING, y + 78,
            palette.getOnSurface(), Fonts.getGoogleSansRegular(36));
        Skia.drawText("Everything you need, one click away.", x + PAGE_PADDING, y + 122,
            palette.getOnSurfaceVariant(), Fonts.getRegular(15));

        float right = x + width - PAGE_PADDING;
        drawRightAligned(currentTime, right, y + 48, palette.getOnSurface(), Fonts.getMedium(28));
        drawRightAligned(currentDate.toUpperCase(Locale.ENGLISH), right, y + 82,
            palette.getOnSurfaceVariant(), Fonts.getMedium(12));

        String version = PupperClient.getInstance().getDisplayVersion();
        drawRightAligned(version, right, y + 111, palette.getOnSurfaceVariant(), Fonts.getRegular(13));
    }

    private void drawQuickActions(double mouseX, double mouseY, ColorPalette palette) {
        float headingY = y + 170;
        Skia.drawText("Quick actions", x + PAGE_PADDING, headingY,
            palette.getOnSurface(), Fonts.getMedium(18));
        drawRightAligned("Choose a workspace", x + width - PAGE_PADDING, headingY + 2,
            palette.getOnSurfaceVariant(), Fonts.getRegular(13));

        float cardY = y + 204;
        float cardWidth = (width - PAGE_PADDING * 2 - CARD_GAP * 2) / 3;

        for (int i = 0; i < quickActions.size(); i++) {
            float cardX = x + PAGE_PADDING + i * (cardWidth + CARD_GAP);
            QuickAction action = quickActions.get(i);
            action.setBounds(cardX, cardY, cardWidth, CARD_HEIGHT);
            drawQuickAction(action, mouseX, mouseY, palette.getOnSurfaceVariant(),
                palette.getSurfaceContainerHighest(), palette.getOnSurface(), palette);
        }
    }

    private void drawQuickAction(QuickAction action, double mouseX, double mouseY, Color accent,
                                 Color iconBackground, Color iconColor, ColorPalette palette) {
        boolean hovered = action.contains(mouseX, mouseY);
        action.hoverAnimation.onTick(hovered || action == pressedAction ? 1 : 0, 12);

        float hover = action.hoverAnimation.getValue();
        float visualY = action.y - hover * 4;

        Skia.drawRoundedRect(action.x, visualY, action.width, action.height, 20,
            palette.getSurfaceContainer());
        Skia.drawRoundedRect(action.x, visualY, action.width, action.height, 20,
            ColorUtils.applyAlpha(accent, hover * 0.035F));
        Skia.drawOutline(action.x, visualY, action.width, action.height, 20, 1,
            ColorUtils.applyAlpha(palette.getOutlineVariant(), 0.45F + hover * 0.25F));

        Skia.drawRoundedRect(action.x + 18, visualY + 18, 48, 48, 14, iconBackground);
        Skia.drawFullCenteredText(action.icon, action.x + 42, visualY + 42,
            iconColor, Fonts.getIconFill(27));

        Skia.drawCircle(action.x + action.width - 30, visualY + 30, 15,
            ColorUtils.applyAlpha(accent, 0.10F + hover * 0.10F));
        Skia.drawFullCenteredText(Icon.CHEVRON_RIGHT, action.x + action.width - 30, visualY + 30,
            accent, Fonts.getIconFill(19));

        Skia.drawText(action.title, action.x + 18, visualY + 88,
            palette.getOnSurface(), Fonts.getMedium(20));
        Skia.drawText(action.description, action.x + 18, visualY + 118,
            palette.getOnSurfaceVariant(), Fonts.getRegular(13));

        Skia.drawLine(action.x + 18, visualY + 148, action.x + action.width - 18, visualY + 148, 1,
            ColorUtils.applyAlpha(palette.getOutlineVariant(), 0.55F));
        Skia.drawText(getActionDetail(action), action.x + 18, visualY + 166,
            palette.getOnSurfaceVariant(), Fonts.getMedium(12));
        Skia.drawCircle(action.x + action.width - 24, visualY + 171, 4, accent);
    }

    private String getActionDetail(QuickAction action) {
        PupperClient client = PupperClient.getInstance();
        if (action.target == ModsPage.class) {
            long enabled = client.getModManager().getMods().stream().filter(mod -> mod.isEnabled()).count();
            return enabled + " of " + client.getModManager().getMods().size() + " enabled";
        }
        if (action.target == ProfilePage.class) {
            int profiles = client.getProfileManager().getProfiles().size();
            return profiles + (profiles == 1 ? " profile" : " profiles");
        }
        int tracks = client.getMusicManager().getMusics().size();
        return tracks + (tracks == 1 ? " track" : " tracks");
    }

    private void drawOverview(ColorPalette palette) {
        float headingY = y + 438;
        Skia.drawText("Overview", x + PAGE_PADDING, headingY,
            palette.getOnSurface(), Fonts.getMedium(18));

        float statusRight = x + width - PAGE_PADDING;
        drawRightAligned("All systems ready", statusRight, headingY + 2,
            palette.getOnSurfaceVariant(), Fonts.getRegular(13));
        float statusWidth = Skia.getTextBounds("All systems ready", Fonts.getRegular(13)).getWidth();
        Skia.drawCircle(statusRight - statusWidth - 11, headingY + 8, 4, palette.getPrimary());

        float panelX = x + PAGE_PADDING;
        float panelY = y + 466;
        float panelWidth = width - PAGE_PADDING * 2;
        float panelHeight = 80;

        Skia.drawRoundedRect(panelX, panelY, panelWidth, panelHeight, 18,
            palette.getSurfaceContainerLowest());
        Skia.drawOutline(panelX, panelY, panelWidth, panelHeight, 18, 1,
            ColorUtils.applyAlpha(palette.getOutlineVariant(), 0.45F));

        int totalMods = PupperClient.getInstance().getModManager().getMods().size();
        long enabledMods = PupperClient.getInstance().getModManager().getMods().stream()
            .filter(mod -> mod.isEnabled()).count();
        int profiles = PupperClient.getInstance().getProfileManager().getProfiles().size();
        int tracks = PupperClient.getInstance().getMusicManager().getMusics().size();

        float metricWidth = panelWidth / 3;
        drawMetric(panelX, panelY, metricWidth, Icon.BOLT, enabledMods + " / " + totalMods,
            "Active modules", palette.getOnSurfaceVariant(), palette);
        drawMetric(panelX + metricWidth, panelY, metricWidth, Icon.DESCRIPTION, String.valueOf(profiles),
            "Saved profiles", palette.getOnSurfaceVariant(), palette);
        drawMetric(panelX + metricWidth * 2, panelY, metricWidth, Icon.MUSIC_NOTE, String.valueOf(tracks),
            "Music tracks", palette.getOnSurfaceVariant(), palette);

        Skia.drawLine(panelX + metricWidth, panelY + 16, panelX + metricWidth, panelY + panelHeight - 16, 1,
            ColorUtils.applyAlpha(palette.getOutlineVariant(), 0.55F));
        Skia.drawLine(panelX + metricWidth * 2, panelY + 16, panelX + metricWidth * 2,
            panelY + panelHeight - 16, 1, ColorUtils.applyAlpha(palette.getOutlineVariant(), 0.55F));
    }

    private void drawMetric(float metricX, float metricY, float metricWidth, String icon, String value,
                            String label, Color accent, ColorPalette palette) {
        Skia.drawCircle(metricX + 34, metricY + 40, 18, ColorUtils.applyAlpha(accent, 0.12F));
        Skia.drawFullCenteredText(icon, metricX + 34, metricY + 40, accent, Fonts.getIconFill(20));
        Skia.drawText(value, metricX + 62, metricY + 24, palette.getOnSurface(), Fonts.getMedium(17));
        Skia.drawText(label, metricX + 62, metricY + 49,
            palette.getOnSurfaceVariant(), Fonts.getRegular(12));
    }

    @Override
    public void mousePressed(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        pressedAction = quickActions.stream().filter(action -> action.contains(mouseX, mouseY))
            .findFirst().orElse(null);
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pressedAction != null && pressedAction.contains(mouseX, mouseY)) {
            if (parent instanceof GuiModMenu menu) {
                menu.navigateTo(pressedAction.target);
            } else {
                parent.setCurrentPage(pressedAction.target);
            }
        }
        pressedAction = null;
    }

    private void drawRightAligned(String text, float right, float y, Color color, io.github.humbleui.skija.Font font) {
        float textWidth = Skia.getTextBounds(text, font).getWidth();
        Skia.drawText(text, right - textWidth, y, color, font);
    }

    private void updateTime() {
        LocalDateTime now = LocalDateTime.now();
        currentTime = TIME_FORMAT.format(now);
        currentDate = DATE_FORMAT.format(now);
    }

    @Override
    public void onClosed() {
        setTransition(new RightLeftTransition(false));
        pressedAction = null;
    }

    private static final class QuickAction {
        private final String icon;
        private final String title;
        private final String description;
        private final Class<? extends SimplePage> target;
        private final SimpleAnimation hoverAnimation = new SimpleAnimation();

        private float x;
        private float y;
        private float width;
        private float height;

        private QuickAction(String icon, String title, String description, Class<? extends SimplePage> target) {
            this.icon = icon;
            this.title = title;
            this.description = description;
            this.target = target;
        }

        private void setBounds(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private boolean contains(double mouseX, double mouseY) {
            return MouseUtils.isInside(mouseX, mouseY, x, y, width, height);
        }
    }
}
