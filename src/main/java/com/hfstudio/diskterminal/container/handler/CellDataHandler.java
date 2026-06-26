package com.hfstudio.diskterminal.container.handler;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.InventoryHelper;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

import appeng.api.AEApi;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.implementations.tiles.IMEChest;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.helpers.IPriorityHost;
import appeng.tile.AEBaseInvTile;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.IterationCounter;

/**
 * Handles cell and storage data generation for NBT serialization.
 * Extracts data generation logic from ContainerCellTerminalBase.
 */
public class CellDataHandler {

    /**
     * Create NBT data for a storage device (ME Drive or ME Chest).
     *
     * @param storage         The storage device
     * @param defaultName     The default localization key for the storage name
     * @param trackerCallback Callback to register the storage tracker
     * @param slotLimit       Maximum number of item types to include per cell
     * @return NBT data for the storage
     */
    public static NBTTagCompound createStorageData(IChestOrDrive storage, String defaultName,
        StorageTrackerCallback trackerCallback, int slotLimit) {
        TileEntity te = (TileEntity) storage;
        int dim = te.getWorldObj().provider.dimensionId;
        long pos = PosUtil.toLong(te.xCoord, te.yCoord, te.zCoord);
        long id = pos ^ ((long) dim << 48);

        if (trackerCallback != null) trackerCallback.register(id, te, storage);

        NBTTagCompound storageData = new NBTTagCompound();
        storageData.setLong("id", id);
        storageData.setLong("pos", pos);
        storageData.setInteger("dim", dim);

        String name = getStorageName(storage, defaultName);
        storageData.setString("name", name);

        if (te instanceof IPriorityHost) {
            storageData.setInteger("priority", ((IPriorityHost) te).getPriority());
            storageData.setBoolean("supportsPriority", true);
        }

        ItemStack blockItem = getBlockItem(te);
        if (!ItemStacks.isEmpty(blockItem)) {
            NBTTagCompound blockNbt = new NBTTagCompound();
            blockItem.writeToNBT(blockNbt);
            storageData.setTag("blockItem", blockNbt);
        }

        storageData.setInteger("slotCount", storage.getCellCount());

        NBTTagList cellList = new NBTTagList();
        IInventory cellInventory = getCellInventory(storage);

        if (cellInventory != null) {
            for (int slot = 0; slot < storage.getCellCount(); slot++) {
                ItemStack cellStack = getCellStack(cellInventory, storage, slot);
                if (ItemStacks.isEmpty(cellStack)) continue;

                NBTTagCompound cellData = createCellData(slot, cellStack, storage.getCellStatus(slot), slotLimit);
                cellList.appendTag(cellData);
            }
        }

        storageData.setTag("cells", cellList);

        return storageData;
    }

