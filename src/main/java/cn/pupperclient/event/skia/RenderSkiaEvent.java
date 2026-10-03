package cn.pupperclient.event.skia;

import cn.pupperclient.event.Event;
import cn.pupperclient.ui.render.UiCanvas;

public class RenderSkiaEvent extends Event {
    private final UiCanvas canvas;

    public RenderSkiaEvent(UiCanvas canvas) {
        this.canvas = canvas;
    }

    public UiCanvas getCanvas() {
        return canvas;
    }
}
