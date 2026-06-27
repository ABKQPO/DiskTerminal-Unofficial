package com.hfstudio.diskterminal.storagebus.runtime;

import net.minecraft.tileentity.TileEntity;

import com.glodblock.github.common.tile.TileSuperStockReplenisher;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;

/**
 * Handle wrapping a freshly resolved AE2FluidCraft Super Stock Replenisher tile.
 */
public class StockReplenisherHandle implements StorageBusHandle {

    private final StorageBusId id;
    private final TileSuperStockReplenisher tile;

    public StockReplenisherHandle(StorageBusId id, TileSuperStockReplenisher tile) {
        this.id = id;
        this.tile = tile;
    }

    @Override
    public StorageBusId getId() {
        return id;
    }

    public TileSuperStockReplenisher getTile() {
        return tile;
    }

    public TileEntity getHostTile() {
        return tile;
    }
}
