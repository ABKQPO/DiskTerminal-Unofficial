package com.hfstudio.diskterminal.integration.storagebus;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusSource;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.parts.automation.PartExportBus;
import appeng.parts.automation.PartImportBus;
import appeng.parts.misc.PartStorageBus;

/**
 * Scanner for AE2 item storage buses.
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
    public void scanStorageBuses(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap,
        int contentLimit) {
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
            NBTTagCompound nbt = StorageBusDataHandler.createItemStorageBusData(bus, busId, contentLimit);
            applyCapabilities(nbt);
            applySlotParameters(nbt);
            out.appendTag(nbt);
            StorageBusId targetId = StorageBusId.of(
                hostTile,
                bus.getSide()
                    .ordinal(),
                BusRole.STORAGE,
                StorageType.ITEM);
            trackerMap.put(
                busId,
                new StorageBusTracker(
                    busId,
                    bus,
                    hostTile,
                    bus.getSide()
                        .ordinal(),
                    StorageType.ITEM).withTarget(targetId, StorageBusSource.AE_STORAGE_BUS));
        }

        for (IGridNode gn : grid.getMachines(PartImportBus.class)) {
            if (!gn.isActive()) continue;

            appendSharedBus((PartImportBus) gn.getMachine(), BusRole.IMPORT, out, contentLimit, trackerMap);
        }

        for (IGridNode gn : grid.getMachines(PartExportBus.class)) {
            if (!gn.isActive()) continue;

            appendSharedBus((PartExportBus) gn.getMachine(), BusRole.EXPORT, out, contentLimit, trackerMap);
        }
    }
}
