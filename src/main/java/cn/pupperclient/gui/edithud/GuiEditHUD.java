package cn.pupperclient.gui.edithud;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.screens.Screen;
import cn.pupperclient.PupperClient;
import org.lwjgl.glfw.GLFW;

import cn.pupperclient.gui.api.SimpleSoarGui;
import cn.pupperclient.gui.edithud.api.GrabOffset;
import cn.pupperclient.gui.edithud.api.HUDCore;
import cn.pupperclient.gui.edithud.api.SnappingLine;
import cn.pupperclient.management.mod.api.Position;
import cn.pupperclient.management.mod.api.hud.HUDMod;
import cn.pupperclient.management.mod.api.hud.design.HUDColors;
import cn.pupperclient.management.mod.api.hud.design.HUDTokens;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import io.github.humbleui.skija.Font;

public class GuiEditHUD extends SimpleSoarGui {

	private static final float SCALE_CHANGE_AMOUNT = 0.1F;
	private static final float DEFAULT_LINE_WIDTH = 0.5F;
	private static final float EDGE_GAP = 8;
	private static final float HEADER_WIDTH = 224;
	private static final float HEADER_HEIGHT = 42;
	private static final float CLOSE_SIZE = 32;
	private static final float FOOTER_WIDTH = 320;
	private static final float FOOTER_HEIGHT = 26;

	private final Screen prevScreen;
	private final List<HUDMod> mods;
	private final int snappingDistance;

	private Optional<ObjectObjectImmutablePair<HUDMod, GrabOffset>> selectedMod;
	private HUDMod focusedMod;
	private boolean snapping;

	public GuiEditHUD(Screen prevScreen) {
		super(CoordinateSpace.MINECRAFT_GUI);
		this.prevScreen = prevScreen;
		this.snappingDistance = 6;
		this.mods = initializeMods();
		this.selectedMod = Optional.empty();
		HUDCore.isEditing = true;
	}

	private List<HUDMod> initializeMods() {
		List<HUDMod> modsList = new ArrayList<>(PupperClient.getInstance().getModManager().getHUDMods());
		Collections.reverse(modsList);
		return modsList;
	}

	@Override
	public void draw(double mouseX, double mouseY) {
		selectedMod.ifPresent(mod -> updateModPosition(mod, mouseX, mouseY));
		HUDColors colors = PupperClient.getInstance().getModManager().getCurrentDesign().colors();
		HUDMod hoveredMod = isOverCloseButton(mouseX, mouseY) ? null : getHoveredMod(mouseX, mouseY).orElse(null);
		drawEditorChrome(mouseX, mouseY, colors);

		if (hoveredMod != null && hoveredMod != focusedMod) {
			drawFocusOutline(hoveredMod, false, colors);
		}
		if (focusedMod != null && isModInteractable(focusedMod)) {
			drawFocusOutline(focusedMod, true, colors);
			drawModLabel(focusedMod, colors);
		}

	}

