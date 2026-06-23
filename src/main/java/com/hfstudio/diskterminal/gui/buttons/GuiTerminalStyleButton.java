package com.hfstudio.diskterminal.gui.buttons;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.hfstudio.diskterminal.config.DiskTerminalClientConfig.TerminalStyle;
import com.hfstudio.diskterminal.gui.GuiConstants;

/**
 * Button to toggle between terminal styles (small/tall).
 * Uses atlas.png for the background and states.png for the style icon overlay.
 */
public class GuiTerminalStyleButton extends GuiAtlasButton {

    private static final int SIZE = GuiConstants.TERMINAL_SIDE_BUTTON_SIZE;
    private static final ResourceLocation AE2_STATES = new ResourceLocation(
        "appliedenergistics2",
        "textures/guis/states.png");

    private TerminalStyle currentStyle;

    public GuiTerminalStyleButton(int buttonId, int x, int y, TerminalStyle initialStyle) {
        super(buttonId, x, y, SIZE);
        this.currentStyle = initialStyle;
    }

    public void setStyle(TerminalStyle style) {
        this.currentStyle = style;
    }

    public TerminalStyle getStyle() {
        return currentStyle;
    }

    @Override
    protected int getBackgroundTexX() {
        return GuiConstants.TERMINAL_STYLE_BUTTON_X;
    }

    @Override
    protected int getBackgroundTexY() {
        return GuiConstants.TERMINAL_STYLE_BUTTON_Y + (this.field_146123_n ? SIZE : 0);
    }

    @Override
    protected void drawForeground(Minecraft mc) {
        // Terminal style icon: row 13 in states.png, column 0 = tall, column 1 = compact
        mc.getTextureManager()
            .bindTexture(AE2_STATES);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        int iconX = currentStyle == TerminalStyle.TALL ? 0 : SIZE;
        int iconY = 13 * SIZE;
        drawTexturedModalRect(this.xPosition, this.yPosition, iconX, iconY, SIZE, SIZE);
    }

    /**
     * Get the tooltip lines for this button.
     */
    public List<String> getTooltip() {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.disk_terminal.terminal_style"));

        String styleKey = currentStyle == TerminalStyle.SMALL ? "gui.disk_terminal.terminal_style.small"
            : "gui.disk_terminal.terminal_style.tall";
        tooltip.add("§7" + I18n.format(styleKey));

        return tooltip;
    }
}
