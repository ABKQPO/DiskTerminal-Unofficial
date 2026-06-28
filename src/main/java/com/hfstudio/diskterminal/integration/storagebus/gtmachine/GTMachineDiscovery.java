package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

import java.util.Collections;
import java.util.Iterator;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.util.IReadOnlyCollection;

public class GTMachineDiscovery {

    public Iterable<IGridNode> discover(IGrid grid) {
        return grid == null ? EmptyNodeCollection.INSTANCE : grid.getNodes();
    }

    private enum EmptyNodeCollection implements IReadOnlyCollection<IGridNode> {

        INSTANCE;

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean contains(Object node) {
            return false;
        }

        @Override
        public Iterator<IGridNode> iterator() {
            return Collections.emptyIterator();
        }
    }
}