	private void drawEditorChrome(double mouseX, double mouseY, HUDColors colors) {
		float screenWidth = client.getWindow().getGuiScaledWidth();
		float screenHeight = client.getWindow().getGuiScaledHeight();
		float headerWidth = Math.min(HEADER_WIDTH, Math.max(0, screenWidth - CLOSE_SIZE - EDGE_GAP * 3));
		if (headerWidth >= 120) {
			drawGlass(EDGE_GAP, EDGE_GAP, headerWidth, HEADER_HEIGHT, HUDTokens.RADIUS, colors);
			Skia.drawRoundedRect(EDGE_GAP + 7, EDGE_GAP + 8, 26, 26, 9, colors.accentContainer());
			Skia.drawFullCenteredText(Icon.SPACE_DASHBOARD, EDGE_GAP + 20, EDGE_GAP + 21,
					colors.onAccentContainer(), HUDTokens.icon());
			Skia.drawText(localized("HUD layout", "HUD 布局"), EDGE_GAP + 40, EDGE_GAP + 8,
					colors.text(), HUDTokens.title());
			String subtitle = localized("Drag to place · Esc to exit", "拖动组件调整位置 · Esc 退出");
			if (HUDTokens.label().measureText(subtitle).getWidth() > headerWidth - 47) {
				subtitle = localized("Esc to exit", "Esc 退出");
			}
			Skia.drawText(subtitle,
					EDGE_GAP + 40, EDGE_GAP + 24, colors.secondaryText(), HUDTokens.label());
		}

		float closeX = screenWidth - EDGE_GAP - CLOSE_SIZE;
		boolean closeHovered = isInside(mouseX, mouseY, closeX, EDGE_GAP, CLOSE_SIZE, CLOSE_SIZE);
		Skia.drawRoundedRect(closeX, EDGE_GAP, CLOSE_SIZE, CLOSE_SIZE, HUDTokens.COMPACT_RADIUS,
				closeHovered ? colors.raised() : colors.surface());
		Skia.drawOutline(closeX, EDGE_GAP, CLOSE_SIZE, CLOSE_SIZE, HUDTokens.COMPACT_RADIUS, 0.8f,
				withAlpha(colors.outline(), 120));
		Skia.drawFullCenteredText(Icon.CLOSE, closeX + CLOSE_SIZE / 2, EDGE_GAP + CLOSE_SIZE / 2,
				colors.text(), HUDTokens.icon());

		if (screenHeight > HEADER_HEIGHT + FOOTER_HEIGHT + EDGE_GAP * 3) {
			float footerWidth = Math.min(FOOTER_WIDTH, screenWidth - EDGE_GAP * 2);
			float footerX = (screenWidth - footerWidth) / 2;
			float footerY = screenHeight - FOOTER_HEIGHT - EDGE_GAP;
			drawGlass(footerX, footerY, footerWidth, FOOTER_HEIGHT, FOOTER_HEIGHT / 2, colors);
			String instruction = localized("Left: snap   ·   Right: free   ·   Wheel: scale   ·   Middle: reset",
					"左键吸附  ·  右键自由  ·  滚轮缩放  ·  中键重置");
			if (HUDTokens.label().measureText(instruction).getWidth() > footerWidth - 16) {
				instruction = localized("Drag to move   ·   Wheel to scale", "拖动移动  ·  滚轮缩放");
			}
			if (HUDTokens.label().measureText(instruction).getWidth() > footerWidth - 16) {
				instruction = localized("Drag · Wheel", "拖动 · 滚轮");
			}
			Skia.drawFullCenteredText(instruction, screenWidth / 2, footerY + FOOTER_HEIGHT / 2,
					colors.text(), HUDTokens.label());
		}
	}

	private void drawGlass(float x, float y, float width, float height, float radius, HUDColors colors) {
		Skia.drawRoundedRect(x, y + 2, width, height, radius, new Color(0, 0, 0, 42));
		Skia.drawRoundedRect(x, y, width, height, radius, colors.surface());
		Skia.drawOutline(x, y, width, height, radius, 0.8f, withAlpha(colors.outline(), 130));
		Skia.drawLine(x + radius, y + 1, x + width - radius, y + 1, 0.7f,
				new Color(255, 255, 255, 64));
	}

	private void drawFocusOutline(HUDMod mod, boolean focused, HUDColors colors) {
		Position pos = mod.getPosition();
		if (pos.getWidth() < 3 || pos.getHeight() < 3) return;
		float x = pos.getX() - 3;
		float y = pos.getY() - 3;
		float width = pos.getWidth() + 6;
		float height = pos.getHeight() + 6;
		float radius = Math.min(HUDTokens.RADIUS + 3, Math.min(width, height) / 2);
		Color accent = focused ? colors.accent() : colors.outline();
		Skia.drawRoundedRect(x, y, width, height, radius, withAlpha(accent, focused ? 24 : 12));
		Skia.drawOutline(x, y, width, height, radius, focused ? 2.8f : 2.2f,
				new Color(0, 0, 0, focused ? 140 : 90));
		Skia.drawOutline(x, y, width, height, radius, focused ? 1.5f : 1,
				withAlpha(accent, focused ? 255 : 210));
		if (focused) {
			float gripX = pos.getCenterX() - 10;
			Skia.drawRoundedRect(gripX, y - 3, 20, 8, 4, colors.accentContainer());
			for (int i = -1; i <= 1; i++) {
				Skia.drawCircle(pos.getCenterX() + i * 4, y + 1, 1.1f, colors.onAccentContainer());
			}
		}
	}

