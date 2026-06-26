package com.hfstudio.diskterminal.gui.widget.tab;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.hfstudio.diskterminal.client.CellContentRow;
import com.hfstudio.diskterminal.client.CellInfo;
import com.hfstudio.diskterminal.client.EmptySlotInfo;
import com.hfstudio.diskterminal.client.KeyBindings;
import com.hfstudio.diskterminal.client.SearchFilterMode;
import com.hfstudio.diskterminal.client.StorageInfo;
import com.hfstudio.diskterminal.client.TabStateManager;
import com.hfstudio.diskterminal.config.DiskTerminalServerConfig;
import com.hfstudio.diskterminal.gui.GuiConstants;
import com.hfstudio.diskterminal.gui.PriorityFieldManager;
import com.hfstudio.diskterminal.gui.handler.GhostIngredientHandler;
import com.hfstudio.diskterminal.gui.handler.GhostTarget;
import com.hfstudio.diskterminal.gui.handler.QuickPartitionHandler;
import com.hfstudio.diskterminal.gui.handler.TerminalDataManager;
import com.hfstudio.diskterminal.gui.widget.CardsDisplay;
import com.hfstudio.diskterminal.gui.widget.DoubleClickTracker;
import com.hfstudio.diskterminal.gui.widget.IWidget;
import com.hfstudio.diskterminal.gui.widget.button.ButtonType;
import com.hfstudio.diskterminal.gui.widget.button.SmallButton;
import com.hfstudio.diskterminal.gui.widget.header.AbstractHeader;
import com.hfstudio.diskterminal.gui.widget.header.StorageHeader;
import com.hfstudio.diskterminal.gui.widget.line.AbstractLine;
import com.hfstudio.diskterminal.gui.widget.line.CellSlotsLine;
import com.hfstudio.diskterminal.gui.widget.line.ContinuationLine;
import com.hfstudio.diskterminal.gui.widget.line.SlotsLine;
import com.hfstudio.diskterminal.integration.ThaumicEnergisticsIntegration;
import com.hfstudio.diskterminal.network.PacketExtractUpgrade;
import com.hfstudio.diskterminal.network.PacketInsertCell;
import com.hfstudio.diskterminal.network.PacketPartitionAction;
import com.hfstudio.diskterminal.network.PacketPickupCell;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.AEApi;

/**
 * Tab widget for the Inventory (Tab 1) and Partition (Tab 2) tabs.
 * <p>
 * Both tabs display the same structure (StorageInfo headers + cell content rows)
 * but differ in the slot mode (CONTENT vs PARTITION) and tree button type:
 * <ul>
 * <li><b>Inventory tab:</b> Shows cell contents with counts. Tree button is
 * DO_PARTITION (adds item to partition). Content slots show "P" indicator
 * for items that are in the partition.</li>
 * <li><b>Partition tab:</b> Shows cell partitions with amber tint. Tree button is
 * CLEAR_PARTITION (removes partition entry). Supports NEI ghost drops.</li>
 * </ul>
 *
 * Each row in the line list is one of:
 * <ul>
 * <li>{@link StorageInfo} → {@link StorageHeader}</li>
 * <li>{@link CellContentRow} (first row) → {@link CellSlotsLine} (with cell slot + cards)</li>
 * <li>{@link CellContentRow} (continuation) → {@link ContinuationLine}</li>
 * <li>{@link EmptySlotInfo} → {@link CellSlotsLine} (empty cell slot placeholder)</li>
 * </ul>
 */
public class CellContentTabWidget extends AbstractTabWidget {

    /** Slots per row for cell content: 8 */
    private static final int SLOTS_PER_ROW = GuiConstants.CELL_SLOTS_PER_ROW;

    /**
     * X offset where content/partition slots begin.
     * After cell slot (16px) + cards area + gap.
     */
    private static final int SLOTS_X_OFFSET = GuiConstants.CELL_INDENT + 20;

