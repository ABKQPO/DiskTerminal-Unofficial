package com.hfstudio.diskterminal.gui.widget.tab;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.client.AdvancedSearchParser;
import com.hfstudio.diskterminal.client.SearchFilterMode;
import com.hfstudio.diskterminal.client.SlotLimit;
import com.hfstudio.diskterminal.client.SubnetConnectionEntry;
import com.hfstudio.diskterminal.client.SubnetConnectionRow;
import com.hfstudio.diskterminal.client.SubnetInfo;
import com.hfstudio.diskterminal.config.DiskTerminalClientConfig;
import com.hfstudio.diskterminal.gui.GuiConstants;
import com.hfstudio.diskterminal.gui.handler.GhostIngredientHandler;
import com.hfstudio.diskterminal.gui.handler.GhostTarget;
import com.hfstudio.diskterminal.gui.handler.TerminalDataManager;
import com.hfstudio.diskterminal.gui.overlay.MessageHelper;
import com.hfstudio.diskterminal.gui.widget.DoubleClickTracker;
import com.hfstudio.diskterminal.gui.widget.IWidget;
import com.hfstudio.diskterminal.gui.widget.button.ButtonType;
import com.hfstudio.diskterminal.gui.widget.button.SmallButton;
import com.hfstudio.diskterminal.gui.widget.header.AbstractHeader;
import com.hfstudio.diskterminal.gui.widget.header.SubnetHeader;
import com.hfstudio.diskterminal.gui.widget.line.AbstractLine;
import com.hfstudio.diskterminal.gui.widget.line.ContinuationLine;
import com.hfstudio.diskterminal.gui.widget.line.SlotsLine;
import com.hfstudio.diskterminal.network.DiskTerminalNetwork;
import com.hfstudio.diskterminal.network.PacketHighlightBlock;
import com.hfstudio.diskterminal.network.PacketSubnetAction;
import com.hfstudio.diskterminal.network.PacketSubnetPartitionAction;
import com.hfstudio.diskterminal.network.chunked.DeltaApplier;
import com.hfstudio.diskterminal.network.chunked.PayloadMode;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

/**
 * Tab widget for the subnet overview mode.
 * <p>
 * This is a pseudo-tab: it has no tab button in the tab bar, but it uses the
 * full widget infrastructure (proper IWidget rows, tree line propagation,
 * InlineRenameManager, DoubleClickTracker) just like any real tab.
 * <p>
 * Layout follows the same pattern as {@link TempAreaTabWidget}:
 * <ul>
 * <li>{@link SubnetInfo} → {@link SubnetHeader} (main network only, no connections)</li>
 * <li>{@link SubnetConnectionEntry} → {@link SubnetHeader} (per connection, with direction arrow)</li>
 * <li>{@link SubnetConnectionRow} (content) → {@link SlotsLine}/{@link ContinuationLine}</li>
 * <li>{@link SubnetConnectionRow} (partition) → {@link SlotsLine}/{@link ContinuationLine}</li>
 * </ul>
 * <p>
 * Activated via the back button.
 */
public class SubnetOverviewTabWidget extends AbstractTabWidget {

    private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    private static final int SLOTS_PER_ROW = GuiConstants.STORAGE_BUS_SLOTS_PER_ROW;
    private static final int SLOTS_X_OFFSET = GuiConstants.CELL_INDENT + 4;

    // Subnet data
    // subnetById is the canonical state map (kept across delta updates).
    // subnetList is rebuilt from subnetById whenever the data changes (sorted display order).
    private final Map<Long, SubnetInfo> subnetById = new LinkedHashMap<>();
    private final List<SubnetInfo> subnetList = new ArrayList<>();
    private final List<Object> subnetLines = new ArrayList<>();
    private String lastSearchQuery = null;
    private SearchFilterMode lastSearchMode = null;
    private SlotLimit lastSlotLimit = null;

    // Context for GUI-level operations
    private SubnetOverviewContext subnetContext;

