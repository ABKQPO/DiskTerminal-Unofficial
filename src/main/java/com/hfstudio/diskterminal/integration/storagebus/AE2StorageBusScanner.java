package com.hfstudio.diskterminal.integration.storagebus;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.glodblock.github.common.parts.PartFluidStorageBus;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.parts.misc.PartStorageBus;

/**
 * Scanner for AE2 item storage buses and AE2FluidCraft fluid storage buses.
 * <p>
 * In this fork the fluid storage bus is a subclass of {@link PartStorageBus}; both share the same
 * data-generation path, distinguished only by their stack type.
 */
public class AE2StorageBusScanner extends AbstractStorageBusScanner {

    public static final AE2StorageBusScanner INSTANCE = new AE2StorageBusScanner();

    private AE2StorageBusScanner() {}

    @Override
    public String getId() {
        return "appliedenergistics2";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void scanStorageBuses(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap) {
        if (grid == null) return;

        // Item storage buses (exact PartStorageBus class registration)
        for (IGridNode gn : grid.getMachines(PartStorageBus.class)) {
            if (!gn.isActive()) continue;
            PartStorageBus bus = (PartStorageBus) gn.getMachine();
            TileEntity hostTile = bus.getHost()
                .getTile();
            if (hostTile == null) continue;

            long busId = StorageBusDataHandler.createBusId(
                hostTile,
                bus.getSide()
                    .ordinal(),
                StorageType.ITEM.ordinal());
            NBTTagCompound nbt = StorageBusDataHandler.createItemStorageBusData(bus, busId);
            applyCapabilities(nbt);
            applySlotParameters(nbt);
            out.appendTag(nbt);
            trackerMap.put(
                busId,
                new StorageBusTracker(
                    busId,
                    bus,
                    hostTile,
                    bus.getSide()
                        .ordinal(),
                    StorageType.ITEM));
        }

        // Fluid storage buses (AE2FluidCraft)
        for (IGridNode gn : grid.getMachines(PartFluidStorageBus.class)) {
            if (!gn.isActive()) continue;
            PartFluidStorageBus bus = (PartFluidStorageBus) gn.getMachine();
            TileEntity hostTile = bus.getHost()
                .getTile();
            if (hostTile == null) continue;

            long busId = StorageBusDataHandler.createBusId(
                hostTile,
                bus.getSide()
                    .ordinal(),
                StorageType.FLUID.ordinal());
            NBTTagCompound nbt = StorageBusDataHandler.createFluidStorageBusData(bus, busId);
            applyCapabilities(nbt);
            applySlotParameters(nbt);
            out.appendTag(nbt);
            trackerMap.put(
                busId,
                new StorageBusTracker(
                    busId,
                    bus,
                    hostTile,
                    bus.getSide()
                        .ordinal(),
                    StorageType.FLUID));
        }
    }
}
