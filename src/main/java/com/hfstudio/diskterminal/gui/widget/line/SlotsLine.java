package com.hfstudio.diskterminal.gui.widget.line;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import com.hfstudio.diskterminal.gui.ComparisonUtils;
import com.hfstudio.diskterminal.gui.GuiConstants;
import com.hfstudio.diskterminal.gui.widget.AbstractWidget;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.util.ReadableNumberConverter;

/**
 * A line widget that renders a grid of content or partition slots.
 * <p>
 * Supports two modes:
 * - **Content mode**: Shows item contents with count labels and partition indicators ("P").
 * Click toggles the item into/out of partition.
 * - **Partition mode**: Shows partition entries with amber tint.
 * Click sets/clears individual partition slots. Supports NEI ghost ingredient drop.
 * <p>
 * Configuration:
 * - {@code slotsPerRow}: Number of slots per row (8 for cells, 9 for storage buses)
 * - {@code slotsXOffset}: X offset from GUI left for the first slot
 * - {@code startIndex}: Index into the data list for the first slot in this row
 *
 * @see CellSlotsLine
 * @see ContinuationLine
 */
public class SlotsLine extends AbstractLine {

    /**
     * Determines the behavior and visual style of the slot grid.
     */
    public enum SlotMode {
        /** Show item contents with counts and partition indicators */
        CONTENT,
        /** Show partition entries with amber tint, supports drag-and-drop */
        PARTITION
    }

    /**
     * Callback for slot interactions (click on content or partition slot).
     */
    @FunctionalInterface
    public interface SlotClickCallback {

        /**
         * @param slotIndex   The absolute index into the data list
         * @param mouseButton Mouse button (0=left, 1=right)
         */
        void onSlotClicked(int slotIndex, int mouseButton);
    }

    /**
     * Tracks a visible partition slot for NEI ghost ingredient integration.
     */
    public static class PartitionSlotTarget {

        public final int absoluteIndex;
        public final int absX;
        public final int absY;
        public final int width;
        public final int height;

        public PartitionSlotTarget(int absoluteIndex, int absX, int absY, int width, int height) {
            this.absoluteIndex = absoluteIndex;
            this.absX = absX;
            this.absY = absY;
            this.width = width;
            this.height = height;
        }
    }

    private static final int SIZE = GuiConstants.MINI_SLOT_SIZE;

    protected final int slotsPerRow;
    protected final int slotsXOffset;
    protected final SlotMode mode;
    protected final FontRenderer fontRenderer;
    protected final RenderItem itemRender;

    /** Supplier for the items to display (content or partition list) */
    protected Supplier<List<ItemStack>> itemsSupplier;

    /** Supplier for the partition list (used in content mode for the "P" indicator) */
    protected Supplier<List<ItemStack>> partitionSupplier;

    /** Supplier for item counts (used in content mode, index-aligned with items) */
    protected Supplier<ContentCountProvider> countProvider;
    protected IntFunction<String> slotTypeProvider;
    protected IntFunction<String> contentTypeProvider;

    /** Starting index into the data list for this row */
    protected int startIndex;

    /** Maximum number of slots allowed */
    protected int maxSlots = Integer.MAX_VALUE;
    protected int visibleSlotCount = -1;

    /** Absolute GUI position offsets for NEI target registration */
    protected int guiLeft;
    protected int guiTop;

    // Hover tracking (computed during draw, consumed by tooltip/click)
    protected int hoveredSlotIndex = -1;
    protected ItemStack hoveredStack = null;

    // NEI targets accumulated during draw
    protected final List<PartitionSlotTarget> partitionTargets = new ArrayList<>();

    protected SlotClickCallback slotClickCallback;

    /** Supplier for the selection state (selected lines get a highlight overlay) */
    protected Supplier<Boolean> selectedSupplier;

    /** Whether to draw a horizontal separator line at the top of this row */
    protected boolean drawTopSeparator = false;
    protected int separatorRightOffset = 0;

    /**
     * @param y            Y position relative to GUI
     * @param slotsPerRow  Number of slots per row (8 for cells, 9 for buses)
     * @param slotsXOffset X offset from GUI left where slots start
     * @param mode         Content or Partition mode
     * @param startIndex   Index into data list for first slot
     * @param fontRenderer Font renderer
     * @param itemRender   Item renderer
     */
    public SlotsLine(int y, int slotsPerRow, int slotsXOffset, SlotMode mode, int startIndex, FontRenderer fontRenderer,
        RenderItem itemRender) {
        super(0, y, GuiConstants.CONTENT_RIGHT_EDGE);
        this.slotsPerRow = slotsPerRow;
        this.slotsXOffset = slotsXOffset;
        this.mode = mode;
        this.startIndex = startIndex;
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
    }

    public void setItemsSupplier(Supplier<List<ItemStack>> supplier) {
        this.itemsSupplier = supplier;
    }

    public void setPartitionSupplier(Supplier<List<ItemStack>> supplier) {
        this.partitionSupplier = supplier;
    }

