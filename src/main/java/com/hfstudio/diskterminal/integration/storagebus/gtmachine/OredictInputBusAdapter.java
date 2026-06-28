package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

import java.util.Optional;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusSource;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.networking.IGridNode;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

public class OredictInputBusAdapter implements GTMachineAdapter {

    @Override
    public String getId() {
        return "gtnl_oredict_input_bus";
    }

    @Override
    public boolean supports(MetaTileEntity metaTileEntity) {
        return GTMachineReflectionHelper
            .hasAnyClassName(metaTileEntity, GTMachineClassNames.GTNL_SUPER_ITEM_INPUT_CLASSES)
            && GTMachineReflectionHelper.readBooleanField(metaTileEntity, "isSuper")
                .orElse(false);
    }

    @Override
    public GTMachineTargetKind getTargetKind(MetaTileEntity metaTileEntity) {
        return GTMachineTargetKind.ITEM;
    }

    @Override
    public Optional<GTMachineScanResult> scan(IGridNode node, MetaTileEntity metaTileEntity,
        IGregTechTileEntity baseTile, TileEntity hostTile, int contentLimit) {
        if (hostTile == null) {
            return Optional.empty();
        }

        int slotCount = GTMachineReflectionHelper.invokeInt(metaTileEntity, "getFilterSlotCountForGui")
            .orElse(100);
        ItemStack[] inventory = GTMachineReflectionHelper.readItemStackArrayField(metaTileEntity, "mInventory")
            .orElse(null);
        if (inventory == null || inventory.length < slotCount) {
            return Optional.empty();
        }

        ItemStack[] configs = new ItemStack[slotCount];
        ItemStack[] extracted = new ItemStack[slotCount];
        long[] extractedAmounts = new long[slotCount];

        for (int slot = 0; slot < slotCount; slot++) {
            configs[slot] = copyItem(inventory[slot]);
            ItemStack preview = GTMachineReflectionHelper.invokeItemStack(metaTileEntity, "getShadowItemStack", slot)
                .map(this::copyItem)
                .orElse(null);
            extracted[slot] = preview;
            extractedAmounts[slot] = ItemStacks.isEmpty(preview) ? 0L : preview.stackSize;
        }

        long busId = StorageBusDataHandler.createBusId(
            hostTile,
            baseTile.getFrontFacing()
                .ordinal(),
            StorageType.ITEM.ordinal() + BusRole.IMPORT.ordinal() * 16);
        NBTTagCompound busData = StorageBusDataHandler.createGenericGregTechItemBusData(
            metaTileEntity,
            busId,
            StorageType.ITEM,
            BusRole.IMPORT,
            GTMachineReflectionHelper.readBooleanField(metaTileEntity, "autoPullAvailable")
                .orElse(false),
            GTMachineReflectionHelper.invokeBoolean(metaTileEntity, "isAutoPullItemList")
                .orElse(false),
            configs,
            extracted,
            extractedAmounts,
            contentLimit);
        applyCapabilities(busData);
        applySlotParameters(busData, slotCount);

        StorageBusId targetId = StorageBusId.of(
            hostTile,
            baseTile.getFrontFacing()
                .ordinal(),
            BusRole.IMPORT,
            StorageType.ITEM);
        StorageBusTracker tracker = new StorageBusTracker(
            busId,
            metaTileEntity,
            hostTile,
            baseTile.getFrontFacing()
                .ordinal(),
            StorageType.ITEM).withTarget(targetId, StorageBusSource.GREGTECH);
        return Optional.of(new GTMachineScanResult(busData, tracker));
    }

    private ItemStack copyItem(ItemStack stack) {
        return ItemStacks.isEmpty(stack) ? null : stack.copy();
    }

    private void applyCapabilities(NBTTagCompound busData) {
        busData.setBoolean("supportsPriority", false);
        busData.setBoolean("supportsIOMode", false);
        busData.setBoolean("supportsRename", true);
    }

    private void applySlotParameters(NBTTagCompound busData, int slotCount) {
        busData.setInteger("baseConfigSlots", slotCount);
        busData.setInteger("slotsPerUpgrade", 0);
        busData.setInteger("maxConfigSlots", slotCount);
    }
}
