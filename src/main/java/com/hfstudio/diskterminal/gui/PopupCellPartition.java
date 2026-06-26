package com.hfstudio.diskterminal.gui;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.hfstudio.diskterminal.client.CellInfo;
import com.hfstudio.diskterminal.gui.handler.GhostIngredientHandler;
import com.hfstudio.diskterminal.gui.handler.GhostTarget;
import com.hfstudio.diskterminal.gui.handler.QuickPartitionHandler;
import com.hfstudio.diskterminal.gui.widget.AbstractWidget;
import com.hfstudio.diskterminal.integration.NEIIntegration;
import com.hfstudio.diskterminal.network.DiskTerminalNetwork;
import com.hfstudio.diskterminal.network.PacketPartitionAction;
import com.hfstudio.diskterminal.util.ItemStacks;

/**
 * Popup overlay for editing cell partition.
 * Shows 63 slots, click to remove, NEI drag to add.
 */
public class PopupCellPartition extends Gui {

    private static final int SLOTS_PER_ROW = GuiConstants.POPUP_SLOTS_PER_ROW;
    private static final int MAX_PARTITION_SLOTS = 63;
    private static final int MAX_ROWS = (MAX_PARTITION_SLOTS + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW;
    private static final int SLOT_SIZE = GuiConstants.SLOT_SIZE;
    private static final int PADDING = GuiConstants.PADDING;
    private static final int HEADER_HEIGHT = GuiConstants.POPUP_HEADER_HEIGHT;
    private static final int FOOTER_HEIGHT = GuiConstants.POPUP_FOOTER_HEIGHT;

    private final GuiScreen parent;
    private final CellInfo cell;
    private int x;
    private int y;
    private final int width;
    private final int height;
    private final int slotOffsetX;
    private final int screenWidth;
    private final int screenHeight;

    private final List<ItemStack> editablePartition;

    // Hovered item for tooltip
    private ItemStack hoveredStack = null;
    private int hoveredX = 0;
    private int hoveredY = 0;
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public PopupCellPartition(GuiScreen parent, CellInfo cell, int mouseX, int mouseY) {
        this.parent = parent;
        this.cell = cell;

        // Copy partition for editing
        this.editablePartition = new ArrayList<>(cell.getPartition());
        while (editablePartition.size() < MAX_PARTITION_SLOTS) editablePartition.add(null);

        // Calculate width based on title, slots, or hint, whichever is wider
        Minecraft mc = Minecraft.getMinecraft();
        String partitionSuffix = I18n.format("gui.disk_terminal.popup.partition_suffix");
        String title = cell.getDisplayName() + partitionSuffix;
        String hint = I18n.format("gui.disk_terminal.hint.partition");
        int titleWidth = mc.fontRenderer.getStringWidth(title) + PADDING * 2;
        int hintWidth = mc.fontRenderer.getStringWidth(hint) + PADDING * 2;
        int slotsWidth = SLOTS_PER_ROW * SLOT_SIZE + PADDING * 2;
        this.width = Math.max(Math.max(titleWidth, slotsWidth), hintWidth);
        this.height = HEADER_HEIGHT + MAX_ROWS * SLOT_SIZE + FOOTER_HEIGHT;

        // Calculate slot area offset to center slots within modal
        int slotAreaWidth = SLOTS_PER_ROW * SLOT_SIZE;
        this.slotOffsetX = (this.width - slotAreaWidth) / 2;

        // Center on screen using scaled resolution
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        this.screenWidth = sr.getScaledWidth();
        this.screenHeight = sr.getScaledHeight();
        this.x = (sr.getScaledWidth() - this.width) / 2;
        this.y = (sr.getScaledHeight() - this.height) / 2;
    }

    public void draw(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;

        // Reset hovered state
        hoveredStack = null;

        if (dragging) moveTo(mouseX - dragOffsetX, mouseY - dragOffsetY);

        // Reset GL state to known good state before drawing
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
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
        String partitionSuffix = I18n.format("gui.disk_terminal.popup.partition_suffix");
        String title = cell.getDisplayName() + partitionSuffix;
        fr.drawString(title, x + PADDING, y + 6, 0x404040);

        // Draw partition slots
        int slotStartY = y + HEADER_HEIGHT;

        for (int i = 0; i < MAX_PARTITION_SLOTS; i++) {
            int slotX = x + slotOffsetX + (i % SLOTS_PER_ROW) * SLOT_SIZE;
            int slotY = slotStartY + (i / SLOTS_PER_ROW) * SLOT_SIZE;

            ItemStack stack = i < editablePartition.size() ? editablePartition.get(i) : null;

            // Draw textured slot background (partition variant with amber tint)
            drawPartitionSlotBackground(slotX, slotY);

            // Check hover
            boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                && mouseY >= slotY
                && mouseY < slotY + SLOT_SIZE;

            // Draw item
            if (!ItemStacks.isEmpty(stack)) {
                AbstractWidget.renderItemStack(RenderItem.getInstance(), stack, slotX + 1, slotY + 1);

                // Track hovered item for tooltip
                if (hovered) {
                    hoveredStack = stack;
                    hoveredX = mouseX;
                    hoveredY = mouseY;
                }
            }

            // Draw hover highlight
            if (hovered) drawSlotHoverHighlight(slotX, slotY);
        }

        // Draw hint at bottom
        String hint = I18n.format("gui.disk_terminal.hint.partition");
        int hintWidth = fr.getStringWidth(hint);
        fr.drawString(hint, x + (width - hintWidth) / 2, y + height - FOOTER_HEIGHT + 2, 0x606060);

        // Reset state for subsequent rendering
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Compute the tooltip for the hovered item, if any. The parent GUI draws it (it has the
     * protected access to {@code drawHoveringText}); this avoids calling protected GuiScreen
     * methods on an external reference.
     *
     * @return the tooltip lines and screen position, or null if nothing is hovered
     */
    public List<String> getHoveredTooltip() {
        if (ItemStacks.isEmpty(hoveredStack)) return null;

        return hoveredStack.getTooltip(Minecraft.getMinecraft().thePlayer, false);
    }

    public int getHoveredTooltipX() {
        return hoveredX;
    }

    public int getHoveredTooltipY() {
        return hoveredY;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!isInsidePopup(mouseX, mouseY)) return false;

        if (mouseButton == 0 && isInHeader(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
            return true;
        }

        // Check slot click to replace or remove.
        int slotStartY = y + HEADER_HEIGHT;
        int relX = mouseX - x - slotOffsetX;
        int relY = mouseY - slotStartY;

        if (relX >= 0 && relX < SLOTS_PER_ROW * SLOT_SIZE && relY >= 0 && relY < MAX_ROWS * SLOT_SIZE) {
            int slotCol = relX / SLOT_SIZE;
            int slotRow = relY / SLOT_SIZE;
            int slotIndex = slotRow * SLOTS_PER_ROW + slotCol;

            if (slotIndex < MAX_PARTITION_SLOTS && slotIndex < editablePartition.size()) {
                ItemStack heldStack = Minecraft.getMinecraft().thePlayer.inventory.getItemStack();
                Object replacementIngredient = getReplacementIngredient(heldStack);
                ItemStack replacement = convertIngredientForCell(replacementIngredient);

                if (!ItemStacks.isEmpty(replacement)) {
                    editablePartition.set(slotIndex, replacement.copy());

                    DiskTerminalNetwork.INSTANCE.sendToServer(
                        new PacketPartitionAction(
                            cell.getParentStorageId(),
                            cell.getSlot(),
                            PacketPartitionAction.Action.ADD_ITEM,
                            slotIndex,
                            replacement));

                    return true;
                }

                ItemStack removed = editablePartition.get(slotIndex);
                if (replacementIngredient == null && ItemStacks.isEmpty(heldStack) && !ItemStacks.isEmpty(removed)) {
                    editablePartition.set(slotIndex, null);

                    DiskTerminalNetwork.INSTANCE.sendToServer(
                        new PacketPartitionAction(
                            cell.getParentStorageId(),
                            cell.getSlot(),
                            PacketPartitionAction.Action.REMOVE_ITEM,
                            slotIndex));
                }

                return true;
            }
        }

        return true;
    }

    private Object getReplacementIngredient(ItemStack heldStack) {
        if (!ItemStacks.isEmpty(heldStack)) return heldStack;

        ItemStack draggedStack = NEIIntegration.getDraggedStack();
        if (!ItemStacks.isEmpty(draggedStack)) return draggedStack;

        return QuickPartitionHandler.getModIngredientUnderMouse();
    }

    /**
     * Convert any NEI ingredient to an ItemStack for use with AE2 cells.
     * Handles ItemStack, FluidStack, EnchantmentData (NEI's hack for enchanted books),
     * and any future/unknown ingredient types.
     *
     * @param ingredient The NEI ingredient to convert
     * @return The converted ItemStack, or null if conversion failed or was rejected
     */
    private ItemStack convertIngredientForCell(Object ingredient) {
        return GhostIngredientHandler.convertIngredientForType(ingredient, cell.getStackTypeId(), false);
    }

    /**
     * Handle NEI ghost ingredient drop.
     */
    public boolean handleGhostDrop(int slotIndex, Object ingredient) {
        if (slotIndex < 0 || slotIndex >= MAX_PARTITION_SLOTS) return false;

        ItemStack stack = convertIngredientForCell(ingredient);

        if (ItemStacks.isEmpty(stack)) return false;

        editablePartition.set(slotIndex, stack.copy());

        DiskTerminalNetwork.INSTANCE.sendToServer(
            new PacketPartitionAction(
                cell.getParentStorageId(),
                cell.getSlot(),
                PacketPartitionAction.Action.ADD_ITEM,
                slotIndex,
                stack));

        return true;
    }

    public boolean isInsidePopup(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public boolean handleDrag(int mouseX, int mouseY, int mouseButton) {
        if (!dragging || mouseButton != 0) return false;

        moveTo(mouseX - dragOffsetX, mouseY - dragOffsetY);
        return true;
    }

    public void stopDragging() {
        dragging = false;
    }

    private boolean isInHeader(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEADER_HEIGHT;
    }

    private void moveTo(int newX, int newY) {
        int maxX = Math.max(0, screenWidth - width);
        int maxY = Math.max(0, screenHeight - height);
        x = Math.clamp(newX, 0, maxX);
        y = Math.clamp(newY, 0, maxY);
    }

    /**
     * Get NEI ghost ingredient targets for this popup.
     * The parent GUI will wrap these to handle clearing the drag state.
     */
    public List<GhostTarget<?>> getGhostTargets() {
        List<GhostTarget<?>> targets = new ArrayList<>();
        int slotStartY = y + HEADER_HEIGHT;

        for (int i = 0; i < MAX_PARTITION_SLOTS; i++) {
            final int slotIndex = i;
            int slotX = x + slotOffsetX + (i % SLOTS_PER_ROW) * SLOT_SIZE;
            int slotY = slotStartY + (i / SLOTS_PER_ROW) * SLOT_SIZE;

            Rectangle area = new Rectangle(slotX, slotY, SLOT_SIZE, SLOT_SIZE);

            targets.add(new GhostTarget<>() {

                @Override
                public Rectangle getArea() {
                    return area;
                }

                @Override
                public void accept(Object ingredient) {
                    // Handle both ItemStack and FluidStack
                    handleGhostDrop(slotIndex, ingredient);
                }
            });
        }

        return targets;
    }

    public CellInfo getCell() {
        return cell;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public ItemStack getHoveredStack() {
        return hoveredStack;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    private void drawPartitionSlotBackground(int slotX, int slotY) {
        // Use partition variant (right half of slot texture with amber tint)
        int texX = GuiConstants.SLOT_BACKGROUND_X + SLOT_SIZE;
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
}
