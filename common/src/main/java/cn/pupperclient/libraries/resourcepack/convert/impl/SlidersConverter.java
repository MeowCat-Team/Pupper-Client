package cn.pupperclient.libraries.resourcepack.convert.impl;

import java.io.File;
import java.nio.file.Path;

import cn.pupperclient.libraries.resourcepack.convert.Converter;
import cn.pupperclient.libraries.resourcepack.utils.ImageConverter;

public class SlidersConverter extends Converter {

	@Override
	public void convert(File assetsDir) {

		try {

			Path widgetsPath = new File(assetsDir, "assets/minecraft/textures/gui/widgets.png").toPath();

			if (!widgetsPath.toFile().exists())
				return;

			ImageConverter converter = new ImageConverter(256, 256, widgetsPath);

			int button_width = 200;
			int button_height = 20;

			converter.newImage(256, 256);

			int y = 46;
			converter.subImageSized(0, y, button_width, button_height, 0, 0);
			converter.subImageSized(0, y, button_width, button_height, 0, button_height);
			converter.subImageSized(0, (y + button_height), button_width, button_height, 0, (button_height * 2));
			converter.subImageSized(0, (y + (button_height * 2)), button_width, button_height, 0, (button_height * 3));

			converter.store(widgetsPath.resolveSibling("slider.png"));
		} catch (Exception e) {
		}
	}
}
