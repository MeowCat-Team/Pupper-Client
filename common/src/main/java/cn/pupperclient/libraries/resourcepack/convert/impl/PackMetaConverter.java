package cn.pupperclient.libraries.resourcepack.convert.impl;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import cn.pupperclient.libraries.resourcepack.ResourcePackConverter;
import cn.pupperclient.libraries.resourcepack.convert.Converter;

public class PackMetaConverter extends Converter {

	public PackMetaConverter() {
		super();
	}

	@Override
	public void convert(File assetsDir) {

		Path file = new File(assetsDir, "pack.mcmeta").toPath();
		Gson gson = new Gson();

		if (!file.toFile().exists()) {
			return;
		}

		try {
			FileReader reader = new FileReader(file.toFile());
			JsonObject json = gson.fromJson(new JsonReader(reader), JsonObject.class);

			JsonObject packObject = json.getAsJsonObject("pack");

			if (packObject == null)
				packObject = new JsonObject();
			packObject.addProperty("pack_format", ResourcePackConverter.MC_VERSION);
			json.add("pack", packObject);
			json.addProperty("convert", true);

			Files.write(file, Collections.singleton(gson.toJson(json)), Charset.forName("UTF-8"));
			reader.close();
		} catch (JsonIOException | JsonSyntaxException | IOException e) {
		}
	}

}
