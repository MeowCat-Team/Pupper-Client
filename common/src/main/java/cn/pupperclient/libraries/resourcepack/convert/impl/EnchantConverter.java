package cn.pupperclient.libraries.resourcepack.convert.impl;

import java.awt.Color;
import java.io.File;
import java.nio.file.Path;

import cn.pupperclient.libraries.resourcepack.convert.Converter;
import cn.pupperclient.libraries.resourcepack.utils.ImageConverter;

public class EnchantConverter extends Converter {

	@Override
	public void convert(File assetsDir) {
		
		try {
			
	        Path itemGlintPath = new File(assetsDir, "assets/minecraft/textures/misc/enchanted_item_glint.png").toPath();
	        
	        if (!itemGlintPath.toFile().exists()) {
	            return;
	        }

	        ImageConverter imageConverter = new ImageConverter(64, 64, itemGlintPath);
	        
	        if (imageConverter.fileIsPowerOfTwo()) {
	            imageConverter.colorizeGrayscale(new Color(128, 64, 204));
	            imageConverter.store();
	        }
		} catch(Exception e) {}
	}
}
