package com.hfstudio.diskterminal.gui.networktools;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Modal dialog for confirming network tool execution.
 */
public class GuiToolConfirmationModal {

    private static final int MIN_MODAL_WIDTH = 200;
    private static final int MAX_MODAL_WIDTH = 300;
    private static final int PADDING = 10;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 20;
    private static final int TITLE_HEIGHT = 20;

    private final INetworkTool tool;
    private final FontRenderer fontRenderer;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    // Dynamically calculated modal dimensions
    private final int modalWidth;
    private final int modalHeight;
    private final int modalX;
    private final int modalY;
    private final List<String> wrappedMessage;

    private boolean confirmHovered = false;
    private boolean cancelHovered = false;

    public GuiToolConfirmationModal(INetworkTool tool, INetworkTool.ToolContext context, FontRenderer fontRenderer,
        int screenWidth, int screenHeight, Runnable onConfirm, Runnable onCancel) {
        this.tool = tool;
        this.fontRenderer = fontRenderer;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        // Calculate modal dimensions based on content
        String title = I18n.format("gui.disk_terminal.networktools.confirm.title", tool.getName());
        String message = tool.getConfirmationMessage(context);

        // Calculate width based on title length
        int titleWidth = fontRenderer.getStringWidth(title) + PADDING * 2;
        int contentWidth = Math.max(MIN_MODAL_WIDTH, Math.min(MAX_MODAL_WIDTH, titleWidth));

        // Wrap message text
        int maxTextWidth = contentWidth - PADDING * 2;
        this.wrappedMessage = fontRenderer.listFormattedStringToWidth(message, maxTextWidth);

        // Calculate height based on wrapped message
        int messageHeight = wrappedMessage.size() * (fontRenderer.FONT_HEIGHT + 2);
        int totalHeight = PADDING + TITLE_HEIGHT + messageHeight + PADDING + BUTTON_HEIGHT + PADDING;

        this.modalWidth = contentWidth;
        this.modalHeight = totalHeight;
        this.modalX = (screenWidth - modalWidth) / 2;
        this.modalY = (screenHeight - modalHeight) / 2;
    }

    /**
     * Draw the modal.
     */
    public void draw(int mouseX, int mouseY) {
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 500); // Above other content but below tooltips

        // Reset GL state for proper color rendering
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw modal background
        Gui.drawRect(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF303030);

        // Draw modal border
        Gui.drawRect(modalX, modalY, modalX + modalWidth, modalY + 2, 0xFFFFFFFF);
        Gui.drawRect(modalX, modalY, modalX + 2, modalY + modalHeight, 0xFFFFFFFF);
        Gui.drawRect(modalX + modalWidth - 2, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF555555);
        Gui.drawRect(modalX, modalY + modalHeight - 2, modalX + modalWidth, modalY + modalHeight, 0xFF555555);

        // Draw title
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        String title = I18n.format("gui.disk_terminal.networktools.confirm.title", tool.getName());
        fontRenderer.drawStringWithShadow(title, modalX + PADDING, modalY + PADDING, 0xFFFF00);

        // Draw confirmation message (wrapped)
        int textY = modalY + PADDING + TITLE_HEIGHT;

        for (String line : wrappedMessage) {
            fontRenderer.drawString(line, modalX + PADDING, textY, 0xFFFFFF);
            textY += fontRenderer.FONT_HEIGHT + 2;
        }

        // Draw buttons
        int buttonsY = modalY + modalHeight - BUTTON_HEIGHT - PADDING;
        int confirmX = modalX + (modalWidth / 2) - BUTTON_WIDTH - (BUTTON_SPACING / 2);
        int cancelX = modalX + (modalWidth / 2) + (BUTTON_SPACING / 2);

        // Check hover state
        confirmHovered = isMouseOver(mouseX, mouseY, confirmX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT);
        cancelHovered = isMouseOver(mouseX, mouseY, cancelX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT);

        // Draw Confirm button
        drawButton(
            confirmX,
            buttonsY,
            BUTTON_WIDTH,
            BUTTON_HEIGHT,
            I18n.format("gui.disk_terminal.networktools.confirm.do_it"),
            confirmHovered,
            true);

        // Draw Cancel button
        drawButton(
            cancelX,
            buttonsY,
            BUTTON_WIDTH,
            BUTTON_HEIGHT,
            I18n.format("gui.disk_terminal.networktools.confirm.cancel"),
            cancelHovered,
            false);

        // Restore GL state. GUI render pass expects lighting OFF, so do NOT re-enable it here
        // (raw GL11 has no GlStateManager-style context restore; re-enabling would darken later
        // GUI elements drawn after this modal).
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    private void drawButton(int x, int y, int width, int height, String text, boolean hovered, boolean isConfirm) {
        int bgColor;
        int borderTopColor;
        int borderLeftColor;
        int borderRightColor;
        int borderBottomColor;

        if (isConfirm) {
            bgColor = hovered ? 0xFF40A040 : 0xFF308030;
            borderTopColor = hovered ? 0xFF60C060 : 0xFF50A050;
            borderRightColor = hovered ? 0xFF206020 : 0xFF105010;
        } else {
            bgColor = hovered ? 0xFF606060 : 0xFF505050;
            borderTopColor = hovered ? 0xFF808080 : 0xFF707070;
            borderRightColor = hovered ? 0xFF303030 : 0xFF202020;
        }

        borderLeftColor = borderTopColor;
        borderBottomColor = borderRightColor;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Gui.drawRect(x, y, x + width, y + height, bgColor);
        Gui.drawRect(x, y, x + width, y + 1, borderTopColor);
        Gui.drawRect(x, y, x + 1, y + height, borderLeftColor);
        Gui.drawRect(x + width - 1, y, x + width, y + height, borderRightColor);
        Gui.drawRect(x, y + height - 1, x + width, y + height, borderBottomColor);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        int textX = x + (width - fontRenderer.getStringWidth(text)) / 2;
        int textY = y + (height - fontRenderer.FONT_HEIGHT) / 2;
        fontRenderer.drawStringWithShadow(text, textX, textY, 0xFFFFFF);
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * Handle mouse click.
     * 
     * @return true if the modal handled the click and should be closed
     */
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        if (confirmHovered) {
            onConfirm.run();

            return true;
        }

        if (cancelHovered) {
            onCancel.run();

            return true;
        }

        // Click outside modal also cancels
        if (!isMouseOver(mouseX, mouseY, modalX, modalY, modalWidth, modalHeight)) {
            onCancel.run();

            return true;
        }

        return false;
    }

    /**
     * Handle key press.
     * 
     * @return true if the modal handled the key
     */
    public boolean handleKeyTyped(int keyCode) {
        // ESC key cancels
        if (keyCode == Keyboard.KEY_ESCAPE) {
            onCancel.run();

            return true;
        }

        // Enter key confirms
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            onConfirm.run();

            return true;
        }

        return false;
    }
}
