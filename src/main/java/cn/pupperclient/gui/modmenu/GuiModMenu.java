package cn.pupperclient.gui.modmenu;

import java.util.ArrayList;
import java.util.List;

import cn.pupperclient.gui.api.SoarGui;
import cn.pupperclient.gui.api.page.SimplePage;
import cn.pupperclient.gui.modmenu.component.NavigationRail;
import cn.pupperclient.gui.modmenu.pages.*;

public class GuiModMenu extends SoarGui {

	private NavigationRail navigationRail;

	public GuiModMenu() {
		super();
	}

	@Override
	public void init() {
		components.clear();
		navigationRail = new NavigationRail(this, getX(), getY(), 104, getHeight());
		components.add(navigationRail);
		super.init();
	}

	public void navigateTo(Class<? extends SimplePage> pageClass) {
		SimplePage page = getPage(pageClass);
		if (page != null) {
			navigationRail.selectPage(page);
		}
	}

	@Override
	public void setPageSize(SimplePage p) {
		p.setX(getX() + navigationRail.getWidth());
		p.setY(getY());
		p.setWidth(getWidth() - navigationRail.getWidth());
		p.setHeight(getHeight());
	}

	@Override
	public List<SimplePage> createPages() {

		List<SimplePage> pages = new ArrayList<>();

		pages.add(new HomePage(this));
		pages.add(new ModsPage(this));
		pages.add(new MusicPage(this));
        pages.add(new CosmeticsPage(this));
		pages.add(new ProfilePage(this));
		pages.add(new SettingsPage(this));

		return pages;
	}

	@Override
	public float getX() {
		return ((float) client.getWindow().getWidth() / 2) - (getWidth() / 2);
	}

	@Override
	public float getY() {
		return ((float) client.getWindow().getHeight() / 2) - (getHeight() / 2);
	}

	@Override
	public float getWidth() {
		return 938;
	}

	@Override
	public float getHeight() {
		return 580;
	}
}
