package cn.pupperclient.gui.api.page;

import cn.pupperclient.gui.api.SoarGui;
import cn.pupperclient.ui.component.impl.text.SearchBar;
import cn.pupperclient.utils.mouse.ScrollHelper;

public class Page extends SimplePage {

	protected ScrollHelper scrollHelper = new ScrollHelper();
	protected SearchBar searchBar;

	public Page(SoarGui parent, String title, String icon, GuiTransition transition) {
		super(parent, title, icon, transition);
	}

	@Override
	public void init() {

		String text = "";

		if (searchBar != null) {
			text = searchBar.getText();
		}

		searchBar = new SearchBar(x + width - 260 - 32, y + 32, 260, text, () -> {
			scrollHelper.reset();
		});
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		scrollHelper.onUpdate();

		searchBar.draw(mouseX, mouseY);
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {
		searchBar.mousePressed(mouseX, mouseY, button);
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		searchBar.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void charTyped(int chr) {
		searchBar.charTyped(chr);
	}

	@Override
	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		searchBar.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scrollHelper.onScroll(verticalAmount);
	}
}
