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

package cn.pupperclient.libraries.material3;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import cn.pupperclient.libraries.material3.dynamiccolor.DynamicScheme;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.libraries.material3.quantize.QuantizerCelebi;
import cn.pupperclient.libraries.material3.scheme.SchemeTonalSpot;
import cn.pupperclient.libraries.material3.score.Score;
import cn.pupperclient.utils.render.ImageUtils;

public class Material3 {

	public static DynamicScheme getDynamicScheme(Hct hct, boolean dark, double contrast) {
		return new SchemeTonalSpot(hct, dark, contrast);
	}

	public static Hct getImageHct(BufferedImage image) {

		if (image == null) {
			return Hct.from(0, 0, 0);
		}

		Map<Integer, Integer> quantizerResult = QuantizerCelebi.quantize(ImageUtils.imageToPixels(image), 128);
		List<Integer> colors = Score.score(quantizerResult);

		return Hct.fromInt(colors.get(0));
	}

	public static Hct getImageHct(File imageFile) {

		try {
			return getImageHct(ImageIO.read(imageFile));
		} catch (IOException e) {
		}

		return Hct.from(0, 0, 0);
	}
}
