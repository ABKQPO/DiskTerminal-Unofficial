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
import gregtech.common.tileentities.machines.MTEHatchInputBusME;

public class StandardGTItemBusAdapter implements GTMachineAdapter {

    @Override
    public String getId() {
        return "standard_gt_item_bus";
    }

    @Override
    public boolean supports(MetaTileEntity metaTileEntity) {
        return metaTileEntity instanceof MTEHatchInputBusME;
    }

    @Override
    public GTMachineTargetKind getTargetKind(MetaTileEntity metaTileEntity) {
        return GTMachineTargetKind.ITEM;
    }

    @Override
    public Optional<GTMachineScanResult> scan(IGridNode node, MetaTileEntity metaTileEntity,
        IGregTechTileEntity baseTile, TileEntity hostTile, int contentLimit) {
        if (!(metaTileEntity instanceof MTEHatchInputBusME inputBus) || hostTile == null) {
            return Optional.empty();
        }

        long busId = StorageBusDataHandler.createBusId(
            hostTile,
            baseTile.getFrontFacing()
                .ordinal(),
            StorageType.ITEM.ordinal() + BusRole.IMPORT.ordinal() * 16);
        NBTTagCompound busData = StorageBusDataHandler
            .createGregTechInputBusData(inputBus, busId, StorageType.ITEM, BusRole.IMPORT, contentLimit);
        applyCapabilities(busData);
        applySlotParameters(busData, MTEHatchInputBusME.SLOT_COUNT);

        StorageBusId targetId = StorageBusId.of(
            hostTile,
            baseTile.getFrontFacing()
                .ordinal(),
            BusRole.IMPORT,
            StorageType.ITEM);
        StorageBusTracker tracker = new StorageBusTracker(
            busId,
            inputBus,
            hostTile,
            baseTile.getFrontFacing()
                .ordinal(),
            StorageType.ITEM).withTarget(targetId, StorageBusSource.GREGTECH);
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