	private void drawModLabel(HUDMod mod, HUDColors colors) {
		Position pos = mod.getPosition();
		if (pos.getWidth() < 3 || pos.getHeight() < 3) return;
		float screenWidth = client.getWindow().getGuiScaledWidth();
		float screenHeight = client.getWindow().getGuiScaledHeight();
		Font font = HUDTokens.label();
		String suffix = "  ·  " + Math.round(pos.getScale() * 100) + "%";
		String fullName = mod.getName();
		String name = fullName;
		float maxTextWidth = Math.max(40, screenWidth - EDGE_GAP * 2 - 16);
		while (name.length() > 2 && font.measureText(name + "…" + suffix).getWidth() > maxTextWidth) {
			name = name.substring(0, name.length() - 1);
		}
		String label = name.equals(fullName) ? name + suffix : name + "…" + suffix;
		float width = Math.min(screenWidth - EDGE_GAP * 2, font.measureText(label).getWidth() + 16);
		float x = Math.max(EDGE_GAP, Math.min(pos.getX(), screenWidth - EDGE_GAP - width));
		float preferredY = pos.getY() >= 29 ? pos.getY() - 25 : pos.getBottomY() + 7;
		float y = Math.max(EDGE_GAP, Math.min(screenHeight - 22, preferredY));
		Skia.drawRoundedRect(x, y, width, 19, 9.5f, colors.raised());
		Skia.drawOutline(x, y, width, 19, 9.5f, 0.8f, withAlpha(colors.outline(), 120));
		Skia.drawCircle(x + 9, y + 9.5f, 2.5f, colors.accent());
		Skia.drawText(label, x + 15, y + 5, colors.text(), font);
	}

	private static Color withAlpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	private static String localized(String english, String chinese) {
		return I18n.getCurrentLanguage() == Language.CHINESE ? chinese : english;
	}

	private static boolean isInside(double mouseX, double mouseY, float x, float y, float width, float height) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	private boolean isOverCloseButton(double mouseX, double mouseY) {
		float screenWidth = client.getWindow().getGuiScaledWidth();
		return isInside(mouseX, mouseY, screenWidth - EDGE_GAP - CLOSE_SIZE,
				EDGE_GAP, CLOSE_SIZE, CLOSE_SIZE);
	}

