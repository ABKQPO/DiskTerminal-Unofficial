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
import com.hfstudio.diskterminal.gui.handler.GhostTarget;
import com.hfstudio.diskterminal.gui.handler.JeiGhostHandler;
import com.hfstudio.diskterminal.gui.widget.AbstractWidget;
import com.hfstudio.diskterminal.network.DiskTerminalNetwork;
import com.hfstudio.diskterminal.network.PacketPartitionAction;
import com.hfstudio.diskterminal.util.ItemStacks;

/**
 * Popup overlay for editing cell partition.
 * Shows 63 slots, click to remove, JEI drag to add.
 */
public class PopupCellPartition extends Gui {

    private static final int SLOTS_PER_ROW = GuiConstants.POPUP_SLOTS_PER_ROW;
    private static final int MAX_ROWS = GuiConstants.POPUP_MAX_ROWS;
    private static final int SLOT_SIZE = GuiConstants.SLOT_SIZE;
    private static final int PADDING = GuiConstants.PADDING;
    private static final int HEADER_HEIGHT = GuiConstants.POPUP_HEADER_HEIGHT;
    private static final int FOOTER_HEIGHT = GuiConstants.POPUP_FOOTER_HEIGHT;
    private static final int MAX_PARTITION_SLOTS = SLOTS_PER_ROW * MAX_ROWS;

    private final GuiScreen parent;
    private final CellInfo cell;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int slotOffsetX;

    private final List<ItemStack> editablePartition;

