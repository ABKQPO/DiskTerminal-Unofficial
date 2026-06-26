package com.hfstudio.diskterminal.gui.buttons;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

import com.hfstudio.diskterminal.client.SlotLimit;
import com.hfstudio.diskterminal.gui.GuiConstants;

/**
 * Button for cycling through slot limit options (8, 32, 64, unlimited).
 * Controls how many content types are displayed per cell/storage bus.
 */
public class GuiSlotLimitButton extends GuiAtlasButton {

    private static final int SIZE = GuiConstants.TERMINAL_SIDE_BUTTON_SIZE;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private SlotLimit currentLimit;

    public GuiSlotLimitButton(int buttonId, int x, int y, SlotLimit initialLimit) {
        super(buttonId, x, y, SIZE);
        this.currentLimit = initialLimit;
    }

    public SlotLimit getLimit() {
        return currentLimit;
    }

    public void setLimit(SlotLimit limit) {
        this.currentLimit = limit;
    }

    /**
     * Cycle to the next limit and return the new value.
     */
    public SlotLimit cycleLimit() {
        this.currentLimit = this.currentLimit.next();

        return this.currentLimit;
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
        FontRenderer fr = mc.fontRenderer;
        String text = currentLimit.getDisplayText();
        int textWidth = fr.getStringWidth(text);
        int textX = this.xPosition + (this.width - textWidth) / 2;
        // The infinity glyph can be visually wider than normal digits; nudge right slightly to better center it
        if (currentLimit != null && currentLimit.getDisplayText() != null && currentLimit == SlotLimit.UNLIMITED) {
            textX += 1;
        }
        int textY = this.yPosition + (this.height - 8) / 2;
        fr.drawString(text, textX, textY, COLOR_TEXT);
    }

    /**
     * Tooltip lines for the slot limit button.
     */
    public List<String> getTooltip() {
        String limitText = "";

        if (currentLimit != null) {
            limitText = switch (currentLimit) {
                case LIMIT_8 -> I18n.format("gui.disk_terminal.slot_limit.8");
                case LIMIT_32 -> I18n.format("gui.disk_terminal.slot_limit.32");
                case LIMIT_64 -> I18n.format("gui.disk_terminal.slot_limit.64");
                default -> I18n.format("gui.disk_terminal.slot_limit.unlimited");
            };
        }

        List<String> tooltip = new ArrayList<>();
        tooltip.add(I18n.format("gui.disk_terminal.slot_limit", limitText));
        return tooltip;
    }
}
