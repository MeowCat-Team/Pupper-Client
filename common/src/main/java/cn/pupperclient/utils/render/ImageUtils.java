package cn.pupperclient.utils.render;

import java.awt.Color;
import java.awt.image.BufferedImage;

public class ImageUtils {

	public static Color calculateAverageColor(BufferedImage image) {
		long totalRed = 0;
		long totalGreen = 0;
		long totalBlue = 0;
		int width = image.getWidth();
		int height = image.getHeight();
		int totalPixels = width * height;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				Color pixelColor = new Color(image.getRGB(x, y));
				totalRed += pixelColor.getRed();
				totalGreen += pixelColor.getGreen();
				totalBlue += pixelColor.getBlue();
			}
		}

		int averageRed = (int) (totalRed / totalPixels);
		int averageGreen = (int) (totalGreen / totalPixels);
		int averageBlue = (int) (totalBlue / totalPixels);

		return new Color(averageRed, averageGreen, averageBlue);
	}

	public static int[] imageToPixels(BufferedImage image) {

		int width = image.getWidth();
		int height = image.getHeight();

		return image.getRGB(0, 0, width, height, null, 0, width);
	}
}
