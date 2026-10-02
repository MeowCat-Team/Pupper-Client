package cn.pupperclient.music;

import cn.pupperclient.gui.modmenu.component.MusicUi;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.management.music.MusicTrack;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.context.SkiaContext;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.theme.MaterialControls;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/** A layout specimen from production painters, with fixture tracks rather than a running Minecraft client. */
public final class MusicPlayerPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        Field field = SkiaContext.class.getDeclaredField("surface");
        field.setAccessible(true);
        Object previous = field.get(null);
        try {
            for (boolean dark : new boolean[] { false, true }) {
                I18n.setLanguage(dark ? Language.CHINESE : Language.ENGLISH);
                try (Surface surface = Surface.makeRasterN32Premul(1120, 720)) {
                    field.set(null, surface);
                    var canvas = surface.getCanvas();
                    canvas.clear(dark ? 0xff252735 : 0xffe9edf4);
                    ColorPalette palette = new ColorPalette(Hct.from(265, 42, 60), dark);
                    int count = canvas.getSaveCount();
                    MaterialTheme.panel(0, 0, 1120, 720, 28, palette);
                    MusicUi.playerHeader(1120, palette);
                    MusicTrack track = new MusicTrack(3356975915L, "Montagem pitty",
                        "DJ fixture · Sample artist", "Montagem pitty", "", 137_000);
                    MusicUi.nowPlaying(28, 108, 304, track, null, true, -1, -1, palette);
                    Skia.drawLine(350, 116, 350, 594, 1, MaterialTheme.alpha(palette.getOutlineVariant(), .45f));
                    String[] keys = { "music.tab.library", "music.tab.search", "music.tab.liked" };
                    String[] icons = { Icon.LIBRARY_MUSIC, Icon.SEARCH, Icon.FAVORITE };
                    float tabWidth = (724 - 60) / 3f;
                    for (int i = 0; i < 3; i++) MusicUi.tab(368 + i * tabWidth, 108, tabWidth - 4,
                        icons[i], MusicText.get(keys[i]), i == 1, false, palette);
                    MusicUi.iconButton(1044, 108, Icon.REFRESH, false, true, false, palette);
                    MaterialControls.textInput(368, 172, 608, 42, true, palette, MaterialTheme.opacity(), 0, 0,
                        new MaterialControls.TextState("MONTAGEM PITTY", 0, 0, 0, 0, "", 0));
                    MusicUi.button(988, 170, 104, MusicText.get("music.action.search"), true, false, palette);
                    Skia.drawText(MusicText.get("music.results.for", "MONTAGEM PITTY", 6), 380, 234,
                        palette.getOnSurfaceVariant(), Fonts.getMedium(13));
                    canvas.save();
                    try {
                        Skia.clip(368, 258, 724, 296, 16);
                        for (int i = 0; i < 6; i++) MusicUi.row(368, 258 + i * 76, 716, null,
                            i == 0 ? "Montagem pitty" : i == 1 ? "Montagem pitty (Slowed)" : "Montagem pitty · Mix " + i,
                            dark ? "示例歌手 · Montagem pitty" : "Sample artist · Montagem pitty", i == 0 ? "2:17" : "2:42",
                            i == 0, true, i == 0, i == 0, i == 2 ? 46 : -1, false, true, -1, -1, palette);
                    } finally { canvas.restore(); }
                    Skia.drawHeightCenteredText(MusicText.get("music.status.downloaded", "Montagem pitty"), 376, 586,
                        palette.getOnSurfaceVariant(), Fonts.getRegular(12));
                    MusicUi.button(952, 562, 140, MusicText.get("music.quality.button", MusicText.get("music.quality.exhigh")),
                        false, false, palette);
                    MusicUi.playback(28, 616, 1064, new MusicUi.Playback(track.title(), "Sample artist", null,
                        true, false, false, true, .65f, 37, 137, true), -1, -1, palette);
                    if (canvas.getSaveCount() != count) throw new AssertionError("Player painters leaked canvas state");
                    try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData(EncodedImageFormat.PNG)) {
                        Path file = output.resolve("player-" + (dark ? "cn-dark.png" : "en-light.png"));
                        Files.write(file, data.getBytes());
                        System.out.println("Player production-painter preview: " + file);
                    }
                }
            }
        } finally { field.set(null, previous); }
    }
}
