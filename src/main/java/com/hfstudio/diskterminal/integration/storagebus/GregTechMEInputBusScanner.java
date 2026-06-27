package com.hfstudio.diskterminal.integration.storagebus;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.integration.GregTechIntegration;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusSource;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import gregtech.common.tileentities.machines.MTEHatchInputME;

public class GregTechMEInputBusScanner extends AbstractStorageBusScanner {

    public static final GregTechMEInputBusScanner INSTANCE = new GregTechMEInputBusScanner();

    private GregTechMEInputBusScanner() {}

    @Override
    public String getId() {
        return "gregtech";
    }

    @Override
    public boolean isAvailable() {
        return GregTechIntegration.isEnabled();
    }

    @Override
    public void scanStorageBuses(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap,
        int contentLimit) {
        if (grid == null) return;

        scanInputBusMachines(grid, out, trackerMap, contentLimit);
        scanInputHatchMachines(grid, out, trackerMap, contentLimit);
    }

    private void scanInputBusMachines(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap,
        int contentLimit) {
        for (IGridNode node : grid.getMachines(MTEHatchInputBusME.class)) {
            if (!node.isActive()) continue;

            Object machine = node.getMachine();
            if (!(machine instanceof MTEHatchInputBusME inputBus)) continue;

            IGregTechTileEntity baseTile = getBaseTile(inputBus);
            TileEntity hostTile = (TileEntity) baseTile;
            if (hostTile == null) continue;

            long busId = StorageBusDataHandler.createBusId(
                hostTile,
                baseTile.getFrontFacing()
                    .ordinal(),
                StorageType.ITEM.ordinal() + BusRole.IMPORT.ordinal() * 16);
            NBTTagCompound busData = StorageBusDataHandler
                .createGregTechInputBusData(inputBus, busId, StorageType.ITEM, BusRole.IMPORT, contentLimit);
            applyCapabilities(busData, false, false, true);
            applySlotParameters(busData, MTEHatchInputBusME.SLOT_COUNT, 0, MTEHatchInputBusME.SLOT_COUNT);
            out.appendTag(busData);
            StorageBusId targetId = StorageBusId.of(
                hostTile,
                baseTile.getFrontFacing()
                    .ordinal(),
                BusRole.IMPORT,
                StorageType.ITEM);
            trackerMap.put(
                busId,
                new StorageBusTracker(
                    busId,
                    inputBus,
                    hostTile,
                    baseTile.getFrontFacing()
                        .ordinal(),
                    StorageType.ITEM).withTarget(targetId, StorageBusSource.GREGTECH));
        }
    }

    private void scanInputHatchMachines(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap,
        int contentLimit) {
        for (IGridNode node : grid.getMachines(MTEHatchInputME.class)) {
            if (!node.isActive()) continue;

            Object machine = node.getMachine();
            if (!(machine instanceof MTEHatchInputME inputHatch)) continue;

            IGregTechTileEntity baseTile = getBaseTile(inputHatch);
            TileEntity hostTile = (TileEntity) baseTile;
            if (hostTile == null) continue;

            long busId = StorageBusDataHandler.createBusId(
                hostTile,
                baseTile.getFrontFacing()
                    .ordinal(),
                StorageType.FLUID.ordinal() + BusRole.IMPORT.ordinal() * 16);
            NBTTagCompound busData = StorageBusDataHandler
                .createGregTechInputHatchData(inputHatch, busId, StorageType.FLUID, BusRole.IMPORT, contentLimit);
            applyCapabilities(busData, false, false, true);
            applySlotParameters(busData, MTEHatchInputME.SLOT_COUNT, 0, MTEHatchInputME.SLOT_COUNT);
            out.appendTag(busData);
            StorageBusId targetId = StorageBusId.of(
                hostTile,
                baseTile.getFrontFacing()
                    .ordinal(),
                BusRole.IMPORT,
                StorageType.FLUID);
            trackerMap.put(
                busId,
                new StorageBusTracker(
                    busId,
                    inputHatch,
                    hostTile,
                    baseTile.getFrontFacing()
                        .ordinal(),
                    StorageType.FLUID).withTarget(targetId, StorageBusSource.GREGTECH));
        }
    }

    private IGregTechTileEntity getBaseTile(MetaTileEntity metaTileEntity) {
        if (!(metaTileEntity.getBaseMetaTileEntity() instanceof IGregTechTileEntity base)) return null;

        return base;
    }
}
