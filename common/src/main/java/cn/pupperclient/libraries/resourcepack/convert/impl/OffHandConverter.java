package cn.pupperclient.libraries.resourcepack.convert.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import cn.pupperclient.libraries.resourcepack.convert.Converter;
import cn.pupperclient.libraries.resourcepack.utils.ImageConverter;

public class OffHandConverter extends Converter {

	@Override
	public void convert(File assetsDir) {
		
		Path widgetsPath = new File(assetsDir, "assets/minecraft/textures/gui/widgets.png").toPath();
		
		if (!widgetsPath.toFile().exists())
			return;

		try {
			ImageConverter converter = new ImageConverter(256, 256, widgetsPath);
			
			int hotbar_end_x = 182;

			int hotbar_offhand_width = 29;
			int hotbar_offhand_height = 24;

			int hotbar_offhand_l_x = 24;
			int hotbar_offhand_r_x = 60;
			int hotbar_offhand_y = 22;

			int slice_w = 11;
			int slice_h = 22;

			converter.fillEmpty(hotbar_offhand_l_x, hotbar_offhand_y, hotbar_offhand_width, hotbar_offhand_height);
			converter.fillEmpty(hotbar_offhand_r_x, hotbar_offhand_y, hotbar_offhand_width, hotbar_offhand_height);

			converter.subImage(0, 0, slice_w, slice_h, hotbar_offhand_l_x, hotbar_offhand_y + 1);
			converter.subImage(hotbar_end_x - slice_w, 0, hotbar_end_x, slice_h, hotbar_offhand_l_x + slice_w,
					hotbar_offhand_y + 1);

			converter.subImage(0, 0, slice_w, slice_h, hotbar_offhand_r_x, hotbar_offhand_y + 1);
			converter.subImage(hotbar_end_x - slice_w, 0, hotbar_end_x, slice_h, hotbar_offhand_r_x + slice_w,
					hotbar_offhand_y + 1);

			converter.store();
		} catch (IOException e) {
		}
	}
}
