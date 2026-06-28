package com.hfstudio.diskterminal.storagebus.runtime;

import net.minecraft.tileentity.TileEntity;

import com.glodblock.github.common.tile.TileSuperStockReplenisher;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;

import gregtech.api.metatileentity.MetaTileEntity;

/**
 * Handle wrapping a freshly resolved mixed configuration target.
 */
public class StockReplenisherHandle implements StorageBusHandle {

    private final StorageBusId id;
    private final Object target;
    private final TileEntity hostTile;

    public StockReplenisherHandle(StorageBusId id, Object target, TileEntity hostTile) {
        this.id = id;
        this.target = target;
        this.hostTile = hostTile;
    }

    @Override
    public StorageBusId getId() {
        return id;
    }

    public Object getTarget() {
        return target;
    }

    public TileSuperStockReplenisher getTile() {
        return target instanceof TileSuperStockReplenisher replenisher ? replenisher : null;
    }

    public MetaTileEntity getMetaTileEntity() {
        return target instanceof MetaTileEntity metaTileEntity ? metaTileEntity : null;
    }

    public TileEntity getHostTile() {
        return hostTile;
    }
}
