package cn.pupperclient.gui.modmenu.pages;

import java.util.ArrayList;
import java.util.List;

import cn.pupperclient.PupperClient;
import org.lwjgl.glfw.GLFW;

import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.gui.api.SoarGui;
import cn.pupperclient.gui.api.page.Page;
import cn.pupperclient.gui.api.page.impl.LeftRightTransition;
import cn.pupperclient.gui.api.page.impl.RightLeftTransition;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.misc.SearchUtils;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.mouse.MouseUtils;

public class SettingsPage extends Page {

	private List<Item> items = new ArrayList<>();

	public SettingsPage(SoarGui parent) {
		super(parent, "text.settings", Icon.SETTINGS, new RightLeftTransition(true));

		for (Mod m : PupperClient.getInstance().getModManager().getMods()) {
			if (m.isHidden()) {
				items.add(new Item(m));
			}
		}
	}

	@Override
	public void draw(double mouseX, double mouseY) {
		super.draw(mouseX, mouseY);

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		float offsetY = 96;

		mouseY = mouseY - scrollHelper.getValue();

		Skia.save();
		Skia.translate(0, scrollHelper.getValue());

		for (Item i : items) {

			SimpleAnimation yAnimation = i.yAnimation;
			Mod m = i.mod;
			if (!searchBar.getText().isEmpty() && !SearchUtils
					.isSimilar(m.getName() + " " + I18n.get(m.getDescription()), searchBar.getText())) {
				continue;
			}

			float itemY = y + offsetY;

			yAnimation.onTick(itemY, 14);

			itemY = yAnimation.getValue();

			Skia.drawRoundedRect(x + 32, itemY, width - 64, 68, 18, palette.getSurfaceContainerLow());
			Skia.drawFullCenteredText(m.getIcon(), x + 32 + 30, itemY + ((float) 68 / 2), palette.getOnSurface(),
					Fonts.getIcon(32));
			Skia.drawText(m.getName(), x + 32 + 52, itemY + 20, palette.getOnSurface(), Fonts.getRegular(17));
			Skia.drawText(I18n.get(m.getDescription()), x + 32 + 52, itemY + 37, palette.getOnSurfaceVariant(),
					Fonts.getRegular(14));
			Skia.drawHeightCenteredText(">", x + width - 54, itemY + ((float) 68 / 2), palette.getOnSurface(),
					Fonts.getRegular(20));

			offsetY += 68 + 18;
		}

		scrollHelper.setMaxScroll(offsetY, height);
		Skia.restore();
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {
		super.mousePressed(mouseX, mouseY, button);

		mouseY = mouseY - scrollHelper.getValue();
		searchBar.mousePressed(mouseX, mouseY, button);
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		super.mouseReleased(mouseX, mouseY, button);

		mouseY = mouseY - scrollHelper.getValue();

		searchBar.mouseReleased(mouseX, mouseY, button);

		for (Item i : items) {

			float itemY = i.yAnimation.getValue();
			Mod m = i.mod;

			if (!searchBar.getText().isEmpty() && !SearchUtils
					.isSimilar(I18n.get(m.getName()) + " " + I18n.get(m.getDescription()), searchBar.getText())) {
				continue;
			}

			if (MouseUtils.isInside(mouseX, mouseY, x + 32, itemY, width - 64, 68)
					&& button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				parent.setCurrentPage(new SettingsImplPage(parent, this.getClass(), m));
				this.setTransition(new LeftRightTransition(true));
			}
		}
	}

	@Override
	public void onClosed() {
		this.setTransition(new RightLeftTransition(true));
	}

	private class Item {

		private SimpleAnimation yAnimation = new SimpleAnimation();
		private Mod mod;

		private Item(Mod mod) {
			this.mod = mod;
		}
	}
}