	@Override
	public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (selectedMod.isEmpty() && !isOverCloseButton(mouseX, mouseY) && verticalAmount != 0) {
			handleMouseWheel(mouseX, mouseY, verticalAmount);
		}
        return true;
	}

	private void updateModPosition(ObjectObjectImmutablePair<HUDMod, GrabOffset> mod, double mouseX, double mouseY) {
		setHudPositions(mod, mouseX, mouseY, snapping);
	}

	private void handleMouseWheel(double mouseX, double mouseY, double amount) {

		double dWheel = amount;

		getHoveredMod(mouseX, mouseY).ifPresent(mod -> {
			focusedMod = mod;
			Position position = mod.getPosition();
			float newScale = calculateNewScale(position.getScale(), dWheel);
			position.setScale(newScale);
		});
	}

	private float calculateNewScale(float currentScale, double wheelDelta) {
		float change = wheelDelta > 0 ? SCALE_CHANGE_AMOUNT : -SCALE_CHANGE_AMOUNT;
		float newScale = currentScale + change;
		return Math.round(newScale * 10.0F) / 10.0F;
	}

	@Override
	public boolean onMousePressed(double mouseX, double mouseY, int button, boolean doubled) {
		float closeX = client.getWindow().getGuiScaledWidth() - EDGE_GAP - CLOSE_SIZE;
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInside(mouseX, mouseY, closeX, EDGE_GAP, CLOSE_SIZE, CLOSE_SIZE)) {
			closeEditor();
			return true;
		}
		if (isOverCloseButton(mouseX, mouseY)) return true;
		Optional<HUDMod> hovered = getHoveredMod(mouseX, mouseY);
		focusedMod = hovered.orElse(null);
		hovered.ifPresent(mod -> {
			if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
				mod.getPosition().setScale(1.0F);
				return;
			}

			GrabOffset offset = new GrabOffset((float) (mouseX - mod.getPosition().getX()),
					(float) (mouseY - mod.getPosition().getY()));
			selectedMod = Optional.of(ObjectObjectImmutablePair.of(mod, offset));

			snapping = button == 0;
		});
        return true;
	}

	@Override
	public boolean onMouseReleased(double mouseX, double mouseY, int button) {
		selectedMod = Optional.empty();
        return true;
	}

	@Override
	public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			closeEditor();
            return true;
		}
        return super.onKeyPressed(keyCode, scanCode, modifiers);
	}

	private void closeEditor() {
		HUDCore.isEditing = false;
		client.gui.setScreen(prevScreen);
	}

	private Optional<HUDMod> getHoveredMod(double mouseX, double mouseY) {
		return mods.stream().filter(mod -> isModInteractable(mod) && isInside(mod, mouseX, mouseY)).findFirst();
	}

	private boolean isModInteractable(HUDMod mod) {
		return mod.isEnabled() && !mod.isHidden() && mod.isMovable();
	}

	private boolean isInside(HUDMod mod, double mouseX, double mouseY) {
		Position pos = mod.getPosition();
		return mouseX >= pos.getX() && mouseX <= pos.getRightX() && mouseY >= pos.getY() && mouseY <= pos.getBottomY();
	}

	private void setHudPositions(ObjectObjectImmutablePair<HUDMod, GrabOffset> modPair, double mouseX, double mouseY,
			boolean snap) {
		GrabOffset offset = modPair.right();
		Position position = modPair.left().getPosition();

		float x = (float) (mouseX - offset.getX());
		float y = (float) (mouseY - offset.getY());

		if (snap) {
			x = getXSnapping(GuiEditHUD.DEFAULT_LINE_WIDTH, x, position.getWidth(), true);
			y = getYSnapping(GuiEditHUD.DEFAULT_LINE_WIDTH, y, position.getHeight(), true);
		}

		position.setPosition(x, y);
	}

	private float getXSnapping(float lineWidth, float x, float width, boolean multipleSides) {
		return getSnappingPosition(getXSnappingLines(), x, width, multipleSides, lineWidth, true);
	}

	private float getYSnapping(float lineWidth, float y, float height, boolean multipleSides) {
		return getSnappingPosition(getYSnappingLines(), y, height, multipleSides, lineWidth, false);
	}

	private float getSnappingPosition(FloatArrayList lines, float position, float size, boolean multipleSides,
			float lineWidth, boolean isHorizontal) {
		List<SnappingLine> snappingLines = findClosestSnappingLines(lines, position, size, multipleSides);

		if (snappingLines.isEmpty()) {
			return position;
		}

		snappingLines.forEach(line -> line.drawLine(lineWidth, isHorizontal));
		return snappingLines.get(0).getPosition();
	}

	private List<SnappingLine> findClosestSnappingLines(FloatArrayList lines, float position, float size,
			boolean multipleSides) {
		List<SnappingLine> snappingLines = new ArrayList<>();
		float closest = snappingDistance;

		for (Float line : lines) {
			SnappingLine snappingLine = new SnappingLine(line, position, size, multipleSides);
			float distance = snappingLine.getDistance();

			if (Math.round(distance) == Math.round(closest)) {
				snappingLines.add(snappingLine);
			} else if (distance < closest) {
				closest = distance;
				snappingLines.clear();
				snappingLines.add(snappingLine);
			}
		}

		return snappingLines;
	}

	private FloatArrayList getXSnappingLines() {
		return getSnappingLines(true);
	}

	private FloatArrayList getYSnappingLines() {
		return getSnappingLines(false);
	}

	private FloatArrayList getSnappingLines(boolean isHorizontal) {

		FloatArrayList lines = new FloatArrayList();

		lines.add(isHorizontal ? client.getWindow().getGuiScaledWidth() / 2F : client.getWindow().getGuiScaledHeight() / 2F);

		mods.stream().filter(
				mod -> isModInteractable(mod) && !selectedMod.map(pair -> pair.left().equals(mod)).orElse(false))
				.forEach(mod -> {
					Position p = mod.getPosition();
					if (isHorizontal) {
						lines.add(p.getX());
						lines.add(p.getCenterX());
						lines.add(p.getRightX());
					} else {
						lines.add(p.getY());
						lines.add(p.getCenterY());
						lines.add(p.getBottomY());
					}
				});

		return lines;
	}
}
