package cn.pupperclient.utils.color;

import cn.pupperclient.utils.math.MathUtils;

import java.awt.Color;
import java.util.regex.Pattern;

public class ColorUtils {

	public static Color getColorFromInt(int color) {

		float r = (float) (color >> 16 & 255) / 255.0F;
		float g = (float) (color >> 8 & 255) / 255.0F;
		float b = (float) (color & 255) / 255.0F;
		float a = (float) (color >> 24 & 255) / 255.0F;

		return new Color(r, g, b, a);
	}

	public static Color blend(Color color1, Color color2, double ratio) {
		float r = (float) ratio;
		float ir = 1.0f - r;
		float[] rgb1 = new float[3];
		float[] rgb2 = new float[3];
		color1.getColorComponents(rgb1);
		color2.getColorComponents(rgb2);
        return new Color(rgb1[0] * r + rgb2[0] * ir, rgb1[1] * r + rgb2[1] * ir, rgb1[2] * r + rgb2[2] * ir);
	}

	public static String removeColorCode(String text) {
		return Pattern.compile("\\u00a7[0-9a-fklmnor]").matcher(text).replaceAll("");
	}

	public static Color applyAlpha(Color color, int alpha) {

		int red = color.getRed();
		int green = color.getGreen();
		int blue = color.getBlue();

		return new Color(red, green, blue, MathUtils.clamp(alpha, 0, 255));
	}

    public static Color applyAlpha(Color color, float alpha) {
        return applyAlpha(color, (int) (alpha * 255));
    }

    public static Color interpolate(Color start, Color end, float amount) {
        amount = MathUtils.clamp(amount, 0, 1);

        float[] startHSB = Color.RGBtoHSB(start.getRed(), start.getGreen(), start.getBlue(), null);
        float[] endHSB = Color.RGBtoHSB(end.getRed(), end.getGreen(), end.getBlue(), null);

        Color resultColor = Color.getHSBColor(
            MathUtils.interpolateFloat(startHSB[0], endHSB[0], amount),
            MathUtils.interpolateFloat(startHSB[1], endHSB[1], amount),
            MathUtils.interpolateFloat(startHSB[2], endHSB[2], amount)
        );

        return new Color(
            resultColor.getRed(),
            resultColor.getGreen(),
            resultColor.getBlue(),
            MathUtils.interpolateInt(start.getAlpha(), end.getAlpha(), amount)
        );
    }

    public static Color rainbow(int speed, int index) {
        int angle = (int) (((System.currentTimeMillis()) / speed + index) % 360);
        return Color.getHSBColor(angle / 360f, 0.6f, 1f);
    }

    public static Color oscillate(Color start, Color end, int speed, int index) {
        int angle = (int) (((System.currentTimeMillis()) / speed + index) % 360);
        float amount = (angle >= 180 ? 360 - angle : angle) / 180f;
        return interpolate(start, end, amount);
    }
}
