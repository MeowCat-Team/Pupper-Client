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
    public enum CoordinateSpace {
        FRAMEBUFFER,
        MINECRAFT_GUI
    }

    protected final Minecraft client = Minecraft.getInstance();
    private final CoordinateSpace coordinateSpace;

    protected SimpleSoarGui() {
        this(CoordinateSpace.FRAMEBUFFER);
    }

    protected SimpleSoarGui(CoordinateSpace coordinateSpace) {
        super(Component.empty());
        this.coordinateSpace = coordinateSpace;
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
     * The render bridge supplies Minecraft GUI coordinates; this method converts
     * them into the coordinate space selected by the screen.
     */
    public final void renderSkia(double guiMouseX, double guiMouseY) {
        Skia.save();
        draw(toScreenX(guiMouseX), toScreenY(guiMouseY));
        Skia.restore();
    }

    public final boolean usesMinecraftGuiScale() {
        return coordinateSpace == CoordinateSpace.MINECRAFT_GUI;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return onMousePressed(toScreenX(click.x()), toScreenY(click.y()), click.button(), doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        return onMouseReleased(toScreenX(click.x()), toScreenY(click.y()), click.button());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return onMouseScrolled(toScreenX(mouseX), toScreenY(mouseY), horizontalAmount, verticalAmount);
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

    private double toScreenX(double guiX) {
        if (coordinateSpace == CoordinateSpace.MINECRAFT_GUI) {
            return guiX;
        }
        int guiWidth = client.getWindow().getGuiScaledWidth();
        return guiWidth > 0 ? guiX * client.getWindow().getWidth() / guiWidth : guiX;
    }

    private double toScreenY(double guiY) {
        if (coordinateSpace == CoordinateSpace.MINECRAFT_GUI) {
            return guiY;
        }
        int guiHeight = client.getWindow().getGuiScaledHeight();
        return guiHeight > 0 ? guiY * client.getWindow().getHeight() / guiHeight : guiY;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