    /**
     * Comparator for sorting subnets: main network first, then favorites, then by dimension and distance.
     */
    private static final Comparator<SubnetInfo> SUBNET_COMPARATOR = (a, b) -> {
        // Main network always first
        if (a.isMainNetwork()) return -1;
        if (b.isMainNetwork()) return 1;

        // Favorites next
        if (a.isFavorite() != b.isFavorite()) return a.isFavorite() ? -1 : 1;

        // Then by dimension
        if (a.getDimension() != b.getDimension()) {
            return Integer.compare(a.getDimension(), b.getDimension());
        }

        // Then by distance from origin
        double distA = PosUtil.distSq(a.getPrimaryPos(), ORIGIN);
        double distB = PosUtil.distSq(b.getPrimaryPos(), ORIGIN);

        return Double.compare(distA, distB);
    };

    /**
     * Callback interface for GUI-level operations that this pseudo-tab cannot perform directly.
     */
    public interface SubnetOverviewContext {

        /** Switch the terminal to view a different network. */
        void switchToNetwork(long networkId);

        /** Request fresh subnet list from the server. */
        void requestSubnetList();
    }

    public SubnetOverviewTabWidget(FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
    }

    /**
     * Set the subnet-specific context for GUI callbacks.
     * Must be called during initialization (after init(GuiContext)).
     */
    public void setSubnetContext(SubnetOverviewContext context) {
        this.subnetContext = context;
    }
    // Subnet data management

    /**
     * Handle subnet list update from server.
     * Parses the NBT data, sorts subnets, and rebuilds flattened display lines.
     */
    public void handleSubnetListUpdate(NBTTagCompound data) {
        applySubnetPayload(PayloadMode.FULL, data);
    }

    /**
     * Apply a chunked subnet payload (FULL or DELTA).
     * Maintains {@link #subnetById} as the canonical state, then rebuilds the sorted display
     * list and flattened lines.
     */
    public void applySubnetPayload(PayloadMode mode, NBTTagCompound data) {
        DeltaApplier.applyRaw(mode, data, "id", "subnets", this.subnetById::clear, (id, entry) -> {
            if (entry == null) {
                this.subnetById.remove(id);
            } else {
                this.subnetById.put(id, new SubnetInfo(entry));
            }
            return null;
        });

        // Rebuild display list: main network first, then sorted subnets.
        this.subnetList.clear();
        this.subnetList.add(SubnetInfo.createMainNetwork());
        this.subnetList.addAll(this.subnetById.values());
        this.subnetList.sort(SUBNET_COMPARATOR);

        buildSubnetLines();
    }

    /**
     * Build the flattened subnet lines list from the sorted subnet list.
     * <p>
     * Each connection gets its own header ({@link SubnetConnectionEntry}),
     * followed by content rows and partition rows, just like
     * TempArea has TempCellInfo → CellContentRow(content) → CellContentRow(partition).
     */
    private void buildSubnetLines() {
        this.subnetLines.clear();
        TerminalDataManager dataManager = this.guiContext != null ? this.guiContext.getDataManager() : null;
        String simpleSearch = dataManager != null ? dataManager.getSearchFilter() : "";
        AdvancedSearchParser.SearchMatcher advancedMatcher = dataManager != null ? dataManager.getAdvancedMatcher()
            : null;
        boolean useAdvancedSearch = dataManager != null && dataManager.isUsingAdvancedSearch()
            && advancedMatcher != null;
        SearchFilterMode searchMode = DiskTerminalClientConfig.getInstance()
            .getSearchMode();
        SlotLimit slotLimit = DiskTerminalClientConfig.getInstance()
            .getSubnetSlotLimit();
        boolean hasSearch = useAdvancedSearch || !simpleSearch.isEmpty();

        for (SubnetInfo subnet : this.subnetList) {
            // Main network gets a single header with no connections
            if (subnet.isMainNetwork()) {
                if (hasSearch && !matchesSubnetHeader(subnet, searchMode, advancedMatcher, useAdvancedSearch)) {
                    continue;
                }

                this.subnetLines.add(subnet);
                continue;
            }

            // Each connection gets its own header + content/partition rows
            for (int connIdx = 0; connIdx < subnet.getConnections()
                .size(); connIdx++) {
                SubnetInfo.ConnectionPoint conn = subnet.getConnections()
                    .get(connIdx);
                boolean usesSubnetInventory = conn.usesSubnetInventory() && subnet.hasInventory();
                if (hasSearch && !matchesConnectionSearch(
                    subnet,
                    conn,
                    usesSubnetInventory,
                    searchMode,
                    simpleSearch,
                    advancedMatcher,
                    useAdvancedSearch)) {
                    continue;
                }

                this.subnetLines.add(new SubnetConnectionEntry(subnet, conn, connIdx));

                // Content + partition rows (like TempArea's CellContentRow with isPartitionRow flag)
                List<SubnetConnectionRow> rows = SubnetInfo
                    .buildConnectionContentRows(subnet, conn, connIdx, SLOTS_PER_ROW, slotLimit);
                this.subnetLines.addAll(rows);
            }
        }

        this.lastSearchQuery = DiskTerminalClientConfig.getInstance()
            .getSearchFilter();
        this.lastSearchMode = searchMode;
        this.lastSlotLimit = slotLimit;
    }

