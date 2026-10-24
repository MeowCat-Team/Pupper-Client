/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.pupperclient.ui.component.impl;

import java.io.File;

import org.lwjgl.glfw.GLFW;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.api.PressAnimation;
import cn.pupperclient.ui.component.handler.impl.FileSelectorHandler;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.ui.theme.MaterialControls;
import cn.pupperclient.utils.thread.Multithreading;
import cn.pupperclient.utils.file.FileDialog;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.mouse.MouseUtils;

import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;

public class FileSelector extends Component {

	private PressAnimation pressAnimation = new PressAnimation();
	private String[] extensions;
	private File file;

	public FileSelector(float x, float y, File file, String[] extensions) {
		super(x, y);
		width = MaterialControls.COMPACT_WIDTH;
		height = MaterialControls.COMPACT_HEIGHT;
		this.file = file;
		this.extensions = extensions;
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		MaterialControls.fileSelector(x, y, width, height, file != null ? file.getName() : "None",
				palette, MaterialTheme.opacity(), hoverState(mouseX, mouseY));
		Skia.save();
		try {
			Skia.clip(x, y, width, height, MaterialTheme.CONTROL_RADIUS);
			pressAnimation.draw(x, y, width, height, palette.getPrimary(), 0.12F);
		} finally {
			Skia.restore();
		}
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {
		if (MouseUtils.isInside(mouseX, mouseY, x, y, width, height) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			pressAnimation.onPressed(mouseX, mouseY, x, y);
			Multithreading.runAsync(() -> {

				ObjectObjectImmutablePair<Boolean, File> p = FileDialog.chooseFile(I18n.get("text.selectfile"), extensions);

				if (p.left()) {
					file = p.right();
					if (handler instanceof FileSelectorHandler) {
						((FileSelectorHandler) handler).onSelect(file);
					}
				}
			});
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		pressAnimation.onReleased(mouseX, mouseY, x, y);
	}
}
