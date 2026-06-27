package com.hfstudio.diskterminal.container.handler;

import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.common.tile.TileSuperStockReplenisher;
import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.data.StorageBusCustomNameData;
import com.hfstudio.diskterminal.integration.storagebus.StorageBusScannerRegistry;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusSource;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEFluidStack;
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

    private record GregTechItemSnapshot(ItemStack[] configs, ItemStack[] extracted, int[] extractedAmounts) {}

    private record GregTechFluidSnapshot(FluidStack[] configs, FluidStack[] extracted, int[] extractedAmounts) {}

    /**
     * Tracker for storage bus instances, keyed by a synthetic bus ID.
     */
    public static class StorageBusTracker {

        public final long id;
        public final Object storageBus; // A PartStorageBus (item or fluid) or storage-bus-like machine.
        public final TileEntity hostTile;
        public final int sideOrdinal;
        public final StorageType storageType;
        public StorageBusId targetId;
        public StorageBusSource source;
        public boolean hasConnectedContents;

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

        /**
         * Attach the stable identity and source family used by the formal capability runtime. Returns
         * this tracker for fluent use from scanners.
         */
        public StorageBusTracker withTarget(StorageBusId targetId, StorageBusSource source) {
            this.targetId = targetId;
            this.source = source;

            return this;
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

    /**
     * Create read-model NBT for the AE2FluidCraft Super Stock Replenisher as a single mixed-type target.
     * The tile exposes multiple independent config inventories (currently fluids and items). They are
     * flattened into one slot-addressable model so the GUI and capability layer can treat the target as
     * one extensible object with typed slots rather than two fake buses.
     */
    public static NBTTagCompound createMixedStockReplenisherData(TileSuperStockReplenisher tile, ItemStack iconStack,
        String displayName, long busId) {
        NBTTagCompound busData = new NBTTagCompound();
        busData.setLong("id", busId);
        busData.setLong("pos", PosUtil.toLong(tile.xCoord, tile.yCoord, tile.zCoord));
        busData.setInteger("dim", tile.getWorldObj().provider.dimensionId);
        busData.setInteger("side", ForgeDirection.UNKNOWN.ordinal());
        busData.setInteger("priority", 0);
        StorageType.ITEM.writeToNBT(busData);
        BusRole.STORAGE.writeToNBT(busData);
        busData.setInteger("access", AccessRestriction.READ_WRITE.ordinal());
        busData.setString("stackType", "mixed");
        busData.setBoolean("preferDisplayName", true);
        busData.setInteger("upgradeSlotCount", 0);
        if (displayName != null) busData.setString("displayName", displayName);

        int fluidSlots = tile.getConfigFluids() == null ? 0
            : tile.getConfigFluids()
                .getSizeInventory();
        int itemSlots = tile.getConfigItems() == null ? 0
            : tile.getConfigItems()
                .getSizeInventory();
        int totalSlots = fluidSlots + itemSlots;

        busData.setInteger("baseConfigSlots", totalSlots);
        busData.setInteger("slotsPerUpgrade", 0);
        busData.setInteger("maxConfigSlots", totalSlots);

        addBusItemIcon(busData, iconStack);
        addStorageBusCustomName(busData, tile.getWorldObj(), busId);
        addBusItemIconAsConnectedIconIfMissing(busData, iconStack);
        addStockReplenisherSlotTypes(busData, fluidSlots, itemSlots);
        addMixedStockReplenisherPartitionData(busData, tile, fluidSlots);
        addMixedStockReplenisherContentsData(busData, tile);

        return busData;
    }

    private static void addStockReplenisherSlotTypes(NBTTagCompound busData, int fluidSlots, int itemSlots) {
        NBTTagList slotTypes = new NBTTagList();
        for (int i = 0; i < fluidSlots; i++) {
            slotTypes.appendTag(new NBTTagString("fluid"));
        }
        for (int i = 0; i < itemSlots; i++) {
            slotTypes.appendTag(new NBTTagString("item"));
        }
        busData.setTag("slotTypes", slotTypes);
    }

    private static void addMixedStockReplenisherPartitionData(NBTTagCompound busData, TileSuperStockReplenisher tile,
        int fluidSlotCount) {
        NBTTagList partitionList = new NBTTagList();
        appendStockReplenisherPartitionGroup(partitionList, tile.getConfigFluids(), 0, "fluid");
        appendStockReplenisherPartitionGroup(partitionList, tile.getConfigItems(), fluidSlotCount, "item");
        busData.setTag("partition", partitionList);
    }

    private static void appendStockReplenisherPartitionGroup(NBTTagList partitionList, IAEStackInventory config,
        int slotOffset, String stackTypeId) {
        if (config == null) return;

        for (int i = 0; i < config.getSizeInventory(); i++) {
            IAEStack<?> stack = config.getAEStackInSlot(i);
            if (stack == null) continue;

            NBTTagCompound partNbt = createPartitionSlotData(slotOffset + i, stack);
            partNbt.setString("stackTypeId", stackTypeId);
            partitionList.appendTag(partNbt);
        }
    }

    private static void addMixedStockReplenisherContentsData(NBTTagCompound busData, TileSuperStockReplenisher tile) {
        NBTTagList contentsList = new NBTTagList();

        if (tile.getInternalFluid() != null) {
            for (int i = 0; i < tile.getInternalFluid()
                .getSlots(); i++) {
                IAEFluidStack fluid = tile.getInternalFluid()
                    .getFluidInSlot(i);
                if (fluid == null) continue;

                NBTTagCompound stackNbt = new NBTTagCompound();
                AEStackUtil.writeStackToNBT(stackNbt, fluid);
                stackNbt.setLong("Cnt", fluid.getStackSize());
                stackNbt.setString("stackTypeId", "fluid");
                contentsList.appendTag(stackNbt);
            }
        }

        if (tile.getInternalInventory() != null) {
            for (int i = 0; i < tile.getInternalInventory()
                .getSizeInventory(); i++) {
                ItemStack stack = tile.getInternalInventory()
                    .getStackInSlot(i);
                if (ItemStacks.isEmpty(stack)) continue;

                NBTTagCompound stackNbt = new NBTTagCompound();
                AEStackUtil.writeStackToNBT(stackNbt, AEStackUtil.createItemStack(stack));
                stackNbt.setLong("Cnt", stack.stackSize);
                stackNbt.setString("stackTypeId", "item");
                contentsList.appendTag(stackNbt);
            }
        }

        busData.setTag("contents", contentsList);
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
                NBTTagCompound partNbt = AEStackUtil.writeItemLikePartitionStack(configStack);
                if (partNbt == null) continue;
                partNbt.setInteger("slot", slotIndex);
                partNbt.setString("stackTypeId", "item");
                partitionList.appendTag(partNbt);
            }

            if (isContentLimitReached(writtenContents, contentLimit)) continue;

            ItemStack extractedStack = snapshot.extracted()[slotIndex];
            if (ItemStacks.isEmpty(extractedStack)) continue;

            NBTTagCompound stackNbt = AEStackUtil.writeItemLikePartitionStack(extractedStack);
            if (stackNbt == null) continue;
            stackNbt.setLong("Cnt", snapshot.extractedAmounts()[slotIndex]);
            stackNbt.setString("stackTypeId", "item");
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
                AEStackUtil.writeStackToNBT(
                    partNbt,
                    AEFluidStack.create(configStack)
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
        int effectiveContentLimit = getEffectiveContentLimit(contentLimit);
        if (effectiveContentLimit <= 0) {
            busData.setTag("contents", new NBTTagList());
            return;
        }

        IItemList<T> list = type.createList();
        handler.getAvailableItems(list, IterationCounter.fetchNewId());

        NBTTagList contentsList = new NBTTagList();
        int written = 0;
        for (T stack : list) {
            if (written >= effectiveContentLimit) break;

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
        if (contentLimit == 0) return 0;
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
        if (tracker.hasConnectedContents) return true;

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

        if (tracker.storageBus instanceof PartSharedItemBus<?>) return false;

        return false;
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

    private static IAEStackInventory getConfigInventory(Object bus) {
        if (bus instanceof PartStorageBus storageBus) return storageBus.getAEInventoryByName(StorageName.CONFIG);
        if (bus instanceof PartSharedItemBus<?>sharedBus) return sharedBus.getAEInventoryByName(StorageName.CONFIG);

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
