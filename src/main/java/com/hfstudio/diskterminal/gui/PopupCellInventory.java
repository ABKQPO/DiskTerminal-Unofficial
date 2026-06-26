package com.hfstudio.diskterminal.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.hfstudio.diskterminal.client.CellInfo;
import com.hfstudio.diskterminal.client.StorageInfo;
import com.hfstudio.diskterminal.gui.widget.AbstractWidget;
import com.hfstudio.diskterminal.network.DiskTerminalNetwork;
import com.hfstudio.diskterminal.network.PacketPartitionAction;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.util.ReadableNumberConverter;

/**
 * Popup overlay for viewing cell inventory contents.
 * Read-only view with double-click to toggle partition status.
 */
public class PopupCellInventory extends Gui {

    private static final int SLOTS_PER_ROW = GuiConstants.POPUP_SLOTS_PER_ROW;
    private static final int MAX_ROWS = GuiConstants.POPUP_MAX_ROWS;
    private static final int SLOT_SIZE = GuiConstants.SLOT_SIZE;
    private static final int PADDING = GuiConstants.PADDING;
    private static final int HEADER_HEIGHT = GuiConstants.POPUP_HEADER_HEIGHT;
    private static final int BUTTON_HEIGHT = GuiConstants.POPUP_BUTTON_HEIGHT;
    private static final int FOOTER_HEIGHT = GuiConstants.POPUP_FOOTER_HEIGHT;

    private final GuiScreen parent;
    private final CellInfo cell;
    private final long storageId;
    private final int cellSlot;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int slotOffsetX;

    // Button for set/unset all partition
    private final int partitionButtonX;
    private final int partitionButtonY;
    private final int partitionButtonWidth;
    private boolean partitionAllHovered = false;

    // Hovered item for tooltip
    private ItemStack hoveredStack = null;
    private int hoveredX = 0;
    private int hoveredY = 0;

    public PopupCellInventory(GuiScreen parent, CellInfo cell, int mouseX, int mouseY) {
        this.parent = parent;
        this.cell = cell;
        this.storageId = cell.getParentStorageId();
        this.cellSlot = cell.getSlot();

        int contentRows = Math.min(
            MAX_ROWS,
            (cell.getContents()
                .size() + SLOTS_PER_ROW
                - 1) / SLOTS_PER_ROW);
        if (contentRows == 0) contentRows = 1;

        // Calculate width based on title or slots, whichever is wider
        Minecraft mc = Minecraft.getMinecraft();
        String contentsSuffix = net.minecraft.client.resources.I18n.format("gui.disk_terminal.popup.contents_suffix");
        String title = cell.getDisplayName() + contentsSuffix;
        int titleWidth = mc.fontRenderer.getStringWidth(title) + PADDING * 2;
        int slotsWidth = SLOTS_PER_ROW * SLOT_SIZE + PADDING * 2;
        this.width = Math.max(titleWidth, slotsWidth);
        this.height = HEADER_HEIGHT + BUTTON_HEIGHT + 4 + contentRows * SLOT_SIZE + FOOTER_HEIGHT;

        // Calculate slot area offset to center slots within modal
        int slotAreaWidth = SLOTS_PER_ROW * SLOT_SIZE;
        this.slotOffsetX = (this.width - slotAreaWidth) / 2;

        // Center on screen using scaled resolution
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        this.x = (sr.getScaledWidth() - this.width) / 2;
        this.y = (sr.getScaledHeight() - this.height) / 2;

        this.partitionButtonX = this.x + PADDING;
        this.partitionButtonY = this.y + HEADER_HEIGHT;
        this.partitionButtonWidth = this.width - PADDING * 2;
    }

