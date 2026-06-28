package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

import java.util.LinkedHashSet;
import java.util.Set;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

public class GTMachineDiscovery {

    public Set<IGridNode> discover(IGrid grid) {
        Set<IGridNode> nodes = new LinkedHashSet<>();
        if (grid == null) {
            return nodes;
        }

        for (IGridNode node : grid.getNodes()) {
            if (node != null) {
                nodes.add(node);
            }
        }

        return nodes;
    }
}
