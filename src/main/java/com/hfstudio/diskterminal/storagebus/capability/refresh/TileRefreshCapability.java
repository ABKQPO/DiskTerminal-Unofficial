package com.hfstudio.diskterminal.storagebus.capability.refresh;

import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.api.capability.IRefreshCapability;

/**
 * Server-internal refresh capability backed by the bus host tile. {@link #markDirty()} persists the
 * tile; {@link #requestRefresh()} regenerating the client read model is driven by the action handler
 * that owns the container, so this implementation only marks the tile and is a safe no-op for refresh.
 */
public class TileRefreshCapability implements IRefreshCapability {

    private final TileEntity hostTile;

    public TileRefreshCapability(TileEntity hostTile) {
        this.hostTile = hostTile;
    }

    @Override
    public void markDirty() {
        if (hostTile != null) hostTile.markDirty();
    }

    @Override
    public void requestRefresh() {
        // The container-side refresh is requested by the capability action handler that has the
        // container; the capability layer only persists the underlying tile here.
    }
}