    private boolean matchesSubnetHeader(SubnetInfo subnet, SearchFilterMode searchMode,
        AdvancedSearchParser.SearchMatcher advancedMatcher, boolean useAdvancedSearch) {
        if (!useAdvancedSearch || advancedMatcher == null) return false;

        return advancedMatcher.matchesSubnetConnectionFilter(subnet, null, false, searchMode);
    }

    private boolean matchesConnectionSearch(SubnetInfo subnet, SubnetInfo.ConnectionPoint connection,
        boolean usesSubnetInventory, SearchFilterMode searchMode, String simpleSearch,
        AdvancedSearchParser.SearchMatcher advancedMatcher, boolean useAdvancedSearch) {
        if (useAdvancedSearch && advancedMatcher != null) {
            return advancedMatcher.matchesSubnetConnectionFilter(subnet, connection, usesSubnetInventory, searchMode);
        }

        if (simpleSearch.isEmpty()) return true;

        boolean matchesInventory = matchesItemList(
            usesSubnetInventory ? subnet.getInventory() : connection.getContent(),
            simpleSearch);
        boolean matchesPartition = matchesItemList(connection.getPartition(), simpleSearch);

        switch (searchMode) {
            case INVENTORY:
                return matchesInventory;
            case PARTITION:
                return matchesPartition;
            case MIXED:
            default:
                return matchesInventory || matchesPartition;
        }
    }

    private boolean matchesItemList(List<ItemStack> items, String searchFilter) {
        for (ItemStack stack : items) {
            if (ItemStacks.isEmpty(stack)) continue;

            String displayName = stack.getDisplayName()
                .toLowerCase(Locale.ROOT);
            if (displayName.contains(searchFilter)) return true;

            String regName = Item.itemRegistry.getNameForObject(stack.getItem());
            if (regName == null) continue;
            String registryName = regName.toLowerCase(Locale.ROOT);
            if (registryName.contains(searchFilter)) return true;
        }

        return false;
    }

    private void rebuildLinesIfSearchChanged(TerminalDataManager dataManager) {
        String searchQuery = DiskTerminalClientConfig.getInstance()
            .getSearchFilter();
        SearchFilterMode searchMode = DiskTerminalClientConfig.getInstance()
            .getSearchMode();
        SlotLimit slotLimit = DiskTerminalClientConfig.getInstance()
            .getSubnetSlotLimit();

        if (searchQuery.equals(this.lastSearchQuery) && searchMode == this.lastSearchMode
            && slotLimit == this.lastSlotLimit) {
            return;
        }

        buildSubnetLines();
    }

    /**
     * Called when entering subnet overview mode.
     * Rebuilds lines from existing data (if any) for immediate display,
     * and requests fresh data from the server.
     */
    public void onEnterOverview() {
        // Rebuild lines from existing data for immediate display (avoids flicker)
        if (!this.subnetList.isEmpty()) buildSubnetLines();

        // Request fresh subnet list from server
        if (subnetContext != null) subnetContext.requestSubnetList();
    }
    // Row widget creation (mirrors TempAreaTabWidget pattern)

    @Override
    protected IWidget createRowWidget(Object lineData, int y, List<?> allLines, int lineIndex) {
        // Main network header (SubnetInfo directly, no connection)
        if (lineData instanceof SubnetInfo) {
            return createMainNetworkHeader((SubnetInfo) lineData, y);
        }

        // Per-connection header (with direction arrow)
        if (lineData instanceof SubnetConnectionEntry) {
            return createConnectionHeader((SubnetConnectionEntry) lineData, y);
        }

        // Content or partition row (SlotsLine / ContinuationLine)
        if (lineData instanceof SubnetConnectionRow) {
            return createContentLine((SubnetConnectionRow) lineData, y);
        }

        return null;
    }

