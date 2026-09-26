package cn.pupperclient.management.mod.impl.hud;

import java.awt.Color;
import java.util.Arrays;
import cn.pupperclient.PupperClient;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.gui.edithud.api.HUDCore;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.management.mod.api.hud.design.HUDMotion;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.management.music.Music;
import cn.pupperclient.management.music.MusicManager;
import cn.pupperclient.management.music.MusicPlayer;
import cn.pupperclient.management.music.lyric.LyricsManager;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.language.I18n;

/** Artwork stays contained while the selected HUD surface remains visible. */
public class MusicInfoMod extends SimpleHUDMod {
    private final ComboSetting typeSetting = new ComboSetting("setting.type", "setting.type.description",
        Icon.FORMAT_LIST_BULLETED, this, Arrays.asList("setting.simple", "setting.normal", "setting.cover"), "setting.simple");
    private final BooleanSetting lyricsDisplaySetting = new BooleanSetting("setting.lyrics.display.name",
        "setting.lyrics.display.description", Icon.TEXT_FIELDS, this, false) {
        @Override public boolean isVisible() { return !typeSetting.getOption().equals("setting.simple"); }
    };
    private final BooleanSetting coverAnimationSetting = new BooleanSetting("setting.cover.animation.name",
        "setting.cover.animation.description", Icon.MOVIE, this, true) {
        @Override public boolean isVisible() { return !typeSetting.getOption().equals("setting.simple"); }
    };
    private final LyricsManager lyricsManager = new LyricsManager();
    private final HUDMotion motion = new HUDMotion();
    private final HUDMotion.Spring reveal = new HUDMotion.Spring(0);
    private final HUDMotion.Spring widthMotion = new HUDMotion.Spring(200);
    private final HUDMotion.Spring progressMotion = new HUDMotion.Spring(0);
    private final HUDMotion.Spring lyricHeightMotion = new HUDMotion.Spring(0);
    private String displayedTitle, displayedArtist, previousTitle, previousArtist;
    private String displayedLyric, previousLyric;
    private float trackReveal = 1, lyricReveal = 1;
    private String lyric = "";

    public MusicInfoMod() { super("mod.musicinfo.name", "mod.musicinfo.description", Icon.MUSIC_NOTE); }

