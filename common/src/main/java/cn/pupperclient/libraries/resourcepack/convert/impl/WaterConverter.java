package cn.pupperclient.libraries.resourcepack.convert.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import cn.pupperclient.libraries.resourcepack.convert.Converter;
import cn.pupperclient.libraries.resourcepack.utils.ImageConverter;

public class WaterConverter extends Converter {

	public WaterConverter() {
		super();
	}

	@Override
	public void convert(File assetsDir) {

		try {

			Path blockFolder = new File(assetsDir, "assets/minecraft/textures/block").toPath();

			if (!blockFolder.toFile().exists())
				return;
			grayscale(blockFolder.resolve("water_flow.png"), 16, 1024);
			grayscale(blockFolder.resolve("water_still.png"), 16, 512);
			grayscale(blockFolder.resolve("water_overlay.png"), 16, 16);
		} catch (Exception e) {
		}
	}

	private void grayscale(Path path, int w, int h) throws IOException {
		if (!path.toFile().exists())
			return;
		ImageConverter imageConverter = new ImageConverter(w, h, path);
		if (!imageConverter.fileIsPowerOfTwo())
			return;
		imageConverter.newImage(w, h);
		imageConverter.grayscale();
		imageConverter.store();
	}
}
