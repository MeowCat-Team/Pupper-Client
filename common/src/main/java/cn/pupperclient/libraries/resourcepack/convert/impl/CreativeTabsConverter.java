package cn.pupperclient.libraries.resourcepack.convert.impl;

import java.io.File;
import java.nio.file.Path;

import cn.pupperclient.libraries.resourcepack.convert.Converter;
import cn.pupperclient.libraries.resourcepack.utils.ImageConverter;

public class CreativeTabsConverter extends Converter {

	private static int old_tab_width = 28;
	private static int new_tab_width = 26;
	private static int old_half = old_tab_width / 2;

	public CreativeTabsConverter() {
		super();
	}

	@Override
	public void convert(File assetsDir) {

		try {
			Path guiPath = new File(assetsDir, "assets/minecraft/textures/gui").toPath();
			if (!guiPath.toFile().exists())
				return;

			Path tabsImage = guiPath.resolve("container/creative_inventory/tabs.png");
			if (!tabsImage.toFile().exists())
				return;

			int originalWidth = 256;
			int originalHeight = 256;
			ImageConverter converter = new ImageConverter(originalWidth, originalHeight, tabsImage);
			converter.newImage(originalWidth, originalHeight);

			copy_tab(converter, 0, 0, 0, 2);
			copy_tab(converter, 1, 1, 0, 2);
			copy_tab(converter, 2, 2, 0, 2);
			copy_tab(converter, 3, 3, 0, 2);
			copy_tab(converter, 4, 4, 0, 2);
			copy_tab(converter, 5, 4, 0, 2);
			copy_tab(converter, 6, 5, 0, 2);
			copy_tab(converter, 7, 6, 0, 2);

			int scrollers_width = 24;
			int scroller_height = 15;
			int scroller_start_x = 232;
			converter.subImage(scroller_start_x, 0, scroller_start_x + scrollers_width, scroller_height,
					scroller_start_x, 0);

			converter.store();
		} catch (Exception e) {
		}
	}

	private static void copy_tab(ImageConverter converter, int index, int original_index, int left_padding,
			int right_padding) {
		int first_tab_start_x = original_index * old_tab_width;
		int first_tab_start_end_x = (original_index * old_tab_width) + old_tab_width;

		converter.subImage(first_tab_start_x, 0, first_tab_start_end_x - old_half, 160,
				(index * new_tab_width) + left_padding, 0);

		converter.subImage(first_tab_start_x + old_half, 0, first_tab_start_end_x, 160,
				((index * new_tab_width) + (old_half - right_padding)), 0);
	}
}
