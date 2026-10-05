package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.MusicExperienceStore.Settings;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.ui.theme.MaterialTheme;

/** Common listening choices use presets; service configuration is a separate view. */
public final class MusicSettingsUi {
    public enum Control { QUALITY, TRANSITION, NORMALIZE, SIZE, TRANSLATIONS, CONNECTION, TRUST, SAVE }
    public record State(Settings settings, String endpoint, boolean trusted, String quality, String statusKey,
            String errorKey, boolean connectionOpen) { }
    private MusicSettingsUi() { }
    private static float rowWidth(MusicPlayerLayout.Box box) { return Math.min(820, box.width() - 32); }
    public static MusicPlayerLayout.Box endpoint(MusicPlayerLayout.Box box) {
        return new MusicPlayerLayout.Box(box.x() + 16, box.y() + 128, rowWidth(box), 42);
    }
    public static boolean visible(Control control, boolean connectionOpen) {
        return control == Control.CONNECTION || (control == Control.TRUST || control == Control.SAVE) == connectionOpen;
    }
    public static MusicPlayerLayout.Box control(MusicPlayerLayout.Box box, Control control) {
        return control(box, control, false);
    }
    public static MusicPlayerLayout.Box control(MusicPlayerLayout.Box box, Control control, boolean connectionOpen) {
        float right = box.x() + 16 + rowWidth(box);
        return switch (control) {
            case QUALITY -> new MusicPlayerLayout.Box(right - 208, box.y() + 40, 208, 48);
            case TRANSITION -> new MusicPlayerLayout.Box(right - 208, box.y() + 104, 208, 48);
            case NORMALIZE -> new MusicPlayerLayout.Box(right - 48, box.y() + 168, 48, 48);
            case SIZE -> new MusicPlayerLayout.Box(right - 208, box.y() + 288, 208, 48);
            case TRANSLATIONS -> new MusicPlayerLayout.Box(right - 48, box.y() + 352, 48, 48);
            case CONNECTION -> new MusicPlayerLayout.Box(box.x() + 16, box.y() + (connectionOpen ? 0 : 440), 240, 48);
            case TRUST -> new MusicPlayerLayout.Box(right - 48, box.y() + 192, 48, 48);
            case SAVE -> new MusicPlayerLayout.Box(right - 144, box.y() + 280, 144, 48);
        };
    }
    public static Settings nextSize(Settings options) {
        float size = options.lyricSize() < 32 ? 36 : options.lyricSize() < 40 ? 44 : 28;
        return options.lyrics(size, options.translations(), options.lyricOffset());
    }
    public static Settings nextTransition(Settings options) {
        if (options.crossfadeSeconds() > 0) return options.audio(false, 0, options.normalize());
        if (options.gapless()) return options.audio(true, 3, options.normalize());
        return options.audio(true, 0, options.normalize());
    }
    public static void draw(MusicPlayerLayout.Box box, State state, double mx, double my, ColorPalette palette) {
        var options = state.settings();
        if (state.connectionOpen()) {
            var back = control(box, Control.CONNECTION, true);
            MusicUi.button(back.x(), back.y(), back.width(), MusicText.get("music.settings.back"), false, back.contains(mx, my), palette);
            Skia.drawText(MusicText.get("music.settings.api.title"), box.x() + 16, box.y() + 64, palette.getOnSurface(), Fonts.getMedium(20));
            Skia.drawText(Skia.getLimitText(MusicText.get("music.settings.api.help"), Fonts.getRegular(16), rowWidth(box)),
                box.x() + 16, box.y() + 96, palette.getOnSurfaceVariant(), Fonts.getRegular(16));
            label(box, 192, 72, "music.settings.api.trust", palette);
            var trust = control(box, Control.TRUST, true); toggle(trust, state.trusted(), trust.contains(mx, my), palette);
            Skia.drawText(Skia.getLimitText(MusicText.get("music.settings.api.trust.help"), Fonts.getRegular(16), rowWidth(box)),
                box.x() + 16, box.y() + 244, palette.getOnSurfaceVariant(), Fonts.getRegular(16));
            var save = control(box, Control.SAVE, true);
            MusicUi.button(save.x(), save.y(), save.width(), MusicText.get("music.action.save"), true, save.contains(mx, my), palette);
        } else {
            section(box, 0, "music.settings.audio", palette);
            label(box, 40, 232, "music.settings.quality", palette);
            label(box, 104, 232, "music.settings.transition", palette);
            label(box, 168, 72, "music.settings.normalize", palette);
            section(box, 248, "music.settings.lyrics", palette);
            label(box, 288, 232, "music.settings.size", palette);
            label(box, 352, 72, "music.settings.translations", palette);
            for (Control control : Control.values()) {
                if (!visible(control, false)) continue;
                var target = control(box, control); boolean hover = target.contains(mx, my);
                switch (control) {
                    case QUALITY -> choice(target, MusicText.get("music.quality." + state.quality()), hover, palette);
                    case SIZE -> choice(target, MusicText.get(options.lyricSize() < 32 ? "music.settings.size.small"
                        : options.lyricSize() < 40 ? "music.settings.size.standard" : "music.settings.size.large"), hover, palette);
                    case TRANSITION -> choice(target, MusicText.get(options.crossfadeSeconds() > 0 ? "music.settings.transition.fade"
                        : options.gapless() ? "music.settings.transition.gapless" : "music.settings.transition.off"), hover, palette);
                    case TRANSLATIONS -> toggle(target, options.translations(), hover, palette);
                    case NORMALIZE -> toggle(target, options.normalize(), hover, palette);
                    case CONNECTION -> MusicUi.button(target.x(), target.y(), target.width(), MusicText.get("music.settings.connection"), false, hover, palette);
                    default -> { }
                }
            }
        }
        String feedback = !state.errorKey().isEmpty() ? MusicText.get(state.errorKey())
            : state.statusKey().isEmpty() ? "" : MusicText.get(state.statusKey());
        Skia.drawText(Skia.getLimitText(feedback, Fonts.getRegular(16), rowWidth(box)), box.x() + 16,
            box.y() + (state.connectionOpen() ? 344 : 512), state.errorKey().isEmpty() ? palette.getOnSurfaceVariant() : palette.getError(), Fonts.getRegular(16));
    }
    private static void choice(MusicPlayerLayout.Box box, String text, boolean hover, ColorPalette palette) {
        MusicUi.button(box.x(), box.y(), box.width(), text, false, hover, palette);
    }
    private static void section(MusicPlayerLayout.Box box, float y, String key, ColorPalette palette) {
        Skia.drawText(MusicText.get(key), box.x() + 16, box.y() + y + 8, palette.getOnSurface(), Fonts.getMedium(20));
    }
    private static void label(MusicPlayerLayout.Box box, float y, float reserved, String key, ColorPalette palette) {
        Skia.drawHeightCenteredText(Skia.getLimitText(MusicText.get(key), Fonts.getMedium(16), rowWidth(box) - reserved),
            box.x() + 16, box.y() + y + 24, palette.getOnSurface(), Fonts.getMedium(16));
    }
    private static void toggle(MusicPlayerLayout.Box box, boolean selected, boolean hover, ColorPalette palette) {
        var fill = selected ? palette.getPrimary() : palette.getSurfaceContainerHighest();
        Skia.drawRoundedRect(box.x() + 4, box.y() + 12, 40, 24, 12, fill);
        if (hover) Skia.drawRoundedRect(box.x(), box.y() + 4, 48, 40, 12, MaterialTheme.alpha(palette.getOnSurface(), .06f));
        Skia.drawCircle(box.x() + (selected ? 32 : 16), box.y() + 24, 8,
            selected ? palette.getOnPrimary() : palette.getOutline());
    }
}
