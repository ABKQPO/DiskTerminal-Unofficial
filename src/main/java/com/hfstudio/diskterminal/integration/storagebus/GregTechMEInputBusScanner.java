package com.hfstudio.diskterminal.integration.storagebus;

import java.util.Map;

import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.integration.GregTechIntegration;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineAdapters;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineDiscovery;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

public class GregTechMEInputBusScanner extends AbstractStorageBusScanner {

    public static final GregTechMEInputBusScanner INSTANCE = new GregTechMEInputBusScanner();

    private final GTMachineDiscovery discovery = new GTMachineDiscovery();

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
        if (grid == null) {
            return;
        }

        for (IGridNode node : discovery.discover(grid)) {
            IGregTechTileEntity baseTile = resolveBaseTile(node.getMachine());
            if (baseTile == null) {
                continue;
            }

            MetaTileEntity metaTileEntity = resolveMetaTileEntity(baseTile);
            if (metaTileEntity == null) {
                continue;
            }

            TileEntity hostTile = baseTile instanceof TileEntity tile ? tile : null;
            if (hostTile == null) {
                continue;
            }

            GTMachineAdapters.registry()
                .find(metaTileEntity)
                .flatMap(adapter -> adapter.scan(node, metaTileEntity, baseTile, hostTile, contentLimit))
                .ifPresent(result -> {
                    out.appendTag(result.busData());
                    trackerMap.put(result.tracker().id, result.tracker());
                });
        }
    }

    private IGregTechTileEntity resolveBaseTile(Object machine) {
        if (machine instanceof IGregTechTileEntity baseTile) {
            return baseTile;
        }

        if (machine instanceof MetaTileEntity metaTileEntity
            && metaTileEntity.getBaseMetaTileEntity() instanceof IGregTechTileEntity baseTile) {
            return baseTile;
        }

        return null;
    }

    private MetaTileEntity resolveMetaTileEntity(IGregTechTileEntity baseTile) {
        return baseTile.getMetaTileEntity() instanceof MetaTileEntity metaTileEntity ? metaTileEntity : null;
    }
}
