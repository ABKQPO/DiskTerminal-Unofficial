package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

import java.util.Optional;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;

import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusSource;

import appeng.api.networking.IGridNode;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

public class SuperInputHatchAdapter implements GTMachineAdapter {

    @Override
    public String getId() {
        return "gtnl_super_input_hatch";
    }

    @Override
    public boolean supports(MetaTileEntity metaTileEntity) {
        return GTMachineReflectionHelper.hasClassName(metaTileEntity, GTMachineClassNames.SUPER_INPUT_HATCH_ME);
    }

    @Override
    public GTMachineTargetKind getTargetKind(MetaTileEntity metaTileEntity) {
        return GTMachineTargetKind.FLUID;
    }

    @Override
    public Optional<GTMachineScanResult> scan(IGridNode node, MetaTileEntity metaTileEntity,
        IGregTechTileEntity baseTile, TileEntity hostTile, int contentLimit) {
        if (hostTile == null) {
            return Optional.empty();
        }

        int slotCount = GTMachineReflectionHelper.invokeInt(metaTileEntity, "getFluidSlotCountForGui")
            .orElse(16);
        FluidStack[] configs = new FluidStack[slotCount];
        FluidStack[] extracted = new FluidStack[slotCount];
        long[] extractedAmounts = new long[slotCount];

        for (int slot = 0; slot < slotCount; slot++) {
            FluidStack config = GTMachineReflectionHelper.invokeFluidStack(metaTileEntity, "getFilterFluidForGui", slot)
                .map(FluidStack::copy)
                .orElse(null);
            FluidStack preview = GTMachineReflectionHelper
                .invokeFluidStack(metaTileEntity, "getInformationFluidForGui", slot)
                .map(FluidStack::copy)
                .orElse(null);
            configs[slot] = config;
            extracted[slot] = preview;
            extractedAmounts[slot] = preview == null ? 0L : preview.amount;
        }

        long busId = StorageBusDataHandler.createBusId(
            hostTile,
            baseTile.getFrontFacing()
                .ordinal(),
            StorageType.FLUID.ordinal() + BusRole.IMPORT.ordinal() * 16);
        NBTTagCompound busData = StorageBusDataHandler.createGenericGregTechFluidHatchData(
            metaTileEntity,
            busId,
            StorageType.FLUID,
            BusRole.IMPORT,
            GTMachineReflectionHelper.readBooleanField(metaTileEntity, "autoPullAvailable")
                .orElse(false),
            GTMachineReflectionHelper.invokeBoolean(metaTileEntity, "isAutoPullFluidListForGui")
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
            StorageType.FLUID);
        StorageBusTracker tracker = new StorageBusTracker(
            busId,
            metaTileEntity,
            hostTile,
            baseTile.getFrontFacing()
                .ordinal(),
            StorageType.FLUID).withTarget(targetId, StorageBusSource.GREGTECH);
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
