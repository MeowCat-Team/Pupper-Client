package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventListener;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.system.JNAWindowChecker;
import io.github.humbleui.skija.Font;

public class CloudMusicHudMod extends SimpleHUDMod {
    public CloudMusicHudMod() {
        super("mod.cloudmusic.name", "mod.cloudmusic.description", Icon.MUSIC_VIDEO);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        JNAWindowChecker.startBackgroundMonitoring();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        JNAWindowChecker.stopBackgroundMonitoring();
    }

    @EventListener
    public void onSkiaRender(RenderSkiaEvent event) {
        this.draw();
    }

    @Override
    public String getText() {
		String title = JNAWindowChecker.getCurrentWindowTitle();
		return title == null || title.isBlank() ? "Nothing is playing" : title;
    }

    @Override
    public String getIcon() {
        return Icon.MUSIC_NOTE;
    }

	@Override
	protected Font getTextFont(float size) {
		return Fonts.getGoogleSansRegular(size);
	}
}
