package cn.pupperclient.gui.modmenu.pages;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import cn.pupperclient.PupperClient;
import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.gui.api.SoarGui;
import cn.pupperclient.gui.api.page.Page;
import cn.pupperclient.gui.api.page.impl.LeftRightTransition;
import cn.pupperclient.gui.api.page.impl.RightLeftTransition;
import cn.pupperclient.gui.modmenu.pages.profile.ProfileAddPage;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.profile.Profile;
import cn.pupperclient.management.profile.ProfileIcon;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.ui.theme.MaterialTheme;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.component.handler.impl.ButtonHandler;
import cn.pupperclient.ui.component.impl.IconButton;
import cn.pupperclient.utils.misc.SearchUtils;
import cn.pupperclient.utils.mouse.MouseUtils;

public class ProfilePage extends Page {

	private final List<Item> items = new ArrayList<>();
	private IconButton addButton;

	public ProfilePage(SoarGui parent) {
		super(parent, "text.profile", Icon.DESCRIPTION, new RightLeftTransition(true));
		addButton = new IconButton(Icon.ADD, 0, 0, IconButton.Size.LARGE, IconButton.Style.SECONDARY);
	}

	@Override
	public void init() {
		super.init();
		
		items.clear();
		
		for (Profile p : PupperClient.getInstance().getProfileManager().getProfiles()) {
			items.add(new Item(p));
		}

		for (Item i : items) {
			i.xAnimation.setFirstTick(true);
			i.yAnimation.setFirstTick(true);
		}

		addButton = new IconButton(Icon.ADD, x + width - addButton.getWidth() - 20,
				y + height - addButton.getHeight() - 20, IconButton.Size.LARGE, IconButton.Style.SECONDARY);
		addButton.setHandler(new ButtonHandler() {
			@Override
			public void onAction() {
				parent.setCurrentPage(new ProfileAddPage(parent, ProfilePage.this.getClass()));
				ProfilePage.this.setTransition(new LeftRightTransition(true));
			}
		});
	}

	@Override
	public void draw(double mouseX, double mouseY) {
		super.draw(mouseX, mouseY);

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		mouseY = mouseY - scrollHelper.getValue();

		int index = 0;
		float offsetX = 26;
		float offsetY = 0;

		Skia.save();
		Skia.clip(x, y + 88, width, height - 88, 0);
		Skia.translate(0, scrollHelper.getValue());

		for (Item i : items) {

			Profile p = i.profile;
			SimpleAnimation xAnimation = i.xAnimation;
			SimpleAnimation yAnimation = i.yAnimation;
			Object icon = p.getIcon();

			float itemX = x + offsetX;
			float itemY = y + 96 + offsetY;

			if (!searchBar.getText().isEmpty()
					&& !SearchUtils.isSimilar(p.getName() + " " + p.getAuthor(), searchBar.getText())) {
				continue;
			}

			xAnimation.onTick(itemX, 14);
			yAnimation.onTick(itemY, 14);

			itemX = xAnimation.getValue();
			itemY = yAnimation.getValue();

			MaterialTheme.card(itemX, itemY, 245, 88, MaterialTheme.CARD_RADIUS, palette);
			
			if (icon instanceof ProfileIcon) {
				Skia.drawRoundedImage(((ProfileIcon) icon).getIconPath(), itemX + 8, itemY + 8, 72, 72, 12);
			} else if (icon instanceof File) {
				Skia.drawRoundedImage(((File) icon), itemX + 8, itemY + 8, 72, 72, 12);
			} else {
				Skia.drawRoundedRect(itemX + 8, itemY + 8, 72, 72, MaterialTheme.CONTROL_RADIUS, MaterialTheme.surface(palette.getSecondaryContainer()));
			}
			
			Skia.drawText(Skia.getLimitText(p.getName(), Fonts.getMedium(18), 144), itemX + 86, itemY + 20,
					palette.getOnSurface(), Fonts.getMedium(18));
			Skia.drawText(Skia.getLimitText(p.getAuthor(), Fonts.getRegular(12), 144), itemX + 86, itemY + 47,
					palette.getOnSurfaceVariant(), Fonts.getRegular(12));

			index++;
			offsetX += 26 + 245;

			if (index % 3 == 0) {
				offsetX = 26;
				offsetY += 22 + 88;
			}
		}
		
		scrollHelper.setMaxScroll(88, 22, index, 3, height - 96);

		Skia.restore();
		addButton.draw(mouseX, mouseY + scrollHelper.getValue());
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {
		super.mousePressed(mouseX, mouseY, button);
		addButton.mousePressed(mouseX, mouseY, button);
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		super.mouseReleased(mouseX, mouseY, button);
		addButton.mouseReleased(mouseX, mouseY, button);
		if (MouseUtils.isInside(mouseX, mouseY, addButton.getX(), addButton.getY(), addButton.getWidth(), addButton.getHeight())
				|| !MouseUtils.isInside(mouseX, mouseY, x, y + 88, width, height - 88)) return;
		mouseY -= scrollHelper.getValue();
		
		for (Item i : items) {
			if (!searchBar.getText().isEmpty()
					&& !SearchUtils.isSimilar(i.profile.getName() + " " + i.profile.getAuthor(), searchBar.getText())) continue;
			
			float itemX = i.xAnimation.getValue();
			float itemY = i.yAnimation.getValue();
			
			if(MouseUtils.isInside(mouseX, mouseY, itemX, itemY, 245, 88) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				PupperClient.getInstance().getProfileManager().load(i.profile);
			}
		}
	}

	@Override
	public void onClosed() {
		this.setTransition(new RightLeftTransition(true));
	}

	private class Item {

		private final Profile profile;
		private final SimpleAnimation xAnimation = new SimpleAnimation();
		private final SimpleAnimation yAnimation = new SimpleAnimation();

		private Item(Profile profile) {
			this.profile = profile;
		}
	}
}
