/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
