package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

import java.util.Optional;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;

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

public class SuperDualInputHatchAdapter implements GTMachineAdapter {

    @Override
    public String getId() {
        return "gtnl_super_dual_input_hatch";
    }

    @Override
    public boolean supports(MetaTileEntity metaTileEntity) {
        return GTMachineReflectionHelper.hasClassName(metaTileEntity, GTMachineClassNames.SUPER_DUAL_INPUT_HATCH_ME);
    }

    @Override
    public GTMachineTargetKind getTargetKind(MetaTileEntity metaTileEntity) {
        return GTMachineTargetKind.MIXED;
    }

    @Override
    public Optional<GTMachineScanResult> scan(IGridNode node, MetaTileEntity metaTileEntity,
        IGregTechTileEntity baseTile, TileEntity hostTile, int contentLimit) {
        if (hostTile == null) {
            return Optional.empty();
        }

        int slotCount = GTMachineReflectionHelper.invokeInt(metaTileEntity, "getDualSlotCountForGui")
            .orElse(16);
        ItemStack[] itemConfigs = new ItemStack[slotCount];
        ItemStack[] itemPreview = new ItemStack[slotCount];
        long[] itemAmounts = new long[slotCount];
        FluidStack[] fluidConfigs = new FluidStack[slotCount];
        FluidStack[] fluidPreview = new FluidStack[slotCount];
        long[] fluidAmounts = new long[slotCount];

        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack configItem = GTMachineReflectionHelper
                .invokeItemStack(metaTileEntity, "getFilterItemForGui", slot)
                .map(ItemStack::copy)
                .orElse(null);
            ItemStack previewItem = GTMachineReflectionHelper
                .invokeItemStack(metaTileEntity, "getInformationItemForGui", slot)
                .map(ItemStack::copy)
                .orElse(null);
            FluidStack configFluid = GTMachineReflectionHelper
                .invokeFluidStack(metaTileEntity, "getFilterFluidForGui", slot)
                .map(FluidStack::copy)
                .orElse(null);
            FluidStack previewFluid = GTMachineReflectionHelper
                .invokeFluidStack(metaTileEntity, "getInformationFluidForGui", slot)
                .map(FluidStack::copy)
                .orElse(null);

            itemConfigs[slot] = configItem;
            itemPreview[slot] = previewItem;
            itemAmounts[slot] = GTMachineReflectionHelper
                .invokeLong(metaTileEntity, "getInformationItemAmountForGui", slot)
                .orElse(ItemStacks.isEmpty(previewItem) ? 0L : previewItem.stackSize);
            fluidConfigs[slot] = configFluid;
            fluidPreview[slot] = previewFluid;
            fluidAmounts[slot] = GTMachineReflectionHelper
                .invokeLong(metaTileEntity, "getInformationFluidAmountForGui", slot)
                .orElse(previewFluid == null ? 0L : previewFluid.amount);
        }

        long busId = StorageBusDataHandler.createBusId(
            hostTile,
            baseTile.getFrontFacing()
                .ordinal(),
            StorageType.ITEM.ordinal() + BusRole.IMPORT.ordinal() * 16);
        NBTTagCompound busData = StorageBusDataHandler.createGenericGregTechMixedBusData(
            metaTileEntity,
            busId,
            BusRole.IMPORT,
            GTMachineReflectionHelper.readBooleanField(metaTileEntity, "allowAuto")
                .orElse(false),
            GTMachineReflectionHelper.invokeBoolean(metaTileEntity, "isAutoPullItemListForGui")
                .orElse(false),
            itemConfigs,
            itemPreview,
            itemAmounts,
            fluidConfigs,
            fluidPreview,
            fluidAmounts,
            contentLimit);
        applyCapabilities(busData);
        applySlotParameters(busData, slotCount * 2);

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
            StorageType.ITEM).withTarget(targetId, StorageBusSource.MIXED_CONFIG_TARGET);
        return Optional.of(new GTMachineScanResult(busData, tracker));
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
