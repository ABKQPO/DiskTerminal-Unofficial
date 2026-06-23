package com.hfstudio.diskterminal.gui.widget;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

/**
 * Base interface for all widgets in the Cell Terminal GUI.
 * <p>
 * A widget is a self-contained visual component that handles its own rendering, click handling,
 * keyboard handling, hover detection, and tooltip provision. Widgets communicate upward only
 * through return values or callbacks provided at construction time.
 */
public interface IWidget {

    /**
     * Draw this widget.
     */
    void draw(int mouseX, int mouseY);

    /**
     * Handle a mouse click.
     *
     * @return true if the click was handled and should not propagate
     */
    boolean handleClick(int mouseX, int mouseY, int button);

    /**
     * Handle a key press.
     *
     * @return true if the key was handled and should not propagate
     */
    default boolean handleKey(char typedChar, int keyCode) {
        return false;
    }

    /**
     * Check if the mouse is over this widget.
     */
    boolean isHovered(int mouseX, int mouseY);

    /**
     * Get tooltip lines to display when this widget is hovered (never null).
     */
    default List<String> getTooltip(int mouseX, int mouseY) {
        return Collections.emptyList();
    }

    /**
     * Get the ItemStack under the mouse cursor, if any (null if none).
     */
    default ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        return null;
    }

    /**
     * Get the X position of this widget relative to GUI.
     */
    int getX();

    /**
     * Get the Y position of this widget relative to GUI.
     */
    int getY();

    /**
     * Get the width of this widget.
     */
    int getWidth();

    /**
     * Get the height of this widget.
     */
    int getHeight();

    /**
     * Whether this widget is visible and should be drawn.
     */
    default boolean isVisible() {
        return true;
    }
}