    @Override
    protected boolean isContentLine(List<?> allLines, int index) {
        if (index < 0 || index >= allLines.size()) return false;

        return allLines.get(index) instanceof SubnetConnectionRow;
    }

    /**
     * Check if a row at the given index is a partition row (vs content row).
     * Only meaningful for SubnetConnectionRow entries.
     */
    private boolean isPartitionRow(List<?> allLines, int index) {
        if (index < 0 || index >= allLines.size()) return false;

        Object line = allLines.get(index);
        return (line instanceof SubnetConnectionRow) && ((SubnetConnectionRow) line).isPartitionRow();
    }

    @Override
    protected void propagateTreeLines(List<?> allLines, int scrollOffset) {
        int lastContentCutY = GuiConstants.CONTENT_START_Y;
        int lastPartitionCutY = GuiConstants.CONTENT_START_Y;
        boolean hasContentAbove = scrollOffset > 0 && isContentLine(allLines, scrollOffset - 1);

        // Track whether any non-partition content rows appeared since the last header.
        // When false at the first partition row, the partition connects directly to the header
        // rather than using a zero-length self-referencing vertical line (which would leave a gap).
        boolean hadContentInSection = false;

        for (int i = 0; i < visibleRows.size(); i++) {
            IWidget widget = visibleRows.get(i);
            int lineIndex = scrollOffset + i;

            if (widget instanceof AbstractHeader) {
                AbstractHeader header = (AbstractHeader) widget;
                // If no content rows below but partition rows exist, connector still needed
                boolean hasAnyContentBelow = (lineIndex + 1) < allLines.size()
                    && isContentLine(allLines, lineIndex + 1);
                header.setDrawConnector(hasAnyContentBelow);
                lastContentCutY = header.getConnectorY();
                // Reset partition cut Y and content tracking for each new header (new connection)
                lastPartitionCutY = GuiConstants.CONTENT_START_Y;
                hadContentInSection = false;

            } else if (widget instanceof AbstractLine) {
                AbstractLine line = (AbstractLine) widget;
                boolean currentIsPartition = isPartitionRow(allLines, lineIndex);
                boolean prevIsPartition = lineIndex > 0 && isPartitionRow(allLines, lineIndex - 1);

                if (currentIsPartition) {
                    if (!prevIsPartition) {
                        if (hadContentInSection) {
                            // First partition row after content rows: zero-length vertical line
                            // (visual break between content and partition sections)
                            line.setTreeLineParams(true, line.getY() + 5);
                        } else {
                            // First partition row with NO content rows above (directly after header):
                            // connect to the header's connector Y so the tree line reaches up
                            line.setTreeLineParams(true, lastContentCutY);
                        }
                    } else {
                        // Continuation partition row: draw tree line connecting to previous partition
                        line.setTreeLineParams(true, lastPartitionCutY);
                    }
                    lastPartitionCutY = line.getTreeLineCutY();
                } else if (i == 0 && hasContentAbove) {
                    // First visible row with content above
                    line.setTreeLineParams(true, GuiConstants.CONTENT_START_Y);
                    lastContentCutY = line.getTreeLineCutY();
                    hadContentInSection = true;
                } else {
                    line.setTreeLineParams(true, lastContentCutY);
                    lastContentCutY = line.getTreeLineCutY();
                    hadContentInSection = true;
                }
            }
        }
    }

    /**
     * Create a SubnetHeader for the main network entry (no connections, no arrow).
     */
    private SubnetHeader createMainNetworkHeader(SubnetInfo subnet, int y) {
        SubnetHeader header = new SubnetHeader(y, fontRenderer, itemRender, true);

        header.setNameSupplier(subnet::getDisplayName);
        header.setHasCustomNameSupplier(subnet::hasCustomName);
        header.setIsFavoriteSupplier(subnet::isFavorite);
        header.setCanLoadSupplier(() -> true);

        // Load button: switch to main network
        header.setOnLoadClick(() -> { if (subnetContext != null) subnetContext.switchToNetwork(0); });

        // FIXME: does not supply the star onClick lambda

        return header;
    }

