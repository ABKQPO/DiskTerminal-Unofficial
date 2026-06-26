package com.hfstudio.diskterminal.container.handler;

import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.integration.storagebus.StorageBusScannerRegistry;
import com.hfstudio.diskterminal.network.PacketStorageBusPartitionAction.Action;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigManager;
import appeng.helpers.ICustomNameObject;
import appeng.parts.automation.PartSharedItemBus;
import appeng.parts.automation.PartUpgradeable;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.IterationCounter;

/**
 * Handles storage bus data collection and NBT generation.
 * Server-side handler that generates the data sent to clients when they view storage bus tabs.
 * <p>
 * In the 1.7.10 fork the fluid storage bus (AE2FluidCraft) is a subclass of {@link PartStorageBus}
 * that only differs by its {@link PartStorageBus#getStackType() stack type}, so item and fluid
 * buses share a single code path here. Stored contents are read uniformly through the bus's
 * internal ME handler.
 */
public class StorageBusDataHandler {

    private static final int MAX_CONTENT_ENTRIES_PER_BUS_PAYLOAD = 1024;

    /**
     * Tracker for storage bus instances, keyed by a synthetic bus ID.
     */
    public static class StorageBusTracker {

        public final long id;
        public final Object storageBus; // A PartStorageBus (item or fluid) or storage-bus-like machine.
        public final TileEntity hostTile;
        public final int sideOrdinal;
        public final StorageType storageType;

        public StorageBusTracker(long id, Object storageBus, TileEntity hostTile) {
            this(id, storageBus, hostTile, -1, null);
        }

        public StorageBusTracker(long id, Object storageBus, TileEntity hostTile, int sideOrdinal,
            StorageType storageType) {
            this.id = id;
            this.storageBus = storageBus;
            this.hostTile = hostTile;
            this.sideOrdinal = sideOrdinal;
            this.storageType = storageType;
        }
    }

    /**
     * Collect all storage buses from the grid and generate NBT data.
     *
     * @param grid       The ME network grid
     * @param trackerMap Map to populate with trackers (keyed by bus ID)
     * @return NBTTagList containing all storage bus data
     */
    public static NBTTagList collectStorageBuses(IGrid grid, Map<Long, StorageBusTracker> trackerMap,
        int contentLimit) {
        NBTTagList storageBusList = new NBTTagList();

        if (grid == null) return storageBusList;

        StorageBusScannerRegistry.scanAll(grid, storageBusList, trackerMap, contentLimit);

        return storageBusList;
    }

    /**
     * Create a unique bus ID from position, dimension, side, and type flag.
     */
    public static long createBusId(TileEntity hostTile, int sideOrdinal, int typeFlag) {
        long pos = PosUtil.toLong(hostTile.xCoord, hostTile.yCoord, hostTile.zCoord);

        return pos ^ ((long) hostTile.getWorldObj().provider.dimensionId << 48)
            ^ ((long) sideOrdinal << 40)
            ^ ((long) typeFlag << 39);
    }

    /**
     * Create NBT data for an item storage bus.
     */
    public static NBTTagCompound createItemStorageBusData(PartStorageBus bus, long busId, int contentLimit) {
        return createStorageBusData(bus, busId, StorageType.ITEM, contentLimit);
    }

    /**
     * Create NBT data for a fluid storage bus.
     */
    public static NBTTagCompound createFluidStorageBusData(PartStorageBus bus, long busId, int contentLimit) {
        return createStorageBusData(bus, busId, StorageType.FLUID, contentLimit);
    }

    public static NBTTagCompound createSharedBusData(PartSharedItemBus<?> bus, long busId, StorageType storageType,
        BusRole busRole, int contentLimit) {
        TileEntity hostTile = bus.getHost()
            .getTile();
        ForgeDirection side = bus.getSide();

        NBTTagCompound busData = new NBTTagCompound();
        busData.setLong("id", busId);
        busData.setLong("pos", PosUtil.toLong(hostTile.xCoord, hostTile.yCoord, hostTile.zCoord));
        busData.setInteger("dim", hostTile.getWorldObj().provider.dimensionId);
        busData.setInteger("side", side.ordinal());
        busData.setInteger("priority", 0);
        storageType.writeToNBT(busData);
        busRole.writeToNBT(busData);

        busData.setInteger("access", AccessRestriction.READ_WRITE.ordinal());
        busData.setString("namePrefixKey", busRole.getPrefixKey());
        applySharedBusSlotParameters(busData, bus);

        addBusItemIcon(busData, bus.getItemStack());
        addCustomName(busData, bus);
        addConnectedInventoryInfo(busData, hostTile, side);
        addBusItemIconAsConnectedIconIfMissing(busData, bus.getItemStack());

        int capacityUpgrades = bus.getInstalledUpgrades(Upgrades.CAPACITY);
        addSharedBusPartitionData(busData, bus, capacityUpgrades);
        addSharedBusContentsData(busData, bus, contentLimit);
        addUpgradesData(busData, bus.getInventoryByName("upgrades"));

        return busData;
    }

