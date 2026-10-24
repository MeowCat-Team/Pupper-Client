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

package cn.pupperclient.gui.api.page;

import cn.pupperclient.gui.api.PupperClientGui;
import cn.pupperclient.ui.component.impl.text.SearchBar;
import cn.pupperclient.utils.mouse.ScrollHelper;

public class Page extends SimplePage {

	protected ScrollHelper scrollHelper = new ScrollHelper();
	protected SearchBar searchBar;

	public Page(PupperClientGui parent, String title, String icon, GuiTransition transition) {
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
