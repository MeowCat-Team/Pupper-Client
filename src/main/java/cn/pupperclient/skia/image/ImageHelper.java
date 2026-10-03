package cn.pupperclient.skia.image;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import cn.pupperclient.skia.utils.SkiaUtils;

import io.github.humbleui.skija.Image;

public class ImageHelper {

	private Map<String, Image> images = new HashMap<>();
	public boolean load(Identifier identifier) {
		
        String key = identifier.toString();
		if (!images.containsKey(key)) {
			ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
			Resource resource;
			try {
				resource = resourceManager.getResourceOrThrow(identifier);
				try (InputStream inputStream = resource.open()) {

					byte[] imageData = inputStream.readAllBytes();
					Image image = Image.makeDeferredFromEncodedBytes(imageData);
                    images.put(key, image);
					return true;
				} catch (IOException e) {
					cn.pupperclient.PupperLogger.error("ImageHelper", "Failed to read identifier bytes", e);
				}
			} catch (FileNotFoundException e) {
				cn.pupperclient.PupperLogger.error("ImageHelper", "Identifier resource not found", e);
			}
		}
		return true;
	}

	public boolean load(String filePath) {
		if (!images.containsKey(filePath)) {
			Optional<byte[]> encodedBytes = SkiaUtils.convertToBytes(filePath);
			if (encodedBytes.isPresent()) {
				images.put(filePath, Image.makeDeferredFromEncodedBytes(encodedBytes.get()));
				return true;
			} else {
				return false;
			}
		}
		return true;
	}

	public boolean load(File file) {

		if (!images.containsKey(file.getName())) {

			try {
				byte[] encoded = org.apache.commons.io.IOUtils.toByteArray(new FileInputStream(file));
				images.put(file.getName(), Image.makeDeferredFromEncodedBytes(encoded));
				return true;
			} catch (IOException e) {
				cn.pupperclient.PupperLogger.error("ImageHelper", "Failed to load image from file: " + file.getName(), e);
				return false;
			}
		}

		return true;
	}

	public Image get(String path) {

		if (images.containsKey(path)) {
			return images.get(path);
		}

		return null;
	}

	public void clear() {
		images.values().forEach(Image::close);
		images.clear();
	}

}
