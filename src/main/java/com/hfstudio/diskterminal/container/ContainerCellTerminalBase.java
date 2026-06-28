package com.hfstudio.diskterminal.container;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.diskterminal.DiskTerminal;
import com.hfstudio.diskterminal.api.IUpgradeable;
import com.hfstudio.diskterminal.client.CellFilter;
import com.hfstudio.diskterminal.config.DiskTerminalServerConfig;
import com.hfstudio.diskterminal.container.handler.CellActionHandler;
import com.hfstudio.diskterminal.container.handler.CellDataHandler;
import com.hfstudio.diskterminal.container.handler.DeltaSnapshot;
import com.hfstudio.diskterminal.container.handler.NetworkToolActionHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.container.handler.SubnetDataHandler;
import com.hfstudio.diskterminal.container.handler.SubnetDataHandler.SubnetTracker;
import com.hfstudio.diskterminal.container.handler.TempCellActionHandler;
import com.hfstudio.diskterminal.data.StorageBusCustomNameData;
import com.hfstudio.diskterminal.gui.GuiConstants;
import com.hfstudio.diskterminal.integration.CellsIntegration;
import com.hfstudio.diskterminal.integration.storage.StorageScannerRegistry;
import com.hfstudio.diskterminal.network.PacketExtractUpgrade;
import com.hfstudio.diskterminal.network.PacketPartitionAction;
import com.hfstudio.diskterminal.network.PacketSubnetPartitionAction;
import com.hfstudio.diskterminal.network.PacketTempCellAction;
import com.hfstudio.diskterminal.network.PacketTempCellPartitionAction;
import com.hfstudio.diskterminal.network.chunked.ChunkedNBTSender;
import com.hfstudio.diskterminal.network.chunked.PayloadMode;
import com.hfstudio.diskterminal.network.chunked.TerminalChannels;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusActionExecutor;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusCapabilityIds;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusCapabilityProviderRegistry;
import com.hfstudio.diskterminal.storagebus.scanner.StorageBusScanCollector;
import com.hfstudio.diskterminal.util.InventoryHelper;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PlayerMessageHelper;
import com.hfstudio.diskterminal.util.PosUtil;

import appeng.api.AEApi;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPart;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.IPriorityHost;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.NetworkToolViewer;
import appeng.items.tools.ToolNetworkTool;
import appeng.parts.automation.PartSharedItemBus;
import appeng.parts.automation.PartUpgradeable;
import appeng.util.Platform;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * Base container for Cell Terminal variants.
 * Contains shared functionality for scanning ME network storage and managing cell partitions.
 */
public abstract class ContainerCellTerminalBase extends AEBaseContainer {

    protected final Map<TileEntity, StorageTracker> trackers = new HashMap<>();
    protected final Map<Long, StorageTracker> byId = new LinkedHashMap<>();
    protected final Map<Long, StorageBusTracker> storageBusById = new LinkedHashMap<>();
    protected final Map<Long, SubnetTracker> subnetById = new LinkedHashMap<>();
    protected final StorageBusCapabilityProviderRegistry storageBusProviders = new StorageBusCapabilityProviderRegistry();
    protected final StorageBusScanCollector storageBusCollector = new StorageBusScanCollector();
    protected final StorageBusActionExecutor storageBusActionExecutor = new StorageBusActionExecutor();
    protected IGrid grid;
    protected boolean needsFullRefresh = true;
    protected boolean needsStorageRefresh = true;
    protected boolean needsStorageBusRefresh = false;
    protected boolean needsTempCellRefresh = true;
    protected boolean needsSubnetRefresh = false;

    protected int tickCounter = 0;
    protected int lastFullRefreshTick = 0;
    protected boolean firstFullRefreshDone = false;

    protected final DeltaSnapshot deltaSnapshot = new DeltaSnapshot();

    protected int activeTab = GuiConstants.TAB_TERMINAL;

    protected int cellSlotLimit = Integer.MAX_VALUE;
    protected int busSlotLimit = Integer.MAX_VALUE;
    protected int subnetSlotLimit = 64;

    protected int storageBusPollCounter = 0;
    protected boolean storageBusRefreshUrgent = false;

    protected long currentNetworkId = 0;
    protected IGrid currentNetworkGrid = null;
    protected long lastSentMetaNetworkId = Long.MIN_VALUE;
    protected long lastSentMetaTerminalPos = Long.MIN_VALUE;
    protected int lastSentMetaTerminalDim = Integer.MIN_VALUE;

    private int toolboxSlot;
    private NetworkToolViewer toolboxInventory;

    public ContainerCellTerminalBase(InventoryPlayer ip, IPart part) {
        super(ip, null, part);

        this.setupToolbox();
    }

    public ContainerCellTerminalBase(InventoryPlayer ip, WirelessTerminalGuiObject guiObject) {
        super(ip, guiObject);

        if (Platform.isServer()) {
            IGridNode node = guiObject.getActionableNode();
            if (node != null && node.isActive()) this.grid = node.getGrid();
        }

        this.setupToolbox();
    }

    public void setupToolbox() {
        final IInventory pi = this.getPlayerInv();
        ItemStack toolbox = null;
        for (int x = 0; x < pi.getSizeInventory(); x++) {
            final ItemStack pii = pi.getStackInSlot(x);
            if (!ItemStacks.isEmpty(pii) && pii.getItem() instanceof ToolNetworkTool) {
                this.lockPlayerInventorySlot(x);
                this.toolboxSlot = x;
                toolbox = pii;
                break;
            }
        }

        if (toolbox != null) {
            IGridNode node = this.getActionHost()
                .getActionableNode();
            IGridHost host = node != null ? node.getMachine() : null;
            this.toolboxInventory = new NetworkToolViewer(toolbox, host, 9);

            for (int v = 0; v < 3; v++) {
                for (int u = 0; u < 3; u++) {
                    this.addSlotToContainer(
                        new SlotRestrictedInput(
                            SlotRestrictedInput.PlacableItemType.UPGRADES,
                            this.toolboxInventory,
                            u + v * 3,
                            8 + 9 * 18 + 14 + 18 + 1 + u * 18,
                            v * 18,
                            this.getInventoryPlayer()).setPlayerSide());
                }
            }
        }
    }

