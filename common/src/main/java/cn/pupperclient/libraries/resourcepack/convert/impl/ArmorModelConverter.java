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

public class ArmorModelConverter extends Converter {

	private final List<ObjectObjectImmutablePair<String, String>> armorTextures = new ArrayList<>();
	private final List<ObjectObjectImmutablePair<String, String>> leggingTextures = new ArrayList<>();
	
	public ArmorModelConverter() {
		super();
		
		armorTextures.add(ObjectObjectImmutablePair.of("chainmail_layer_1", "chainmail"));
		armorTextures.add(ObjectObjectImmutablePair.of("diamond_layer_1", "diamond"));
		armorTextures.add(ObjectObjectImmutablePair.of("gold_layer_1", "gold"));
		armorTextures.add(ObjectObjectImmutablePair.of("iron_layer_1", "iron"));
		armorTextures.add(ObjectObjectImmutablePair.of("leather_layer_1", "leather"));
		armorTextures.add(ObjectObjectImmutablePair.of("leather_layer_1_overlay", "leather_overlay"));
		
		leggingTextures.add(ObjectObjectImmutablePair.of("chainmail_layer_2", "chainmail"));
		leggingTextures.add(ObjectObjectImmutablePair.of("diamond_layer_2", "diamond"));
		leggingTextures.add(ObjectObjectImmutablePair.of("gold_layer_2", "gold"));
		leggingTextures.add(ObjectObjectImmutablePair.of("iron_layer_2", "iron"));
		leggingTextures.add(ObjectObjectImmutablePair.of("leather_layer_2", "leather"));
		leggingTextures.add(ObjectObjectImmutablePair.of("leather_layer_2_overlay", "leather_overlay"));
	}

	@Override
	public void convert(File assetsDir) {
		
		File texturesDir = new File(assetsDir, "assets/minecraft/textures");
		
		for (ObjectObjectImmutablePair<String, String> pair : armorTextures) {
			
			File armorDir = new File(texturesDir, "entity/equipment/humanoid");
			Path oldPath = new File(texturesDir, "models/armor/" + pair.left() + ".png").toPath();
			Path newPath = new File(armorDir, pair.right() + ".png").toPath();
			
			try {
				Files.createDirectories(armorDir.toPath());
				Files.move(oldPath, newPath, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException ignore) {
			}
		}
		
		for (ObjectObjectImmutablePair<String, String> pair : leggingTextures) {
			
			File leggingDir = new File(texturesDir, "entity/equipment/humanoid_leggings");
			Path oldPath = new File(texturesDir, "models/armor/" + pair.left() + ".png").toPath();
			Path newPath = new File(leggingDir, pair.right() + ".png").toPath();
		
			try {
				Files.createDirectories(leggingDir.toPath());
				Files.move(oldPath, newPath, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException ignore) {
			}
		}
	}

}
