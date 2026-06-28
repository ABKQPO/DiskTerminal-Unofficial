package com.hfstudio.diskterminal.gui.networktools;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.hfstudio.diskterminal.gui.GuiConstants;

/**
 * Draggable confirmation popup for network tool execution.
 */
public class GuiToolConfirmationModal {

    private static final int MIN_MODAL_WIDTH = 200;
    private static final int MAX_MODAL_WIDTH = 300;
    private static final int PADDING = GuiConstants.PADDING;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_SPACING = 20;
    private static final int TITLE_HEIGHT = GuiConstants.POPUP_HEADER_HEIGHT;
    private static final int HEADER_TEXT_Y = 6;
    private static final int GREEN_BUTTON_TEX_X = 32;
    private static final int GREEN_BUTTON_TEX_Y = 60;
    private static final int GREEN_BUTTON_HOVER_TEX_Y = 76;
    private static final int GREEN_BUTTON_SOURCE_SIZE = 16;
    private static final int GREEN_BUTTON_BORDER = 2;

    private final INetworkTool tool;
    private final FontRenderer fontRenderer;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private final int screenWidth;
    private final int screenHeight;

    private final int modalWidth;
    private final int modalHeight;
    private final List<String> wrappedMessage;
    private final String title;

    private int modalX;
    private int modalY;
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private boolean confirmHovered = false;
    private boolean cancelHovered = false;

    public GuiToolConfirmationModal(INetworkTool tool, INetworkTool.ToolContext context, FontRenderer fontRenderer,
        int screenWidth, int screenHeight, Runnable onConfirm, Runnable onCancel) {
        this.tool = tool;
        this.fontRenderer = fontRenderer;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        this.title = I18n.format("gui.disk_terminal.networktools.confirm.title", tool.getName());
        String message = tool.getConfirmationMessage(context);

        int titleWidth = fontRenderer.getStringWidth(this.title) + PADDING * 2;
        int contentWidth = Math.clamp(titleWidth, MIN_MODAL_WIDTH, MAX_MODAL_WIDTH);
        int maxTextWidth = contentWidth - PADDING * 2;
        this.wrappedMessage = fontRenderer.listFormattedStringToWidth(message, maxTextWidth);
        int messageHeight = wrappedMessage.size() * (fontRenderer.FONT_HEIGHT + 2);
        int totalHeight = PADDING + TITLE_HEIGHT + messageHeight + PADDING + BUTTON_HEIGHT + PADDING;

        this.modalWidth = contentWidth;
        this.modalHeight = totalHeight;
        this.modalX = (screenWidth - modalWidth) / 2;
        this.modalY = (screenHeight - modalHeight) / 2;
    }

    public void draw(int mouseX, int mouseY) {
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 500);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        if (dragging) moveTo(mouseX - dragOffsetX, mouseY - dragOffsetY);

        GuiConstants.drawChildWindowBackground(modalX, modalY, modalWidth, modalHeight);

        fontRenderer.drawString(this.title, modalX + PADDING, modalY + HEADER_TEXT_Y, 0x404040);

        int textY = modalY + PADDING + TITLE_HEIGHT;
        for (String line : wrappedMessage) {
            fontRenderer.drawString(line, modalX + PADDING, textY, 0xFFFFFF);
            textY += fontRenderer.FONT_HEIGHT + 2;
        }

        int buttonsY = modalY + modalHeight - BUTTON_HEIGHT - PADDING;
        int confirmX = modalX + (modalWidth / 2) - BUTTON_WIDTH - (BUTTON_SPACING / 2);
        int cancelX = modalX + (modalWidth / 2) + (BUTTON_SPACING / 2);

        confirmHovered = isMouseOver(mouseX, mouseY, confirmX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT);
        cancelHovered = isMouseOver(mouseX, mouseY, cancelX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT);

        drawButton(
            confirmX,
            buttonsY,
            BUTTON_WIDTH,
            BUTTON_HEIGHT,
            I18n.format("gui.disk_terminal.networktools.confirm.do_it"),
            confirmHovered,
            true);

        drawButton(
            cancelX,
            buttonsY,
            BUTTON_WIDTH,
            BUTTON_HEIGHT,
            I18n.format("gui.disk_terminal.networktools.confirm.cancel"),
            cancelHovered,
            false);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    private void drawButton(int x, int y, int width, int height, String text, boolean hovered, boolean isConfirm) {
        if (isConfirm) {
            int texY = hovered ? GREEN_BUTTON_HOVER_TEX_Y : GREEN_BUTTON_TEX_Y;
            GuiConstants.drawNineSlicedTexture(
                GuiConstants.ATLAS_TEXTURE,
                x,
                y,
                GREEN_BUTTON_TEX_X,
                texY,
                GREEN_BUTTON_SOURCE_SIZE,
                GREEN_BUTTON_SOURCE_SIZE,
                width,
                height,
                GREEN_BUTTON_BORDER,
                GuiConstants.ATLAS_WIDTH,
                GuiConstants.ATLAS_HEIGHT);
        } else {
            if (hovered) {
                GuiConstants.drawChildWindowButtonHover(x, y, width, height);
            } else {
                GuiConstants.drawChildWindowButton(x, y, width, height);
            }
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        int textX = x + (width - fontRenderer.getStringWidth(text)) / 2;
        int textY = y + (height - fontRenderer.FONT_HEIGHT) / 2;
        fontRenderer.drawString(text, textX, textY, 0x404040);
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!isInside(mouseX, mouseY)) return false;

        if (button == 0 && isInHeader(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - modalX;
            dragOffsetY = mouseY - modalY;
            return true;
        }

        if (button != 0) return true;

        if (isConfirmButtonHovered(mouseX, mouseY)) {
            onConfirm.run();
            return true;
        }

        if (isCancelButtonHovered(mouseX, mouseY)) {
            onCancel.run();
            return true;
        }

        return true;
    }

    public boolean handleKeyTyped(int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            onCancel.run();
            return true;
        }

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            onConfirm.run();
            return true;
        }

        return false;
    }

    public boolean handleDrag(int mouseX, int mouseY, int mouseButton) {
        if (!dragging || mouseButton != 0) return false;

        moveTo(mouseX - dragOffsetX, mouseY - dragOffsetY);
        return true;
    }

    public void stopDragging() {
        dragging = false;
    }

    public boolean isInside(int mouseX, int mouseY) {
        return isMouseOver(mouseX, mouseY, modalX, modalY, modalWidth, modalHeight);
    }

    private boolean isInHeader(int mouseX, int mouseY) {
        return isMouseOver(mouseX, mouseY, modalX, modalY, modalWidth, TITLE_HEIGHT);
    }

    private boolean isConfirmButtonHovered(int mouseX, int mouseY) {
        int buttonsY = modalY + modalHeight - BUTTON_HEIGHT - PADDING;
        int confirmX = modalX + (modalWidth / 2) - BUTTON_WIDTH - (BUTTON_SPACING / 2);
        return isMouseOver(mouseX, mouseY, confirmX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private boolean isCancelButtonHovered(int mouseX, int mouseY) {
        int buttonsY = modalY + modalHeight - BUTTON_HEIGHT - PADDING;
        int cancelX = modalX + (modalWidth / 2) + (BUTTON_SPACING / 2);
        return isMouseOver(mouseX, mouseY, cancelX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private void moveTo(int newX, int newY) {
        int maxX = Math.max(0, screenWidth - modalWidth);
        int maxY = Math.max(0, screenHeight - modalHeight);
        modalX = Math.clamp(newX, 0, maxX);
        modalY = Math.clamp(newY, 0, maxY);
    }
}
