package cn.pupperclient.libraries.resourcepack;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import cn.pupperclient.libraries.resourcepack.convert.Converter;
import cn.pupperclient.libraries.resourcepack.convert.impl.ArmorModelConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.ChestModelConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.CompassConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.CreativeTabsConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.DirectoryConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.EnchantConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.ImageFormatConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.IndicatorConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.NameConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.OffHandConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.PackMetaConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.ParticleSizeChangeConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.SlidersConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.WaterConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.slicer.impl.V1_14_SliceConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.slicer.impl.V1_20_2_SliceConverter;
import cn.pupperclient.libraries.resourcepack.convert.impl.slicer.impl.V1_20_5_SliceConverter;
import cn.pupperclient.utils.file.FileUtils;

import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;

public class ResourcePackConverter {

	public static final int MC_VERSION = 46;

	private List<Converter> converters = new ArrayList<>();

	private final List<ObjectObjectImmutablePair<File, File>> files;
	private final File tempDir;
	private Consumer<ProgressInfo> progressCallback;

	public ResourcePackConverter(List<ObjectObjectImmutablePair<File, File>> files, File tempDir) {
		this(files, tempDir, null);
	}

	public ResourcePackConverter(List<ObjectObjectImmutablePair<File, File>> files, File tempDir,
			Consumer<ProgressInfo> progressCallback) {
		this.files = files;
		this.tempDir = tempDir;
		this.progressCallback = progressCallback != null ? progressCallback : (progress -> {
		});

		this.converters.add(new ImageFormatConverter());
		this.converters.add(new DirectoryConverter());
		this.converters.add(new NameConverter());
		this.converters.add(new ArmorModelConverter());
		this.converters.add(new ParticleSizeChangeConverter());
		this.converters.add(new ChestModelConverter());
		this.converters.add(new OffHandConverter());
		this.converters.add(new CompassConverter());
		this.converters.add(new CreativeTabsConverter());
		this.converters.add(new WaterConverter());
		this.converters.add(new SlidersConverter());
		this.converters.add(new EnchantConverter());
		this.converters.add(new V1_14_SliceConverter());
		this.converters.add(new V1_20_2_SliceConverter());
		this.converters.add(new V1_20_5_SliceConverter());
		this.converters.add(new IndicatorConverter());
		this.converters.add(new PackMetaConverter());
	}

	public void setProgressCallback(Consumer<ProgressInfo> progressCallback) {
		this.progressCallback = progressCallback != null ? progressCallback : (progress -> {
		});
	}

	public void run() throws Exception {

		int totalSteps = 0;

		for (ObjectObjectImmutablePair<File, File> pair : files) {
			if (pair.left().exists()) {
				totalSteps += 2 + converters.size();
			}
		}

		totalSteps += 1;

		int currentStep = 0;
		updateProgress(currentStep, totalSteps, "Starting conversion process...", null);

		for (int i = 0; i < files.size(); i++) {
			ObjectObjectImmutablePair<File, File> pair = files.get(i);

			File inputFile = pair.left();
			File outputFile = pair.right();
			File newTempDir = new File(tempDir, inputFile.getName().replace(".zip", ""));
			
			Files.createDirectories(newTempDir.toPath());

			if (!inputFile.exists()) {
				updateProgress(currentStep, totalSteps,
						String.format("Skipping file %d of %d: %s (file does not exist)", i + 1, files.size(),
								inputFile.getName()),
						inputFile);
				continue;
			}

			updateProgress(currentStep, totalSteps,
					String.format("Processing file %d of %d: %s", i + 1, files.size(), inputFile.getName()), inputFile);

			try {
				updateProgress(++currentStep, totalSteps, String.format("Extracting %s...", inputFile.getName()),
						inputFile);
				FileUtils.unzip(inputFile, newTempDir);

				if (newTempDir.exists()) {

					for (int j = 0; j < converters.size(); j++) {
						Converter converter = converters.get(j);
						updateProgress(++currentStep, totalSteps,
								String.format("Applying converter %d of %d", j + 1, converters.size()), inputFile);
						converter.convert(newTempDir);
					}

					updateProgress(++currentStep, totalSteps,
							String.format("Creating output file: %s", outputFile.getName()), outputFile);
					FileUtils.zip(newTempDir, outputFile);
				} else {
					updateProgress(currentStep, totalSteps, String
							.format("Skipping processing of %s (temp directory creation failed)", inputFile.getName()),
							inputFile);
					currentStep += 1 + converters.size();
				}
			} catch (Exception e) {
				updateProgress(currentStep, totalSteps,
						String.format("Error processing %s: %s", inputFile.getName(), e.getMessage()), inputFile);
				currentStep += 2 + converters.size() - (currentStep % (2 + converters.size()));
			}
		}

		updateProgress(++currentStep, totalSteps, "Cleaning up temporary files...", null);
		FileUtils.deleteDirectory(tempDir.toPath());
		
		updateProgress(totalSteps, totalSteps, "Conversion process completed", null);
	}

	private void updateProgress(int current, int total, String message, File currentFile) {
		float percentage = total > 0 ? (float) current / total * 100 : 0;
		progressCallback.accept(new ProgressInfo(current, total, percentage, message, currentFile));
	}

	public static class ProgressInfo {
		private final int currentStep;
		private final int totalSteps;
		private final float percentage;
		private final String message;
		private final File currentFile;

		public ProgressInfo(int currentStep, int totalSteps, float percentage, String message, File currentFile) {
			this.currentStep = currentStep;
			this.totalSteps = totalSteps;
			this.percentage = percentage;
			this.message = message;
			this.currentFile = currentFile;
		}

		public int getCurrentStep() {
			return currentStep;
		}

		public int getTotalSteps() {
			return totalSteps;
		}

		public float getPercentage() {
			return percentage;
		}

		public String getMessage() {
			return message;
		}

		public File getCurrentFile() {
			return currentFile;
		}

		@Override
		public String toString() {
			return String.format("%.1f%% (%d/%d): %s", percentage, currentStep, totalSteps, message);
		}
	}
}
