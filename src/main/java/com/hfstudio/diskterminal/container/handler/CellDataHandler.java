package com.hfstudio.diskterminal.container.handler;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.glodblock.github.common.item.ItemFluidDrop;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.integration.ThaumicEnergisticsIntegration;
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
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.helpers.IPriorityHost;
import appeng.tile.AEBaseInvTile;
import appeng.util.IterationCounter;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStackType;

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

        // Try each channel type in order
        if (tryPopulateItemCell(cellData, cellHandler, cellStack, slotLimit)) return cellData;
        if (tryPopulateFluidCell(cellData, cellHandler, cellStack, slotLimit)) return cellData;
        tryPopulateEssentiaCell(cellData, cellHandler, cellStack, slotLimit);

        return cellData;
    }

    private static boolean tryPopulateItemCell(NBTTagCompound cellData, ICellHandler cellHandler, ItemStack cellStack,
        int slotLimit) {
        IAEStackType<IAEItemStack> type = AEItemStackType.ITEM_STACK_TYPE;
        IMEInventoryHandler<?> rawHandler = cellHandler.getCellInventory(cellStack, null, type);
        if (!(rawHandler instanceof ICellInventoryHandler)) return false;

        @SuppressWarnings("unchecked")
        ICellInventory<IAEItemStack> cellInv = ((ICellInventoryHandler<IAEItemStack>) rawHandler).getCellInv();

        // If cellInv is null (e.g., VoidCells), fall back to ICellWorkbenchItem for config/upgrades
        if (cellInv == null) {
            if (!(cellStack.getItem() instanceof ICellWorkbenchItem)) return false;

            ICellWorkbenchItem workbenchItem = (ICellWorkbenchItem) cellStack.getItem();
            IInventory configInv = workbenchItem.getConfigInventory(cellStack);
            IInventory upgradesInv = workbenchItem.getUpgradesInventory(cellStack);

            if (configInv == null && upgradesInv == null) return false;

            populateConfigInventory(cellData, configInv);
            populateCellUpgrades(cellData, upgradesInv);

            return true;
        }

        StorageType.ITEM.writeToNBT(cellData);
        populateCellStats(cellData, cellInv);
        populateConfigInventory(cellData, cellInv.getConfigInventory());
        populateItemContents(cellData, cellInv, slotLimit);
        populateCellUpgrades(cellData, cellInv.getUpgradesInventory());

        return true;
    }

    private static boolean tryPopulateFluidCell(NBTTagCompound cellData, ICellHandler cellHandler, ItemStack cellStack,
        int slotLimit) {
        IAEStackType<IAEFluidStack> type = AEFluidStackType.FLUID_STACK_TYPE;
        IMEInventoryHandler<?> rawHandler = cellHandler.getCellInventory(cellStack, null, type);
        if (!(rawHandler instanceof ICellInventoryHandler)) return false;

        @SuppressWarnings("unchecked")
        ICellInventory<IAEFluidStack> cellInv = ((ICellInventoryHandler<IAEFluidStack>) rawHandler).getCellInv();

        if (cellInv == null) {
            if (!(cellStack.getItem() instanceof ICellWorkbenchItem)) return false;

            ICellWorkbenchItem workbenchItem = (ICellWorkbenchItem) cellStack.getItem();
            IInventory configInv = workbenchItem.getConfigInventory(cellStack);
            IInventory upgradesInv = workbenchItem.getUpgradesInventory(cellStack);

            if (configInv == null && upgradesInv == null) return false;

            StorageType.FLUID.writeToNBT(cellData);
            populateConfigInventory(cellData, configInv);
            populateCellUpgrades(cellData, upgradesInv);

            return true;
        }

        StorageType.FLUID.writeToNBT(cellData);
        populateCellStats(cellData, cellInv);
        populateConfigInventory(cellData, cellInv.getConfigInventory());
        populateFluidContents(cellData, cellInv, slotLimit);
        populateCellUpgrades(cellData, cellInv.getUpgradesInventory());

        return true;
    }

    private static boolean tryPopulateEssentiaCell(NBTTagCompound cellData, ICellHandler cellHandler,
        ItemStack cellStack, int slotLimit) {
        NBTTagCompound essentiaData = ThaumicEnergisticsIntegration
            .tryPopulateEssentiaCell(cellHandler, cellStack, slotLimit);
        if (essentiaData == null) return false;

        for (Object key : essentiaData.func_150296_c()) {
            String k = (String) key;
            cellData.setTag(k, essentiaData.getTag(k));
        }

        return true;
    }

    private static void populateCellStats(NBTTagCompound cellData, ICellInventory<?> cellInv) {
        cellData.setLong("usedBytes", cellInv.getUsedBytes());
        cellData.setLong("totalBytes", cellInv.getTotalBytes());
        cellData.setLong("usedTypes", cellInv.getStoredItemTypes());
        cellData.setLong("totalTypes", cellInv.getTotalItemTypes());
        cellData.setLong("storedItemCount", cellInv.getStoredItemCount());
    }

    private static void populateConfigInventory(NBTTagCompound cellData, IInventory configInv) {
        if (configInv == null) return;

        NBTTagList partitionList = new NBTTagList();
        for (int i = 0; i < configInv.getSizeInventory(); i++) {
            ItemStack partItem = configInv.getStackInSlot(i);
            NBTTagCompound partNbt = new NBTTagCompound();
            partNbt.setInteger("slot", i);
            if (!ItemStacks.isEmpty(partItem)) partItem.writeToNBT(partNbt);
            partitionList.appendTag(partNbt);
        }

        cellData.setTag("partition", partitionList);
    }

    private static void populateItemContents(NBTTagCompound cellData, ICellInventory<IAEItemStack> cellInv,
        int slotLimit) {
        IItemList<IAEItemStack> contents = cellInv
            .getAvailableItems(AEItemStackType.ITEM_STACK_TYPE.createList(), IterationCounter.fetchNewId());
        NBTTagList contentsList = new NBTTagList();
        int count = 0;

        for (IAEItemStack stack : contents) {
            if (count >= slotLimit) break;

            NBTTagCompound stackNbt = new NBTTagCompound();
            stack.writeToNBT(stackNbt);
            contentsList.appendTag(stackNbt);
            count++;
        }

        cellData.setTag("contents", contentsList);
    }

    private static void populateFluidContents(NBTTagCompound cellData, ICellInventory<IAEFluidStack> cellInv,
        int slotLimit) {
        IItemList<IAEFluidStack> contents = cellInv
            .getAvailableItems(AEFluidStackType.FLUID_STACK_TYPE.createList(), IterationCounter.fetchNewId());
        NBTTagList contentsList = new NBTTagList();
        int count = 0;

        for (IAEFluidStack stack : contents) {
            if (count >= slotLimit) break;

            ItemStack itemRep = ItemFluidDrop.newStack(stack.getFluidStack());
            if (ItemStacks.isEmpty(itemRep)) continue;

            NBTTagCompound stackNbt = new NBTTagCompound();
            itemRep.writeToNBT(stackNbt);
            stackNbt.setLong("fluidAmount", stack.getStackSize());
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

    private static String getStorageName(IChestOrDrive storage, String defaultName) {
        if (storage instanceof IInventory) {
            IInventory inv = (IInventory) storage;
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

    private static ItemStack getCellStack(IInventory cellInventory, IChestOrDrive storage, int slot) {
        // ME Chest stores its single cell in internal slot 1 (slot 0 is the input buffer).
        if (storage instanceof IMEChest) return cellInventory.getStackInSlot(1);

        return cellInventory.getStackInSlot(slot);
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
