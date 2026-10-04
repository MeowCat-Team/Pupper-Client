package cn.pupperclient.event.client;

import cn.pupperclient.event.Event;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class RenderGameOverlayEvent extends Event {
	
	private final GuiGraphicsExtractor context;
	
	public RenderGameOverlayEvent(GuiGraphicsExtractor context) {
		this.context = context;
	}

	public GuiGraphicsExtractor getContext() {
		return context;
	}
}