    private static NBTTagCompound createStorageBusData(PartStorageBus bus, long busId, StorageType storageType,
        int contentLimit) {
        TileEntity hostTile = bus.getHost()
            .getTile();
        ForgeDirection side = bus.getSide();

        NBTTagCompound busData = new NBTTagCompound();
        busData.setLong("id", busId);
        busData.setLong("pos", PosUtil.toLong(hostTile.xCoord, hostTile.yCoord, hostTile.zCoord));
        busData.setInteger("dim", hostTile.getWorldObj().provider.dimensionId);
        busData.setInteger("side", side.ordinal());
        busData.setInteger("priority", bus.getPriority());
        storageType.writeToNBT(busData);
        BusRole.STORAGE.writeToNBT(busData);

        AccessRestriction access = (AccessRestriction) bus.getConfigManager()
            .getSetting(Settings.ACCESS);
        busData.setInteger("access", access.ordinal());

        addBusItemIcon(busData, bus.getItemStack());
        addCustomName(busData, bus);
        addConnectedInventoryInfo(busData, hostTile, side);
        addBusItemIconAsConnectedIconIfMissing(busData, bus.getItemStack());

        int capacityUpgrades = bus.getInstalledUpgrades(Upgrades.CAPACITY);
        addPartitionData(busData, bus, capacityUpgrades);
        addContentsData(busData, bus, storageType, contentLimit);
        addUpgradesData(busData, bus.getInventoryByName("upgrades"));

        return busData;
    }

    private static void addCustomName(NBTTagCompound busData, Object bus) {
        if (!(bus instanceof ICustomNameObject nameable)) return;

        if (nameable.hasCustomName()) {
            String customName = nameable.getCustomName();
            if (customName != null && !customName.isEmpty()) busData.setString("customName", customName);
        }
    }

    private static void addConnectedInventoryInfo(NBTTagCompound busData, TileEntity hostTile, ForgeDirection facing) {
        World world = hostTile.getWorldObj();
        int x = hostTile.xCoord + facing.offsetX;
        int y = hostTile.yCoord + facing.offsetY;
        int z = hostTile.zCoord + facing.offsetZ;

        ItemStack blockStack = getBlockAsItemStack(world, x, y, z);
        if (ItemStacks.isEmpty(blockStack)) return;

        busData.setBoolean("connectedIconIsTarget", true);
        busData.setString("connectedName", blockStack.getDisplayName());

        NBTTagCompound iconNbt = new NBTTagCompound();
        blockStack.writeToNBT(iconNbt);
        busData.setTag("connectedIcon", iconNbt);
    }

    private static void addBusItemIcon(NBTTagCompound busData, ItemStack busStack) {
        if (ItemStacks.isEmpty(busStack)) return;

        NBTTagCompound iconNbt = new NBTTagCompound();
        busStack.writeToNBT(iconNbt);
        busData.setTag("busIcon", iconNbt);
    }

    private static void addBusItemIconAsConnectedIconIfMissing(NBTTagCompound busData, ItemStack busStack) {
        if (busData.hasKey("connectedIcon") || ItemStacks.isEmpty(busStack)) return;

        busData.setBoolean("connectedIconIsTarget", false);
        busData.setString("connectedName", busStack.getDisplayName());

        NBTTagCompound iconNbt = new NBTTagCompound();
        busStack.writeToNBT(iconNbt);
        busData.setTag("connectedIcon", iconNbt);
        busData.removeTag("busIcon");
    }

    private static void applySharedBusSlotParameters(NBTTagCompound busData, PartSharedItemBus<?> bus) {
        IAEStackInventory configInv = bus.getAEInventoryByName(StorageName.CONFIG);
        int maxConfigSlots = configInv == null ? 9 : configInv.getSizeInventory();

        busData.setInteger("baseConfigSlots", 1);
        busData.setInteger("slotsPerUpgrade", 4);
        busData.setInteger("maxConfigSlots", maxConfigSlots);
    }

