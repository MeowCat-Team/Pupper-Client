package cn.pupperclient.libraries.resourcepack.convert.impl;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import javax.imageio.ImageIO;

import cn.pupperclient.libraries.resourcepack.convert.Converter;

public class ImageFormatConverter extends Converter {

	 private static final String[] types = new String[]{"jpg", "jpeg", "raw", "ico", "bmp"};
	 
	@Override
	public void convert(File assetsDir) {
		
		try {
			
	        Path texturesPath = new File(assetsDir, "assets/minecraft/textures").toPath();
	        
	        for (String type : types) {
	            findImage(texturesPath, type);
	        }
		} catch(Exception e) {}
	}

    protected void findImage(Path path, String type) throws IOException {
    	
        File directory = path.toFile();
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.isDirectory()) {
                findImage(file.toPath(), type);
            } else if (file.getName().endsWith(type)) {
                remapFile(file, type);
            }
        }
    }

    protected void remapFile(File file, String oldFormat) throws IOException {
        BufferedImage image = ImageIO.read(file);
        ImageIO.write(image, "png", new File(file.getAbsolutePath().replaceAll(oldFormat, "png")));
        if (file.exists()) {
            file.delete();
        }
    }
}
