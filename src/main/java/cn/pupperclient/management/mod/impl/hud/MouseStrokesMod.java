package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.management.mod.api.hud.design.HUDMotion;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.event.client.PlayerDirectionChangeEvent;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import net.minecraft.util.Mth;

public class MouseStrokesMod extends HUDMod {

	private float mouseX, mouseY, lastMouseX, lastMouseY;
	private final HUDMotion motion = new HUDMotion();
	private final HUDMotion.Spring xAnimation = new HUDMotion.Spring(0);
	private final HUDMotion.Spring yAnimation = new HUDMotion.Spring(0);

	public MouseStrokesMod() {
		super("mod.mousestrokes.name", "mod.mousestrokes.description", Icon.TOUCHPAD_MOUSE);
	}

	public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = event -> {

		float calculatedMouseX = lastMouseX + (mouseX - lastMouseX);
		float calculatedMouseY = lastMouseY + (mouseY - lastMouseY);

		float dt = motion.deltaSeconds();
		float x = xAnimation.update(calculatedMouseX, dt, reducedMotion());
		float y = yAnimation.update(calculatedMouseY, dt, reducedMotion());

		this.begin();
		this.drawBackground(getX(), getY(), 58, 58);
		Skia.drawRoundedRect(getX() + 12, getY() + 28.5f, 34, 1, 0.5f, colors().outline());
		Skia.drawRoundedRect(getX() + 28.5f, getY() + 12, 1, 34, 0.5f, colors().outline());
		Skia.drawCircle(getX() + x + 29, getY() + y + 29, 4.5F, colors().accent());
		this.finish();
		position.setSize(58, 58);
	};

	public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		mouseX *= 0.75f;
		mouseY *= 0.75f;
	};

	public final EventBus.EventListener<PlayerDirectionChangeEvent> onPlayerDirectionChange = event -> {
		mouseX += (event.getYaw() - event.getPrevYaw()) / 7F;
		mouseY += (event.getPitch() - event.getPrevPitch()) / 7F;
		mouseX = Mth.clamp(mouseX, -20, 20);
		mouseY = Mth.clamp(mouseY, -20, 20);
	};

	@Override
	public float getRadius() {
		return 14;
	}
}
