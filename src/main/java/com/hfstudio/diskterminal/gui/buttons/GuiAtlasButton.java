package com.hfstudio.diskterminal.gui.buttons;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import com.hfstudio.diskterminal.gui.GuiConstants;

/**
 * Base class for all buttons that use atlas.png for background rendering.
 * <p>
 * Provides shared logic for:
 * <ul>
 * <li>Visibility check and early return</li>
 * <li>Hover detection from mouse coordinates</li>
 * <li>Binding the atlas texture and setting up blend state</li>
 * <li>Drawing the background from the atlas at the UV returned by subclasses</li>
 * </ul>
 *
 * Subclasses implement:
 * <ul>
 * <li>{@link #getBackgroundTexX()}: atlas U for background (may depend on state)</li>
 * <li>{@link #getBackgroundTexY()}: atlas V for background (typically offset by size when hovered)</li>
 * <li>{@link #drawForeground(Minecraft)}: any overlay on top of the background</li>
 * <li>{@link #getTooltip()}: tooltip lines for the button</li>
 * </ul>
 */
public abstract class GuiAtlasButton extends GuiButton {

    protected GuiAtlasButton(int buttonId, int x, int y, int size) {
        super(buttonId, x, y, size, size, "");
    }

    /**
     * Get the texture X coordinate in the atlas for the background.
     * Called every frame, may depend on button state.
     */
    protected abstract int getBackgroundTexX();

    /**
     * Get the texture Y coordinate in the atlas for the background.
     * Called every frame, typically returns baseY + (hovered ? size : 0).
     */
    protected abstract int getBackgroundTexY();

    /**
     * Draw any content on top of the atlas background.
     * Called after the background is drawn with the atlas still bound.
     * Default implementation does nothing.
     */
    protected void drawForeground(Minecraft mc) {
        // Override in subclasses for icons, text, state indicators, etc.
    }

    /**
     * Get tooltip lines for this button.
     */
    public abstract List<String> getTooltip();

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) return;

        this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition
            && mouseX < this.xPosition + this.width
            && mouseY < this.yPosition + this.height;

        GuiConstants.drawAtlasSprite(
            this.xPosition,
            this.yPosition,
            getBackgroundTexX(),
            getBackgroundTexY(),
            this.width,
            this.height);

        drawForeground(mc);
    }

    /**
     * Whether the mouse was over this button at the last draw (mirrors 1.12 GuiButton#isMouseOver).
     */
    public boolean isMouseOver() {
        return this.func_146115_a();
    }
}