    public boolean hasToolbox() {
        return this.toolboxInventory != null;
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isClient()) return;
        super.detectAndSendChanges();

        if (!canSendUpdates()) return;

        this.tickCounter++;

        int minInterval = DiskTerminalServerConfig.getInstance()
            .getMinRefreshIntervalTicks();
        boolean throttleSatisfied = !firstFullRefreshDone
            || (this.tickCounter - this.lastFullRefreshTick) >= minInterval;

        if (needsFullRefresh && throttleSatisfied) {
            sendMetaIfChanged();
            regenDirtySectionsForActiveTab();

            needsFullRefresh = false;
            this.lastFullRefreshTick = this.tickCounter;
            this.firstFullRefreshDone = true;
        }

        handleStorageBusPolling();

        if (needsSubnetRefresh) {
            this.regenSubnetList();
            needsSubnetRefresh = false;
        }

        this.checkToolbox();
    }

    public void checkToolbox() {
        if (!hasToolbox()) return;

        final ItemStack currentItem = this.getPlayerInv()
            .getStackInSlot(this.toolboxSlot);

        if (currentItem == this.toolboxInventory.getItemStack()) return;

        if (ItemStacks.isEmpty(currentItem)) {
            this.setValidContainer(false);
            return;
        }

        if (currentItem.isItemEqual(this.toolboxInventory.getItemStack())) {
            this.getPlayerInv()
                .setInventorySlotContents(this.toolboxSlot, this.toolboxInventory.getItemStack());
        } else {
            this.setValidContainer(false);
        }
    }

    protected void handleStorageBusPolling() {
        boolean isOnStorageBusTab = (activeTab == GuiConstants.TAB_STORAGE_BUS_INVENTORY
            || activeTab == GuiConstants.TAB_STORAGE_BUS_PARTITION);

        if (!isOnStorageBusTab) return;

        DiskTerminalServerConfig config = DiskTerminalServerConfig.getInstance();

        if (needsStorageBusRefresh) {
            if (!storageBusRefreshUrgent && needsFullRefresh) return;

            regenStorageBusList();
            storageBusPollCounter = 0;
            needsStorageBusRefresh = false;
            storageBusRefreshUrgent = false;

            return;
        }

        if (!config.isStorageBusPollingEnabled()) return;

        storageBusPollCounter++;

        if (storageBusPollCounter >= config.getPollingInterval()) {
            regenStorageBusList();
            storageBusPollCounter = 0;
        }
    }

    public void setActiveTab(int tab) {
        if (this.activeTab == tab) return;

        boolean isOnStorageBusTab = (tab == GuiConstants.TAB_STORAGE_BUS_INVENTORY
            || tab == GuiConstants.TAB_STORAGE_BUS_PARTITION);

        this.activeTab = tab;

        if (isOnStorageBusTab) {
            requestStorageBusRefresh(true);
            storageBusPollCounter = 0;
        }

        if (tabNeedsStorages(tab)) requestStorageRefresh();
        if (tabNeedsTempCells(tab)) requestTempCellRefresh();
    }

    public void setSlotLimits(int cellLimit, int busLimit, int subnetLimit) {
        int normalizedCellLimit = cellLimit < 0 ? Integer.MAX_VALUE : cellLimit;
        int normalizedBusLimit = busLimit < 0 ? Integer.MAX_VALUE : busLimit;
        int normalizedSubnetLimit = subnetLimit < 0 ? Integer.MAX_VALUE : subnetLimit;

        boolean cellChanged = this.cellSlotLimit != normalizedCellLimit;
        boolean busChanged = this.busSlotLimit != normalizedBusLimit;
        boolean subnetChanged = this.subnetSlotLimit != normalizedSubnetLimit;

        if (!cellChanged && !busChanged && !subnetChanged) return;

        this.cellSlotLimit = normalizedCellLimit;
        this.busSlotLimit = normalizedBusLimit;
        this.subnetSlotLimit = normalizedSubnetLimit;

        if (cellChanged) {
            requestStorageRefresh();
            requestTempCellRefresh();
        }
        if (busChanged) requestStorageBusRefresh();
        if (subnetChanged) requestSubnetRefresh();
    }

    public int getCellSlotLimit() {
        return cellSlotLimit;
    }

    public int getBusSlotLimit() {
        return busSlotLimit;
    }

    protected boolean canSendUpdates() {
        if (this.grid == null) return false;

        final IActionHost host = this.getActionHost();
        if (host == null) return false;

        final IGridNode agn = host.getActionableNode();

        return agn != null && agn.isActive();
    }

    protected void regenStorageList() {
        this.needsStorageRefresh = false;
        this.trackers.clear();
        this.byId.clear();

        NBTTagCompound data = new NBTTagCompound();
        NBTTagList storageList = new NBTTagList();

        IGrid effectiveGrid = getEffectiveGrid();
        if (effectiveGrid != null) {
            CellDataHandler.StorageTrackerCallback callback = (id, tile, storage) -> {
                StorageTracker tracker = new StorageTracker(id, tile, storage);
                this.trackers.put(tile, tracker);
                this.byId.put(id, tracker);
            };

            StorageScannerRegistry.scanAllStorages(effectiveGrid, storageList, callback, cellSlotLimit);
        } else {
            DiskTerminal.LOG.warn("regenStorageList: grid is null!");
        }

        data.setTag("storages", storageList);
        sendChunked(TerminalChannels.STORAGES, data, "storages", "id");
    }

    protected void regenStorageBusList() {
        this.needsStorageBusRefresh = false;
        this.storageBusRefreshUrgent = false;
        this.storageBusById.clear();

        NBTTagList storageBusList = storageBusCollector
            .collect(getEffectiveGrid(), this.storageBusById, this.storageBusProviders, busSlotLimit);

        NBTTagCompound data = new NBTTagCompound();
        data.setTag("storageBuses", storageBusList);
        sendChunked(TerminalChannels.BUSES, data, "storageBuses", "id");
    }

    protected void regenTempCellList() {
        this.needsTempCellRefresh = false;
        IInventory tempInv = getTempCellInventory();
        if (tempInv == null) return;

        NBTTagList tempCellList = new NBTTagList();

        int highestOccupied = -1;
        for (int i = tempInv.getSizeInventory() - 1; i >= 0; i--) {
            if (!ItemStacks.isEmpty(tempInv.getStackInSlot(i))) {
                highestOccupied = i;
                break;
            }
        }

        int slotsToSend = Math.min(highestOccupied + 2, tempInv.getSizeInventory());

        if (slotsToSend < 1) slotsToSend = 1;

        for (int i = 0; i < slotsToSend; i++) {
            ItemStack cellStack = tempInv.getStackInSlot(i);
            NBTTagCompound slotData = new NBTTagCompound();
            slotData.setInteger("tempSlot", i);
            slotData.setLong("id", i);

            if (!ItemStacks.isEmpty(cellStack)) {
                NBTTagCompound cellData = CellDataHandler.createCellData(i, cellStack, 1, cellSlotLimit);
                slotData.setTag("cellData", cellData);
            }

            tempCellList.appendTag(slotData);
        }

        NBTTagCompound data = new NBTTagCompound();
        data.setTag("tempCells", tempCellList);
        sendChunked(TerminalChannels.TEMP_CELLS, data, "tempCells", "id");
    }

    protected void sendMetaIfChanged() {
        EntityPlayerMP player = getServerPlayer();
        if (player == null) return;

        NBTTagCompound meta = createMetaPayload();
        long terminalPos = meta.hasKey("terminalPos") ? meta.getLong("terminalPos") : Long.MIN_VALUE;
        int terminalDim = meta.hasKey("terminalDim") ? meta.getInteger("terminalDim") : Integer.MIN_VALUE;

        if (this.lastSentMetaNetworkId == this.currentNetworkId && this.lastSentMetaTerminalPos == terminalPos
            && this.lastSentMetaTerminalDim == terminalDim) {
            return;
        }

        ChunkedNBTSender.send(player, TerminalChannels.META, PayloadMode.FULL, meta);
        this.lastSentMetaNetworkId = this.currentNetworkId;
        this.lastSentMetaTerminalPos = terminalPos;
        this.lastSentMetaTerminalDim = terminalDim;
    }

    protected NBTTagCompound createMetaPayload() {
        NBTTagCompound meta = new NBTTagCompound();
        meta.setLong("networkId", this.currentNetworkId);
        addTerminalPosition(meta);
        return meta;
    }

    protected void sendChunked(String channel, NBTTagCompound fullPayload, String listKey, String idKey) {
        EntityPlayerMP player = getServerPlayer();
        if (player == null) return;

        fullPayload.setLong("networkId", this.currentNetworkId);

        boolean deltaEnabled = DiskTerminalServerConfig.getInstance()
            .isDeltaUpdatesEnabled();
        DeltaSnapshot.DeltaResult result;

        if (deltaEnabled) {
            result = this.deltaSnapshot.buildDelta(channel, fullPayload, listKey, idKey);
        } else {
            this.deltaSnapshot.reset(channel);
            result = new DeltaSnapshot.DeltaResult(fullPayload, true);
        }

        if (!result.isFull && result.isEmpty) return;

        ChunkedNBTSender.send(player, channel, result.isFull ? PayloadMode.FULL : PayloadMode.DELTA, result.payload);
    }

    protected EntityPlayerMP getServerPlayer() {
        EntityPlayer player = this.getPlayerInv().player;
        return (player instanceof EntityPlayerMP) ? (EntityPlayerMP) player : null;
    }

    protected void addTerminalPosition(NBTTagCompound data) {
        IActionHost host = this.getActionHost();
        if (host instanceof IUpgradeableHost) {
            TileEntity te = ((IUpgradeableHost) host).getTile();
            if (te != null) {
                data.setLong("terminalPos", PosUtil.toLong(0, 0, 0));
                data.setInteger("terminalDim", te.getWorldObj().provider.dimensionId);
            }
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return this.isValidContainer();
    }

    public void handlePartitionAction(long storageId, int cellSlot, PacketPartitionAction.Action action,
        int partitionSlot, NBTTagCompound stackData) {
        if (!DiskTerminalServerConfig.getInstance()
            .isPartitionEditEnabled()) {
            PlayerMessageHelper.error(this.getPlayerInv().player, "disk_terminal.error.partition_edit_disabled");

            return;
        }

        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        if (CellActionHandler
            .handlePartitionAction(tracker.storage, tracker.tile, cellSlot, action, partitionSlot, stackData)) {
            requestStorageRefresh();
        }
    }

    public void handleStorageBusIOModeToggle(long storageBusId) {
        StorageBusTracker tracker = this.storageBusById.get(storageBusId);
        if (tracker == null) return;

        if (StorageBusDataHandler.toggleIOMode(tracker)) requestStorageBusRefresh();
    }

    /**
     * Execute a unified capability action against a storage bus. The behavior chain resolves the
     * provider by identity and dispatches to the capability without inspecting concrete bus types.
     * Edit gates mirror the legacy per-capability config checks.
     */
    public void handleCapabilityAction(StorageBusId targetId, ResourceLocation capability, ResourceLocation action,
        NBTTagCompound payload) {
        if (!isCapabilityEditEnabled(capability)) return;

        if (storageBusActionExecutor.execute(this.storageBusProviders, targetId, capability, action, payload)) {
            requestStorageBusRefresh();
        }
    }

    private boolean isCapabilityEditEnabled(ResourceLocation capability) {
        DiskTerminalServerConfig config = DiskTerminalServerConfig.getInstance();
        EntityPlayer player = this.getPlayerInv().player;

        if (StorageBusCapabilityIds.PRIORITY.equals(capability)) {
            if (config.isPriorityEditEnabled()) return true;

            PlayerMessageHelper.error(player, "disk_terminal.error.priority_edit_disabled");

            return false;
        }

        if (StorageBusCapabilityIds.FILTER.equals(capability)) {
            if (config.isPartitionEditEnabled()) return true;

            PlayerMessageHelper.error(player, "disk_terminal.error.partition_edit_disabled");

            return false;
        }

        return true;
    }

    public void handleEjectCell(long storageId, int cellSlot, EntityPlayer player) {
        if (!DiskTerminalServerConfig.getInstance()
            .isCellEjectEnabled()) {
            PlayerMessageHelper.error(player, "disk_terminal.error.cell_eject_disabled");

            return;
        }

        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        if (CellActionHandler.ejectCell(tracker.storage, cellSlot, player)) requestStorageRefresh();
    }

    public void handleSetPriority(long storageId, int priority) {
        if (!DiskTerminalServerConfig.getInstance()
            .isPriorityEditEnabled()) {
            PlayerMessageHelper.error(this.getPlayerInv().player, "disk_terminal.error.priority_edit_disabled");

            return;
        }

        StorageTracker tracker = this.byId.get(storageId);
        if (tracker != null) {
            if (tracker.tile instanceof IPriorityHost priorityHost) {
                priorityHost.setPriority(priority);
                tracker.tile.markDirty();
                requestStorageRefresh();
            }
        }
    }

    public void handleUpgradeCell(EntityPlayer player, long storageId, int cellSlot, boolean shiftClick, int fromSlot) {
        if (!DiskTerminalServerConfig.getInstance()
            .isUpgradeInsertEnabled()) {
            PlayerMessageHelper.error(player, "disk_terminal.error.upgrade_insert_disabled");

            return;
        }

        ItemStack upgradeStack = fromSlot >= 0 ? player.inventory.getStackInSlot(fromSlot)
            : player.inventory.getItemStack();

        if (shiftClick) {
            StorageTracker targetTracker = this.byId.get(storageId);
            if (targetTracker != null) {
                IInventory cellInventory = CellDataHandler.getCellInventory(targetTracker.storage);
                if (cellInventory != null) {
                    for (int slot = 0; slot < targetTracker.storage.getCellCount(); slot++) {
                        if (CellActionHandler.upgradeCell(
                            targetTracker.storage,
                            targetTracker.tile,
                            slot,
                            upgradeStack,
                            player,
                            fromSlot)) {
                            requestStorageRefresh();

                            return;
                        }
                    }
                }

                warnUpgradeInsertFailure(player, getStorageDisplayName(targetTracker));

                return;
            }

            int terminalDim = getTerminalDimension();
            List<StorageTracker> sortedTrackers = new ArrayList<>(this.byId.values());
            sortedTrackers.sort(createTrackerComparator(terminalDim));

            for (StorageTracker tracker : sortedTrackers) {
                IInventory cellInventory = CellDataHandler.getCellInventory(tracker.storage);
                if (cellInventory == null) continue;

                for (int slot = 0; slot < tracker.storage.getCellCount(); slot++) {
                    if (CellActionHandler
                        .upgradeCell(tracker.storage, tracker.tile, slot, upgradeStack, player, fromSlot)) {
                        requestStorageRefresh();

                        return;
                    }
                }
            }

            PlayerMessageHelper.warning(player, "disk_terminal.warning.upgrade_insert_failed_any_cell");

            return;
        }

        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        if (CellActionHandler.upgradeCell(tracker.storage, tracker.tile, cellSlot, upgradeStack, player, fromSlot)) {
            requestStorageRefresh();

            return;
        }

        warnUpgradeInsertFailure(player, getCellDisplayName(tracker, cellSlot));
    }

    public void handleUpgradeStorageBus(EntityPlayer player, long storageBusId, boolean shiftClick, int fromSlot) {
        if (!DiskTerminalServerConfig.getInstance()
            .isUpgradeInsertEnabled()) {
            PlayerMessageHelper.error(player, "disk_terminal.error.upgrade_insert_disabled");

            return;
        }

        ItemStack upgradeStack = fromSlot >= 0 ? player.inventory.getStackInSlot(fromSlot)
            : player.inventory.getItemStack();

        if (ItemStacks.isEmpty(upgradeStack)) return;
        if (!(upgradeStack.getItem() instanceof IUpgradeModule)) return;

        if (shiftClick) {
            if (storageBusId != 0) {
                StorageBusTracker targetTracker = this.storageBusById.get(storageBusId);
                if (targetTracker != null) {
                    if (tryInsertUpgradeIntoStorageBus(targetTracker, upgradeStack, player, fromSlot)) {
                        requestStorageBusRefresh();

                        return;
                    }

                    warnUpgradeInsertFailure(player, getStorageBusDisplayName(targetTracker));

                    return;
                }
            }

            int terminalDim = getTerminalDimension();
            List<StorageBusTracker> sortedTrackers = new ArrayList<>(this.storageBusById.values());
            sortedTrackers.sort(createStorageBusTrackerComparator(terminalDim));

            for (StorageBusTracker tracker : sortedTrackers) {
                if (tryInsertUpgradeIntoStorageBus(tracker, upgradeStack, player, fromSlot)) {
                    requestStorageBusRefresh();

                    return;
                }
            }

            PlayerMessageHelper.warning(player, "disk_terminal.warning.upgrade_insert_failed_any_storage_bus");

            return;
        }

        StorageBusTracker tracker = this.storageBusById.get(storageBusId);
        if (tracker == null) return;

        if (tryInsertUpgradeIntoStorageBus(tracker, upgradeStack, player, fromSlot)) {
            requestStorageBusRefresh();

            return;
        }

        warnUpgradeInsertFailure(player, getStorageBusDisplayName(tracker));
    }

    private boolean tryInsertUpgradeIntoStorageBus(StorageBusTracker tracker, ItemStack upgradeStack,
        EntityPlayer player, int fromSlot) {
        IInventory upgradesInv = getStorageBusUpgradeInventory(tracker);
        if (upgradesInv == null) return false;

        ItemStack toInsert = upgradeStack.copy();
        toInsert.stackSize = 1;

        for (int slot = 0; slot < upgradesInv.getSizeInventory(); slot++) {
            ItemStack remainder = InventoryHelper.insert(upgradesInv, slot, toInsert, false);
            if (ItemStacks.isEmpty(remainder)) {
                upgradeStack.stackSize--;
                upgradesInv.markDirty();
                tracker.hostTile.markDirty();

                if (fromSlot >= 0) {
                    if (upgradeStack.stackSize <= 0) player.inventory.setInventorySlotContents(fromSlot, null);
                    player.inventory.markDirty();
                } else {
                    if (upgradeStack.stackSize <= 0) player.inventory.setItemStack(null);
                    ((EntityPlayerMP) player).updateHeldItem();
                }

                return true;
            }
        }

        return false;
    }

    private IInventory getStorageBusUpgradeInventory(Object storageBus) {
        if (storageBus instanceof PartUpgradeable) {
            return ((PartUpgradeable) storageBus).getInventoryByName("upgrades");
        }

        if (storageBus instanceof IUpgradeable) {
            return ((IUpgradeable) storageBus).getUpgradeInventory();
        }

        return null;
    }

    private IInventory getStorageBusUpgradeInventory(StorageBusTracker tracker) {
        if (tracker == null) return null;

        return getStorageBusUpgradeInventory(tracker.storageBus);
    }

    private void warnUpgradeInsertFailure(EntityPlayer player, IChatComponent targetName) {
        if (targetName != null) {
            PlayerMessageHelper
                .warning(player, "disk_terminal.warning.upgrade_insert_failed", targetName.getUnformattedText());

            return;
        }

        PlayerMessageHelper.warning(player, "disk_terminal.warning.upgrade_insert_failed_generic");
    }

    private IChatComponent getStorageDisplayName(StorageTracker tracker) {
        if (tracker == null) return null;

        IChatComponent customName = getCustomInventoryName(tracker.tile);
        if (customName != null) return customName;

        if (tracker.tile != null) {
            ItemStack displayStack = CellsIntegration.getTileDisplayStack(tracker.tile);
            if (!ItemStacks.isEmpty(displayStack)) return new ChatComponentText(displayStack.getDisplayName());
        }

        return tracker.tile == null ? null
            : new ChatComponentText(
                tracker.tile.getClass()
                    .getSimpleName());
    }

    private IChatComponent getCellDisplayName(StorageTracker tracker, int cellSlot) {
        if (tracker == null) return null;

        IInventory cellInventory = CellDataHandler.getCellInventory(tracker.storage);
        if (cellInventory == null || cellSlot < 0 || cellSlot >= tracker.storage.getCellCount()) {
            return getStorageDisplayName(tracker);
        }

        ItemStack cellStack = CellDataHandler.getCellStack(cellInventory, tracker.storage, cellSlot);
        if (!ItemStacks.isEmpty(cellStack)) return new ChatComponentText(cellStack.getDisplayName());

        return new ChatComponentTranslation("gui.disk_terminal.cell_empty");
    }

    private IChatComponent getStorageBusDisplayName(StorageBusTracker tracker) {
        if (tracker == null) return null;

        IChatComponent persistedCustomName = getStorageBusPersistedCustomName(tracker);
        if (persistedCustomName != null) return persistedCustomName;

        IChatComponent customName = getCustomInventoryName(tracker.storageBus);
        if (customName != null) return customName;

        if (tracker.storageBus instanceof MetaTileEntity metaTileEntity) {
            return new ChatComponentText(metaTileEntity.getLocalName());
        }

        if (tracker.storageBus instanceof PartSharedItemBus<?>bus) {
            ItemStack busStack = bus.getItemStack();
            if (!ItemStacks.isEmpty(busStack)) return new ChatComponentText(busStack.getDisplayName());
        }

        ItemStack busDisplay = CellsIntegration.getHostDisplayStack(tracker.storageBus);
        if (ItemStacks.isEmpty(busDisplay) && tracker.hostTile != null) {
            busDisplay = CellsIntegration.getTileDisplayStack(tracker.hostTile);
        }

        IChatComponent baseName = ItemStacks.isEmpty(busDisplay)
            ? new ChatComponentTranslation("gui.disk_terminal.storage_bus.name")
            : new ChatComponentText(busDisplay.getDisplayName());

        return baseName;
    }

    private IChatComponent getStorageBusPersistedCustomName(StorageBusTracker tracker) {
        if (tracker.hostTile == null) return null;

        StorageBusCustomNameData data = StorageBusCustomNameData.get(tracker.hostTile.getWorldObj());
        if (data == null) return null;

        String customName = data.getCustomName(tracker.id);
        if (customName == null || customName.isEmpty()) return null;

        return new ChatComponentText(customName);
    }

    private IChatComponent getCustomInventoryName(Object nameableCandidate) {
        if (!(nameableCandidate instanceof ICustomNameObject nameable)) return null;

        if (!nameable.hasCustomName()) return null;

        String customName = nameable.getCustomName();
        if (customName == null || customName.isEmpty()) return null;

        return new ChatComponentText(customName);
    }

    public void handleExtractUpgrade(EntityPlayer player, PacketExtractUpgrade.TargetType targetType, long targetId,
        int cellSlot, int upgradeIndex, boolean toInventory) {
        if (!DiskTerminalServerConfig.getInstance()
            .isUpgradeExtractEnabled()) {
            PlayerMessageHelper.error(player, "disk_terminal.error.upgrade_extract_disabled");

            return;
        }

        IInventory upgradesInv = null;
        TileEntity tile = null;

        if (targetType == PacketExtractUpgrade.TargetType.CELL) {
            StorageTracker tracker = this.byId.get(targetId);
            if (tracker == null) return;

            IInventory cellInventory = CellDataHandler.getCellInventory(tracker.storage);
            if (cellInventory == null) return;

            ItemStack cellStack = CellDataHandler.getCellStack(cellInventory, tracker.storage, cellSlot);
            if (ItemStacks.isEmpty(cellStack) || !(cellStack.getItem() instanceof ICellWorkbenchItem cellItem)) return;

            if (!cellItem.isEditable(cellStack)) return;

            upgradesInv = cellItem.getUpgradesInventory(cellStack);
            tile = tracker.tile;
        } else if (targetType == PacketExtractUpgrade.TargetType.TEMP_CELL) {
            IInventory tempInv = getTempCellInventory();
            if (tempInv == null) return;

            int tempSlotIndex = (int) targetId;
            if (tempSlotIndex < 0 || tempSlotIndex >= tempInv.getSizeInventory()) return;

            ItemStack cellStack = tempInv.getStackInSlot(tempSlotIndex);
            if (ItemStacks.isEmpty(cellStack) || !(cellStack.getItem() instanceof ICellWorkbenchItem cellItem)) return;

            if (!cellItem.isEditable(cellStack)) return;

            upgradesInv = cellItem.getUpgradesInventory(cellStack);
            tile = null;
        } else {
            StorageBusTracker tracker = this.storageBusById.get(targetId);
            if (tracker == null) return;

            upgradesInv = getStorageBusUpgradeInventory(tracker);
            tile = tracker.hostTile;
        }

        if (upgradesInv == null) return;
        if (upgradeIndex < 0 || upgradeIndex >= upgradesInv.getSizeInventory()) return;

        ItemStack upgradeStack = InventoryHelper.extract(upgradesInv, upgradeIndex, 1, false);
        if (ItemStacks.isEmpty(upgradeStack)) return;

        boolean success;

        if (toInventory) {
            success = player.inventory.addItemStackToInventory(upgradeStack);
            if (success) player.inventory.markDirty();
        } else {
            ItemStack heldStack = player.inventory.getItemStack();
            if (ItemStacks.isEmpty(heldStack)) {
                player.inventory.setItemStack(upgradeStack);
                ((EntityPlayerMP) player).updateHeldItem();
                success = true;
            } else if (heldStack.isItemEqual(upgradeStack) && ItemStack.areItemStackTagsEqual(heldStack, upgradeStack)
                && heldStack.stackSize < heldStack.getMaxStackSize()) {
                    heldStack.stackSize++;
                    ((EntityPlayerMP) player).updateHeldItem();
                    success = true;
                } else {
                    success = false;
                }
        }

        if (!success) {
            player.dropPlayerItemWithRandomChoice(upgradeStack, false);
        }

        if (tile != null) tile.markDirty();

        if (targetType == PacketExtractUpgrade.TargetType.CELL) {
            requestStorageRefresh();
        } else if (targetType == PacketExtractUpgrade.TargetType.TEMP_CELL) {
            IInventory tempInv = getTempCellInventory();
            if (tempInv != null) {
                int tempSlotIndex = (int) targetId;
                if (tempSlotIndex >= 0 && tempSlotIndex < tempInv.getSizeInventory()) {
                    ItemStack cellStack = tempInv.getStackInSlot(tempSlotIndex);
                    tempInv.setInventorySlotContents(tempSlotIndex, cellStack);
                }
            }

            requestTempCellRefresh();
        } else {
            requestStorageBusRefresh();
        }
    }

    public void handlePickupCell(long storageId, int cellSlot, EntityPlayer player, boolean toInventory) {
        DiskTerminalServerConfig config = DiskTerminalServerConfig.getInstance();
        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        IInventory cellInventory = CellDataHandler.getCellInventory(tracker.storage);
        if (cellInventory == null) return;

        ItemStack cellStack = CellDataHandler.getCellStack(cellInventory, tracker.storage, cellSlot);
        ItemStack heldStack = player.inventory.getItemStack();

        if (ItemStacks.isEmpty(cellStack)) {
            if (!toInventory && !ItemStacks.isEmpty(heldStack)) {
                if (!config.isCellInsertEnabled()) {
                    PlayerMessageHelper.error(player, "disk_terminal.error.cell_insert_disabled");

                    return;
                }
            }
        } else if (toInventory) {
            if (!config.isCellEjectEnabled()) {
                PlayerMessageHelper.error(player, "disk_terminal.error.cell_eject_disabled");

                return;
            }
        } else if (!ItemStacks.isEmpty(heldStack)) {
            if (!config.isCellSwapEnabled()) {
                PlayerMessageHelper.error(player, "disk_terminal.error.cell_swap_disabled");

                return;
            }
        } else {
            if (!config.isCellEjectEnabled()) {
                PlayerMessageHelper.error(player, "disk_terminal.error.cell_eject_disabled");

                return;
            }
        }

        if (CellActionHandler.pickupCell(tracker.storage, cellSlot, player, toInventory)) {
            requestStorageRefresh();
        }
    }

    public void handleInsertCell(long storageId, int targetSlot, EntityPlayer player) {
        if (!DiskTerminalServerConfig.getInstance()
            .isCellInsertEnabled()) {
            PlayerMessageHelper.error(player, "disk_terminal.error.cell_insert_disabled");

            return;
        }

        StorageTracker tracker = this.byId.get(storageId);
        if (tracker == null) return;

        if (CellActionHandler.insertCell(tracker.storage, targetSlot, player)) {
            requestStorageRefresh();
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = this.inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return super.transferStackInSlot(player, slotIndex);

        ItemStack stack = slot.getStack();

        if (AEApi.instance()
            .registries()
            .cell()
            .getHandler(stack) == null) {
            return super.transferStackInSlot(player, slotIndex);
        }

        if (activeTab == GuiConstants.TAB_TEMP_AREA) {
            IInventory tempInv = getTempCellInventory();
            if (tempInv != null) {
                for (int i = 0; i < tempInv.getSizeInventory(); i++) {
                    if (ItemStacks.isEmpty(tempInv.getStackInSlot(i))) {
                        ItemStack singleCell = stack.splitStack(1);
                        tempInv.setInventorySlotContents(i, singleCell);

                        if (ItemStacks.isEmpty(stack)) slot.putStack(null);

                        slot.onSlotChanged();
                        requestTempCellRefresh();
                        this.detectAndSendChanges();

                        return null;
                    }
                }

                PlayerMessageHelper.error(player, "gui.disk_terminal.temp_area.full");

                return null;
            }
        }

        if (!canShiftInsertCellsOnActiveTab()) return super.transferStackInSlot(player, slotIndex);

        if (!DiskTerminalServerConfig.getInstance()
            .isCellInsertEnabled()) {
            PlayerMessageHelper.error(player, "disk_terminal.error.cell_insert_disabled");

            return null;
        }

        int terminalDim = getTerminalDimension();

        List<StorageTracker> sortedTrackers = new ArrayList<>(this.byId.values());
        sortedTrackers.sort(createTrackerComparator(terminalDim));

        for (StorageTracker tracker : sortedTrackers) {
            IInventory cellInventory = CellDataHandler.getCellInventory(tracker.storage);
            if (cellInventory == null) continue;

            int emptySlot = CellDataHandler.findEmptyCellSlot(cellInventory, tracker.storage);
            if (emptySlot < 0) continue;

            int inventorySlot = CellDataHandler.toInventorySlot(tracker.storage, emptySlot);
            ItemStack remainder = InventoryHelper.insert(cellInventory, inventorySlot, stack.copy(), false);
            int remainderCount = remainder == null ? 0 : remainder.stackSize;
            if (remainderCount < stack.stackSize) {
                slot.putStack(remainder);
                slot.onSlotChanged();
                cellInventory.markDirty();
                tracker.tile.markDirty();
                requestStorageRefresh();
                this.detectAndSendChanges();

                return null;
            }
        }

        return super.transferStackInSlot(player, slotIndex);
    }

    private boolean canShiftInsertCellsOnActiveTab() {
        return activeTab != GuiConstants.TAB_STORAGE_BUS_INVENTORY
            && activeTab != GuiConstants.TAB_STORAGE_BUS_PARTITION
            && activeTab != GuiConstants.TAB_NETWORK_TOOLS;
    }

    protected int getTerminalDimension() {
        IActionHost host = this.getActionHost();
        if (host instanceof IUpgradeableHost) {
            TileEntity te = ((IUpgradeableHost) host).getTile();
            if (te != null) return te.getWorldObj().provider.dimensionId;
        }

        return 0;
    }

    protected Comparator<StorageTracker> createTrackerComparator(int terminalDim) {
        return (a, b) -> {
            int dimA = a.tile.getWorldObj().provider.dimensionId;
            int dimB = b.tile.getWorldObj().provider.dimensionId;

            boolean aInDim = dimA == terminalDim;
            boolean bInDim = dimB == terminalDim;
            if (aInDim != bInDim) return aInDim ? -1 : 1;

            if (dimA != dimB) return Integer.compare(dimA, dimB);

            double distA = distSqToOrigin(a.tile);
            double distB = distSqToOrigin(b.tile);

            return Double.compare(distA, distB);
        };
    }

    protected Comparator<StorageBusTracker> createStorageBusTrackerComparator(int terminalDim) {
        return (a, b) -> {
            int dimA = a.hostTile.getWorldObj().provider.dimensionId;
            int dimB = b.hostTile.getWorldObj().provider.dimensionId;

            boolean aInDim = dimA == terminalDim;
            boolean bInDim = dimB == terminalDim;
            if (aInDim != bInDim) return aInDim ? -1 : 1;

            if (dimA != dimB) return Integer.compare(dimA, dimB);

            double distA = distSqToOrigin(a.hostTile);
            double distB = distSqToOrigin(b.hostTile);

            return Double.compare(distA, distB);
        };
    }

    private static double distSqToOrigin(TileEntity te) {
        double x = te.xCoord + 0.5;
        double y = te.yCoord + 0.5;
        double z = te.zCoord + 0.5;

        return x * x + y * y + z * z;
    }

    public void handleNetworkToolAction(String toolId, Map<CellFilter, CellFilter.State> activeFilters) {
        EntityPlayer player = this.getPlayerInv().player;
        NetworkToolActionHandler.handleAction(toolId, activeFilters, byId, storageBusById, grid, player);

        if (NetworkToolActionHandler.affectsStorages(toolId)) requestStorageRefresh();
        if (NetworkToolActionHandler.affectsStorageBuses(toolId)) requestStorageBusRefresh();
    }

    public void handleTempCellAction(PacketTempCellAction.Action action, int tempSlotIndex, int playerSlotIndex,
        boolean toInventory) {
        if (!DiskTerminalServerConfig.getInstance()
            .isTabTempAreaEnabled()) {
            PlayerMessageHelper.error(this.getPlayerInv().player, "disk_terminal.error.temp_area_disabled");

            return;
        }

        EntityPlayer player = this.getPlayerInv().player;
        TempCellActionHandler.handleAction(this, action, tempSlotIndex, playerSlotIndex, player, toInventory);
    }

    public void handleTempCellPartitionAction(int tempSlotIndex, PacketTempCellPartitionAction.Action action,
        int partitionSlot, NBTTagCompound stackData) {
        if (!DiskTerminalServerConfig.getInstance()
            .isPartitionEditEnabled()) {
            PlayerMessageHelper.error(this.getPlayerInv().player, "disk_terminal.error.partition_edit_disabled");

            return;
        }

        if (!DiskTerminalServerConfig.getInstance()
            .isTabTempAreaEnabled()) {
            PlayerMessageHelper.error(this.getPlayerInv().player, "disk_terminal.error.temp_area_disabled");

            return;
        }

        TempCellActionHandler.handlePartitionAction(this, tempSlotIndex, action, partitionSlot, stackData);
    }

    public void requestSubnetRefresh() {
        requestSubnetRefresh(false);
    }

    public void requestSubnetRefresh(boolean forceFull) {
        if (forceFull) this.deltaSnapshot.reset(TerminalChannels.SUBNETS);

        this.needsSubnetRefresh = true;
    }

    protected void regenSubnetList() {
        this.subnetById.clear();

        if (this.grid == null) return;

        int playerId = this.getPlayerInv().player.getEntityId();
        NBTTagCompound data = new NBTTagCompound();
        data.setTag(
            "subnets",
            SubnetDataHandler.collectSubnets(this.grid, this.subnetById, playerId, this.subnetSlotLimit));

        sendChunked(TerminalChannels.SUBNETS, data, "subnets", "id");
    }

    public void handleSubnetAction(long subnetId, SubnetDataHandler.SubnetAction action, NBTTagCompound data) {
        EntityPlayer player = this.getPlayerInv().player;

        if (this.subnetById.isEmpty() && this.grid != null) {
            SubnetDataHandler.collectSubnets(this.grid, this.subnetById, player.getEntityId(), this.subnetSlotLimit);
        }

        if (SubnetDataHandler.handleSubnetAction(this.subnetById, subnetId, action, data, player)) {
            requestSubnetRefresh();
        }
    }

    public void handleSubnetPartitionAction(long subnetId, long pos, int side,
        PacketSubnetPartitionAction.Action action, int partitionSlot, NBTTagCompound stackData) {
        if (!DiskTerminalServerConfig.getInstance()
            .isPartitionEditEnabled()) {
            PlayerMessageHelper.error(this.getPlayerInv().player, "disk_terminal.error.partition_edit_disabled");

            return;
        }

        EntityPlayer player = this.getPlayerInv().player;

        if (this.subnetById.isEmpty() && this.grid != null) {
            SubnetDataHandler.collectSubnets(this.grid, this.subnetById, player.getEntityId(), this.subnetSlotLimit);
        }

        if (SubnetDataHandler
            .handleSubnetPartitionAction(this.subnetById, subnetId, pos, side, action, partitionSlot, stackData)) {
            this.needsSubnetRefresh = true;
        }
    }

    public void switchNetwork(long networkId) {
        if (networkId == 0) {
            this.currentNetworkId = 0;
            this.currentNetworkGrid = this.grid;
        } else {
            SubnetTracker tracker = this.subnetById.get(networkId);

            if (tracker == null) {
                regenSubnetList();
                tracker = this.subnetById.get(networkId);
            }

            if (tracker == null) {
                DiskTerminal.LOG.warn("Cannot switch to unknown subnet: {}", networkId);
                return;
            }

            this.currentNetworkId = networkId;
            this.currentNetworkGrid = tracker.targetGrid;
        }

        this.deltaSnapshot.resetAll();
        this.lastSentMetaNetworkId = Long.MIN_VALUE;
        this.lastSentMetaTerminalPos = Long.MIN_VALUE;
        this.lastSentMetaTerminalDim = Integer.MIN_VALUE;

        requestFullRefresh();
        requestStorageBusRefresh();
    }

    public IChatComponent getGridName() {
        if (currentNetworkId == 0) return new ChatComponentTranslation("disk_terminal.subnet.main_network");

        IGrid effectiveGrid = getEffectiveGrid();
        if (effectiveGrid == null) return new ChatComponentText("Unknown Network");

        return getSubnetName(effectiveGrid);
    }

    private IChatComponent getSubnetName(IGrid grid) {
        IInterfaceHost interfaceHost = SubnetDataHandler.findPrimaryInterfaceHost(grid);

        if (interfaceHost instanceof ICustomNameObject nameable) {
            if (nameable.hasCustomName()) return new ChatComponentText(nameable.getCustomName());
        }

        if (interfaceHost != null) {
            TileEntity tile = interfaceHost.getTileEntity();

            if (tile != null) {
                return new ChatComponentTranslation(
                    "gui.disk_terminal.subnet.default_name",
                    tile.xCoord,
                    tile.yCoord,
                    tile.zCoord);
            }
        }

        return new ChatComponentText("Unknown Network");
    }

    protected IGrid getEffectiveGrid() {
        if (currentNetworkId != 0 && currentNetworkGrid != null) return currentNetworkGrid;

        return this.grid;
    }

    public static class StorageTracker {

        public final long id;
        public final TileEntity tile;
        public final IChestOrDrive storage;

        public StorageTracker(long id, TileEntity tile, IChestOrDrive storage) {
            this.id = id;
            this.tile = tile;
            this.storage = storage;
        }
    }

    public StorageTracker getStorageTracker(long storageId) {
        return this.byId.get(storageId);
    }

    public Collection<StorageTracker> getStorageTrackers() {
        return this.byId.values();
    }

    public StorageBusTracker getStorageBusTracker(long storageBusId) {
        return this.storageBusById.get(storageBusId);
    }

    public void requestFullRefresh() {
        this.needsFullRefresh = true;
        this.needsStorageRefresh = true;
        this.needsTempCellRefresh = true;
    }

    public void requestStorageRefresh() {
        this.needsStorageRefresh = true;
        this.needsFullRefresh = true;
    }

    public void requestStorageBusRefresh() {
        requestStorageBusRefresh(false);
    }

    public void requestStorageBusRefresh(boolean urgent) {
        this.needsStorageBusRefresh = true;
        this.storageBusRefreshUrgent |= urgent;
        if (tabNeedsStorageBuses(this.activeTab)) this.needsFullRefresh = true;
    }

    public void requestTempCellRefresh() {
        this.needsTempCellRefresh = true;
        this.needsFullRefresh = true;
    }

    public IInventory getTempCellInventory() {
        return null;
    }

    protected void regenDirtySectionsForActiveTab() {
        if (tabNeedsStorages(this.activeTab) && this.needsStorageRefresh) regenStorageList();
        if (tabNeedsStorageBuses(this.activeTab) && this.needsStorageBusRefresh) regenStorageBusList();
        if (tabNeedsTempCells(this.activeTab) && this.needsTempCellRefresh) regenTempCellList();
    }

    protected boolean tabNeedsStorages(int tab) {
        return tab != GuiConstants.TAB_STORAGE_BUS_INVENTORY && tab != GuiConstants.TAB_STORAGE_BUS_PARTITION
            && tab != GuiConstants.TAB_SUBNETS;
    }

    protected boolean tabNeedsStorageBuses(int tab) {
        return tab == GuiConstants.TAB_STORAGE_BUS_INVENTORY || tab == GuiConstants.TAB_STORAGE_BUS_PARTITION;
    }

    protected boolean tabNeedsTempCells(int tab) {
        return tab == GuiConstants.TAB_TEMP_AREA;
    }
}