    /**
     * Create a SubnetHeader for a connection entry (with direction arrow, per-connection).
     */
    private SubnetHeader createConnectionHeader(SubnetConnectionEntry entry, int y) {
        SubnetInfo subnet = entry.getSubnet();
        SubnetInfo.ConnectionPoint conn = entry.getConnection();
        SubnetHeader header = new SubnetHeader(y, fontRenderer, itemRender);

        header.setNameSupplier(subnet::getDisplayName);
        header.setHasCustomNameSupplier(subnet::hasCustomName);
        header.setIsFavoriteSupplier(subnet::isFavorite);
        header.setCanLoadSupplier(() -> subnet.isAccessible() && subnet.hasPower());

        // Direction arrow (→ for outbound, ← for inbound)
        header.setDirectionSupplier(conn::isOutbound);

        // Icon: the local part's icon (Storage Bus for outbound, Interface for inbound)
        header.setIconSupplier(conn::getLocalIcon);

        // Location text: connection position and dimension
        header.setLocationSupplier(
            () -> I18n.format(
                "disk_terminal.subnet.pos",
                conn.getPos()
                    .getX(),
                conn.getPos()
                    .getY(),
                conn.getPos()
                    .getZ(),
                conn.getDimension()));

        // Star click: toggle favorite for the whole subnet, re-sort and rebuild
        header.setOnStarClick(() -> {
            boolean newFavorite = !subnet.isFavorite();
            subnet.setFavorite(newFavorite);
            DiskTerminalNetwork.INSTANCE.sendToServer(PacketSubnetAction.toggleFavorite(subnet.getId(), newFavorite));
            this.subnetList.sort(SUBNET_COMPARATOR);
            buildSubnetLines();
            if (guiContext != null) guiContext.rebuildAndUpdateScrollbar();
        });

        // Load button: switch to this subnet's network
        header.setOnLoadClick(() -> { if (subnetContext != null) subnetContext.switchToNetwork(subnet.getId()); });

        // Rename info: InlineRenameManager handles right-click on name
        header.setRenameInfo(
            subnet,
            GuiConstants.HEADER_NAME_X - 2,
            0,
            GuiConstants.CONTENT_RIGHT_EDGE - fontRenderer.getStringWidth(I18n.format("disk_terminal.subnet.load"))
                - 12);

        // Double-click to highlight connection position in world
        header.setOnNameDoubleClick(
            () -> highlightConnectionInWorld(subnet, conn),
            DoubleClickTracker.subnetTargetId(subnet.getId() + entry.getConnectionIndex()));

        return header;
    }

    /**
     * Create a SlotsLine or ContinuationLine for a content/partition row.
     */
    private IWidget createContentLine(SubnetConnectionRow row, int y) {
        boolean isPartition = row.isPartitionRow();
        SlotsLine.SlotMode mode = isPartition ? SlotsLine.SlotMode.PARTITION : SlotsLine.SlotMode.CONTENT;

        if (row.isFirstRow()) return createFirstRow(row, mode, isPartition, y);

        return createContinuationRow(row, mode, y);
    }

    /**
     * First content or partition row: SlotsLine with tree junction button.
     * Content first row gets DO_PARTITION, partition first row gets CLEAR_PARTITION.
     */
    private SlotsLine createFirstRow(SubnetConnectionRow row, SlotsLine.SlotMode mode, boolean isPartition, int y) {
        SlotsLine line = new SlotsLine(
            y,
            SLOTS_PER_ROW,
            SLOTS_X_OFFSET,
            mode,
            row.getStartIndex(),
            fontRenderer,
            itemRender);

        configureSlotData(line, row, mode);

        // Tree junction button
        ButtonType buttonType = isPartition ? ButtonType.CLEAR_PARTITION : ButtonType.DO_PARTITION;
        SubnetInfo.ConnectionPoint conn = row.getConnection();
        SmallButton treeBtn = new SmallButton(0, 0, buttonType, () -> {
            if (isPartition) {
                guiContext.sendPacket(
                    new PacketSubnetPartitionAction(
                        row.getSubnet()
                            .getId(),
                        PosUtil.toLong(conn.getPos()),
                        conn.getSide()
                            .ordinal(),
                        PacketSubnetPartitionAction.Action.CLEAR_ALL));
            } else if (row.usesSubnetInventory()) {
                // Connection mirrors the subnet inventory, so build the filter from it.
                guiContext.sendPacket(
                    new PacketSubnetPartitionAction(
                        row.getSubnet()
                            .getId(),
                        PosUtil.toLong(conn.getPos()),
                        conn.getSide()
                            .ordinal(),
                        PacketSubnetPartitionAction.Action.SET_ALL_FROM_SUBNET_INVENTORY));
            } else {
                guiContext.sendPacket(
                    new PacketSubnetPartitionAction(
                        row.getSubnet()
                            .getId(),
                        PosUtil.toLong(conn.getPos()),
                        conn.getSide()
                            .ordinal(),
                        PacketSubnetPartitionAction.Action.SET_ALL_FROM_CONTENTS));
            }
        });
        line.setTreeButton(treeBtn);
        line.setGuiOffsets(guiLeft, guiTop);

        return line;
    }

