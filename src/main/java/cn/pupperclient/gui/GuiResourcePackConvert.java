package cn.pupperclient.gui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import cn.pupperclient.PupperClient;
import cn.pupperclient.gui.api.SimpleSoarGui;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cn.pupperclient.libraries.resourcepack.ResourcePackConverter;
import cn.pupperclient.utils.misc.JsonUtils;
import cn.pupperclient.utils.thread.Multithreading;
import cn.pupperclient.utils.file.FileLocation;

import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import net.minecraft.client.gui.screens.Screen;

public class GuiResourcePackConvert extends SimpleSoarGui {

	private volatile String progress = "Converting...";
	private final Screen prevScreen;
	private boolean conversionStarted;

	public GuiResourcePackConvert(Screen prevScreen) {
		super();
		this.prevScreen = prevScreen;
	}

	@Override
	public void init() {
		super.init();
		if (conversionStarted) return;
		conversionStarted = true;
		Multithreading.runAsync(() -> {
			ResourcePackConverter converter = createConverter();
            try {
                converter.run();
            } catch (Exception e) {
                PupperClient.LOGGER.error("converter error: {}", e.getMessage());
            }
            client.execute(() -> client.gui.setScreen(prevScreen));
        });
	}

	@Override
	public void draw(double mouseX, double mouseY) {
		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();
		float panelWidth = 520;
		float panelHeight = 228;
		float scale = Math.min(1, Math.min(Math.max(1, client.getWindow().getWidth() - 32) / panelWidth,
			Math.max(1, client.getWindow().getHeight() - 32) / panelHeight));
		Skia.save();
		Skia.translate((client.getWindow().getWidth() - panelWidth * scale) / 2,
			(client.getWindow().getHeight() - panelHeight * scale) / 2);
		Skia.scale(scale);
		MaterialTheme.glassPanel(0, 0, panelWidth, panelHeight, MaterialTheme.SURFACE_RADIUS, palette);
		Skia.drawCircle(56, 56, 24, MaterialTheme.surface(palette.getPrimaryContainer()));
		Skia.drawFullCenteredText(Icon.INVENTORY_2, 56, 56, palette.getPrimary(), Fonts.getIcon(26));
		boolean chinese = I18n.getCurrentLanguage() == Language.CHINESE;
		Skia.drawText(chinese ? "正在转换资源包" : "Converting resource packs", 96, 41,
			palette.getOnSurface(), Fonts.getMedium(24));
		Skia.drawText(Skia.getLimitText(progress, Fonts.getRegular(15), panelWidth - 64), 32, 111,
			palette.getOnSurfaceVariant(), Fonts.getRegular(15));
		Skia.drawRoundedRect(32, 150, panelWidth - 64, 6, 3, MaterialTheme.surface(palette.getSecondaryContainer()));
		float phase = (System.nanoTime() % 1_600_000_000L) / 1_600_000_000F;
		float travel = (float) ((1 - Math.cos(phase * Math.PI * 2)) / 2);
		Skia.drawRoundedRect(32 + travel * (panelWidth - 160), 150, 96, 6, 3, palette.getPrimary());
		Skia.drawText(chinese ? "完成后自动返回" : "Returning automatically when ready", 32, 183,
			palette.getOnSurfaceVariant(), Fonts.getRegular(13));
		Skia.restore();
	}

	private ResourcePackConverter createConverter() {

		List<ObjectObjectImmutablePair<File, File>> packs = new ArrayList<>();
		File cacheDir = new File(FileLocation.CACHE_DIR, "resourcepack");

		try {
			Files.createDirectories(cacheDir.toPath());
		} catch (IOException e) {
			PupperClient.LOGGER.error("Failed to create cache directory", e);
		}

		for(File f : detectPacks()) {

			try {

				File targetFile = new File(cacheDir, f.getName());
				File packDir = new File(client.gameDirectory, "resourcepacks");
				File outputFile = new File(packDir, f.getName());

				Files.move(f.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

				packs.add(ObjectObjectImmutablePair.of(targetFile, outputFile));
			} catch (Exception e) {
				PupperClient.LOGGER.error("Failed to move resource pack", e);
			}
		}

		return new ResourcePackConverter(packs, cacheDir, progress -> {
			this.progress = progress.toString();
		});
	}

	private List<File> detectPacks() {

		List<File> packs = getOldResourcePacks();
		List<File> convertPacks = new ArrayList<>();

		for (File f : packs) {

			try (FileInputStream fis = new FileInputStream(f); ZipInputStream zipIn = new ZipInputStream(fis)) {

				ZipEntry entry;
				while ((entry = zipIn.getNextEntry()) != null) {
					if (!entry.isDirectory()) {
						if (entry.getName().equals("pack.mcmeta")) {

							JsonObject jsonObject = readJsonFromZip(zipIn);
							JsonObject packJsonObject = JsonUtils.getObjectProperty(jsonObject, "pack");

							if (packJsonObject != null) {

								int version = JsonUtils.getIntProperty(packJsonObject, "pack_format", -1);
								boolean convert = JsonUtils.getBooleanProperty(packJsonObject, "convert",
										false);

								if (version == 1 || (version != ResourcePackConverter.MC_VERSION && convert)) {
									convertPacks.add(f);
								}
							}
						}
					}
					zipIn.closeEntry();
				}
			} catch (IOException e) {
				PupperClient.LOGGER.error("Failed to detect resource packs", e);
			}
		}

		return convertPacks;
	}

	private List<File> getOldResourcePacks() {

		List<File> files = new ArrayList<>();
		File packDir = new File(client.gameDirectory, "resourcepacks");

		File[] packFiles = packDir.listFiles();
        if (packFiles != null) {
            for (File f : packFiles) {
                if (f.getName().endsWith(".zip")) {
                    files.add(f);
                }
            }
        }

		return files;
	}

	private static JsonObject readJsonFromZip(ZipInputStream zipIn) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int len;
		while ((len = zipIn.read(buffer)) > 0) {
			baos.write(buffer, 0, len);
		}
		String jsonString = baos.toString(StandardCharsets.UTF_8);
		return JsonParser.parseString(jsonString).getAsJsonObject();
	}
}
