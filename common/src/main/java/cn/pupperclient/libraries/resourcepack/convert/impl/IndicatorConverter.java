package cn.pupperclient.libraries.resourcepack.convert.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import cn.pupperclient.libraries.resourcepack.convert.Converter;

public class IndicatorConverter extends Converter {

	private List<String> files = Arrays.asList("crosshair_attack_indicator_background",
			"crosshair_attack_indicator_full", "crosshair_attack_indicator_progress",
			"hotbar_attack_indicator_background", "hotbar_attack_indicator_progress");

	@Override
	public void convert(File assetsDir) {
		
		for(String s : files) {
			
			String resourcePath = "/assets/pupper/converter/indicator/" + s + ".png";
			File destPath = new File(assetsDir, "assets/minecraft/textures/gui/sprites/hud/" + s + ".png");
			
			try {
				extractResource(resourcePath, destPath);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private boolean extractResource(String resourcePath, File destinationPath) throws IOException {

		try (InputStream inputStream = IndicatorConverter.class.getResourceAsStream(resourcePath)) {

			if (inputStream == null) {
				return false;
			}

			try (FileOutputStream outputStream = new FileOutputStream(destinationPath)) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					outputStream.write(buffer, 0, bytesRead);
				}
			}

			return true;
		}
	}
}
