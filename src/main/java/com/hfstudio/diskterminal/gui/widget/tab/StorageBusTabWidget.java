package com.hfstudio.diskterminal.gui.widget.tab;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.hfstudio.diskterminal.client.KeyBindings;
import com.hfstudio.diskterminal.client.SearchFilterMode;
import com.hfstudio.diskterminal.client.StorageBusContentRow;
import com.hfstudio.diskterminal.client.StorageBusInfo;
import com.hfstudio.diskterminal.client.TabStateManager;
import com.hfstudio.diskterminal.config.DiskTerminalServerConfig;
import com.hfstudio.diskterminal.gui.GuiConstants;
import com.hfstudio.diskterminal.gui.PriorityFieldManager;
import com.hfstudio.diskterminal.gui.handler.GhostIngredientHandler;
import com.hfstudio.diskterminal.gui.handler.GhostTarget;
import com.hfstudio.diskterminal.gui.handler.QuickPartitionHandler;
import com.hfstudio.diskterminal.gui.handler.TerminalDataManager;
import com.hfstudio.diskterminal.gui.overlay.MessageHelper;
import com.hfstudio.diskterminal.gui.widget.CardsDisplay;
import com.hfstudio.diskterminal.gui.widget.DoubleClickTracker;
import com.hfstudio.diskterminal.gui.widget.IWidget;
import com.hfstudio.diskterminal.gui.widget.button.ButtonType;
import com.hfstudio.diskterminal.gui.widget.button.SmallButton;
import com.hfstudio.diskterminal.gui.widget.header.StorageBusHeader;
import com.hfstudio.diskterminal.gui.widget.line.ContinuationLine;
import com.hfstudio.diskterminal.gui.widget.line.SlotsLine;
import com.hfstudio.diskterminal.integration.ThaumicEnergisticsIntegration;
import com.hfstudio.diskterminal.network.DiskTerminalNetwork;
import com.hfstudio.diskterminal.network.PacketExtractUpgrade;
import com.hfstudio.diskterminal.network.PacketStorageBusIOMode;
import com.hfstudio.diskterminal.network.PacketStorageBusPartitionAction;
import com.hfstudio.diskterminal.network.PacketUpgradeStorageBus;
import com.hfstudio.diskterminal.util.FluidStacks;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.AEApi;

/**
 * Tab widget for Storage Bus Inventory (Tab 4) and Storage Bus Partition (Tab 5) tabs.
 * <p>
 * Both tabs display storage bus groups with expandable content rows. Each row is either:
 * <ul>
 * <li>{@link StorageBusInfo} → {@link StorageBusHeader} (name, location, IO mode, expand)</li>
 * <li>{@link StorageBusContentRow} (first row) → {@link SlotsLine} with tree button</li>
 * <li>{@link StorageBusContentRow} (continuation) → {@link ContinuationLine}</li>
 * </ul>
 *
 * <h3>Slot mode differences</h3>
 * <ul>
 * <li><b>Inventory tab:</b> Shows bus contents with counts. Tree button is
 * DO_PARTITION (adds item to partition). Content slots show "P" indicator
 * for items that are in the partition.</li>
 * <li><b>Partition tab:</b> Shows bus partitions with amber tint. Tree button is
 * CLEAR_PARTITION (removes partition entry). Supports NEI ghost drops.</li>
 * </ul>
 *
 * Storage bus tabs use the shared storage bus row width at a narrower X offset (no inline cell slot).
 */
public class StorageBusTabWidget extends AbstractTabWidget {

    /** Slots per row for storage buses. */
    private static final int SLOTS_PER_ROW = GuiConstants.STORAGE_BUS_SLOTS_PER_ROW;

    /** X offset for content/partition slots */
    private static final int SLOTS_X_OFFSET = GuiConstants.CELL_INDENT + 5;
    private static final int SLOT_ROW_HEIGHT = GuiConstants.EXPANDED_SLOT_ROW_HEIGHT;
    private static final int SLOT_ROW_SEPARATOR_RIGHT_OFFSET = 2;

