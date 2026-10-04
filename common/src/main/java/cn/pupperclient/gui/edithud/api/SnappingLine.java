package cn.pupperclient.gui.edithud.api;

import java.awt.Color;
import net.minecraft.client.Minecraft;
import cn.pupperclient.PupperClient;
import cn.pupperclient.skia.Skia;

public class SnappingLine {

	private float line;
	private float distance;
	private float position;

	public SnappingLine(float line, float left, float size, boolean multipleSides) {

		this.line = line;
		float center = left + size / 2f;
		float right = left + size;
		float leftDistance = Math.abs(line - left);
		float centerDistance = Math.abs(line - center);
		float rightDistance = Math.abs(line - right);

		if (!multipleSides || leftDistance <= centerDistance && leftDistance <= rightDistance) {
			distance = leftDistance;
			position = line;
		} else if (centerDistance <= rightDistance) {
			distance = centerDistance;
			position = line - size / 2f;
		} else {
			distance = rightDistance;
			position = line - size;
		}
	}

	public void drawLine(float lineWidth, boolean isX) {

		Minecraft client = Minecraft.getInstance();
		Color accent = PupperClient.getInstance().getModManager().getCurrentDesign().colors().accent();
		Color guide = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 210);

		float pos = (float) (line - lineWidth / 2f);

		if (isX) {
			Skia.drawLine(pos, 0, pos, client.getWindow().getGuiScaledHeight(), 3, new Color(0, 0, 0, 95));
			Skia.drawLine(pos, 0, pos, client.getWindow().getGuiScaledHeight(), 1.5f, new Color(255, 255, 255, 150));
			Skia.drawLine(pos, 0, pos, client.getWindow().getGuiScaledHeight(), Math.max(lineWidth, 0.75f), guide);
		} else {
			Skia.drawLine(0, pos, client.getWindow().getGuiScaledWidth(), pos, 3, new Color(0, 0, 0, 95));
			Skia.drawLine(0, pos, client.getWindow().getGuiScaledWidth(), pos, 1.5f, new Color(255, 255, 255, 150));
			Skia.drawLine(0, pos, client.getWindow().getGuiScaledWidth(), pos, Math.max(lineWidth, 0.75f), guide);
		}
	}

	public float getPosition() {
		return position;
	}

	public float getDistance() {
		return distance;
	}
}
