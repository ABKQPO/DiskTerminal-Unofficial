package com.hfstudio.diskterminal.integration.storagebus;

import java.util.Map;

import net.minecraft.nbt.NBTTagList;

import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.integration.Mods;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import thaumicenergistics.common.parts.PartEssentiaExportBus;
import thaumicenergistics.common.parts.PartEssentiaImportBus;

public class ThaumicEnergisticsBusScanner extends AbstractStorageBusScanner {

    public static final ThaumicEnergisticsBusScanner INSTANCE = new ThaumicEnergisticsBusScanner();

    private ThaumicEnergisticsBusScanner() {}

    @Override
    public String getId() {
        return "thaumicenergistics";
    }

    @Override
    public boolean isAvailable() {
        return Mods.ThaumicEnergistics.isModLoaded();
    }

    @Override
    public void scanStorageBuses(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap,
        int contentLimit) {
        if (grid == null) return;

        for (IGridNode node : grid.getMachines(PartEssentiaImportBus.class)) {
            if (!node.isActive()) continue;

            appendSharedBus((PartEssentiaImportBus) node.getMachine(), BusRole.IMPORT, out, contentLimit, trackerMap);
        }

        for (IGridNode node : grid.getMachines(PartEssentiaExportBus.class)) {
            if (!node.isActive()) continue;

            appendSharedBus((PartEssentiaExportBus) node.getMachine(), BusRole.EXPORT, out, contentLimit, trackerMap);
        }
    }
}