    /**
     * Create NBT data for a single cell.
     *
     * @param slot      The slot index in the storage device
     * @param cellStack The cell ItemStack
     * @param status    The cell status
     * @param slotLimit Maximum number of item types to include
     * @return NBT data for the cell
     */
    public static NBTTagCompound createCellData(int slot, ItemStack cellStack, int status, int slotLimit) {
        NBTTagCompound cellData = new NBTTagCompound();
        cellData.setInteger("slot", slot);
        cellData.setInteger("status", status);

        NBTTagCompound cellNbt = new NBTTagCompound();
        cellStack.writeToNBT(cellNbt);
        cellData.setTag("cellItem", cellNbt);

        ICellHandler cellHandler = AEApi.instance()
            .registries()
            .cell()
            .getHandler(cellStack);
        if (cellHandler == null) return cellData;

        for (IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
            if (tryPopulateCellByUnknownType(cellData, cellHandler, cellStack, type, slotLimit)) return cellData;
        }

        return cellData;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> boolean tryPopulateCellByUnknownType(NBTTagCompound cellData,
        ICellHandler cellHandler, ItemStack cellStack, IAEStackType<?> type, int slotLimit) {
        return tryPopulateCellByType(cellData, cellHandler, cellStack, (IAEStackType<T>) type, slotLimit);
    }

    private static <T extends IAEStack<T>> boolean tryPopulateCellByType(NBTTagCompound cellData,
        ICellHandler cellHandler, ItemStack cellStack, IAEStackType<T> type, int slotLimit) {
        IMEInventoryHandler<?> rawHandler = cellHandler.getCellInventory(cellStack, null, type);
        if (!(rawHandler instanceof ICellInventoryHandler)) return false;

        @SuppressWarnings("unchecked")
        ICellInventoryHandler<T> handler = (ICellInventoryHandler<T>) rawHandler;
        ICellInventory<T> cellInv = handler.getCellInv();

        writeStackType(cellData, type);

        if (cellInv == null) {
            return populateWorkbenchOnlyData(cellData, cellStack);
        }

        populateCellStats(cellData, cellInv);
        populateConfigInventory(cellData, cellInv.getConfigAEInventory());
        populateGenericContents(cellData, cellInv, type, slotLimit);
        populateCellUpgrades(cellData, cellInv.getUpgradesInventory());

        return true;
    }

    private static void writeStackType(NBTTagCompound data, IAEStackType<?> type) {
        data.setString("stackType", type.getId());
        storageTypeFrom(type).writeToNBT(data);
    }

    private static void populateCellStats(NBTTagCompound cellData, ICellInventory<?> cellInv) {
        cellData.setLong("usedBytes", cellInv.getUsedBytes());
        cellData.setLong("totalBytes", cellInv.getTotalBytes());
        cellData.setLong("usedTypes", cellInv.getStoredItemTypes());
        cellData.setLong("totalTypes", cellInv.getTotalItemTypes());
        cellData.setLong("storedItemCount", cellInv.getStoredItemCount());
    }

    private static void populateConfigInventory(NBTTagCompound cellData, IAEStackInventory configInv) {
        if (configInv == null) return;

        NBTTagList partitionList = new NBTTagList();
        for (int i = 0; i < configInv.getSizeInventory(); i++) {
            IAEStack<?> partitionStack = configInv.getAEStackInSlot(i);
            if (partitionStack == null) continue;

            NBTTagCompound partNbt = createPartitionSlotData(i, partitionStack);
            partitionList.appendTag(partNbt);
        }

        cellData.setTag("partition", partitionList);
    }

    private static NBTTagCompound createPartitionSlotData(int slot, IAEStack<?> partitionStack) {
        NBTTagCompound partNbt = new NBTTagCompound();
        partNbt.setInteger("slot", slot);
        AEStackUtil.writeStackToNBT(partNbt, partitionStack);

        return partNbt;
    }

    private static <T extends IAEStack<T>> void populateGenericContents(NBTTagCompound cellData,
        ICellInventory<T> cellInv, IAEStackType<T> type, int slotLimit) {
        IItemList<T> contents = cellInv.getAvailableItems(type.createList(), IterationCounter.fetchNewId());
        NBTTagList contentsList = new NBTTagList();
        int count = 0;

        for (T stack : contents) {
            if (count >= slotLimit) break;

            NBTTagCompound stackNbt = new NBTTagCompound();
            AEStackUtil.writeStackToNBT(stackNbt, stack);
            stackNbt.setLong("Cnt", stack.getStackSize());
            contentsList.appendTag(stackNbt);
            count++;
        }

        cellData.setTag("contents", contentsList);
    }

    private static void populateCellUpgrades(NBTTagCompound cellData, IInventory upgradesInv) {
        if (upgradesInv == null) return;

        cellData.setInteger("upgradeSlotCount", upgradesInv.getSizeInventory());

        NBTTagList upgradeList = new NBTTagList();

        for (int i = 0; i < upgradesInv.getSizeInventory(); i++) {
            ItemStack upgrade = upgradesInv.getStackInSlot(i);
            if (ItemStacks.isEmpty(upgrade)) continue;

            NBTTagCompound upgradeNbt = new NBTTagCompound();
            upgrade.writeToNBT(upgradeNbt);
            upgradeNbt.setInteger("slot", i);
            upgradeList.appendTag(upgradeNbt);
        }

        cellData.setTag("upgrades", upgradeList);
    }

    private static boolean populateWorkbenchOnlyData(NBTTagCompound cellData, ItemStack cellStack) {
        if (!(cellStack.getItem() instanceof ICellWorkbenchItem workbenchItem)) return false;

        IAEStackInventory configInv = workbenchItem.getConfigAEInventory(cellStack);
        IInventory upgradesInv = workbenchItem.getUpgradesInventory(cellStack);

        if (configInv == null && upgradesInv == null) return false;

        populateConfigInventory(cellData, configInv);
        populateCellUpgrades(cellData, upgradesInv);

        return true;
    }

    private static StorageType storageTypeFrom(IAEStackType<?> type) {
        String id = type.getId();
        if ("fluid".equals(id)) return StorageType.FLUID;
        if ("essentia".equals(id)) return StorageType.ESSENTIA;

        return StorageType.ITEM;
    }

    private static String getStorageName(IChestOrDrive storage, String defaultName) {
        if (storage instanceof IInventory inv) {
            if (inv.hasCustomInventoryName()) return inv.getInventoryName();
        }

        return defaultName;
    }

    /**
     * Get the cell inventory of a storage device as an {@link IInventory}.
     * <p>
     * For ME Drives the internal inventory IS the cell array. For ME Chests the internal inventory
     * combines an input slot (0) and the cell slot (1); {@link #getCellStack} maps the logical cell
     * slot accordingly.
     */
    public static IInventory getCellInventory(IChestOrDrive storage) {
        if (storage instanceof AEBaseInvTile) return ((AEBaseInvTile) storage).getInternalInventory();

        return null;
    }

    public static int toInventorySlot(IChestOrDrive storage, int logicalSlot) {
        if (storage instanceof IMEChest) return logicalSlot == 0 ? 1 : -1;

        return logicalSlot;
    }

    public static ItemStack getCellStack(IInventory cellInventory, IChestOrDrive storage, int logicalSlot) {
        int slot = toInventorySlot(storage, logicalSlot);
        if (slot < 0 || slot >= cellInventory.getSizeInventory()) return null;

        return cellInventory.getStackInSlot(slot);
    }

    public static int findEmptyCellSlot(IInventory cellInventory, IChestOrDrive storage) {
        if (storage instanceof IMEChest) {
            return ItemStacks.isEmpty(getCellStack(cellInventory, storage, 0)) ? 0 : -1;
        }

        return InventoryHelper.findEmptySlot(cellInventory);
    }

    private static ItemStack getBlockItem(TileEntity te) {
        if (te.getWorldObj() == null) return null;

        Block block = te.getWorldObj()
            .getBlock(te.xCoord, te.yCoord, te.zCoord);
        if (block == null) return null;

        int damage = block.getDamageValue(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord);

        return new ItemStack(block, 1, damage);
    }

    /**
     * Callback interface for registering storage trackers.
     */
    @FunctionalInterface
    public interface StorageTrackerCallback {

        void register(long id, TileEntity tile, IChestOrDrive storage);
    }
}
