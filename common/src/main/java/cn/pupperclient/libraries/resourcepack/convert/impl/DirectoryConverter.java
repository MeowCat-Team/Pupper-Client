package cn.pupperclient.libraries.resourcepack.convert.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import cn.pupperclient.libraries.resourcepack.convert.Converter;

import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;

public class DirectoryConverter extends Converter {

	private final List<ObjectObjectImmutablePair<String, String>> changeDirs = new ArrayList<>();
	
	public DirectoryConverter() {
		super();
		
		changeDirs.add(ObjectObjectImmutablePair.of("blocks", "block"));
		changeDirs.add(ObjectObjectImmutablePair.of("items", "item"));
	}

	@Override
	public void convert(File assetsDir) {
		
		File texturesDir = new File(assetsDir, "assets/minecraft/textures");
		
		for(ObjectObjectImmutablePair<String, String> pair : changeDirs) {
			
			String oldDir = pair.left();
			String newDir = pair.right();
			
			Path oldDirPath = new File(texturesDir, oldDir).toPath();
			Path newDirPath = new File(texturesDir, newDir).toPath();
			
			try {
				Files.move(oldDirPath, newDirPath, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignore) {
			}
		}
	}
}
