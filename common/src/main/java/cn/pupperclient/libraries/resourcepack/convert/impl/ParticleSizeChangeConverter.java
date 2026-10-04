package cn.pupperclient.libraries.resourcepack.convert.impl;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import cn.pupperclient.libraries.resourcepack.convert.Converter;

public class ParticleSizeChangeConverter extends Converter {

	public ParticleSizeChangeConverter() {
		super();
	}

	@Override
	public void convert(File assetsDir) {

		try {

			Path imagePath = new File(assetsDir, "assets/minecraft/textures/particle/particles.png").toPath();

			if (!imagePath.toFile().exists()) {
				return;
			}

			BufferedImage image = ImageIO.read(imagePath.toFile());

			// TODO check how higher resolution will handle this.
			if (image.getWidth() == 128 && image.getHeight() == 128) {

				BufferedImage newImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g2d = (Graphics2D) newImage.getGraphics();
				g2d.drawImage(image, 0, 0, null);

				ImageIO.write(newImage, "png", imagePath.toFile());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