    private static void addPartitionData(NBTTagCompound busData, PartStorageBus bus, int capacityUpgrades) {
        IAEStackInventory configInv = bus.getAEInventoryByName(StorageName.CONFIG);
        if (configInv == null) return;

        int slotsToUse = computeAvailableSlotsFrom(busData, capacityUpgrades);
        NBTTagList partitionList = new NBTTagList();

        for (int i = 0; i < configInv.getSizeInventory() && i < slotsToUse; i++) {
            IAEStack<?> partitionStack = configInv.getAEStackInSlot(i);
            if (partitionStack == null) continue;

            NBTTagCompound partNbt = createPartitionSlotData(i, partitionStack);
            partitionList.appendTag(partNbt);
        }

        busData.setTag("partition", partitionList);
    }

    private static void addSharedBusPartitionData(NBTTagCompound busData, PartSharedItemBus<?> bus,
        int capacityUpgrades) {
        IAEStackInventory configInv = bus.getAEInventoryByName(StorageName.CONFIG);
        if (configInv == null) return;

        int slotsToUse = computeAvailableSlotsFrom(busData, capacityUpgrades);
        NBTTagList partitionList = new NBTTagList();

        for (int i = 0; i < configInv.getSizeInventory() && i < slotsToUse; i++) {
            IAEStack<?> partitionStack = configInv.getAEStackInSlot(i);
            if (partitionStack == null) continue;

            NBTTagCompound partNbt = createPartitionSlotData(i, partitionStack);
            partitionList.appendTag(partNbt);
        }

        busData.setTag("partition", partitionList);
    }

    private static NBTTagCompound createPartitionSlotData(int slot, IAEStack<?> partitionStack) {
        NBTTagCompound partNbt = new NBTTagCompound();
        partNbt.setInteger("slot", slot);
        AEStackUtil.writeStackToNBT(partNbt, partitionStack);

        return partNbt;
    }

    private static void addContentsData(NBTTagCompound busData, PartStorageBus bus, StorageType storageType,
        int contentLimit) {
        IMEInventoryHandler<?> handler = bus.getInternalHandler();
        if (handler == null) return;

        IAEStackType<?> type = handler.getStackType();
        if (type == null) return;

        busData.setString("stackType", type.getId());
        addContentsForUnknownType(busData, handler, type, contentLimit);
    }

    private static void addSharedBusContentsData(NBTTagCompound busData, PartSharedItemBus<?> bus, int contentLimit) {
        IAEStackType<?> type = bus.getStackType();
        if (type == null) return;

        busData.setString("stackType", type.getId());

        IMEMonitor<?> monitor = getSharedBusMonitor(bus);
        if (monitor == null) return;

        addMonitorContentsForUnknownType(busData, monitor, type, contentLimit);
    }