    public void draw(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;

        // Reset hovered state
        hoveredStack = null;

        // Reset GL state to known good state before drawing
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw popup background (similar to vanilla container style)
        drawRect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        drawGradientRect(x, y, x + width, y + height, 0xFFC6C6C6, 0xFFC6C6C6);

        // Draw border highlights
        drawRect(x, y, x + width, y + 1, 0xFFFFFFFF);
        drawRect(x, y, x + 1, y + height, 0xFFFFFFFF);
        drawRect(x, y + height - 1, x + width, y + height, 0xFF555555);
        drawRect(x + width - 1, y, x + width, y + height, 0xFF555555);

        // Draw title
        String contentsSuffix = net.minecraft.client.resources.I18n.format("gui.disk_terminal.popup.contents_suffix");
        String title = cell.getDisplayName() + contentsSuffix;
        fr.drawString(title, x + PADDING, y + 6, 0x404040);

        // Draw partition all button
        partitionAllHovered = mouseX >= partitionButtonX && mouseX < partitionButtonX + partitionButtonWidth
            && mouseY >= partitionButtonY
            && mouseY < partitionButtonY + BUTTON_HEIGHT;

        int buttonColor = partitionAllHovered ? 0xFF707070 : 0xFF8B8B8B;
        drawRect(
            partitionButtonX,
            partitionButtonY,
            partitionButtonX + partitionButtonWidth,
            partitionButtonY + BUTTON_HEIGHT,
            buttonColor);
        drawRect(
            partitionButtonX,
            partitionButtonY,
            partitionButtonX + partitionButtonWidth,
            partitionButtonY + 1,
            0xFFFFFFFF);
        drawRect(
            partitionButtonX,
            partitionButtonY,
            partitionButtonX + 1,
            partitionButtonY + BUTTON_HEIGHT,
            0xFFFFFFFF);
        drawRect(
            partitionButtonX,
            partitionButtonY + BUTTON_HEIGHT - 1,
            partitionButtonX + partitionButtonWidth,
            partitionButtonY + BUTTON_HEIGHT,
            0xFF555555);
        drawRect(
            partitionButtonX + partitionButtonWidth - 1,
            partitionButtonY,
            partitionButtonX + partitionButtonWidth,
            partitionButtonY + BUTTON_HEIGHT,
            0xFF555555);

        String buttonText = net.minecraft.client.resources.I18n.format("gui.disk_terminal.set_all_partition");
        int textWidth = fr.getStringWidth(buttonText);
        fr.drawString(
            buttonText,
            partitionButtonX + (partitionButtonWidth - textWidth) / 2,
            partitionButtonY + 3,
            0x404040);

        // Draw item slots
        int slotStartY = y + HEADER_HEIGHT + BUTTON_HEIGHT + 4;
        List<ItemStack> contents = cell.getContents();
        List<ItemStack> partition = getCurrentPartition();

        for (int i = 0; i < contents.size() && i < MAX_ROWS * SLOTS_PER_ROW; i++) {
            int slotX = x + slotOffsetX + (i % SLOTS_PER_ROW) * SLOT_SIZE;
            int slotY = slotStartY + (i / SLOTS_PER_ROW) * SLOT_SIZE;

            ItemStack stack = contents.get(i);

            // Check if item is in partition
            boolean inPartition = isInPartition(stack, partition);

            // Draw textured slot background
            drawSlotBackground(slotX, slotY);

            // Check hover
            boolean slotHovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                && mouseY >= slotY
                && mouseY < slotY + SLOT_SIZE;

            // Draw item
            if (!ItemStacks.isEmpty(stack)) {
                AbstractWidget.renderItemStack(RenderItem.getInstance(), stack, slotX + 1, slotY + 1);

                // Draw count like AE2 terminal (use actual AE2 count, not ItemStack count)
                drawItemCount(cell.getContentCount(i), slotX, slotY, fr);

                // Draw partition indicator in top-left corner if in partition
                if (inPartition) {
                    drawPartitionIndicator(slotX, slotY, fr);
                }

                // Track hovered item for tooltip
                if (slotHovered) {
                    hoveredStack = stack;
                    hoveredX = mouseX;
                    hoveredY = mouseY;
                }
            }

            // Draw hover highlight
            if (slotHovered) drawSlotHoverHighlight(slotX, slotY);
        }

        // Draw empty message if no contents
        if (contents.isEmpty()) {
            String empty = net.minecraft.client.resources.I18n.format("gui.disk_terminal.cell_empty");
            int emptyWidth = fr.getStringWidth(empty);
            fr.drawString(empty, x + (width - emptyWidth) / 2, slotStartY + 4, 0x606060);
        }

        // Reset state for subsequent rendering
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Get tooltip for the hovered item. The parent GUI draws it (it has protected
     * drawHoveringText access); returns null when nothing is hovered.
     */
    public java.util.List<String> getHoveredTooltip() {
        if (ItemStacks.isEmpty(hoveredStack)) return null;

        return hoveredStack.getTooltip(net.minecraft.client.Minecraft.getMinecraft().thePlayer, false);
    }

    public int getHoveredTooltipX() {
        return hoveredX;
    }

    public int getHoveredTooltipY() {
        return hoveredY;
    }

    private String formatItemCount(long count) {
        if (count < 1000) return String.valueOf(count);

        return ReadableNumberConverter.INSTANCE.toWideReadableForm(count);
    }

    private boolean isInPartition(ItemStack stack, List<ItemStack> partition) {
        return ComparisonUtils.isInPartition(stack, partition);
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!isInsidePopup(mouseX, mouseY)) return false;

        // Check partition all button click
        if (partitionAllHovered) {
            DiskTerminalNetwork.INSTANCE.sendToServer(
                new PacketPartitionAction(
                    cell.getParentStorageId(),
                    cell.getSlot(),
                    PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS));

            return true;
        }

        // Check slot click for partition add
        int slotStartY = y + HEADER_HEIGHT + BUTTON_HEIGHT + 4;
        int relX = mouseX - x - slotOffsetX;
        int relY = mouseY - slotStartY;

        if (relX >= 0 && relX < SLOTS_PER_ROW * SLOT_SIZE && relY >= 0) {
            int slotCol = relX / SLOT_SIZE;
            int slotRow = relY / SLOT_SIZE;
            int slotIndex = slotRow * SLOTS_PER_ROW + slotCol;

            if (slotIndex < cell.getContents()
                .size()) {
                ItemStack clickedStack = cell.getContents()
                    .get(slotIndex);

                if (!ItemStacks.isEmpty(clickedStack)) {
                    DiskTerminalNetwork.INSTANCE.sendToServer(
                        new PacketPartitionAction(
                            cell.getParentStorageId(),
                            cell.getSlot(),
                            PacketPartitionAction.Action.TOGGLE_ITEM,
                            clickedStack));
                }

                return true;
            }
        }

        return true;
    }

