package cn.pupperclient.gui.edithud.api;

public class GrabOffset {

	private float offsetX, offsetY;

	public GrabOffset(float offsetX, float offsetY) {
		this.offsetX = offsetX;
		this.offsetY = offsetY;
	}

	public void setOffset(float offsetX, float offsetY) {
		this.offsetX = offsetX;
		this.offsetY = offsetY;
	}

	public float getX() {
		return offsetX;
	}

	public float getY() {
		return offsetY;
	}
}
