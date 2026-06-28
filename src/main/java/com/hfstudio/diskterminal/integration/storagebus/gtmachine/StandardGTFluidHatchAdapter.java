package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

import java.util.Optional;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusSource;

import appeng.api.networking.IGridNode;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.MTEHatchInputME;

public class StandardGTFluidHatchAdapter implements GTMachineAdapter {

    @Override
    public String getId() {
        return "standard_gt_fluid_hatch";
    }

    @Override
    public boolean supports(MetaTileEntity metaTileEntity) {
        return metaTileEntity instanceof MTEHatchInputME;
    }

    @Override
    public GTMachineTargetKind getTargetKind(MetaTileEntity metaTileEntity) {
        return GTMachineTargetKind.FLUID;
    }

    @Override
    public Optional<GTMachineScanResult> scan(IGridNode node, MetaTileEntity metaTileEntity,
        IGregTechTileEntity baseTile, TileEntity hostTile, int contentLimit) {
        if (!(metaTileEntity instanceof MTEHatchInputME inputHatch) || hostTile == null) {
            return Optional.empty();
        }

        long busId = StorageBusDataHandler.createBusId(
            hostTile,
            baseTile.getFrontFacing()
                .ordinal(),
            StorageType.FLUID.ordinal() + BusRole.IMPORT.ordinal() * 16);
        NBTTagCompound busData = StorageBusDataHandler
            .createGregTechInputHatchData(inputHatch, busId, StorageType.FLUID, BusRole.IMPORT, contentLimit);
        applyCapabilities(busData);
        applySlotParameters(busData, MTEHatchInputME.SLOT_COUNT);

        StorageBusId targetId = StorageBusId.of(
            hostTile,
            baseTile.getFrontFacing()
                .ordinal(),
            BusRole.IMPORT,
            StorageType.FLUID);
        StorageBusTracker tracker = new StorageBusTracker(
            busId,
            inputHatch,
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
