package com.hfstudio.diskterminal.container.handler;

import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigManager;
import appeng.helpers.ICustomNameObject;
import appeng.parts.automation.PartUpgradeable;
import appeng.parts.misc.PartStorageBus;
import appeng.util.IterationCounter;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStackType;

import com.glodblock.github.common.item.ItemFluidDrop;
import com.hfstudio.diskterminal.util.InventoryHelper;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.integration.storagebus.StorageBusScannerRegistry;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

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
    public static NBTTagList collectStorageBuses(IGrid grid, Map<Long, StorageBusTracker> trackerMap) {
        NBTTagList storageBusList = new NBTTagList();

        if (grid == null) return storageBusList;

        StorageBusScannerRegistry.scanAll(grid, storageBusList, trackerMap);

        return storageBusList;
    }

    /**
     * Create a unique bus ID from position, dimension, side, and type flag.
     */
    public static long createBusId(TileEntity hostTile, int sideOrdinal, int typeFlag) {
        long pos = PosUtil.toLong(hostTile.xCoord, hostTile.yCoord, hostTile.zCoord);

        return pos ^ ((long) hostTile.getWorldObj().provider.dimensionId << 48) ^ ((long) sideOrdinal << 40)
            ^ ((long) typeFlag << 39);
    }

    /**
     * Create NBT data for an item storage bus.
     */
    public static NBTTagCompound createItemStorageBusData(PartStorageBus bus, long busId) {
        return createStorageBusData(bus, busId, StorageType.ITEM);
    }

    /**
     * Create NBT data for a fluid storage bus.
     */
    public static NBTTagCompound createFluidStorageBusData(PartStorageBus bus, long busId) {
        return createStorageBusData(bus, busId, StorageType.FLUID);
    }

    private static NBTTagCompound createStorageBusData(PartStorageBus bus, long busId, StorageType storageType) {
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

        AccessRestriction access = (AccessRestriction) bus.getConfigManager()
            .getSetting(Settings.ACCESS);
        busData.setInteger("access", access.ordinal());

        addCustomName(busData, bus);
        addConnectedInventoryInfo(busData, hostTile, side);

        int capacityUpgrades = bus.getInstalledUpgrades(Upgrades.CAPACITY);
        addPartitionData(busData, bus.getInventoryByName("config"), capacityUpgrades, storageType);
        addContentsData(busData, bus, storageType);
        addUpgradesData(busData, bus.getInventoryByName("upgrades"));

        return busData;
    }

    private static void addCustomName(NBTTagCompound busData, Object bus) {
        if (!(bus instanceof ICustomNameObject)) return;

        ICustomNameObject nameable = (ICustomNameObject) bus;
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

        busData.setString("connectedName", blockStack.getDisplayName());

        NBTTagCompound iconNbt = new NBTTagCompound();
        blockStack.writeToNBT(iconNbt);
        busData.setTag("connectedIcon", iconNbt);
    }

    private static void addPartitionData(NBTTagCompound busData, IInventory configInv, int capacityUpgrades,
        StorageType storageType) {
        if (configInv == null) return;

        int slotsToUse = computeAvailableSlotsFrom(busData, capacityUpgrades);
        NBTTagList partitionList = new NBTTagList();

        for (int i = 0; i < configInv.getSizeInventory() && i < slotsToUse; i++) {
            ItemStack partItem = configInv.getStackInSlot(i);
            NBTTagCompound partNbt = new NBTTagCompound();
            partNbt.setInteger("slot", i);
            if (!ItemStacks.isEmpty(partItem)) partItem.writeToNBT(partNbt);
            partitionList.appendTag(partNbt);
        }

        busData.setTag("partition", partitionList);
    }

    private static void addContentsData(NBTTagCompound busData, PartStorageBus bus, StorageType storageType) {
        IMEInventoryHandler<?> handler = bus.getInternalHandler();
        if (handler == null) return;

        NBTTagList contentsList = new NBTTagList();
        boolean fluid = storageType == StorageType.FLUID;

        if (fluid) {
            IItemList<IAEFluidStack> list = AEFluidStackType.FLUID_STACK_TYPE.createList();
            ((IMEInventoryHandler<IAEFluidStack>) castHandler(handler)).getAvailableItems(list,
                IterationCounter.fetchNewId());
            for (IAEFluidStack stack : list) {
                ItemStack rep = ItemFluidDrop.newStack(stack.getFluidStack());
                if (ItemStacks.isEmpty(rep)) continue;

                NBTTagCompound stackNbt = new NBTTagCompound();
                rep.writeToNBT(stackNbt);
                stackNbt.setLong("Cnt", stack.getStackSize());
                contentsList.appendTag(stackNbt);
            }
        } else {
            IItemList<IAEItemStack> list = AEItemStackType.ITEM_STACK_TYPE.createList();
            ((IMEInventoryHandler<IAEItemStack>) castHandler(handler)).getAvailableItems(list,
                IterationCounter.fetchNewId());
            for (IAEItemStack stack : list) {
                ItemStack rep = stack.getItemStack();
                if (ItemStacks.isEmpty(rep)) continue;

                NBTTagCompound stackNbt = new NBTTagCompound();
                rep.writeToNBT(stackNbt);
                stackNbt.setLong("Cnt", stack.getStackSize());
                contentsList.appendTag(stackNbt);
            }
        }

        busData.setTag("contents", contentsList);
    }

    @SuppressWarnings("unchecked")
    private static IMEInventoryHandler<? extends IAEStack> castHandler(IMEInventoryHandler<?> handler) {
        return (IMEInventoryHandler<? extends IAEStack>) handler;
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
    public static boolean isDuplicateFilterAdd(StorageBusTracker tracker,
        com.hfstudio.diskterminal.network.PacketStorageBusPartitionAction.Action action, int partitionSlot,
        ItemStack itemStack) {
        if (tracker == null) return false;
        if (action != com.hfstudio.diskterminal.network.PacketStorageBusPartitionAction.Action.ADD_ITEM) return false;
        if (partitionSlot < 0 || ItemStacks.isEmpty(itemStack)) return false;
        if (!(tracker.storageBus instanceof PartStorageBus)) return false;

        IInventory config = ((PartStorageBus) tracker.storageBus).getInventoryByName("config");
        if (config == null || partitionSlot >= config.getSizeInventory()) return false;

        int existing = findItemInConfig(config, itemStack);

        return existing >= 0 && existing != partitionSlot;
    }

    /**
     * Handle a partition action against a storage bus (item or fluid; both share the config inventory).
     *
     * @return true if the partition was modified
     */
    public static boolean handlePartitionAction(StorageBusTracker tracker,
        com.hfstudio.diskterminal.network.PacketStorageBusPartitionAction.Action action, int partitionSlot,
        ItemStack itemStack) {
        if (!(tracker.storageBus instanceof PartStorageBus)) return false;

        PartStorageBus bus = (PartStorageBus) tracker.storageBus;
        IInventory config = bus.getInventoryByName("config");
        if (config == null) return false;

        boolean fluid = tracker.storageType == StorageType.FLUID;
        ItemStack normalized = fluid ? normalizeFluidConfigStack(itemStack) : itemStack;
        int slots = config.getSizeInventory();

        switch (action) {
            case ADD_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slots && !ItemStacks.isEmpty(normalized)) {
                    InventoryHelper.setSlot(config, partitionSlot, normalized);
                }
                break;
            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slots) InventoryHelper.setSlot(config, partitionSlot, null);
                break;
            case TOGGLE_ITEM:
                if (!ItemStacks.isEmpty(normalized)) {
                    int existing = findItemInConfig(config, normalized);
                    if (existing >= 0) {
                        InventoryHelper.setSlot(config, existing, null);
                    } else {
                        int empty = InventoryHelper.findEmptySlot(config);
                        if (empty >= 0) InventoryHelper.setSlot(config, empty, normalized);
                    }
                }
                break;
            case CLEAR_ALL:
                InventoryHelper.clear(config);
                break;
            case SET_ALL_FROM_CONTENTS:
                // Filling from connected contents is deferred; clear keeps behavior predictable.
                InventoryHelper.clear(config);
                break;
        }

        tracker.hostTile.markDirty();

        return true;
    }

    private static int findItemInConfig(IInventory inv, ItemStack stack) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (ItemStack.areItemStacksEqual(inv.getStackInSlot(i), stack)) return i;
        }

        return -1;
    }

    private static ItemStack normalizeFluidConfigStack(ItemStack stack) {
        if (ItemStacks.isEmpty(stack)) return stack;
        if (stack.getItem() instanceof ItemFluidDrop) {
            FluidStack fs = ItemFluidDrop.getFluidStack(stack);
            if (fs != null && fs.getFluid() != null) {
                return ItemFluidDrop.newStack(new FluidStack(fs.getFluid(), 1000));
            }
        }

        return stack;
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
        if (tracker.storageBus instanceof PartStorageBus) {
            IInventory configInv = ((PartStorageBus) tracker.storageBus).getInventoryByName("config");
            if (configInv == null) return false;

            for (int i = 0; i < configInv.getSizeInventory(); i++) {
                if (!ItemStacks.isEmpty(configInv.getStackInSlot(i))) return true;
            }
        }

        return false;
    }

    private static ItemStack getBlockAsItemStack(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        if (block == null) return null;

        Item item = Item.getItemFromBlock(block);
        if (item == null) return null;

        int meta = block.getDamageValue(world, x, y, z);

        return new ItemStack(item, 1, meta);
    }
}