    /**
     * Continuation row (not the first for this content/partition section).
     */
    private ContinuationLine createContinuationRow(SubnetConnectionRow row, SlotsLine.SlotMode mode, int y) {
        ContinuationLine line = new ContinuationLine(
            y,
            SLOTS_PER_ROW,
            SLOTS_X_OFFSET,
            mode,
            row.getStartIndex(),
            fontRenderer,
            itemRender);

        configureSlotData(line, row, mode);
        line.setGuiOffsets(guiLeft, guiTop);

        return line;
    }

    /**
     * Configure content/partition data suppliers on a SlotsLine.
     */
    private void configureSlotData(SlotsLine line, SubnetConnectionRow row, SlotsLine.SlotMode mode) {
        SubnetInfo.ConnectionPoint conn = row.getConnection();
        SubnetInfo subnet = row.getSubnet();

        if (mode == SlotsLine.SlotMode.CONTENT) {
            if (row.usesSubnetInventory()) {
                // This connection mirrors the subnet's shared ME storage.
                line.setItemsSupplier(subnet::getInventory);
                line.setCountProvider(() -> subnet::getInventoryCount);
            } else {
                line.setItemsSupplier(conn::getContent);
            }
            line.setPartitionSupplier(conn::getPartition);
        } else {
            line.setItemsSupplier(conn::getPartition);
            line.setMaxSlots(conn.getMaxPartitionSlots());
        }

        line.setSlotClickCallback((slotIndex, mouseButton) -> {
            if (mouseButton != 0) return;

            // Defensive: verify slotIndex is within the valid range
            if (slotIndex < 0 || slotIndex >= conn.getMaxPartitionSlots()) return;

            if (mode == SlotsLine.SlotMode.CONTENT) {
                // Content slot: toggle partition for that item
                List<ItemStack> contents = row.usesSubnetInventory() ? subnet.getInventory() : conn.getContent();
                if (slotIndex < contents.size() && !ItemStacks.isEmpty(contents.get(slotIndex))) {
                    NBTTagCompound stackData = row.usesSubnetInventory() ? subnet.getInventoryStackData(slotIndex)
                        : conn.getContentStackData(slotIndex);
                    guiContext.sendPacket(
                        new PacketSubnetPartitionAction(
                            subnet.getId(),
                            PosUtil.toLong(conn.getPos()),
                            conn.getSide()
                                .ordinal(),
                            PacketSubnetPartitionAction.Action.TOGGLE_ITEM,
                            -1,
                            stackData));
                }
            } else {
                // Partition slot: add/remove item
                ItemStack heldStack = guiContext.getHeldStack();
                List<ItemStack> partition = conn.getPartition();
                boolean slotOccupied = slotIndex < partition.size() && !ItemStacks.isEmpty(partition.get(slotIndex));

                if (!ItemStacks.isEmpty(heldStack)) {
                    ItemStack stackToSend = GhostIngredientHandler
                        .convertIngredientForType(heldStack, conn.getStackTypeId(), true);
                    if (ItemStacks.isEmpty(stackToSend)) return;

                    guiContext.sendPacket(
                        new PacketSubnetPartitionAction(
                            subnet.getId(),
                            PosUtil.toLong(conn.getPos()),
                            conn.getSide()
                                .ordinal(),
                            PacketSubnetPartitionAction.Action.ADD_ITEM,
                            slotIndex,
                            stackToSend));
                } else if (slotOccupied) {
                    guiContext.sendPacket(
                        new PacketSubnetPartitionAction(
                            subnet.getId(),
                            PosUtil.toLong(conn.getPos()),
                            conn.getSide()
                                .ordinal(),
                            PacketSubnetPartitionAction.Action.REMOVE_ITEM,
                            slotIndex));
                }
            }
        });
    }