    private static IMEMonitor<?> getSharedBusMonitor(PartSharedItemBus<?> bus) {
        IGrid grid = null;
        try {
            if (bus.getGridNode() != null) grid = bus.getGridNode()
                .getGrid();
        } catch (RuntimeException ignored) {
            return null;
        }

        if (grid == null) return null;

        try {
            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            if (storageGrid == null) return null;

            return storageGrid.getMEMonitor(bus.getStackType());
        } catch (RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> IMEInventoryHandler<T> castHandler(IMEInventoryHandler<?> handler) {
        return (IMEInventoryHandler<T>) handler;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> void addContentsForUnknownType(NBTTagCompound busData,
        IMEInventoryHandler<?> handler, IAEStackType<?> type, int contentLimit) {
        addContentsForType(busData, castHandler(handler), (IAEStackType<T>) type, contentLimit);
    }

    private static <T extends IAEStack<T>> void addContentsForType(NBTTagCompound busData,
        IMEInventoryHandler<T> handler, IAEStackType<T> type, int contentLimit) {
        IItemList<T> list = type.createList();
        handler.getAvailableItems(list, IterationCounter.fetchNewId());

        NBTTagList contentsList = new NBTTagList();
        int written = 0;
        for (T stack : list) {
            if (isContentLimitReached(written, contentLimit)) break;

            NBTTagCompound stackNbt = new NBTTagCompound();
            AEStackUtil.writeStackToNBT(stackNbt, stack);
            stackNbt.setLong("Cnt", stack.getStackSize());
            contentsList.appendTag(stackNbt);
            written++;
        }

        busData.setTag("contents", contentsList);
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> void addMonitorContentsForUnknownType(NBTTagCompound busData,
        IMEMonitor<?> monitor, IAEStackType<?> type, int contentLimit) {
        addMonitorContentsForType(busData, (IMEMonitor<T>) monitor, (IAEStackType<T>) type, contentLimit);
    }

    private static <T extends IAEStack<T>> void addMonitorContentsForType(NBTTagCompound busData, IMEMonitor<T> monitor,
        IAEStackType<T> type, int contentLimit) {
        IItemList<T> list = type.createList();
        monitor.getAvailableItems(list, IterationCounter.fetchNewId());

        NBTTagList contentsList = new NBTTagList();
        int written = 0;
        for (T stack : list) {
            if (isContentLimitReached(written, contentLimit)) break;

            NBTTagCompound stackNbt = new NBTTagCompound();
            AEStackUtil.writeStackToNBT(stackNbt, stack);
            stackNbt.setLong("Cnt", stack.getStackSize());
            contentsList.appendTag(stackNbt);
            written++;
        }

        busData.setTag("contents", contentsList);
    }

    private static boolean isContentLimitReached(int written, int contentLimit) {
        return written >= getEffectiveContentLimit(contentLimit);
    }

    private static int getEffectiveContentLimit(int contentLimit) {
        if (contentLimit < 0 || contentLimit == Integer.MAX_VALUE) return MAX_CONTENT_ENTRIES_PER_BUS_PAYLOAD;

        return Math.min(contentLimit, MAX_CONTENT_ENTRIES_PER_BUS_PAYLOAD);
    }

    private static int computeAvailableSlotsFrom(NBTTagCompound busData, int capacityUpgrades) {
        int base = busData.hasKey("baseConfigSlots") ? busData.getInteger("baseConfigSlots") : 18;
        int perUpg = busData.hasKey("slotsPerUpgrade") ? busData.getInteger("slotsPerUpgrade") : 9;
        int max = busData.hasKey("maxConfigSlots") ? busData.getInteger("maxConfigSlots") : 63;

        int raw = base + perUpg * Math.max(0, capacityUpgrades);

        return Math.min(raw, max);
    }

    private static void addUpgradesData(NBTTagCompound busData, IInventory upgradesInv) {
        if (upgradesInv == null) return;

        busData.setInteger("upgradeSlotCount", upgradesInv.getSizeInventory());

        NBTTagList upgradeList = new NBTTagList();
        for (int i = 0; i < upgradesInv.getSizeInventory(); i++) {
            ItemStack upgrade = upgradesInv.getStackInSlot(i);
            if (ItemStacks.isEmpty(upgrade)) continue;

            NBTTagCompound upgradeNbt = new NBTTagCompound();
            upgrade.writeToNBT(upgradeNbt);
            upgradeNbt.setInteger("slot", i);
            upgradeList.appendTag(upgradeNbt);
        }

        busData.setTag("upgrades", upgradeList);
    }

    /**
     * Check whether an ADD_ITEM request would create a duplicate filter entry on this bus.
     */
    public static boolean isDuplicateFilterAdd(StorageBusTracker tracker, Action action, int partitionSlot,
        NBTTagCompound stackData) {
        if (tracker == null) return false;
        if (action != Action.ADD_ITEM) return false;
        if (partitionSlot < 0 || stackData == null || stackData.hasNoTags()) return false;
        Object bus = tracker.storageBus;

        IAEStackInventory config = getConfigInventory(bus);
        if (config == null || partitionSlot >= config.getSizeInventory()) return false;
        int availableSlots = getAvailableConfigSlots(bus, config);
        if (partitionSlot >= availableSlots) return false;

        IAEStack<?> stack = AEStackUtil.readPartitionStack(stackData, getStackType(bus));
        int existing = findStackInConfig(config, stack, availableSlots);

        return existing >= 0 && existing != partitionSlot;
    }

    /**
     * Handle a partition action against a storage bus (item or fluid; both share the config inventory).
     *
     * @return true if the partition was modified
     */
    public static boolean handlePartitionAction(StorageBusTracker tracker, Action action, int partitionSlot,
        NBTTagCompound stackData) {
        Object bus = tracker.storageBus;

        IAEStackInventory config = getConfigInventory(bus);
        if (config == null) return false;

        IAEStack<?> partitionStack = AEStackUtil.readPartitionStack(stackData, getStackType(bus));
        int slots = getAvailableConfigSlots(bus, config);

        switch (action) {
            case ADD_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slots && partitionStack != null) {
                    setConfigSlot(config, partitionSlot, partitionStack);
                }
                break;
            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slots) setConfigSlot(config, partitionSlot, null);
                break;
            case TOGGLE_ITEM:
                if (partitionStack != null) {
                    int existing = findStackInConfig(config, partitionStack, slots);
                    if (existing >= 0) {
                        setConfigSlot(config, existing, null);
                    } else {
                        int empty = findEmptySlot(config, slots);
                        if (empty >= 0) setConfigSlot(config, empty, partitionStack);
                    }
                }
                break;
            case CLEAR_ALL:
                clearConfig(config);
                break;
            case SET_ALL_FROM_CONTENTS:
                setPartitionFromContents(config, tracker);
                break;
        }

        tracker.hostTile.markDirty();

        return true;
    }

    private static int findStackInConfig(IAEStackInventory inv, IAEStack<?> stack, int slotsToCheck) {
        if (stack == null) return -1;

        int slots = Math.min(inv.getSizeInventory(), Math.max(0, slotsToCheck));
        for (int i = 0; i < slots; i++) {
            IAEStack<?> slotStack = inv.getAEStackInSlot(i);
            if (slotStack != null && slotStack.isSameType(stack)) return i;
        }

        return -1;
    }

    private static int findEmptySlot(IAEStackInventory inv, int slotsToCheck) {
        int slots = Math.min(inv.getSizeInventory(), Math.max(0, slotsToCheck));
        for (int i = 0; i < slots; i++) {
            if (inv.getAEStackInSlot(i) == null) return i;
        }

        return -1;
    }

    private static void clearConfig(IAEStackInventory inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) setConfigSlot(inv, i, null);
    }

    private static void setConfigSlot(IAEStackInventory inv, int slot, IAEStack<?> stack) {
        inv.putAEStackInSlot(slot, stack);
        inv.markDirty();
    }

    private static void setPartitionFromContents(IAEStackInventory config, StorageBusTracker tracker) {
        clearConfig(config);

        Object bus = tracker.storageBus;
        int availableSlots = getAvailableConfigSlots(bus, config);

        IAEStackType<?> type = getStackType(bus);
        if (type == null) return;

        if (tracker.storageBus instanceof PartStorageBus storageBus) {
            IMEInventoryHandler<?> handler = storageBus.getInternalHandler();
            if (handler != null) setPartitionFromContentsForUnknownType(config, handler, type, availableSlots);

            return;
        }

        if (bus instanceof PartSharedItemBus<?>sharedBus) {
            IMEMonitor<?> monitor = getSharedBusMonitor(sharedBus);
            if (monitor != null) setPartitionFromMonitorForUnknownType(config, monitor, type, availableSlots);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> void setPartitionFromContentsForUnknownType(IAEStackInventory config,
        IMEInventoryHandler<?> handler, IAEStackType<?> type, int availableSlots) {
        setPartitionFromContentsForType(config, castHandler(handler), (IAEStackType<T>) type, availableSlots);
    }

    private static <T extends IAEStack<T>> void setPartitionFromContentsForType(IAEStackInventory config,
        IMEInventoryHandler<T> handler, IAEStackType<T> type, int availableSlots) {
        IItemList<T> contents = type.createList();
        handler.getAvailableItems(contents, IterationCounter.fetchNewId());

        int slot = 0;
        for (T stack : contents) {
            if (slot >= availableSlots) break;

            T partitionStack = stack.copy();
            partitionStack.setStackSize(1);
            setConfigSlot(config, slot++, partitionStack);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> void setPartitionFromMonitorForUnknownType(IAEStackInventory config,
        IMEMonitor<?> monitor, IAEStackType<?> type, int availableSlots) {
        setPartitionFromMonitorForType(config, (IMEMonitor<T>) monitor, (IAEStackType<T>) type, availableSlots);
    }

    private static <T extends IAEStack<T>> void setPartitionFromMonitorForType(IAEStackInventory config,
        IMEMonitor<T> monitor, IAEStackType<T> type, int availableSlots) {
        IItemList<T> contents = type.createList();
        monitor.getAvailableItems(contents, IterationCounter.fetchNewId());

        int slot = 0;
        for (T stack : contents) {
            if (slot >= availableSlots) break;

            T partitionStack = stack.copy();
            partitionStack.setStackSize(1);
            setConfigSlot(config, slot++, partitionStack);
        }
    }

    /**
     * Toggle IO mode (access restriction) for a storage bus.
     *
     * @return true if mode was changed
     */
    public static boolean toggleIOMode(StorageBusTracker tracker) {
        if (!(tracker.storageBus instanceof PartUpgradeable)) return false;

        IConfigManager configManager = ((PartUpgradeable) tracker.storageBus).getConfigManager();
        if (configManager == null) return false;

        AccessRestriction current = (AccessRestriction) configManager.getSetting(Settings.ACCESS);
        AccessRestriction next;

        switch (current) {
            case READ_WRITE -> next = AccessRestriction.READ;
            case READ -> next = AccessRestriction.WRITE;
            default -> next = AccessRestriction.READ_WRITE;
        }

        configManager.putSetting(Settings.ACCESS, next);
        tracker.hostTile.markDirty();

        return true;
    }

    /**
     * Check if a storage bus has a partition configured.
     */
    public static boolean busHasPartition(StorageBusTracker tracker) {
        IAEStackInventory configInv = getConfigInventory(tracker.storageBus);
        if (configInv != null) {
            int availableSlots = getAvailableConfigSlots(tracker.storageBus, configInv);

            for (int i = 0; i < availableSlots; i++) {
                if (configInv.getAEStackInSlot(i) != null) return true;
            }
        }

        return false;
    }

    /**
     * Check if a storage bus can see at least one stack from its connected inventory.
     */
    public static boolean busHasConnectedInventory(StorageBusTracker tracker) {
        if (tracker.storageBus instanceof PartStorageBus bus) {
            IMEInventoryHandler<?> handler = bus.getInternalHandler();
            if (handler == null) return false;

            IAEStackType<?> type = handler.getStackType();
            if (type == null) return false;

            return hasContentsForUnknownType(handler, type);
        }

        if (!(tracker.storageBus instanceof PartSharedItemBus<?>bus)) return false;

        IMEMonitor<?> monitor = getSharedBusMonitor(bus);
        IAEStackType<?> type = bus.getStackType();
        if (monitor == null || type == null) return false;

        return monitorHasContentsForUnknownType(monitor, type);
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> boolean hasContentsForUnknownType(IMEInventoryHandler<?> handler,
        IAEStackType<?> type) {
        return hasContentsForType(castHandler(handler), (IAEStackType<T>) type);
    }

    private static <T extends IAEStack<T>> boolean hasContentsForType(IMEInventoryHandler<T> handler,
        IAEStackType<T> type) {
        IItemList<T> contents = type.createList();
        handler.getAvailableItems(contents, IterationCounter.fetchNewId());

        return !contents.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> boolean monitorHasContentsForUnknownType(IMEMonitor<?> monitor,
        IAEStackType<?> type) {
        return monitorHasContentsForType((IMEMonitor<T>) monitor, (IAEStackType<T>) type);
    }

    private static <T extends IAEStack<T>> boolean monitorHasContentsForType(IMEMonitor<T> monitor,
        IAEStackType<T> type) {
        IItemList<T> contents = type.createList();
        monitor.getAvailableItems(contents, IterationCounter.fetchNewId());

        return !contents.isEmpty();
    }

    private static IAEStackInventory getConfigInventory(Object bus) {
        if (bus instanceof PartStorageBus storageBus) return storageBus.getAEInventoryByName(StorageName.CONFIG);
        if (bus instanceof PartSharedItemBus<?>sharedBus) return sharedBus.getAEInventoryByName(StorageName.CONFIG);

        return null;
    }

    private static IAEStackType<?> getStackType(Object bus) {
        if (bus instanceof PartStorageBus storageBus) return storageBus.getStackType();
        if (bus instanceof PartSharedItemBus<?>sharedBus) return sharedBus.getStackType();

        return null;
    }

    private static int getAvailableConfigSlots(Object bus, IAEStackInventory config) {
        if (bus instanceof PartStorageBus storageBus) {
            return Math.min(config.getSizeInventory(), 18 + storageBus.getInstalledUpgrades(Upgrades.CAPACITY) * 9);
        }

        if (bus instanceof PartSharedItemBus<?>sharedBus) {
            return Math.min(config.getSizeInventory(), 1 + sharedBus.getInstalledUpgrades(Upgrades.CAPACITY) * 4);
        }

        return 0;
    }

    private static ItemStack getBlockAsItemStack(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        if (block == null || block == Blocks.air || block.isAir(world, x, y, z)) return null;

        Item item = Item.getItemFromBlock(block);
        if (item == null) return null;

        int meta = block.getDamageValue(world, x, y, z);

        return new ItemStack(item, 1, meta);
    }
}