    private final SlotsLine.SlotMode slotMode;
    private final ButtonType treeButtonType;
    private final boolean isPartitionMode;

    /**
     * @param slotMode CONTENT for Storage Bus Inventory tab, PARTITION for Storage Bus Partition tab
     */
    public StorageBusTabWidget(SlotsLine.SlotMode slotMode, FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
        this.slotMode = slotMode;
        this.isPartitionMode = (slotMode == SlotsLine.SlotMode.PARTITION);
        this.treeButtonType = isPartitionMode ? ButtonType.CLEAR_PARTITION : ButtonType.DO_PARTITION;
    }

    @Override
    public List<Object> getLines(TerminalDataManager dataManager) {
        return isPartitionMode ? dataManager.getStorageBusPartitionLines() : dataManager.getStorageBusInventoryLines();
    }

    @Override
    public SearchFilterMode getEffectiveSearchMode(SearchFilterMode userSelectedMode) {
        return userSelectedMode;
    }

    @Override
    public boolean showSearchModeButton() {
        return true;
    }

    @Override
    protected int getRowStep(List<?> lines, int index) {
        return isContentLine(lines, index) ? SLOT_ROW_HEIGHT : GuiConstants.ROW_HEIGHT;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();

        if (isPartitionMode) {
            lines.add(
                    I18n.format(
                            "gui.disk_terminal.controls.storage_bus_add_key",
                            KeyBindings.ADD_TO_STORAGE_BUS.getDisplayName()));
            lines.add(I18n.format("gui.disk_terminal.controls.storage_bus_capacity"));
            lines.add("");
            lines.add(I18n.format("gui.disk_terminal.controls.nei_drag"));
            lines.add(I18n.format("gui.disk_terminal.controls.click_to_remove"));
        } else {
            lines.add(I18n.format("gui.disk_terminal.controls.filter_indicator"));
            lines.add(I18n.format("gui.disk_terminal.controls.click_to_remove"));
        }

        lines.add(I18n.format("gui.disk_terminal.controls.double_click_storage"));
        lines.add(I18n.format("gui.disk_terminal.right_click_rename"));

        return lines;
    }

    @Override
    public ItemStack getTabIcon() {
        // Returns the base storage bus icon; composite overlay is handled by TabRenderingHandler
        return AEApi.instance()
                .definitions()
                .parts()
                .storageBus()
                .maybeStack(1)
                .orNull();
    }

    @Override
    public String getTabTooltip() {
        if (isPartitionMode) {
            return I18n.format("gui.disk_terminal.tab.storage_bus_partition.tooltip");
        }

        return I18n.format("gui.disk_terminal.tab.storage_bus_inventory.tooltip");
    }

    @Override
    public boolean handleTabKeyTyped(int keyCode) {
        if (!isPartitionMode) return false;
        if (!KeyBindings.ADD_TO_STORAGE_BUS.isActiveAndMatches(keyCode)) return false;

        return handleAddToStorageBusKeybind(
                guiContext.getSelectedStorageBusIds(),
                guiContext.getSlotUnderMouse(),
                guiContext.getDataManager()
                        .getStorageBusMap());
    }

    @Override
    public boolean handleUpgradeClick(Object hoveredData, ItemStack heldStack, boolean isShiftClick) {
        if (isShiftClick) {
            // Shift-click: server finds first bus that can accept
            guiContext.sendPacket(new PacketUpgradeStorageBus(0, true));
            return true;
        }

        StorageBusInfo bus = null;
        if (hoveredData instanceof StorageBusInfo) bus = (StorageBusInfo) hoveredData;
        else if (hoveredData instanceof StorageBusContentRow)
            bus = ((StorageBusContentRow) hoveredData).getStorageBus();

        if (bus != null) {
            guiContext.sendPacket(new PacketUpgradeStorageBus(bus.getId(), false));
            return true;
        }

        return false;
    }