    /**
     * Get NEI ghost ingredient targets for partition slots in the subnet overview.
     * Wraps visible partition SlotsLine targets into proper GhostTarget
     * instances that send PacketSubnetPartitionAction.ADD_ITEM on accept.
     */
    @Override
    public List<GhostTarget<?>> getPhantomTargets(Object ingredient) {
        List<GhostTarget<?>> targets = new ArrayList<>();

        for (Map.Entry<IWidget, Object> entry : getWidgetDataMap().entrySet()) {
            IWidget widget = entry.getKey();
            Object data = entry.getValue();
            if (!(widget instanceof SlotsLine)) continue;

            SlotsLine slotsLine = (SlotsLine) widget;
            List<SlotsLine.PartitionSlotTarget> slotTargets = slotsLine.getPartitionTargets();
            if (slotTargets.isEmpty()) continue;
            if (!(data instanceof SubnetConnectionRow)) continue;

            SubnetConnectionRow row = (SubnetConnectionRow) data;
            if (!row.isPartitionRow()) continue;

            SubnetInfo subnet = row.getSubnet();
            SubnetInfo.ConnectionPoint conn = row.getConnection();

            for (SlotsLine.PartitionSlotTarget slot : slotTargets) {
                targets.add(new GhostTarget<Object>() {

                    @Override
                    public Rectangle getArea() {
                        return new Rectangle(slot.absX, slot.absY, slot.width, slot.height);
                    }

                    @Override
                    public void accept(Object ing) {
                        ItemStack stack = GhostIngredientHandler
                            .convertIngredientForType(ing, conn.getStackTypeId(), true);
                        if (!ItemStacks.isEmpty(stack)) {
                            guiContext.sendPacket(
                                new PacketSubnetPartitionAction(
                                    subnet.getId(),
                                    PosUtil.toLong(conn.getPos()),
                                    conn.getSide()
                                        .ordinal(),
                                    PacketSubnetPartitionAction.Action.ADD_ITEM,
                                    slot.absoluteIndex,
                                    stack));
                        }
                    }
                });
            }
        }

        return targets;
    }

    /**
     * Highlight a connection point's position in the world.
     */
    private void highlightConnectionInWorld(SubnetInfo subnet, SubnetInfo.ConnectionPoint conn) {
        if (subnet == null || subnet.isMainNetwork()) return;

        if (subnet.getDimension() != Minecraft.getMinecraft().thePlayer.dimension) {
            MessageHelper.error("disk_terminal.error.different_dimension");
            return;
        }

        DiskTerminalNetwork.INSTANCE.sendToServer(new PacketHighlightBlock(conn.getPos(), subnet.getDimension()));

        MessageHelper.success(
            "gui.disk_terminal.highlighted",
            conn.getPos()
                .getX(),
            conn.getPos()
                .getY(),
            conn.getPos()
                .getZ(),
            subnet.getDisplayName());
    }
    // Tab overrides: Data and metadata

    /**
     * Return the flattened subnet lines for scrollbar calculation.
     */
    @Override
    public List<Object> getLines(TerminalDataManager dataManager) {
        rebuildLinesIfSearchChanged(dataManager);

        return subnetLines;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();

        lines.add(I18n.format("disk_terminal.subnet.controls.title"));
        lines.add("");
        lines.add(I18n.format("disk_terminal.subnet.controls.click"));
        lines.add(I18n.format("disk_terminal.subnet.controls.dblclick"));
        lines.add(I18n.format("disk_terminal.subnet.controls.star"));
        lines.add(I18n.format("disk_terminal.subnet.controls.rename"));
        lines.add(I18n.format("disk_terminal.subnet.controls.esc"));

        return lines;
    }

    /**
     * No tab button for this pseudo-tab.
     */
    @Override
    public ItemStack getTabIcon() {
        return null;
    }

    /**
     * No tooltip for this pseudo-tab (no tab button).
     */
    @Override
    public String getTabTooltip() {
        return "";
    }

    /**
     * Subnet overview uses the shared mixed/inventory/partition search mode.
     */
    @Override
    public boolean showSearchModeButton() {
        return true;
    }
    // Accessors

    /** Get the subnet list (for external queries). */
    public List<SubnetInfo> getSubnetList() {
        return Collections.unmodifiableList(subnetList);
    }

    /** Check if the subnet list is empty (no data received yet). */
    public boolean hasSubnetData() {
        return !subnetList.isEmpty();
    }
}