    // Hovered item for tooltip
    private ItemStack hoveredStack = null;
    private int hoveredX = 0;
    private int hoveredY = 0;

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
        this.x = (sr.getScaledWidth() - this.width) / 2;
        this.y = (sr.getScaledHeight() - this.height) / 2;
    }

    public void draw(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;

        // Reset hovered state
        hoveredStack = null;

        // Reset GL state to known good state before drawing
        GL11.glDisable(GL11.GL_LIGHTING);
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
        String partitionSuffix = net.minecraft.client.resources.I18n.format("gui.disk_terminal.popup.partition_suffix");
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
        String hint = net.minecraft.client.resources.I18n.format("gui.disk_terminal.hint.partition");
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
    @SuppressWarnings("unchecked")
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

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!isInsidePopup(mouseX, mouseY)) return false;

        // Check slot click to remove
        int slotStartY = y + HEADER_HEIGHT;
        int relX = mouseX - x - slotOffsetX;
        int relY = mouseY - slotStartY;

        if (relX >= 0 && relX < SLOTS_PER_ROW * SLOT_SIZE && relY >= 0 && relY < MAX_ROWS * SLOT_SIZE) {
            int slotCol = relX / SLOT_SIZE;
            int slotRow = relY / SLOT_SIZE;
            int slotIndex = slotRow * SLOTS_PER_ROW + slotCol;

            if (slotIndex < MAX_PARTITION_SLOTS && slotIndex < editablePartition.size()) {
                ItemStack removed = editablePartition.get(slotIndex);
                if (!ItemStacks.isEmpty(removed)) {
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

    /**
     * Convert any JEI ingredient to an ItemStack for use with AE2 cells.
     * Handles ItemStack, FluidStack, EnchantmentData (JEI's hack for enchanted books),
     * and any future/unknown ingredient types.
     *
     * @param ingredient The JEI ingredient to convert
     * @return The converted ItemStack, or null if conversion failed or was rejected
     */
    private ItemStack convertJeiIngredientToItemStack(Object ingredient) {
        return JeiGhostHandler.convertJeiIngredientToItemStack(ingredient, cell.getStorageType());
    }

    /**
     * Handle JEI ghost ingredient drop.
     * FIXME: The green slots do not appear when dragging from JEI into the popup.
     * FIXME: The green slots are not cleared when the popup is closed.
     * FIXME: The green line is not rendered when dragging from bookmarks.
     * FIXME: The item is rendered behind the popup when dragging from bookmarks.
     */
    public boolean handleGhostDrop(int slotIndex, Object ingredient) {
        if (slotIndex < 0 || slotIndex >= MAX_PARTITION_SLOTS) return false;

        ItemStack stack = convertJeiIngredientToItemStack(ingredient);

        if (ItemStacks.isEmpty(stack)) return false;

        // Find first empty slot if dropping on occupied slot
        int targetSlot = slotIndex;
        if (!ItemStacks.isEmpty(editablePartition.get(slotIndex))) {
            targetSlot = findEmptySlot();
            if (targetSlot == -1) return false;
        }

        editablePartition.set(targetSlot, stack.copy());

        DiskTerminalNetwork.INSTANCE.sendToServer(
            new PacketPartitionAction(
                cell.getParentStorageId(),
                cell.getSlot(),
                PacketPartitionAction.Action.ADD_ITEM,
                targetSlot,
                stack));

        return true;
    }

    // OLD EXPLICIT TYPE HANDLING - Uncomment this and remove convertJeiIngredientToItemStack
    // if you need to revert to the previous behavior due to issues with unknown ingredient types.
    /*
     * public boolean handleGhostDrop(int slotIndex, Object ingredient) {
     * if (slotIndex < 0 || slotIndex >= MAX_PARTITION_SLOTS) return false;
     * ItemStack stack;
     * if (ingredient instanceof ItemStack) {
     * ItemStack itemStack = (ItemStack) ingredient;
     * if (cell.isFluid()) {
     * FluidStack contained = FluidUtil.getFluidContained(itemStack);
     * if (contained == null) {
     * Minecraft.getMinecraft().player.sendMessage(new TextComponentTranslation("disk_terminal.error.fluid_cell_item"));
     * return false;
     * }
     * IStorageChannel<IAEFluidStack> fluidChannel =
     * AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
     * IAEFluidStack aeFluidStack = fluidChannel.createStack(contained);
     * if (aeFluidStack == null) return false;
     * stack = aeFluidStack.asItemStackRepresentation();
     * } else {
     * stack = itemStack;
     * }
     * } else if (ingredient instanceof FluidStack) {
     * if (!cell.isFluid()) {
     * Minecraft.getMinecraft().player.sendMessage(new TextComponentTranslation("disk_terminal.error.item_cell_fluid"));
     * return false;
     * }
     * FluidStack fluidStack = (FluidStack) ingredient;
     * IStorageChannel<IAEFluidStack> fluidChannel =
     * AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
     * IAEFluidStack aeFluidStack = fluidChannel.createStack(fluidStack);
     * if (aeFluidStack == null) return false;
     * stack = aeFluidStack.asItemStackRepresentation();
     * } else if (ingredient instanceof EnchantmentData) {
     * if (cell.isFluid()) {
     * Minecraft.getMinecraft().player.sendMessage(new TextComponentTranslation("disk_terminal.error.fluid_cell_item"));
     * return false;
     * }
     * EnchantmentData enchantData = (EnchantmentData) ingredient;
     * stack = ItemEnchantedBook.getEnchantedItemStack(enchantData);
     * } else {
     * return false;
     * }
     * if (ItemStacks.isEmpty(stack)) return false;
     * int targetSlot = slotIndex;
     * if (!ItemStacks.isEmpty(editablePartition.get(slotIndex))) {
     * targetSlot = findEmptySlot();
     * if (targetSlot == -1) return false;
     * }
     * editablePartition.set(targetSlot, stack.copy());
     * DiskTerminalNetwork.INSTANCE.sendToServer(new PacketPartitionAction(
     * cell.getParentStorageId(),
     * cell.getSlot(),
     * PacketPartitionAction.Action.ADD_ITEM,
     * targetSlot,
     * stack
     * ));
     * return true;
     * }
     */

    private int findEmptySlot() {
        for (int i = 0; i < editablePartition.size(); i++) {
            if (ItemStacks.isEmpty(editablePartition.get(i))) return i;
        }

        return -1;
    }

    public boolean isInsidePopup(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * Get JEI ghost ingredient targets for this popup.
     * The parent GUI will wrap these to handle clearing the drag state.
     */
    public List<GhostTarget<?>> getGhostTargets() {
        List<GhostTarget<?>> targets = new ArrayList<>();
        int slotStartY = y + HEADER_HEIGHT;

        for (int i = 0; i < MAX_PARTITION_SLOTS; i++) {
            final int slotIndex = i;
            int slotX = x + slotOffsetX + (i % SLOTS_PER_ROW) * SLOT_SIZE;
            int slotY = slotStartY + (i / SLOTS_PER_ROW) * SLOT_SIZE;

            Rectangle area = new Rectangle(slotX, slotY, SLOT_SIZE - 1, SLOT_SIZE - 1);

            targets.add(new GhostTarget<Object>() {

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

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    // ---- Drawing helpers (consistent with SlotsLine) ----

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
