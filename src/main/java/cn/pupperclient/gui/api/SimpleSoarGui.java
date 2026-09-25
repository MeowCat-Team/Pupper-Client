package cn.pupperclient.gui.api;

import cn.pupperclient.skia.Skia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Base class for all PupperClient GUIs.
 * Directly extends Minecraft's Screen to ensure better compatibility and standard lifecycle.
 */
public abstract class SimpleSoarGui extends Screen {
    
    protected final Minecraft client = Minecraft.getInstance();

    protected SimpleSoarGui() {
        super(Component.empty());
    }

    /**
     * Custom draw logic using Skia.
     */
    public abstract void draw(double mouseX, double mouseY);

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    /**
     * Called directly by the Skia render bridge for the active screen.
     * Coordinates are always Minecraft GUI-scaled coordinates.
     */
    public final void renderSkia(double mouseX, double mouseY) {
        Skia.save();
        draw(mouseX, mouseY);
        Skia.restore();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return onMousePressed(click.x(), click.y(), click.button(), doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        return onMouseReleased(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return onKeyPressed(event.key(), event.scancode(), event.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return onCharTyped(input.codepoint());
    }

    // Abstract or hook methods for subclasses to implement without overriding Screen methods directly
    
    public boolean onMousePressed(double mouseX, double mouseY, int button, boolean doubled) { return false; }
    public boolean onMouseReleased(double mouseX, double mouseY, int button) { return false; }
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) { return false; }
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) { return super.keyPressed(new KeyEvent(keyCode, scanCode, modifiers)); }
    public boolean onCharTyped(int chr) { return super.charTyped(new CharacterEvent(chr)); }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
