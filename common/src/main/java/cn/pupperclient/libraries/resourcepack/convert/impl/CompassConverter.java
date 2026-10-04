package cn.pupperclient.libraries.resourcepack.convert.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import cn.pupperclient.libraries.resourcepack.convert.Converter;
import cn.pupperclient.libraries.resourcepack.utils.ImageConverter;

public class CompassConverter extends Converter {

	private Path items;

	public CompassConverter() {
		super();
	}

	@Override
	public void convert(File assetsDir) {

		try {

			Path compassPath = new File(assetsDir, "assets/minecraft/textures/item/compass.png").toPath();
			items = compassPath.getParent();

			if (compassPath.toFile().exists()) {
				ImageConverter imageConverter = new ImageConverter(16, 512, compassPath);
				if (!imageConverter.fileIsPowerOfTwo())
					return;

				for (int i = 0; i < 32; i++) {
					int h = i * 16;
					String it = String.valueOf(i);
					if (i < 10)
						it = "0" + it;
					compass(0, h, 16, h + 16, "compass_" + it, imageConverter);
				}

				if (items.resolve("compass.png.mcmeta").toFile().exists())
					Files.delete(items.resolve("compass.png.mcmeta"));
			}
		} catch (Exception e) {
		}
	}

	private void compass(int x, int y, int x2, int y2, String name, ImageConverter imageConverter) throws IOException {
		imageConverter.newImage(16, 16);
		imageConverter.subImage(x, y, x2, y2, 0, 0);
		imageConverter.store(items.resolve(name + ".png"));
	}
}
