package cn.pupperclient.libraries.resourcepack.convert.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import cn.pupperclient.libraries.resourcepack.convert.Converter;

import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;

public class NameConverter extends Converter {

	private final List<ObjectObjectImmutablePair<String, String>> blocks = new ArrayList<>();
	private final List<ObjectObjectImmutablePair<String, String>> items = new ArrayList<>();

	public NameConverter() {
		super();
	}

	@Override
	public void convert(File assetsDir) {

		File texturesDir = new File(assetsDir, "assets/minecraft/textures");
		
		load("blocks", blocks);
		load("items", items);

		for (ObjectObjectImmutablePair<String, String> pair : blocks) {

			File blockDir = new File(texturesDir, "block");
			Path oldPath = new File(blockDir, pair.left() + ".png").toPath();
			Path newPath = new File(pair.right() + ".png").toPath();
			Path oldMetaPath = new File(blockDir, pair.left() + ".png.mcmeta").toPath();
			Path newMetaPath = new File(pair.right() + ".png.mcmeta").toPath();

			try {
				Files.move(oldPath, oldPath.resolveSibling(newPath), StandardCopyOption.REPLACE_EXISTING);
				Files.move(oldMetaPath, oldMetaPath.resolveSibling(newMetaPath), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignore) {
			}
		}

		for (ObjectObjectImmutablePair<String, String> pair : items) {

			File itemDir = new File(texturesDir, "item");
			Path oldPath = new File(itemDir, pair.left() + ".png").toPath();
			Path newPath = new File(pair.right() + ".png").toPath();

			try {
				Files.move(oldPath, oldPath.resolveSibling(newPath), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignore) {
			}
		}
	}

	private void load(String name, List<ObjectObjectImmutablePair<String, String>> pairs) {

		try (InputStream is = NameConverter.class.getResourceAsStream("/assets/pupper/converter/" + name + ".txt")) {
			if (is == null) {
				return;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.trim().isEmpty() || !line.contains(":")) {
						continue;
					}
					String[] tokens = line.split(":", 2);
					if (tokens.length == 2) {
						pairs.add(ObjectObjectImmutablePair.of(tokens[0].trim(), tokens[1].trim()));
					}
				}
			}
		} catch (IOException e) {
		}
	}
}
