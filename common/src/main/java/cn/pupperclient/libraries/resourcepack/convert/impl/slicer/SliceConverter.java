package cn.pupperclient.libraries.resourcepack.convert.impl.slicer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

import cn.pupperclient.libraries.resourcepack.api.InputFile;
import cn.pupperclient.libraries.resourcepack.convert.Converter;

public class SliceConverter extends Converter {

	private final Collection<InputFile> inputs;

	public SliceConverter(Collection<InputFile> inputs) {
		this.inputs = inputs;
	}

	@Override
	public void convert(File assetsDir) {
		try {
			process(inputs, assetsDir.toPath(), assetsDir.toPath());
		} catch (IOException e) {
		}
	}

	private static void process(final Collection<InputFile> inputs, final Path inputPath, final Path outputPath)
			throws IOException {
		for (final InputFile input : inputs) {
			input.process(inputPath, outputPath);
		}
	}
}
