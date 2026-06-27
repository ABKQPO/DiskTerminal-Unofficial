package com.hfstudio.diskterminal.integration.storagebus;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.glodblock.github.common.parts.PartFluidExportBus;
import com.glodblock.github.common.parts.PartFluidImportBus;
import com.glodblock.github.common.parts.PartFluidStorageBus;
import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.integration.Mods;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusSource;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

public class AE2FluidCraftStorageBusScanner extends AbstractStorageBusScanner {

    public static final AE2FluidCraftStorageBusScanner INSTANCE = new AE2FluidCraftStorageBusScanner();

    private AE2FluidCraftStorageBusScanner() {}

    @Override
    public String getId() {
        return "ae2fc";
    }

    @Override
    public boolean isAvailable() {
        return Mods.AE2FluidCraft.isModLoaded();
    }

    @Override
    public void scanStorageBuses(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap,
        int contentLimit) {
        if (grid == null) return;

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
            NBTTagCompound nbt = StorageBusDataHandler.createFluidStorageBusData(bus, busId, contentLimit);
            applyCapabilities(nbt);
            applySlotParameters(nbt);
            out.appendTag(nbt);
            StorageBusId targetId = StorageBusId.of(
                hostTile,
                bus.getSide()
                    .ordinal(),
                BusRole.STORAGE,
                StorageType.FLUID);
            trackerMap.put(
                busId,
                new StorageBusTracker(
                    busId,
                    bus,
                    hostTile,
                    bus.getSide()
                        .ordinal(),
                    StorageType.FLUID).withTarget(targetId, StorageBusSource.AE_STORAGE_BUS));
        }

        for (IGridNode gn : grid.getMachines(PartFluidImportBus.class)) {
            if (!gn.isActive()) continue;

            appendSharedBus((PartFluidImportBus) gn.getMachine(), BusRole.IMPORT, out, contentLimit, trackerMap);
        }

        for (IGridNode gn : grid.getMachines(PartFluidExportBus.class)) {
            if (!gn.isActive()) continue;

            appendSharedBus((PartFluidExportBus) gn.getMachine(), BusRole.EXPORT, out, contentLimit, trackerMap);
        }
    }
}
