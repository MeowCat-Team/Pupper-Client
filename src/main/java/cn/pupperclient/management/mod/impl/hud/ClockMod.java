package cn.pupperclient.management.mod.impl.hud;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Icon;

public class ClockMod extends SimpleHUDMod{

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	
	public ClockMod() {
		super("mod.clock.name", "mod.clock.description", Icon.SCHEDULE);
	}

	public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {
		this.draw();
	};
	
	@Override
	public String getText() {
		return TIME_FORMAT.format(LocalTime.now());
	}

	@Override
	public String getIcon() {
		return Icon.SCHEDULE;
	}
}