    public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> {
        MusicManager manager = PupperClient.getInstance().getMusicManager();
        Music music = manager.getCurrentMusic();
        lyric = music != null && lyricsDisplaySetting.isEnabled()
            ? lyricsManager.getCurrentLyric(music, manager.getCurrentTime()) : "";
        if (lyric == null) lyric = "";
    };

    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
        if (typeSetting.getOption().equals("setting.simple")) { draw(); return; }
        MusicManager manager = PupperClient.getInstance().getMusicManager();
        Music music = manager.getCurrentMusic();
        float dt = motion.deltaSeconds();
        float amount = reveal.update(music != null || HUDCore.isEditing ? 1 : 0, dt, reducedMotion());
        if (amount < 0.01f) { position.setSize(0, 0); return; }
        boolean cover = typeSetting.getOption().equals("setting.cover");
        boolean lyrics = lyricsDisplaySetting.isEnabled();
        String title = music == null ? I18n.get("hud.music.preview") : safe(music.getTitle());
        String artist = music == null ? I18n.get("hud.music.artist") : safe(music.getArtist());
        if (displayedTitle == null) {
            displayedTitle = title;
            displayedArtist = artist;
        } else if (!displayedTitle.equals(title) || !displayedArtist.equals(artist)) {
            previousTitle = displayedTitle;
            previousArtist = displayedArtist;
            displayedTitle = title;
            displayedArtist = artist;
            trackReveal = reducedMotion() ? 1 : 0;
        }
        trackReveal = HUDMotion.approach(trackReveal, 1, dt, reducedMotion());
        if (trackReveal >= 0.99f) { previousTitle = null; previousArtist = null; }
        String lyricText = lyric.isBlank() ? "♪" : lyric;
        if (displayedLyric == null) displayedLyric = lyricText;
        else if (!displayedLyric.equals(lyricText)) {
            previousLyric = displayedLyric;
            displayedLyric = lyricText;
            lyricReveal = reducedMotion() ? 1 : 0;
        }
        lyricReveal = HUDMotion.approach(lyricReveal, 1, dt, reducedMotion());
        if (lyricReveal >= 0.99f) previousLyric = null;
        float artSize = cover ? 48 : 36;
        float textOffset = artSize + 20;
        float maxText = Math.max(Skia.getTextBounds(title, HUDTokens.title()).getWidth(),
            Skia.getTextBounds(artist, HUDTokens.label()).getWidth());
        float maximum = Math.max(140, Math.min(320, client.getWindow().getGuiScaledWidth() / position.getScale() - 24));
        float width = widthMotion.update(Math.min(maximum, Math.max(200, textOffset + maxText + 12)), dt, reducedMotion());
        float baseHeight = cover ? 64 : 56;
        float lyricHeight = lyricHeightMotion.update(lyrics ? 18 : 0, dt, reducedMotion());
        float height = (baseHeight + lyricHeight) * amount;
        position.setSize(width, height);
        begin();
        try {
            drawBackground(getX(), getY(), width, height);
            Skia.clip(getX(), getY(), width, height, Math.min(getRadius(), height / 2));
            drawArtwork(music, manager, getX() + 8, getY() + (baseHeight - artSize) / 2, artSize);
            float textX = getX() + textOffset;
            float textWidth = Math.max(8, width - textOffset - 10);
            if (previousTitle != null) {
                Skia.drawText(Skia.getLimitText(previousTitle, HUDTokens.title(), textWidth),
                    textX, getY() + 10 - 3 * trackReveal, faded(colors().text(), 1 - trackReveal), HUDTokens.title());
                Skia.drawText(Skia.getLimitText(previousArtist, HUDTokens.label(), textWidth),
                    textX, getY() + 26 - 3 * trackReveal, faded(colors().secondaryText(), 1 - trackReveal), HUDTokens.label());
            }
            Skia.drawText(Skia.getLimitText(displayedTitle, HUDTokens.title(), textWidth),
                textX, getY() + 10 + 3 * (1 - trackReveal), faded(colors().text(), trackReveal), HUDTokens.title());
            Skia.drawText(Skia.getLimitText(displayedArtist, HUDTokens.label(), textWidth),
                textX, getY() + 26 + 3 * (1 - trackReveal), faded(colors().secondaryText(), trackReveal), HUDTokens.label());
            float end = manager.getEndTime();
            float targetProgress = end > 0 ? Math.max(0, Math.min(1, manager.getCurrentTime() / end)) : 0;
            float progress = progressMotion.update(targetProgress, dt, reducedMotion());
            float progressY = getY() + baseHeight - 12;
            Skia.drawRoundedRect(textX, progressY, textWidth, 4, 2, colors().track());
            if (progress > 0) {
                Skia.drawRoundedRect(textX, progressY, textWidth * progress, 4, 2, colors().accent());
                Skia.drawCircle(textX + textWidth * progress, progressY + 2, 2.5f, colors().accent());
            }
            if (lyricHeight > 0.5f) {
                Skia.drawRoundedRect(getX() + 8, getY() + baseHeight, width - 16,
                    0.75f, 0.375f, colors().outline());
                Skia.drawFullCenteredText(Icon.MUSIC_NOTE, getX() + 14, getY() + baseHeight + 9,
                    colors().accent(), HUDTokens.icon());
                if (previousLyric != null)
                    Skia.drawHeightCenteredText(Skia.getLimitText(previousLyric, HUDTokens.label(), width - 34),
                        getX() + 24, getY() + baseHeight + 9 - 3 * lyricReveal,
                        faded(colors().secondaryText(), 1 - lyricReveal), HUDTokens.label());
                Skia.drawHeightCenteredText(Skia.getLimitText(displayedLyric, HUDTokens.label(), width - 34),
                    getX() + 24, getY() + baseHeight + 9 + 3 * (1 - lyricReveal),
                    faded(colors().secondaryText(), lyricReveal), HUDTokens.label());
            }
        } finally { finish(); }
    };

    private static Color faded(Color color, float fraction) {
        if (fraction >= 0.99f) return color;
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
            Math.round(color.getAlpha() * Math.max(0, Math.min(1, fraction))));
    }

    private void drawArtwork(Music music, MusicManager manager, float x, float y, float size) {
        Skia.drawRoundedRect(x, y, size, size, HUDTokens.COMPACT_RADIUS, colors().accentContainer());
        if (music == null || music.getAlbum() == null || !music.getAlbum().exists()) {
            Skia.drawFullCenteredText(Icon.MUSIC_NOTE, x + size / 2, y + size / 2,
                colors().onAccentContainer(), HUDTokens.icon());
            return;
        }
        float zoom = 1;
        if (!reducedMotion() && coverAnimationSetting.isEnabled() && manager.isPlaying()) {
            float[] spectrum = MusicPlayer.VISUALIZER;
            if (spectrum != null && spectrum.length > 0) {
                float energy = 0;
                int bands = Math.max(1, spectrum.length / 4);
                for (int i = 0; i < bands; i++) energy += Math.max(0, spectrum[i]);
                zoom += Math.min(0.04f, energy / bands * 0.001f);
            }
        }
        Skia.save();
        try {
            Skia.clip(x, y, size, size, HUDTokens.COMPACT_RADIUS);
            float extra = size * (zoom - 1) / 2;
            Skia.drawRoundedImage(music.getAlbum(), x - extra, y - extra, size * zoom, size * zoom, HUDTokens.COMPACT_RADIUS);
        } finally { Skia.restore(); }
    }

    private String safe(String value) { return value == null ? "" : value; }
    @Override public String getText() {
        MusicManager manager = PupperClient.getInstance().getMusicManager();
        return manager.getCurrentMusic() == null ? I18n.get("hud.music.idle") : safe(manager.getCurrentMusic().getTitle());
    }
    @Override public String getIcon() { return Icon.MUSIC_NOTE; }
    @Override public float getRadius() {
        return typeSetting.getOption().equals("setting.simple") ? super.getRadius() : HUDTokens.RADIUS;
    }
}