    public void setCountProvider(Supplier<ContentCountProvider> provider) {
        this.countProvider = provider;
    }

    public void setSlotTypeProvider(IntFunction<String> provider) {
        this.slotTypeProvider = provider;
    }

    public void setContentTypeProvider(IntFunction<String> provider) {
        this.contentTypeProvider = provider;
    }

    public void setSlotClickCallback(SlotClickCallback callback) {
        this.slotClickCallback = callback;
    }

    /**
     * Set the selection state supplier. When selected, the line gets a
     * selection highlight overlay (for batch keybind operations like quick-add).
     */
    public void setSelectedSupplier(Supplier<Boolean> supplier) {
        this.selectedSupplier = supplier;
    }

    /**
     * Get the X offset where the slot grid starts.
     * Used by the tab widget to distinguish clicks on the cell/card area
     * (before the slots) from clicks on the content/partition slot area.
     */
    public int getSlotsXOffset() {
        return slotsXOffset;
    }

    public void setMaxSlots(int maxSlots) {
        this.maxSlots = maxSlots;
    }

    public void setVisibleSlotCount(int visibleSlotCount) {
        this.visibleSlotCount = visibleSlotCount;
    }

    public void setGuiOffsets(int guiLeft, int guiTop) {
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
    }

    public void setSeparatorRightOffset(int separatorRightOffset) {
        this.separatorRightOffset = separatorRightOffset;
    }

