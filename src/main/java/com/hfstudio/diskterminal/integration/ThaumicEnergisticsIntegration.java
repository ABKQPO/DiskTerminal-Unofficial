package com.hfstudio.diskterminal.integration;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IItemList;
import appeng.util.IterationCounter;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import thaumicenergistics.common.storage.AEEssentiaStack;
import thaumicenergistics.common.storage.AEEssentiaStackType;

/**
 * Soft integration hook for Thaumic Energistics essentia cells.
 * <p>
 * Essentia is a third storage channel registered into AE2's {@code AEStackTypeRegistry} by Thaumic
 * Energistics ({@link AEEssentiaStackType#ESSENTIA_STACK_TYPE}). This class isolates all optional
 * access so the core data handler stays decoupled; when Thaumic Energistics is absent, every method
 * is a no-op guarded by {@link Optional.Method}.
 */
public class ThaumicEnergisticsIntegration {

    public static final String MOD_ID = "thaumicenergistics";

    private static Boolean loaded = null;

    private ThaumicEnergisticsIntegration() {}

    public static boolean isModLoaded() {
        if (loaded == null) loaded = Loader.isModLoaded(MOD_ID);

        return loaded;
    }

    /**
     * Populate essentia cell data (stats, partition, contents) for the client.
     *
     * @return an NBT compound matching the item/fluid cell layout (with {@code essentiaAmount} per
     *         content entry), or null if this is not an essentia cell / Thaumic Energistics absent.
     */
    public static NBTTagCompound tryPopulateEssentiaCell(ICellHandler cellHandler, ItemStack cellStack, int slotLimit) {
        if (!isModLoaded()) return null;

        return populateEssentiaCell(cellHandler, cellStack, slotLimit);
    }

    @Optional.Method(modid = MOD_ID)
    private static NBTTagCompound populateEssentiaCell(ICellHandler cellHandler, ItemStack cellStack, int slotLimit) {
        try {
            IMEInventoryHandler<AEEssentiaStack> handler = cellHandler
                .getCellInventory(cellStack, null, AEEssentiaStackType.ESSENTIA_STACK_TYPE);
            if (!(handler instanceof ICellInventoryHandler)) return null;

            ICellInventory<AEEssentiaStack> cellInv = ((ICellInventoryHandler<AEEssentiaStack>) handler).getCellInv();
            if (cellInv == null) return null;

            NBTTagCompound cellData = new NBTTagCompound();
            StorageType.ESSENTIA.writeToNBT(cellData);

            cellData.setLong("usedBytes", cellInv.getUsedBytes());
            cellData.setLong("totalBytes", cellInv.getTotalBytes());
            cellData.setLong("usedTypes", cellInv.getStoredItemTypes());
            cellData.setLong("totalTypes", cellInv.getTotalItemTypes());
            cellData.setLong("storedItemCount", cellInv.getStoredItemCount());

            IInventory configInv = cellInv.getConfigInventory();
            if (configInv != null) {
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

            IItemList<AEEssentiaStack> contents = cellInv
                .getAvailableItems(AEEssentiaStackType.ESSENTIA_STACK_TYPE.createList(), IterationCounter.fetchNewId());

            NBTTagList contentsList = new NBTTagList();
            int count = 0;
            for (AEEssentiaStack stack : contents) {
                if (count >= slotLimit) break;

                ItemStack itemRep = stack.getItemStackForNEI();
                if (ItemStacks.isEmpty(itemRep)) continue;

                NBTTagCompound stackNbt = new NBTTagCompound();
                itemRep.writeToNBT(stackNbt);
                stackNbt.setLong("essentiaAmount", stack.getStackSize());
                contentsList.appendTag(stackNbt);
                count++;
            }
            cellData.setTag("contents", contentsList);

            return cellData;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Try to get the essentia cell config (partition) inventory.
     *
     * @return data array {@code [configInv]} ({@link IInventory}), or null if not an essentia cell.
     */
    public static Object[] tryGetEssentiaConfigInventory(ICellHandler cellHandler, ItemStack cellStack) {
        if (!isModLoaded()) return null;

        return essentiaConfigInventory(cellHandler, cellStack);
    }

    @Optional.Method(modid = MOD_ID)
    private static Object[] essentiaConfigInventory(ICellHandler cellHandler, ItemStack cellStack) {
        try {
            IMEInventoryHandler<AEEssentiaStack> handler = cellHandler
                .getCellInventory(cellStack, null, AEEssentiaStackType.ESSENTIA_STACK_TYPE);
            if (!(handler instanceof ICellInventoryHandler)) return null;

            ICellInventory<AEEssentiaStack> cellInv = ((ICellInventoryHandler<AEEssentiaStack>) handler).getCellInv();
            if (cellInv == null) return null;

            return new Object[] { cellInv.getConfigInventory() };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fill a partition config inventory from an essentia cell's contents.
     */
    public static void setAllFromEssentiaContents(IInventory configInv, Object[] essentiaData) {
        // Essentia partition is edited slot-by-slot via the standard partition packets; bulk
        // set-from-contents is not exposed by the 1.7.10 essentia config inventory.
    }

    /**
     * Convert an essentia container item (phial, jar) into the aspect-stack item representation used
     * by essentia cell/bus partitions. Returns null if not an essentia container or ThE is absent.
     */
    public static ItemStack tryConvertEssentiaContainerToAspect(ItemStack container) {
        if (!isModLoaded()) return null;

        return convertEssentiaContainer(container);
    }

    @Optional.Method(modid = MOD_ID)
    private static ItemStack convertEssentiaContainer(ItemStack container) {
        try {
            if (!AEEssentiaStackType.ESSENTIA_STACK_TYPE.isContainerItemForType(container)) return null;

            AEEssentiaStack stack = AEEssentiaStackType.ESSENTIA_STACK_TYPE.getStackFromContainerItem(container);
            if (stack == null) return null;

            return stack.getItemStackForNEI();
        } catch (Exception e) {
            return null;
        }
    }
}
