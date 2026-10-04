package cn.pupperclient.utils.mouse;

public class MouseUtils {

	public static boolean isInside(double mouseX, double mouseY, double x, double y, double width, double height) {
		return (mouseX > x && mouseX < (x + width)) && (mouseY > y && mouseY < (y + height));
	}

    public static boolean isHovered(float x, float y, float width, float height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
