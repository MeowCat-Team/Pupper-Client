package cn.pupperclient.ui.component.impl;

import java.io.File;

import org.lwjgl.glfw.GLFW;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.api.PressAnimation;
import cn.pupperclient.ui.component.handler.impl.FileSelectorHandler;
import cn.pupperclient.ui.theme.MaterialTheme;
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
		width = 126;
		height = 32;
		this.file = file;
		this.extensions = extensions;
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		drawControlSurface(mouseX, mouseY, MaterialTheme.CONTROL_RADIUS, palette);
		Skia.save();
		Skia.clip(x, y, width, height, MaterialTheme.CONTROL_RADIUS);
		pressAnimation.draw(x, y, width, height, palette.getPrimary(), 0.12F);
		Skia.drawFullCenteredText(Icon.FOLDER_OPEN, x + 18, y + height / 2,
				palette.getPrimary(), Fonts.getIcon(18));

		String fileName = file != null ? file.getName() : "None";
		fileName = Skia.getLimitText(fileName, Fonts.getMedium(14), width - 42);

		Skia.clip(x + 32, y, width - 42, height, 0);
		Skia.drawHeightCenteredText(fileName, x + 32, y + (height / 2), palette.getOnSurface(),
				Fonts.getMedium(14));
		Skia.restore();
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
