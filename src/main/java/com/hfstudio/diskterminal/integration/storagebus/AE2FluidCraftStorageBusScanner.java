package com.hfstudio.diskterminal.integration.storagebus;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.glodblock.github.common.parts.PartFluidStorageBus;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.integration.Mods;

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
    public void scanStorageBuses(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap) {
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