    public boolean isInsidePopup(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * Get current partition list from parent's storageMap.
     * This ensures we show up-to-date partition status after server updates.
     */
    private List<ItemStack> getCurrentPartition() {
        if (!(parent instanceof GuiCellTerminalBase)) return cell.getPartition();

        GuiCellTerminalBase gui = (GuiCellTerminalBase) parent;
        StorageInfo storage = gui.getStorageMap()
            .get(storageId);

        if (storage == null) return cell.getPartition();

        for (CellInfo d : storage.getCells()) {
            if (d.getSlot() == cellSlot) return d.getPartition();
        }

        return cell.getPartition();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    private void drawSlotBackground(int slotX, int slotY) {
        int texX = GuiConstants.SLOT_BACKGROUND_X;
        int texY = GuiConstants.SLOT_BACKGROUND_Y;
        GuiConstants.drawAtlasSprite(slotX, slotY, texX, texY, SLOT_SIZE);
    }

    private void drawSlotHoverHighlight(int slotX, int slotY) {
        Gui.drawRect(
            slotX + 1,
            slotY + 1,
            slotX + SLOT_SIZE - 1,
            slotY + SLOT_SIZE - 1,
            GuiConstants.COLOR_HOVER_HIGHLIGHT);
    }

    private void drawItemCount(long count, int slotX, int slotY, FontRenderer fr) {
        String countStr = formatItemCount(count);
        if (countStr.isEmpty()) return;

        int countWidth = fr.getStringWidth(countStr);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPushMatrix();
        GL11.glScalef(0.5f, 0.5f, 0.5f);
        // Right-align: for 18x18 slot, text right edge at slotX+17, bottom at slotY+13
        fr.drawStringWithShadow(
            countStr,
            (slotX + SLOT_SIZE - 1) * 2 - countWidth,
            (slotY + SLOT_SIZE - 5) * 2,
            0xFFFFFF);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void drawPartitionIndicator(int slotX, int slotY, FontRenderer fr) {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPushMatrix();
        GL11.glScalef(0.5f, 0.5f, 0.5f);
        fr.drawStringWithShadow("P", (slotX + 2) * 2, (slotY + 2) * 2, GuiConstants.COLOR_PARTITION_INDICATOR);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