    private final SlotsLine.SlotMode slotMode;
    private final ButtonType treeButtonType;
    private final boolean isPartitionMode;

    /**
     * @param slotMode CONTENT for Inventory tab, PARTITION for Partition tab
     */
    public CellContentTabWidget(SlotsLine.SlotMode slotMode, FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
        this.slotMode = slotMode;
        this.isPartitionMode = (slotMode == SlotsLine.SlotMode.PARTITION);
        this.treeButtonType = isPartitionMode ? ButtonType.CLEAR_PARTITION : ButtonType.DO_PARTITION;
    }

    @Override
    public List<Object> getLines(TerminalDataManager dataManager) {
        return isPartitionMode ? dataManager.getPartitionLines() : dataManager.getInventoryLines();
    }

    @Override
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        return isPartitionMode ? SearchFilterMode.PARTITION : SearchFilterMode.INVENTORY;
    }

    @Override
    public boolean showSearchModeButton() {
        return false;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();

        if (isPartitionMode) {
            lines.add(I18n.format("gui.disk_terminal.controls.keybind_targets"));
            lines.add("");

            String notSet = I18n.format("gui.disk_terminal.controls.key_not_set");

            String autoKey = KeyBindings.QUICK_PARTITION_AUTO.isBound()
                ? KeyBindings.QUICK_PARTITION_AUTO.getDisplayName()
                : notSet;
            lines.add(I18n.format("gui.disk_terminal.controls.key_auto", autoKey));
            if (!autoKey.equals(notSet)) {
                lines.add(I18n.format("gui.disk_terminal.controls.auto_warning"));
            }
            lines.add("");

            String itemKey = KeyBindings.QUICK_PARTITION_ITEM.isBound()
                ? KeyBindings.QUICK_PARTITION_ITEM.getDisplayName()
                : notSet;
            lines.add(I18n.format("gui.disk_terminal.controls.key_item", itemKey));

            String fluidKey = KeyBindings.QUICK_PARTITION_FLUID.isBound()
                ? KeyBindings.QUICK_PARTITION_FLUID.getDisplayName()
                : notSet;
            lines.add(I18n.format("gui.disk_terminal.controls.key_fluid", fluidKey));

            String essentiaKey = KeyBindings.QUICK_PARTITION_ESSENTIA.isBound()
                ? KeyBindings.QUICK_PARTITION_ESSENTIA.getDisplayName()
                : notSet;
            lines.add(I18n.format("gui.disk_terminal.controls.key_essentia", essentiaKey));

            if (!essentiaKey.equals(notSet) && !ThaumicEnergisticsIntegration.isModLoaded()) {
                lines.add(I18n.format("gui.disk_terminal.controls.essentia_warning"));
            }

            lines.add("");
            lines.add(I18n.format("gui.disk_terminal.controls.nei_drag"));
            lines.add(I18n.format("gui.disk_terminal.controls.click_to_remove"));
        } else {
            lines.add(I18n.format("gui.disk_terminal.controls.partition_indicator"));
            lines.add(I18n.format("gui.disk_terminal.controls.click_partition_toggle"));
        }

        lines.add(I18n.format("gui.disk_terminal.controls.double_click_storage"));
        lines.add(I18n.format("gui.disk_terminal.right_click_rename"));

        return lines;
    }

    @Override
    public ItemStack getTabIcon() {
        if (isPartitionMode) {
            return AEApi.instance()
                .definitions()
                .blocks()
                .cellWorkbench()
                .maybeStack(1)
                .orNull();
        }

        return AEApi.instance()
            .definitions()
            .blocks()
            .chest()
            .maybeStack(1)
            .orNull();
    }

    @Override
    public String getTabTooltip() {
        if (isPartitionMode) {
            return I18n.format("gui.disk_terminal.tab.partition.tooltip");
        }

        return I18n.format("gui.disk_terminal.tab.inventory.tooltip");
    }

    @Override
    public boolean handleTabKeyTyped(int keyCode) {
        if (!isPartitionMode) return false;

        QuickPartitionHandler.PartitionType type = null;

        if (KeyBindings.QUICK_PARTITION_AUTO.isActiveAndMatches(keyCode)) {
            type = QuickPartitionHandler.PartitionType.AUTO;
        } else if (KeyBindings.QUICK_PARTITION_ITEM.isActiveAndMatches(keyCode)) {
            type = QuickPartitionHandler.PartitionType.ITEM;
        } else if (KeyBindings.QUICK_PARTITION_FLUID.isActiveAndMatches(keyCode)) {
            type = QuickPartitionHandler.PartitionType.FLUID;
        } else if (KeyBindings.QUICK_PARTITION_ESSENTIA.isActiveAndMatches(keyCode)) {
            type = QuickPartitionHandler.PartitionType.ESSENTIA;
        }

        if (type == null) return false;

        QuickPartitionHandler.QuickPartitionResult result = QuickPartitionHandler.attemptQuickPartition(
            type,
            getLines(guiContext.getDataManager()),
            guiContext.getDataManager()
                .getStorageMap());

        if (result.success) {
            guiContext.showSuccess(result.message);
            if (result.scrollToLine >= 0) guiContext.scrollToLine(result.scrollToLine);
        } else {
            guiContext.showError(result.message);
        }

        return true;
    }

    @Override
    public List<GhostTarget<?>> getPhantomTargets(Object ingredient) {
        if (!isPartitionMode) return Collections.emptyList();

        List<GhostTarget<?>> targets = new ArrayList<>();
        for (Map.Entry<IWidget, Object> entry : getWidgetDataMap().entrySet()) {
            IWidget widget = entry.getKey();
            Object data = entry.getValue();
            if (!(widget instanceof SlotsLine slotsLine)) continue;

            List<SlotsLine.PartitionSlotTarget> slotTargets = slotsLine.getPartitionTargets();
            if (slotTargets.isEmpty()) continue;
            if (!(data instanceof CellContentRow)) continue;

            CellInfo cell = ((CellContentRow) data).getCell();
            for (SlotsLine.PartitionSlotTarget slot : slotTargets) {
                Rectangle targetArea = clipTargetToContentViewport(slot);
                if (targetArea == null) continue;

                targets.add(new GhostTarget<>() {

                    @Override
                    public Rectangle getArea() {
                        return targetArea;
                    }

                    @Override
                    public void accept(Object ing) {
                        // Defensive: verify the target index is still valid for this cell's type limit
                        if (slot.absoluteIndex < 0 || slot.absoluteIndex >= cell.getTotalTypes()) return;

                        ItemStack stack = GhostIngredientHandler
                            .convertIngredientForType(ing, cell.getStackTypeId(), false);
                        if (!ItemStacks.isEmpty(stack)) {
                            guiContext.sendPacket(
                                new PacketPartitionAction(
                                    cell.getParentStorageId(),
                                    cell.getSlot(),
                                    PacketPartitionAction.Action.ADD_ITEM,
                                    slot.absoluteIndex,
                                    stack));
                        }
                    }
                });
            }
        }

        return targets;
    }

    @Override
    protected IWidget createRowWidget(Object lineData, int y, List<?> allLines, int lineIndex) {
        if (lineData instanceof StorageInfo) {
            return createStorageHeader((StorageInfo) lineData, y);
        }

        if (lineData instanceof CellContentRow) {
            return createCellContentLine((CellContentRow) lineData, y);
        }

        if (lineData instanceof EmptySlotInfo) {
            return createEmptySlotLine((EmptySlotInfo) lineData, y);
        }

        return null;
    }

    @Override
    protected boolean isContentLine(List<?> allLines, int index) {
        if (index < 0 || index >= allLines.size()) return false;

        Object line = allLines.get(index);

        return line instanceof CellContentRow || line instanceof EmptySlotInfo;
    }

    @Override
    protected void propagateTreeLines(List<?> allLines, int scrollOffset) {
        int lastCutY = GuiConstants.CONTENT_START_Y;
        boolean hasContentAbove = scrollOffset > 0 && isContentLine(allLines, scrollOffset - 1);

        for (int i = 0; i < visibleRows.size(); i++) {
            IWidget widget = visibleRows.get(i);
            int lineIndex = getLineIndexForVisibleRow(i);

            if (widget instanceof AbstractHeader header) {
                header.setDrawConnector(hasContentBelow(allLines, lineIndex));
                lastCutY = header.getConnectorY();

                continue;
            }

            if (!(widget instanceof AbstractLine line)) continue;

            boolean drawTreeLine = shouldDrawCellTreeLine(allLines, lineIndex);
            int lineAboveCutY = (i == 0 && hasContentAbove) ? GuiConstants.CONTENT_START_Y : lastCutY;
            line.setTreeLineParams(drawTreeLine, lineAboveCutY);

            if (!drawTreeLine) continue;

            lastCutY = line.getTreeLineCutY();
        }

        int lastVisibleIndex = getLastVisibleLineIndex(scrollOffset);
        if (lastVisibleIndex < 0) {
            bottomContinuationFromY = -1;

            return;
        }

        int nextIndex = lastVisibleIndex + 1;
        if (nextIndex >= allLines.size() || !shouldDrawCellTreeLine(allLines, nextIndex)) {
            bottomContinuationFromY = -1;

            return;
        }

        bottomContinuationFromY = lastCutY;
    }

    /**
     * Continuation rows only carry the storage tree trunk when another cell slot
     * exists later in the same storage section. Extra rows of the last cell are
     * part of that cell's body, not separate tree nodes.
     */
    private boolean shouldDrawCellTreeLine(List<?> allLines, int index) {
        if (index < 0 || index >= allLines.size()) return false;

        Object line = allLines.get(index);
        if (line instanceof EmptySlotInfo) return true;
        if (!(line instanceof CellContentRow row)) return false;

        if (row.isFirstRow()) return true;

        return hasCellBelowInStorage(allLines, index);
    }

    private boolean hasCellBelowInStorage(List<?> allLines, int index) {
        for (int i = index + 1; i < allLines.size(); i++) {
            Object next = allLines.get(i);

            if (next instanceof StorageInfo) return false;
            if (next instanceof EmptySlotInfo) return true;
            if (!(next instanceof CellContentRow)) continue;
            if (((CellContentRow) next).isFirstRow()) return true;
        }

        return false;
    }

    private StorageHeader createStorageHeader(StorageInfo storage, int y) {
        StorageHeader header = new StorageHeader(y, fontRenderer, itemRender);
        header.setIconSupplier(storage::getBlockItem);
        header.setNameSupplier(storage::getName);
        header.setHasCustomNameSupplier(storage::hasCustomName);
        header.setLocationSupplier(storage::getLocationString);

        // Use TabStateManager for expand/collapse state (persists across rebuilds)
        // Determine tab type based on slot mode (INVENTORY or PARTITION)
        TabStateManager.TabType tabType = isPartitionMode ? TabStateManager.TabType.PARTITION
            : TabStateManager.TabType.INVENTORY;
        header.setExpandedSupplier(
            () -> TabStateManager.getInstance()
                .isExpanded(tabType, storage.getId()));

        // Rename info: header handles right-click directly via InlineRenameManager
        int renameRightEdge = GuiConstants.CONTENT_RIGHT_EDGE - PriorityFieldManager.FIELD_WIDTH
            - PriorityFieldManager.RIGHT_MARGIN
            - 4;
        header.setRenameInfo(storage, GuiConstants.GUI_INDENT + 20 - 2, 0, renameRightEdge);
        header.setOnNameDoubleClick(
            () -> guiContext.highlightInWorld(storage.getPos(), storage.getDimension(), storage.getName()),
            DoubleClickTracker.storageTargetId(storage.getId()));
        header.setOnExpandToggle(() -> {
            TabStateManager.getInstance()
                .toggleExpanded(tabType, storage.getId());
            guiContext.rebuildAndUpdateScrollbar();
        });

        // Priority field: header registers its own field with the singleton during draw
        header.setPrioritizable(storage);
        header.setGuiOffsets(guiLeft, guiTop);

        return header;
    }

    private IWidget createCellContentLine(CellContentRow row, int y) {
        CellInfo cell = row.getCell();

        if (row.isFirstRow()) {
            return createFirstRow(cell, row.getStartIndex(), y);
        }

        return createContinuationRow(cell, row.getStartIndex(), y);
    }

    /**
     * First row of a cell: cell slot + upgrade cards + content/partition slots + tree button.
     */
    private CellSlotsLine createFirstRow(CellInfo cell, int startIndex, int y) {
        CellSlotsLine line = new CellSlotsLine(
            y,
            SLOTS_PER_ROW,
            SLOTS_X_OFFSET,
            slotMode,
            startIndex,
            fontRenderer,
            itemRender);

        // Cell slot configuration
        line.setCellItemSupplier(cell::getCellItem);
        line.setCellFilledSupplier(() -> !ItemStacks.isEmpty(cell.getCellItem()));

        // Cell slot click (insert/extract cell)
        line.setCellSlotCallback(button -> {
            if (button != 0) return;

            ItemStack heldStack = guiContext.getHeldStack();
            if (ItemStacks.isEmpty(cell.getCellItem()) && !ItemStacks.isEmpty(heldStack)) {
                // Insert held cell into this slot
                guiContext.sendPacket(new PacketInsertCell(cell.getParentStorageId(), cell.getSlot()));
            } else if (!ItemStacks.isEmpty(cell.getCellItem())) {
                // Pick up cell: shift = to inventory, normal click = to cursor (swap/pickup)
                boolean toInventory = guiContext.isShiftDown();
                guiContext.sendPacket(new PacketPickupCell(cell.getParentStorageId(), cell.getSlot(), toInventory));
            }
        });

        // Configure content/partition data suppliers
        configureSlotData(line, cell);

        // Upgrade cards
        CardsDisplay cards = createCellCardsDisplay(cell, y, this::handleCardClick);
        if (cards != null) line.setCardsDisplay(cards);

        // Tree junction button (DoPartition or ClearPartition)
        SmallButton treeBtn = new SmallButton(0, 0, treeButtonType, () -> {
            if (isPartitionMode) {
                guiContext.sendPacket(
                    new PacketPartitionAction(
                        cell.getParentStorageId(),
                        cell.getSlot(),
                        PacketPartitionAction.Action.CLEAR_ALL));
            } else {
                guiContext.sendPacket(
                    new PacketPartitionAction(
                        cell.getParentStorageId(),
                        cell.getSlot(),
                        PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS));
            }
        });
        line.setTreeButton(treeBtn);
        line.setTreeButtonXOffset(-4);

        // GUI offsets for NEI targets
        line.setGuiOffsets(guiLeft, guiTop);

        return line;
    }

    /**
     * Continuation row (not the first row for a cell): just content/partition slots.
     */
    private ContinuationLine createContinuationRow(CellInfo cell, int startIndex, int y) {
        ContinuationLine line = new ContinuationLine(
            y,
            SLOTS_PER_ROW,
            SLOTS_X_OFFSET,
            slotMode,
            startIndex,
            fontRenderer,
            itemRender);

        // Tabs 2/3 have no junction element (cell slot/button) on continuation rows,
        // so the horizontal branch would point at empty space.
        line.setDrawHorizontalBranch(false);
        configureSlotData(line, cell);
        line.setGuiOffsets(guiLeft, guiTop);

        return line;
    }

    /**
     * Configure the content/partition data suppliers on a SlotsLine.
     */
    private void configureSlotData(SlotsLine line, CellInfo cell) {
        if (slotMode == SlotsLine.SlotMode.CONTENT) {
            line.setItemsSupplier(cell::getContents);
            line.setPartitionSupplier(cell::getPartition);
            line.setCountProvider(() -> cell::getContentCount);
        } else {
            // Partition mode: partition list is the items source
            line.setItemsSupplier(cell::getPartition);
            line.setMaxSlots((int) cell.getTotalTypes());
        }

        line.setSlotClickCallback((slotIndex, mouseButton) -> {
            if (mouseButton != 0) return;

            // Defensive: verify slotIndex is within the cell's actual type limit.
            // Prevents stale index values from sending wrong slot indices to the server,
            // which could happen if GUI data changed between draw (index computed) and click.
            if (slotIndex < 0 || slotIndex >= cell.getTotalTypes()) return;

            if (isPartitionMode) {
                ItemStack heldStack = guiContext.getHeldStack();
                List<ItemStack> partition = cell.getPartition();
                boolean slotOccupied = slotIndex < partition.size() && !ItemStacks.isEmpty(partition.get(slotIndex));

                if (!ItemStacks.isEmpty(heldStack)) {
                    ItemStack stackToSend = GhostIngredientHandler
                        .convertIngredientForType(heldStack, cell.getStackTypeId(), false);
                    if (ItemStacks.isEmpty(stackToSend)) return;

                    guiContext.sendPacket(
                        new PacketPartitionAction(
                            cell.getParentStorageId(),
                            cell.getSlot(),
                            PacketPartitionAction.Action.ADD_ITEM,
                            slotIndex,
                            stackToSend));
                } else if (slotOccupied) {
                    guiContext.sendPacket(
                        new PacketPartitionAction(
                            cell.getParentStorageId(),
                            cell.getSlot(),
                            PacketPartitionAction.Action.REMOVE_ITEM,
                            slotIndex));
                }
            } else {
                // Content mode: toggle partition for content item
                List<ItemStack> contents = cell.getContents();
                if (slotIndex < contents.size() && !ItemStacks.isEmpty(contents.get(slotIndex))) {
                    guiContext.sendPacket(
                        new PacketPartitionAction(
                            cell.getParentStorageId(),
                            cell.getSlot(),
                            PacketPartitionAction.Action.TOGGLE_ITEM,
                            contents.get(slotIndex)));
                }
            }
        });
    }

    /**
     * Empty cell slot: shows an empty cell slot placeholder (no content slots).
     */
    private CellSlotsLine createEmptySlotLine(EmptySlotInfo emptySlot, int y) {
        CellSlotsLine line = new CellSlotsLine(y, SLOTS_PER_ROW, SLOTS_X_OFFSET, slotMode, 0, fontRenderer, itemRender);

        // Empty cell slot (no item, not filled)
        line.setCellItemSupplier(() -> null);
        line.setCellFilledSupplier(() -> false);

        // Cell slot click (insert cell into empty slot)
        line.setCellSlotCallback(button -> {
            if (button != 0) return;

            ItemStack heldStack = guiContext.getHeldStack();
            if (!ItemStacks.isEmpty(heldStack)) {
                guiContext.sendPacket(new PacketInsertCell(emptySlot.getParentStorageId(), emptySlot.getSlot()));
            }
        });

        line.setGuiOffsets(guiLeft, guiTop);

        return line;
    }

    private void handleCardClick(CellInfo cell, int upgradeSlotIndex) {
        if (DiskTerminalServerConfig.isInitialized() && !DiskTerminalServerConfig.getInstance()
            .isUpgradeExtractEnabled()) {
            guiContext.showError("disk_terminal.error.upgrade_extract_disabled");
            return;
        }

        boolean toInventory = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        guiContext.sendPacket(
            PacketExtractUpgrade.forCell(cell.getParentStorageId(), cell.getSlot(), upgradeSlotIndex, toInventory));
    }
}
