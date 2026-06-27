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
import net.minecraftforge.fluids.FluidStack;

import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.data.StorageBusCustomNameData;
import com.hfstudio.diskterminal.integration.storagebus.StorageBusScannerRegistry;
import com.hfstudio.diskterminal.network.PacketStorageBusPartitionAction.Action;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.FluidStacks;
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
import appeng.util.item.AEFluidStack;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import gregtech.common.tileentities.machines.MTEHatchInputME;

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

    private record GregTechItemSnapshot(ItemStack[] configs, ItemStack[] extracted, int[] extractedAmounts) {
    }

    private record GregTechFluidSnapshot(FluidStack[] configs, FluidStack[] extracted, int[] extractedAmounts) {
    }

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

    public static NBTTagCompound createGregTechInputBusData(MTEHatchInputBusME inputBus, long busId,
                                                            StorageType storageType, BusRole busRole, int contentLimit) {
        NBTTagCompound busData = createGregTechBusBaseData(inputBus, busId, storageType, busRole);
        addGregTechItemBusData(busData, inputBus, contentLimit);
        return busData;
    }

    public static NBTTagCompound createGregTechInputHatchData(MTEHatchInputME inputHatch, long busId,
                                                              StorageType storageType, BusRole busRole, int contentLimit) {
        NBTTagCompound busData = createGregTechBusBaseData(inputHatch, busId, storageType, busRole);
        addGregTechFluidHatchData(busData, inputHatch, contentLimit);
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

    private static NBTTagCompound createGregTechBusBaseData(MetaTileEntity metaTileEntity, long busId,
                                                            StorageType storageType, BusRole busRole) {
        TileEntity hostTile = (TileEntity) metaTileEntity.getBaseMetaTileEntity();
        ForgeDirection facing = metaTileEntity.getBaseMetaTileEntity()
                .getFrontFacing();

        NBTTagCompound busData = new NBTTagCompound();
        busData.setLong("id", busId);
        busData.setLong("pos", PosUtil.toLong(hostTile.xCoord, hostTile.yCoord, hostTile.zCoord));
        busData.setInteger("dim", hostTile.getWorldObj().provider.dimensionId);
        busData.setInteger("side", facing.ordinal());
        busData.setInteger("priority", 0);
        storageType.writeToNBT(busData);
        busRole.writeToNBT(busData);
        busData.setInteger("access", AccessRestriction.READ.ordinal());
        busData.setString("namePrefixKey", busRole.getPrefixKey());
        busData.setBoolean("preferDisplayName", true);
        busData.setInteger("upgradeSlotCount", 0);
        busData.setString("displayName", metaTileEntity.getLocalName());

        ItemStack iconStack = metaTileEntity.getStackForm(1);
        addBusItemIcon(busData, iconStack);
        addStorageBusCustomName(busData, hostTile.getWorldObj(), busId);
        addConnectedInventoryInfo(busData, hostTile, facing);
        addBusItemIconAsConnectedIconIfMissing(busData, iconStack);

        return busData;
    }

    private static void addGregTechItemBusData(NBTTagCompound busData, MTEHatchInputBusME inputBus, int contentLimit) {
        busData.setString("stackType", "item");
        busData.setBoolean("supportsAutoPull", inputBus.autoPullAvailable);
        busData.setBoolean("autoPullEnabled", inputBus.isAutoPullItemList());

        NBTTagList partitionList = new NBTTagList();
        NBTTagList contentsList = new NBTTagList();
        GregTechItemSnapshot snapshot = createGregTechItemSnapshot(inputBus);
        int writtenContents = 0;

        for (int slotIndex = 0; slotIndex < snapshot.configs().length; slotIndex++) {
            ItemStack configStack = snapshot.configs()[slotIndex];
            if (!ItemStacks.isEmpty(configStack)) {
                NBTTagCompound partNbt = new NBTTagCompound();
                partNbt.setInteger("slot", slotIndex);
                AEStackUtil.writeStackToNBT(partNbt, AEStackUtil.createItemStack(configStack));
                partitionList.appendTag(partNbt);
            }

            if (isContentLimitReached(writtenContents, contentLimit)) continue;

            ItemStack extractedStack = snapshot.extracted()[slotIndex];
            if (ItemStacks.isEmpty(extractedStack)) continue;

            NBTTagCompound stackNbt = new NBTTagCompound();
            extractedStack.writeToNBT(stackNbt);
            stackNbt.setLong("Cnt", snapshot.extractedAmounts()[slotIndex]);
            contentsList.appendTag(stackNbt);
            writtenContents++;
        }

        busData.setTag("partition", partitionList);
        busData.setTag("contents", contentsList);
    }

    private static void addGregTechFluidHatchData(NBTTagCompound busData, MTEHatchInputME inputHatch,
                                                  int contentLimit) {
        busData.setString("stackType", "fluid");
        busData.setBoolean("supportsAutoPull", inputHatch.autoPullAvailable);
        busData.setBoolean("autoPullEnabled", inputHatch.isAutoPullFluidList());

        NBTTagList partitionList = new NBTTagList();
        NBTTagList contentsList = new NBTTagList();
        GregTechFluidSnapshot snapshot = createGregTechFluidSnapshot(inputHatch);
        int writtenContents = 0;

        for (int slotIndex = 0; slotIndex < snapshot.configs().length; slotIndex++) {
            FluidStack configStack = snapshot.configs()[slotIndex];
            if (configStack != null) {
                NBTTagCompound partNbt = new NBTTagCompound();
                partNbt.setInteger("slot", slotIndex);
                AEStackUtil.writeStackToNBT(partNbt, AEFluidStack.create(configStack)
                        .setStackSize(1));
                partitionList.appendTag(partNbt);
            }

            if (isContentLimitReached(writtenContents, contentLimit)) continue;

            FluidStack extractedStack = snapshot.extracted()[slotIndex];
            if (extractedStack == null) continue;

            NBTTagCompound stackNbt = new NBTTagCompound();
            AEStackUtil.writeStackToNBT(stackNbt, AEFluidStack.create(extractedStack));
            stackNbt.setLong("Cnt", snapshot.extractedAmounts()[slotIndex]);
            contentsList.appendTag(stackNbt);
            writtenContents++;
        }

        busData.setTag("partition", partitionList);
        busData.setTag("contents", contentsList);
    }

    private static void addCustomName(NBTTagCompound busData, Object bus) {
        if (!(bus instanceof ICustomNameObject nameable)) return;

        if (nameable.hasCustomName()) {
            String customName = nameable.getCustomName();
            if (customName != null && !customName.isEmpty()) busData.setString("customName", customName);
        }
    }

    private static void addStorageBusCustomName(NBTTagCompound busData, World world, long busId) {
        StorageBusCustomNameData data = StorageBusCustomNameData.get(world);
        if (data == null) return;

        String customName = data.getCustomName(busId);
        if (customName != null && !customName.isEmpty()) {
            busData.setString("customName", customName);
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
        if (config != null) {
            if (partitionSlot >= config.getSizeInventory()) return false;
            int availableSlots = getAvailableConfigSlots(bus, config);
            if (partitionSlot >= availableSlots) return false;

            IAEStack<?> stack = AEStackUtil.readPartitionStack(stackData, getStackType(bus));
            int existing = findStackInConfig(config, stack, availableSlots);

            return existing >= 0 && existing != partitionSlot;
        }

        return isDuplicateGregTechFilterAdd(bus, partitionSlot, stackData);
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
        if (config == null) return handleGregTechPartitionAction(tracker, action, partitionSlot, stackData);

        int slots = getAvailableConfigSlots(bus, config);

        switch (action) {
            case ADD_ITEM:
                IAEStack<?> addedStack = AEStackUtil.readPartitionStack(stackData, getStackType(bus));
                if (partitionSlot >= 0 && partitionSlot < slots && addedStack != null) {
                    setConfigSlot(config, partitionSlot, addedStack);
                }
                break;
            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slots) setConfigSlot(config, partitionSlot, null);
                break;
            case TOGGLE_ITEM:
                IAEStack<?> toggledStack = AEStackUtil.readPartitionStack(stackData, getStackType(bus));
                if (toggledStack != null) {
                    int existing = findStackInConfig(config, toggledStack, slots);
                    if (existing >= 0) {
                        setConfigSlot(config, existing, null);
                    } else {
                        int empty = findEmptySlot(config, slots);
                        if (empty >= 0) setConfigSlot(config, empty, toggledStack);
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

        if (bus instanceof PartSharedItemBus<?> sharedBus) {
            IMEMonitor<?> monitor = getSharedBusMonitor(sharedBus);
            if (monitor != null) setPartitionFromMonitorForUnknownType(config, monitor, type, availableSlots);
        }
    }

    private static boolean handleGregTechPartitionAction(StorageBusTracker tracker, Action action, int partitionSlot,
                                                         NBTTagCompound stackData) {
        Object bus = tracker.storageBus;

        if (bus instanceof MTEHatchInputBusME inputBus) {
            return handleGregTechItemBusPartitionAction(tracker, inputBus, action, partitionSlot, stackData);
        }

        if (bus instanceof MTEHatchInputME inputHatch) {
            return handleGregTechFluidHatchPartitionAction(tracker, inputHatch, action, partitionSlot, stackData);
        }

        return false;
    }

    private static boolean handleGregTechItemBusPartitionAction(StorageBusTracker tracker, MTEHatchInputBusME inputBus,
                                                                Action action, int partitionSlot, NBTTagCompound stackData) {
        int slots = MTEHatchInputBusME.SLOT_COUNT;

        switch (action) {
            case ADD_ITEM:
                ItemStack addedStack = sanitizeItemPartitionStack(stackData);
                if (partitionSlot >= 0 && partitionSlot < slots && !ItemStacks.isEmpty(addedStack)) {
                    inputBus.setSlotConfig(partitionSlot, addedStack);
                }
                break;
            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slots) inputBus.setSlotConfig(partitionSlot, null);
                break;
            case TOGGLE_ITEM:
                ItemStack toggledStack = sanitizeItemPartitionStack(stackData);
                if (!ItemStacks.isEmpty(toggledStack)) {
                    GregTechItemSnapshot snapshot = createGregTechItemSnapshot(inputBus);
                    int existing = findGregTechItemConfigSlot(snapshot, toggledStack);
                    if (existing >= 0) {
                        inputBus.setSlotConfig(existing, null);
                    } else {
                        int empty = findFirstGregTechItemEmptySlot(snapshot);
                        if (empty >= 0) inputBus.setSlotConfig(empty, toggledStack);
                    }
                }
                break;
            case CLEAR_ALL:
                clearGregTechItemConfigs(inputBus);
                break;
            case SET_ALL_FROM_CONTENTS:
                setGregTechItemPartitionFromContents(inputBus, createGregTechItemSnapshot(inputBus));
                break;
        }

        tracker.hostTile.markDirty();
        return true;
    }

    private static boolean handleGregTechFluidHatchPartitionAction(StorageBusTracker tracker, MTEHatchInputME inputHatch,
                                                                   Action action, int partitionSlot, NBTTagCompound stackData) {
        int slots = MTEHatchInputME.SLOT_COUNT;

        switch (action) {
            case ADD_ITEM:
                FluidStack addedStack = sanitizeFluidPartitionStack(stackData);
                if (partitionSlot >= 0 && partitionSlot < slots && addedStack != null) {
                    inputHatch.setSlotConfig(partitionSlot, addedStack);
                }
                break;
            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slots) inputHatch.setSlotConfig(partitionSlot, null);
                break;
            case TOGGLE_ITEM:
                FluidStack toggledStack = sanitizeFluidPartitionStack(stackData);
                if (toggledStack != null) {
                    GregTechFluidSnapshot snapshot = createGregTechFluidSnapshot(inputHatch);
                    int existing = findGregTechFluidConfigSlot(snapshot, toggledStack);
                    if (existing >= 0) {
                        inputHatch.setSlotConfig(existing, null);
                    } else {
                        int empty = findFirstGregTechFluidEmptySlot(snapshot);
                        if (empty >= 0) inputHatch.setSlotConfig(empty, toggledStack);
                    }
                }
                break;
            case CLEAR_ALL:
                clearGregTechFluidConfigs(inputHatch);
                break;
            case SET_ALL_FROM_CONTENTS:
                setGregTechFluidPartitionFromContents(inputHatch, createGregTechFluidSnapshot(inputHatch));
                break;
        }

        tracker.hostTile.markDirty();
        return true;
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
        if (tracker.storageBus instanceof MTEHatchInputBusME inputBus) {
            if (!inputBus.autoPullAvailable) return false;

            inputBus.setAutoPullItemList(!inputBus.isAutoPullItemList());
            tracker.hostTile.markDirty();
            return true;
        }

        if (tracker.storageBus instanceof MTEHatchInputME inputHatch) {
            if (!inputHatch.autoPullAvailable) return false;

            inputHatch.setAutoPullFluidList(!inputHatch.isAutoPullFluidList());
            tracker.hostTile.markDirty();
            return true;
        }

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
        if (tracker.storageBus instanceof MTEHatchInputBusME inputBus) {
            return hasGregTechItemPartition(createGregTechItemSnapshot(inputBus));
        }

        if (tracker.storageBus instanceof MTEHatchInputME inputHatch) {
            return hasGregTechFluidPartition(createGregTechFluidSnapshot(inputHatch));
        }

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
        if (tracker.storageBus instanceof MTEHatchInputBusME inputBus) {
            return hasGregTechItemContents(createGregTechItemSnapshot(inputBus));
        }

        if (tracker.storageBus instanceof MTEHatchInputME inputHatch) {
            return hasGregTechFluidContents(createGregTechFluidSnapshot(inputHatch));
        }

        if (tracker.storageBus instanceof PartStorageBus bus) {
            IMEInventoryHandler<?> handler = bus.getInternalHandler();
            if (handler == null) return false;

            IAEStackType<?> type = handler.getStackType();
            if (type == null) return false;

            return hasContentsForUnknownType(handler, type);
        }

        if (!(tracker.storageBus instanceof PartSharedItemBus<?> bus)) return false;

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
        if (bus instanceof PartSharedItemBus<?> sharedBus) return sharedBus.getAEInventoryByName(StorageName.CONFIG);

        return null;
    }

    private static IAEStackType<?> getStackType(Object bus) {
        if (bus instanceof PartStorageBus storageBus) return storageBus.getStackType();
        if (bus instanceof PartSharedItemBus<?> sharedBus) return sharedBus.getStackType();

        return null;
    }

    private static int getAvailableConfigSlots(Object bus, IAEStackInventory config) {
        if (bus instanceof PartStorageBus storageBus) {
            return Math.min(config.getSizeInventory(), 18 + storageBus.getInstalledUpgrades(Upgrades.CAPACITY) * 9);
        }

        if (bus instanceof PartSharedItemBus<?> sharedBus) {
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

    private static boolean isDuplicateGregTechFilterAdd(Object bus, int partitionSlot, NBTTagCompound stackData) {
        if (bus instanceof MTEHatchInputBusME inputBus) {
            ItemStack configStack = sanitizeItemPartitionStack(stackData);
            int existing = findGregTechItemConfigSlot(createGregTechItemSnapshot(inputBus), configStack);
            return existing >= 0 && existing != partitionSlot;
        }

        if (bus instanceof MTEHatchInputME inputHatch) {
            FluidStack configStack = sanitizeFluidPartitionStack(stackData);
            int existing = findGregTechFluidConfigSlot(createGregTechFluidSnapshot(inputHatch), configStack);
            return existing >= 0 && existing != partitionSlot;
        }

        return false;
    }

    private static ItemStack sanitizeItemPartitionStack(NBTTagCompound stackData) {
        if (stackData == null || stackData.hasNoTags()) return null;

        IAEStack<?> stack = AEStackUtil.readPartitionStack(stackData, null);
        ItemStack display = AEStackUtil.getDisplayStack(stack);
        if (ItemStacks.isEmpty(display)) {
            display = AEStackUtil.readDisplayStack(stackData);
        }
        if (ItemStacks.isEmpty(display)) return null;

        ItemStack copy = display.copy();
        copy.stackSize = 1;
        return copy;
    }

    private static FluidStack sanitizeFluidPartitionStack(NBTTagCompound stackData) {
        if (stackData == null || stackData.hasNoTags()) return null;

        IAEStack<?> stack = AEStackUtil.readPartitionStack(stackData, null);
        ItemStack display = AEStackUtil.getDisplayStack(stack);
        FluidStack fluid = FluidStacks.extract(display);
        if (fluid != null) {
            fluid.amount = 1;
            return fluid;
        }

        return null;
    }

    private static int findGregTechItemConfigSlot(GregTechItemSnapshot snapshot, ItemStack target) {
        if (ItemStacks.isEmpty(target)) return -1;

        for (int i = 0; i < snapshot.configs().length; i++) {
            ItemStack slotStack = snapshot.configs()[i];
            if (!ItemStacks.isEmpty(slotStack) && ItemStack.areItemStacksEqual(slotStack, target)) return i;
        }

        return -1;
    }

    private static int findGregTechFluidConfigSlot(GregTechFluidSnapshot snapshot, FluidStack target) {
        if (target == null) return -1;

        for (int i = 0; i < snapshot.configs().length; i++) {
            FluidStack slotStack = snapshot.configs()[i];
            if (slotStack != null && slotStack.isFluidEqual(target)) return i;
        }

        return -1;
    }

    private static int findFirstGregTechItemEmptySlot(GregTechItemSnapshot snapshot) {
        for (int i = 0; i < snapshot.configs().length; i++) {
            if (ItemStacks.isEmpty(snapshot.configs()[i])) return i;
        }

        return -1;
    }

    private static int findFirstGregTechFluidEmptySlot(GregTechFluidSnapshot snapshot) {
        for (int i = 0; i < snapshot.configs().length; i++) {
            if (snapshot.configs()[i] == null) return i;
        }

        return -1;
    }

    private static void clearGregTechItemConfigs(MTEHatchInputBusME inputBus) {
        for (int i = 0; i < MTEHatchInputBusME.SLOT_COUNT; i++) {
            inputBus.setSlotConfig(i, null);
        }
    }

    private static void clearGregTechFluidConfigs(MTEHatchInputME inputHatch) {
        for (int i = 0; i < MTEHatchInputME.SLOT_COUNT; i++) {
            inputHatch.setSlotConfig(i, null);
        }
    }

    private static void setGregTechItemPartitionFromContents(MTEHatchInputBusME inputBus, GregTechItemSnapshot snapshot) {
        clearGregTechItemConfigs(inputBus);
        int slot = 0;
        for (int i = 0; i < snapshot.extracted().length && slot < MTEHatchInputBusME.SLOT_COUNT; i++) {
            ItemStack extracted = snapshot.extracted()[i];
            if (ItemStacks.isEmpty(extracted)) continue;

            ItemStack copy = extracted.copy();
            copy.stackSize = 1;
            inputBus.setSlotConfig(slot++, copy);
        }
    }

    private static void setGregTechFluidPartitionFromContents(MTEHatchInputME inputHatch,
                                                              GregTechFluidSnapshot snapshot) {
        clearGregTechFluidConfigs(inputHatch);
        int slot = 0;
        for (int i = 0; i < snapshot.extracted().length && slot < MTEHatchInputME.SLOT_COUNT; i++) {
            FluidStack extracted = snapshot.extracted()[i];
            if (extracted == null) continue;

            FluidStack copy = extracted.copy();
            copy.amount = 1;
            inputHatch.setSlotConfig(slot++, copy);
        }
    }

    private static boolean hasGregTechItemPartition(GregTechItemSnapshot snapshot) {
        for (ItemStack config : snapshot.configs()) {
            if (!ItemStacks.isEmpty(config)) return true;
        }

        return false;
    }

    private static boolean hasGregTechFluidPartition(GregTechFluidSnapshot snapshot) {
        for (FluidStack config : snapshot.configs()) {
            if (config != null) return true;
        }

        return false;
    }

    private static boolean hasGregTechItemContents(GregTechItemSnapshot snapshot) {
        for (ItemStack extracted : snapshot.extracted()) {
            if (!ItemStacks.isEmpty(extracted)) return true;
        }

        return false;
    }

    private static boolean hasGregTechFluidContents(GregTechFluidSnapshot snapshot) {
        for (FluidStack extracted : snapshot.extracted()) {
            if (extracted != null) return true;
        }

        return false;
    }

    private static GregTechItemSnapshot createGregTechItemSnapshot(MTEHatchInputBusME inputBus) {
        ItemStack[] configs = new ItemStack[MTEHatchInputBusME.SLOT_COUNT];
        ItemStack[] extracted = new ItemStack[MTEHatchInputBusME.SLOT_COUNT];
        int[] extractedAmounts = new int[MTEHatchInputBusME.SLOT_COUNT];
        NBTTagCompound serialized = new NBTTagCompound();
        inputBus.saveNBTData(serialized);
        NBTTagList slots = serialized.getTagList("slots", 10);

        for (int i = 0; i < slots.tagCount(); i++) {
            NBTTagCompound slotTag = slots.getCompoundTagAt(i);
            int slotIndex = slotTag.getInteger("index");
            if (!isValidGregTechSlotIndex(slotIndex, configs.length)) continue;

            if (slotTag.hasKey("config")) {
                configs[slotIndex] = ItemStack.loadItemStackFromNBT(slotTag.getCompoundTag("config"));
            }
            if (slotTag.hasKey("extracted")) {
                extracted[slotIndex] = ItemStack.loadItemStackFromNBT(slotTag.getCompoundTag("extracted"));
                extractedAmounts[slotIndex] = slotTag.getInteger("extractedAmount");
            }
        }

        return new GregTechItemSnapshot(configs, extracted, extractedAmounts);
    }

    private static GregTechFluidSnapshot createGregTechFluidSnapshot(MTEHatchInputME inputHatch) {
        FluidStack[] configs = new FluidStack[MTEHatchInputME.SLOT_COUNT];
        FluidStack[] extracted = new FluidStack[MTEHatchInputME.SLOT_COUNT];
        int[] extractedAmounts = new int[MTEHatchInputME.SLOT_COUNT];
        NBTTagCompound serialized = new NBTTagCompound();
        inputHatch.saveNBTData(serialized);
        NBTTagList slots = serialized.getTagList("slots", 10);

        for (int i = 0; i < slots.tagCount(); i++) {
            NBTTagCompound slotTag = slots.getCompoundTagAt(i);
            int slotIndex = slotTag.getInteger("index");
            if (!isValidGregTechSlotIndex(slotIndex, configs.length)) continue;

            if (slotTag.hasKey("config")) {
                configs[slotIndex] = FluidStack.loadFluidStackFromNBT(slotTag.getCompoundTag("config"));
            }
            if (slotTag.hasKey("extracted")) {
                extracted[slotIndex] = FluidStack.loadFluidStackFromNBT(slotTag.getCompoundTag("extracted"));
                extractedAmounts[slotIndex] = slotTag.getInteger("extractedAmount");
            }
        }

        return new GregTechFluidSnapshot(configs, extracted, extractedAmounts);
    }

    private static boolean isValidGregTechSlotIndex(int slotIndex, int slotCount) {
        return slotIndex >= 0 && slotIndex < slotCount;
    }
}
