package com.hfstudio.diskterminal.gui.widget.button;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

import com.hfstudio.diskterminal.gui.GuiConstants;
import com.hfstudio.diskterminal.gui.widget.AbstractWidget;

/**
 * An 8x8 terminal button with a caller-supplied 16x16 icon and tooltip.
 */
public class SmallIconButton extends AbstractWidget {

    private static final int SIZE = GuiConstants.SMALL_BUTTON_SIZE;

    private final Runnable iconRenderer;
    private final Supplier<List<String>> tooltipSupplier;
    private final int stateColor;
    private final Runnable onClick;

    public SmallIconButton(int x, int y, Runnable iconRenderer, Supplier<List<String>> tooltipSupplier, int stateColor,
        Runnable onClick) {
        super(x, y, SIZE, SIZE);
        this.iconRenderer = iconRenderer;
        this.tooltipSupplier = tooltipSupplier;
        this.stateColor = stateColor;
        this.onClick = onClick;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        boolean hovered = isHovered(mouseX, mouseY);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (hovered) {
                GuiConstants.drawChildWindowButtonHover(x, y, SIZE, SIZE);
            } else {
                GuiConstants.drawChildWindowButton(x, y, SIZE, SIZE);
            }
            int borderColor = hovered ? GuiConstants.COLOR_BUTTON_HIGHLIGHT : stateColor;
            Gui.drawRect(x, y, x + SIZE, y + 1, borderColor);
            Gui.drawRect(x, y + SIZE - 1, x + SIZE, y + SIZE, borderColor);
            Gui.drawRect(x, y + 1, x + 1, y + SIZE - 1, borderColor);
            Gui.drawRect(x + SIZE - 1, y + 1, x + SIZE, y + SIZE - 1, borderColor);
            if (iconRenderer != null) {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glTranslatef(x, y, 0.0F);
                GL11.glScalef(SIZE / 16.0F, SIZE / 16.0F, 1.0F);
                iconRenderer.run();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible || button != 0 || !isHovered(mouseX, mouseY)) return false;
        onClick.run();
        return true;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY) || tooltipSupplier == null) return Collections.emptyList();
        List<String> tooltip = tooltipSupplier.get();
        return tooltip == null ? Collections.emptyList() : tooltip;
    }
}