    /**
     * Get the NEI partition slot targets accumulated during the last draw.
     * Only populated in PARTITION mode.
     */
    public List<PartitionSlotTarget> getPartitionTargets() {
        return Collections.unmodifiableList(partitionTargets);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        // Draw horizontal separator at top if requested (before selection background)
        if (drawTopSeparator) {
            int separatorWidth = GuiConstants.CONTENT_RIGHT_EDGE - GuiConstants.GUI_INDENT + separatorRightOffset;
            GuiConstants.drawTerminalUpperBorderLine(GuiConstants.GUI_INDENT + 1, y - 1, separatorWidth);
        }

        // Draw selection background first (below everything else)
        boolean isSelected = selectedSupplier != null && selectedSupplier.get();
        if (isSelected) {
            Gui.drawRect(
                GuiConstants.GUI_INDENT,
                y,
                GuiConstants.CONTENT_RIGHT_EDGE,
                y + rowHeight,
                GuiConstants.COLOR_SELECTION);
        }

        // Draw tree lines first (background layer)
        drawTreeLines(mouseX, mouseY);

        // Reset hover state
        hoveredSlotIndex = -1;
        hoveredStack = null;
        partitionTargets.clear();

        // Draw slot grid
        if (mode == SlotMode.CONTENT) {
            drawContentSlots(mouseX, mouseY);
        } else {
            drawPartitionSlots(mouseX, mouseY);
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        // Let tree button handle click first
        if (super.handleClick(mouseX, mouseY, button)) return true;

        if (!visible || hoveredSlotIndex < 0) return false;
        if (slotClickCallback == null) return false;

        slotClickCallback.onSlotClicked(hoveredSlotIndex, button);

        return true;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        // Check tree button tooltip first
        List<String> buttonTooltip = super.getTooltip(mouseX, mouseY);
        if (!buttonTooltip.isEmpty()) return buttonTooltip;

        // Slot tooltip is handled by the parent tab/GUI since it requires
        // rendering an item tooltip (which needs the full GUI context)
        return Collections.emptyList();
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return null;

        return hoveredStack;
    }

    protected void drawContentSlots(int mouseX, int mouseY) {
        List<ItemStack> items = itemsSupplier != null ? itemsSupplier.get() : Collections.emptyList();
        List<ItemStack> partition = partitionSupplier != null ? partitionSupplier.get() : Collections.emptyList();
        ContentCountProvider counts = countProvider != null ? countProvider.get() : null;

        int rowSlots = getVisibleSlotsForRow();
        for (int x = slotsXOffset; x < slotsXOffset + (rowSlots * SIZE); x += SIZE) {
            int localIndex = (x - slotsXOffset) / SIZE;
            drawSlotBackground(x, y, startIndex + localIndex, contentTypeProvider);
        }

        int slots = Integer.min(startIndex + rowSlots, items.size()) - startIndex;
        boolean[] drawPartitionIndicators = new boolean[Math.max(slots, 0)];
        int hoveredLocalIndex = -1;

        for (int i = 0; i < slots; i++) {
            int absIndex = startIndex + i;
            int slotX = slotsXOffset + (i * SIZE);

            ItemStack stack = items.get(absIndex);
            if (ItemStacks.isEmpty(stack)) continue;

            renderItemStack(stack, slotX, y);

            if (contentTypeProvider != null && ComparisonUtils.isInPartition(
                stack,
                contentTypeProvider.apply(absIndex),
                partition,
                slotTypeProvider != null ? slotTypeProvider::apply : ignored -> "item")) {
                drawPartitionIndicators[i] = true;
            }

            if (mouseX >= slotX && mouseX < slotX + SIZE && mouseY >= y && mouseY < y + SIZE) {
                hoveredLocalIndex = i;
                hoveredSlotIndex = absIndex;
                hoveredStack = stack;
            }
        }

        if (hoveredLocalIndex >= 0) {
            int slotX = slotsXOffset + (hoveredLocalIndex * SIZE);
            drawSlotHoverHighlight(slotX, y);
        }

        for (int i = 0; i < slots; i++) {
            int absIndex = startIndex + i;
            int slotX = slotsXOffset + (i * SIZE);
            ItemStack stack = items.get(absIndex);
            if (ItemStacks.isEmpty(stack)) continue;

            if (counts != null) {
                drawItemCount(counts.getCount(absIndex), slotX, y);
            }

            if (drawPartitionIndicators[i]) {
                drawPartitionIndicator(slotX, y);
            }
        }
    }

    protected void drawPartitionSlots(int mouseX, int mouseY) {
        List<ItemStack> partition = itemsSupplier != null ? itemsSupplier.get() : Collections.emptyList();

        // Only draw slot backgrounds for slots that are within the valid range
        int rowSlots = getVisibleSlotsForRow();
        for (int i = 0; i < rowSlots; i++) {
            int absIndex = startIndex + i;
            if (absIndex >= maxSlots) break;

            int slotBgX = slotsXOffset + (i * SIZE);
            drawPartitionSlotBackground(slotBgX, y, absIndex);
        }

        for (int i = 0; i < rowSlots; i++) {
            int absIndex = startIndex + i;
            if (absIndex >= maxSlots) break;

            int slotX = slotsXOffset + (i * SIZE);

            // Register NEI ghost target
            partitionTargets.add(new PartitionSlotTarget(absIndex, guiLeft + slotX, guiTop + y, SIZE, SIZE));

            // Draw partition item if present
            ItemStack partItem = absIndex < partition.size() ? partition.get(absIndex) : null;
            if (!ItemStacks.isEmpty(partItem)) {
                renderItemStack(partItem, slotX, y);
            }

            // Check hover
            if (mouseX >= slotX && mouseX < slotX + SIZE && mouseY >= y && mouseY < y + SIZE) {
                drawSlotHoverHighlight(slotX, y);
                hoveredSlotIndex = absIndex;

                if (!ItemStacks.isEmpty(partItem)) {
                    hoveredStack = partItem;
                }
            }
        }
    }

    protected void drawSlotBackground(int slotX, int slotY, int index, IntFunction<String> typeProvider) {
        int texX = slotTextureX(typeProvider != null ? typeProvider.apply(index) : "item", false);
        int texY = GuiConstants.MINI_SLOT_Y;
        GuiConstants.drawAtlasSprite(slotX, slotY, texX, texY, SIZE, SIZE);
    }

    protected void drawPartitionSlotBackground(int slotX, int slotY, int index) {
        int texX = slotTextureX(slotTypeProvider != null ? slotTypeProvider.apply(index) : "item", true);
        int texY = GuiConstants.MINI_SLOT_Y;
        GuiConstants.drawAtlasSprite(slotX, slotY, texX, texY, SIZE, SIZE);
    }

    protected void drawSlotHoverHighlight(int slotX, int slotY) {
        Gui.drawRect(slotX + 1, slotY + 1, slotX + SIZE - 1, slotY + SIZE - 1, GuiConstants.COLOR_HOVER_HIGHLIGHT);
    }

    private int slotTextureX(String stackTypeId, boolean partition) {
        if ("fluid".equals(stackTypeId)) return 36;
        if ("essentia".equals(stackTypeId)) return 54;

        return partition ? GuiConstants.MINI_SLOT_X + SIZE : GuiConstants.MINI_SLOT_X;
    }

    private int getVisibleSlotsForRow() {
        if (visibleSlotCount > 0) return Math.min(slotsPerRow, visibleSlotCount);

        return slotsPerRow;
    }

    protected void renderItemStack(ItemStack stack, int renderX, int renderY) {
        AbstractWidget.renderItemStack(itemRender, stack, renderX + 1, renderY + 1);
    }

    protected void drawItemCount(long count, int slotX, int slotY) {
        String countStr = formatItemCount(count);
        if (countStr.isEmpty()) return;

        AbstractWidget.drawItemOverlayText(fontRenderer, countStr, slotX, slotY, SIZE, 0xFFFFFF);
    }

    protected void drawPartitionIndicator(int slotX, int slotY) {
        AbstractWidget.drawPartitionIndicator(fontRenderer, slotX, slotY, GuiConstants.COLOR_PARTITION_INDICATOR);
    }

    private String formatItemCount(long count) {
        if (count < 1000) return String.valueOf(count);

        return ReadableNumberConverter.INSTANCE.toWideReadableForm(count);
    }

    /**
     * Provider interface for getting item counts by index.
     * Separates count access from the data model to support
     * different backends (CellInfo, StorageBusInfo, etc.).
     */
    @FunctionalInterface
    public interface ContentCountProvider {

        long getCount(int index);
    }
}
