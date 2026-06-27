package com.hfstudio.diskterminal.integration;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.integration.storagebus.StorageBusScannerRegistry;
import com.hfstudio.diskterminal.integration.storagebus.ThaumicEnergisticsBusScanner;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IItemList;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.IterationCounter;
import cpw.mods.fml.common.Optional;
import thaumicenergistics.common.storage.AEEssentiaStack;
import thaumicenergistics.common.storage.AEEssentiaStackType;

public class ThaumicEnergisticsIntegration {

    public static final String MOD_ID = "thaumicenergistics";

    private ThaumicEnergisticsIntegration() {}

    public static boolean isModLoaded() {
        return Mods.ThaumicEnergistics.isModLoaded();
    }

    public static NBTTagCompound tryPopulateEssentiaCell(ICellHandler cellHandler, ItemStack cellStack, int slotLimit) {
        if (!isModLoaded()) return null;

        return populateEssentiaCell(cellHandler, cellStack, slotLimit);
    }

    public static ItemStack tryConvertEssentiaContainerToAspect(ItemStack container) {
        if (!isModLoaded()) return null;

        return convertEssentiaContainer(container);
    }

    public static void registerStorageBusScanner() {
        if (!isModLoaded()) return;

        registerStorageBusScannerInternal();
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

            IAEStackInventory configInv = cellInv.getConfigAEInventory();
            if (configInv != null) {
                NBTTagList partitionList = new NBTTagList();
                for (int i = 0; i < configInv.getSizeInventory(); i++) {
                    AEEssentiaStack partitionStack = (AEEssentiaStack) configInv.getAEStackInSlot(i);
                    NBTTagCompound partNbt = new NBTTagCompound();
                    partNbt.setInteger("slot", i);
                    ItemStack partItem = partitionStack != null ? partitionStack.getItemStackForNEI() : null;
                    if (!ItemStacks.isEmpty(partItem)) partItem.writeToNBT(partNbt);
                    partitionList.appendTag(partNbt);
                }
                cellData.setTag("partition", partitionList);
            }

            if (slotLimit <= 0) {
                cellData.setTag("contents", new NBTTagList());
                return cellData;
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

    @Optional.Method(modid = MOD_ID)
    private static void registerStorageBusScannerInternal() {
        StorageBusScannerRegistry.register(ThaumicEnergisticsBusScanner.INSTANCE);
    }
}
