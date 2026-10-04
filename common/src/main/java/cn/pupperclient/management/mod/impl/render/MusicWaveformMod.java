package cn.pupperclient.management.mod.impl.render;

import cn.pupperclient.PupperClient;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.music.Music;
import cn.pupperclient.management.music.MusicManager;
import cn.pupperclient.management.music.MusicPlayer;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.color.ColorUtils;

public class MusicWaveformMod extends Mod {

	public MusicWaveformMod() {
		super("mod.musicwaveform.name", "mod.musicwaveform.description", Icon.AIRWAVE, ModCategory.RENDER);
	}

	public EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {

		MusicManager musicManager = PupperClient.getInstance().getMusicManager();
		Music m = musicManager.getCurrentMusic();

		int offsetX = 0;

		if (musicManager.isPlaying()) {

			for (int i = 0; i < MusicPlayer.SPECTRUM_BANDS; i++) {

				MusicPlayer.ANIMATIONS[i].onTick(MusicPlayer.VISUALIZER[i], 10);
				Skia.drawRect(offsetX, client.getWindow().getGuiScaledHeight() + MusicPlayer.ANIMATIONS[i].getValue(), 10,
						client.getWindow().getGuiScaledHeight(), ColorUtils.applyAlpha(m.getColor(), 80));

				offsetX += 10;
			}
		}
	};
}