    @Override
    public boolean handleShiftUpgradeClick(ItemStack heldStack) {
        // Shift-click: server finds first bus that can accept
        guiContext.sendPacket(new PacketUpgradeStorageBus(0, true));
        return true;
    }

    @Override
    public boolean handleInventorySlotShiftClick(ItemStack upgradeStack, int sourceSlotIndex) {
        // Delegate target selection to the server so addon-specific slot rules are respected.
        guiContext.sendPacket(new PacketUpgradeStorageBus(0, true, sourceSlotIndex));

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
            if (!(data instanceof StorageBusContentRow)) continue;

            StorageBusInfo bus = ((StorageBusContentRow) data).getStorageBus();
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
                        ItemStack stack = GhostIngredientHandler
                                .convertIngredientForType(ing, bus.getStackTypeId(), true);
                        if (!ItemStacks.isEmpty(stack)) {
                            guiContext.sendPacket(
                                    new PacketStorageBusPartitionAction(
                                            bus.getId(),
                                            PacketStorageBusPartitionAction.Action.ADD_ITEM,
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
        if (lineData instanceof StorageBusInfo) {
            return createBusHeader((StorageBusInfo) lineData, y);
        }

        if (lineData instanceof StorageBusContentRow) {
            return createContentLine((StorageBusContentRow) lineData, y);
        }

        return null;
    }

    @Override
    protected boolean isContentLine(List<?> allLines, int index) {
        if (index < 0 || index >= allLines.size()) return false;

        return allLines.get(index) instanceof StorageBusContentRow;
    }

    private StorageBusHeader createBusHeader(StorageBusInfo bus, int y) {
        StorageBusHeader header = new StorageBusHeader(y, fontRenderer, itemRender);
        header.setIconSupplier(bus::getBusIcon);
        header.setOverlayIconSupplier(bus::getConnectedInventoryIcon);
        header.setConnectedIconTargetSupplier(bus::isConnectedIconTarget);
        header.setNameSupplier(bus::getLocalizedName);
        header.setHasCustomNameSupplier(bus::hasCustomName);
        // Use TabStateManager for expand/collapse state (persists across rebuilds)
        TabStateManager.TabType tabType = isPartitionMode ? TabStateManager.TabType.STORAGE_BUS_PARTITION
                : TabStateManager.TabType.STORAGE_BUS_INVENTORY;
        header.setExpandedSupplier(
                () -> TabStateManager.getInstance()
                        .isBusExpanded(tabType, bus.getId()));
        header.setLocationSupplier(bus::getLocationString);
        header.setAccessModeSupplier(bus::getAccessRestriction);
        header.setSupportsIOModeSupplier(bus::supportsIOMode);
        header.setModeButtonKindSupplier(bus::getHeaderModeButtonKind);
        header.setAutoPullEnabledSupplier(bus::isAutoPullEnabled);

        // Upgrade cards
        CardsDisplay cards = createBusCards(bus, y);
        if (cards != null) header.setCardsDisplay(cards);

        // Rename info: header handles right-click directly via InlineRenameManager
        if (bus.isRenameable()) {
            header.setRenameInfo(bus, GuiConstants.GUI_INDENT + 20 - 2, 0, getBusRenameRightEdge(bus));
        }
        header.setOnNameDoubleClick(
                () -> guiContext.highlightInWorld(bus.getPos(), bus.getDimension(), bus.getLocalizedName()),
                DoubleClickTracker.storageBusTargetId(bus.getId()));
        header.setOnExpandToggle(() -> {
            TabStateManager.getInstance()
                    .toggleBusExpanded(tabType, bus.getId());
            guiContext.rebuildAndUpdateScrollbar();
        });
        header.setOnIOModeClick(() -> guiContext.sendPacket(new PacketStorageBusIOMode(bus.getId())));

        // Header selection for quick-add (partition tab only)
        if (isPartitionMode) {
            header.setOnHeaderClick(() -> {
                long busId = bus.getId();
                Set<Long> selected = guiContext.getSelectedStorageBusIds();
                if (selected.contains(busId)) {
                    selected.remove(busId);
                } else {
                    selected.add(busId);
                }
            });
            header.setSelectedSupplier(
                    () -> guiContext.getSelectedStorageBusIds()
                            .contains(bus.getId()));
        }

        // Priority field: header registers its own field with the singleton during draw
        header.setPrioritizable(bus);
        header.setGuiOffsets(guiLeft, guiTop);

        return header;
    }

    private int getBusRenameRightEdge(StorageBusInfo bus) {
        int rightEdge = GuiConstants.EXPAND_ICON_X - 4;

        if (bus.supportsPriority()) {
            rightEdge = GuiConstants.CONTENT_RIGHT_EDGE - PriorityFieldManager.FIELD_WIDTH
                    - PriorityFieldManager.RIGHT_MARGIN
                    - 4;
        }

        if (bus.hasHeaderModeButton()) {
            rightEdge = GuiConstants.BUTTON_IO_MODE_X - 4;
        }

        return rightEdge;
    }

    private IWidget createContentLine(StorageBusContentRow row, int y) {
        StorageBusInfo bus = row.getStorageBus();

        if (row.isFirstRow()) {
            return createFirstRow(bus, row.getStartIndex(), y);
        }

        return createContinuationRow(bus, row.getStartIndex(), y);
    }

    /**
     * First content row: SlotsLine with tree junction button.
     */
    private SlotsLine createFirstRow(StorageBusInfo bus, int startIndex, int y) {
        SlotsLine line = new SlotsLine(
                y,
                SLOTS_PER_ROW,
                SLOTS_X_OFFSET,
                slotMode,
                startIndex,
                fontRenderer,
                itemRender);

        configureSlotData(line, bus);

        // Tree junction button
        SmallButton treeBtn = new SmallButton(0, 0, treeButtonType, () -> {
            if (isPartitionMode) {
                guiContext.sendPacket(
                        new PacketStorageBusPartitionAction(bus.getId(), PacketStorageBusPartitionAction.Action.CLEAR_ALL));
            } else {
                guiContext.sendPacket(
                        new PacketStorageBusPartitionAction(
                                bus.getId(),
                                PacketStorageBusPartitionAction.Action.SET_ALL_FROM_CONTENTS));
            }
        });
        line.setTreeButton(treeBtn);
        line.setTreeButtonXOffset(-4);
        line.setRowHeight(SLOT_ROW_HEIGHT);
        line.setSeparatorRightOffset(SLOT_ROW_SEPARATOR_RIGHT_OFFSET);

        line.setGuiOffsets(guiLeft, guiTop);

        // Selection highlight (partition mode only)
        if (isPartitionMode) {
            line.setSelectedSupplier(
                    () -> guiContext.getSelectedStorageBusIds()
                            .contains(bus.getId()));
        }

        return line;
    }

    /**
     * Continuation row (not the first row for this bus).
     */
    private ContinuationLine createContinuationRow(StorageBusInfo bus, int startIndex, int y) {
        ContinuationLine line = new ContinuationLine(
                y,
                SLOTS_PER_ROW,
                SLOTS_X_OFFSET,
                slotMode,
                startIndex,
                fontRenderer,
                itemRender);

        configureSlotData(line, bus);
        line.setRowHeight(SLOT_ROW_HEIGHT);
        line.setSeparatorRightOffset(SLOT_ROW_SEPARATOR_RIGHT_OFFSET);
        line.setGuiOffsets(guiLeft, guiTop);

        // Selection highlight (partition mode only)
        if (isPartitionMode) {
            line.setSelectedSupplier(
                    () -> guiContext.getSelectedStorageBusIds()
                            .contains(bus.getId()));
        }

        return line;
    }

    /**
     * Configure content/partition data suppliers on a SlotsLine.
     */
    private void configureSlotData(SlotsLine line, StorageBusInfo bus) {
        if (slotMode == SlotsLine.SlotMode.CONTENT) {
            line.setItemsSupplier(bus::getContents);
            line.setPartitionSupplier(bus::getPartition);
            line.setCountProvider(() -> bus::getContentCount);
        } else {
            line.setItemsSupplier(bus::getPartition);
            line.setMaxSlots(bus.getAvailableConfigSlots());
        }

        line.setSlotClickCallback((slotIndex, mouseButton) -> {
            if (mouseButton != 0) return;

            // Defensive: verify slotIndex is within the currently available config slots.
            if (slotIndex < 0 || slotIndex >= bus.getAvailableConfigSlots()) return;

            if (isPartitionMode) {
                ItemStack heldStack = guiContext.getHeldStack();
                List<ItemStack> partition = bus.getPartition();
                boolean slotOccupied = slotIndex < partition.size() && !ItemStacks.isEmpty(partition.get(slotIndex));

                if (!ItemStacks.isEmpty(heldStack)) {
                    // Use the storage-bus NEI conversion rules for held inventory items so
                    // fluid and essentia clicks get normalization and user feedback.
                    ItemStack stackToSend = GhostIngredientHandler
                            .convertIngredientForType(heldStack, bus.getStackTypeId(), true);
                    if (ItemStacks.isEmpty(stackToSend)) return;

                    guiContext.sendPacket(
                            new PacketStorageBusPartitionAction(
                                    bus.getId(),
                                    PacketStorageBusPartitionAction.Action.ADD_ITEM,
                                    slotIndex,
                                    stackToSend));
                } else if (slotOccupied) {
                    guiContext.sendPacket(
                            new PacketStorageBusPartitionAction(
                                    bus.getId(),
                                    PacketStorageBusPartitionAction.Action.REMOVE_ITEM,
                                    slotIndex));
                }
            } else {
                // Content mode: toggle partition for content item
                List<ItemStack> contents = bus.getContents();
                if (slotIndex < contents.size() && !ItemStacks.isEmpty(contents.get(slotIndex))) {
                    guiContext.sendPacket(
                            new PacketStorageBusPartitionAction(
                                    bus.getId(),
                                    PacketStorageBusPartitionAction.Action.TOGGLE_ITEM,
                                    contents.get(slotIndex)));
                }
            }
        });
    }

    /**
     * Handle the add-to-storage-bus keybind.
     * Adds the hovered item to the partition of all selected storage buses.
     * Converts items for fluid/essentia buses, finds empty slots.
     *
     * @param selectedBusIds The set of selected storage bus IDs
     * @param hoveredSlot    The slot the mouse is over (or null)
     * @param storageBusMap  Map of storage bus IDs to info
     * @return true if the keybind was handled
     */
    private static boolean handleAddToStorageBusKeybind(Set<Long> selectedBusIds, Slot hoveredSlot,
                                                        Map<Long, StorageBusInfo> storageBusMap) {
        if (selectedBusIds.isEmpty()) {
            if (Minecraft.getMinecraft().thePlayer != null) {
                MessageHelper.warning("disk_terminal.storage_bus.no_selection");
            }

            return true;
        }

        // Try to get item from inventory slot first
        ItemStack stack = null;

        if (hoveredSlot != null && hoveredSlot.getHasStack()) {
            stack = hoveredSlot.getStack();
        }

        // If no inventory item, try NEI/bookmark
        if (ItemStacks.isEmpty(stack)) {
            QuickPartitionHandler.HoveredIngredient NEIItem = QuickPartitionHandler.getHoveredIngredient();
            if (NEIItem != null && !ItemStacks.isEmpty(NEIItem.stack)) stack = NEIItem.stack;
        }

        if (ItemStacks.isEmpty(stack)) {
            if (Minecraft.getMinecraft().thePlayer != null) {
                MessageHelper.warning("disk_terminal.storage_bus.no_item");
            }

            return true;
        }

        // Add to all selected storage buses
        int successCount = 0;
        int invalidItemCount = 0;
        int noSlotCount = 0;

        for (Long busId : selectedBusIds) {
            StorageBusInfo storageBus = storageBusMap.get(busId);
            if (storageBus == null) continue;

            // Convert the item for non-item bus types first to check validity
            ItemStack stackToSend = stack;
            boolean validForBusType = true;

            if (storageBus.isFluid()) {
                // For fluid buses, the item must carry a fluid (AE2FC drop or a fluid container).
                if (FluidStacks.extract(stack) == null) {
                    invalidItemCount++;
                    validForBusType = false;
                }
            } else if (storageBus.isEssentia()) {
                // For essentia buses, need ItemDummyAspect or essentia container
                ItemStack essentiaRep = ThaumicEnergisticsIntegration.tryConvertEssentiaContainerToAspect(stack);
                // Can't use this item on essentia bus
                if (ItemStacks.isEmpty(essentiaRep)) {
                    invalidItemCount++;
                    validForBusType = false;
                } else {
                    stackToSend = essentiaRep;
                }
            }

            if (!validForBusType) continue;

            // Find first empty slot in this storage bus
            List<ItemStack> partition = storageBus.getPartition();
            int availableSlots = storageBus.getAvailableConfigSlots();
            int targetSlot = -1;

            for (int i = 0; i < availableSlots; i++) {
                if (i >= partition.size() || ItemStacks.isEmpty(partition.get(i))) {
                    targetSlot = i;
                    break;
                }
            }

            if (targetSlot < 0) {
                noSlotCount++;
                continue;
            }

            DiskTerminalNetwork.INSTANCE.sendToServer(
                    new PacketStorageBusPartitionAction(
                            busId,
                            PacketStorageBusPartitionAction.Action.ADD_ITEM,
                            targetSlot,
                            stackToSend));
            successCount++;
        }

        if (successCount == 0 && Minecraft.getMinecraft().thePlayer != null) {
            // Show appropriate error message based on what failed
            if (invalidItemCount > 0 && noSlotCount == 0) {
                MessageHelper.error("disk_terminal.storage_bus.invalid_item");
            } else if (noSlotCount > 0 && invalidItemCount == 0) {
                MessageHelper.error("disk_terminal.storage_bus.partition_full");
            } else {
                // Mixed: some were invalid, some were full
                MessageHelper.error("disk_terminal.storage_bus.partition_full");
            }
        }

        return true;
    }

    private CardsDisplay createBusCards(StorageBusInfo bus, int rowY) {
        List<CardsDisplay.CardEntry> entries = buildCardEntries(bus);
        if (entries.isEmpty()) return null;

        // Position cards at left margin (x=3)
        CardsDisplay cards = new CardsDisplay(GuiConstants.CARDS_X, rowY, () -> entries, itemRender);

        cards.setClickCallback(slotIndex -> handleBusCardClick(bus, slotIndex));

        return cards;
    }

    private List<CardsDisplay.CardEntry> buildCardEntries(StorageBusInfo bus) {
        List<CardsDisplay.CardEntry> entries = new ArrayList<>();
        int slotCount = bus.getUpgradeSlotCount();

        // Build a slot-indexed array so empty slots are visually present
        ItemStack[] slotStacks = new ItemStack[slotCount];
        Arrays.fill(slotStacks, null);
        for (int i = 0; i < bus.getUpgrades()
                .size(); i++) {
            int slotIdx = bus.getUpgradeSlotIndex(i);
            if (slotIdx >= 0 && slotIdx < slotCount) {
                slotStacks[slotIdx] = bus.getUpgrades()
                        .get(i);
            }
        }

        for (int i = 0; i < slotCount; i++) {
            entries.add(new CardsDisplay.CardEntry(slotStacks[i], i));
        }

        return entries;
    }

    private void handleBusCardClick(StorageBusInfo bus, int upgradeSlotIndex) {
        if (DiskTerminalServerConfig.isInitialized() && !DiskTerminalServerConfig.getInstance()
                .isUpgradeExtractEnabled()) {
            guiContext.showError("disk_terminal.error.upgrade_extract_disabled");
            return;
        }

        boolean toInventory = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        guiContext.sendPacket(PacketExtractUpgrade.forStorageBus(bus.getId(), upgradeSlotIndex, toInventory));
    }
}
