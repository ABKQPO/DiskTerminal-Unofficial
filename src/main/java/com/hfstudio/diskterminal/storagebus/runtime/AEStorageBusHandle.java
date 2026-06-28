package com.hfstudio.diskterminal.storagebus.runtime;

import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.storagebus.model.StorageBusId;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

/**
 * Handle wrapping a freshly resolved AE2 part-based storage bus. Only the storage bus domain's AE
 * capabilities use {@link #getPart()}; the rest of the system sees only {@link StorageBusHandle}.
 */
public class AEStorageBusHandle implements StorageBusHandle {

    private final StorageBusId id;
    private final IPart part;
    private final IPartHost host;
    private final TileEntity hostTile;

    public AEStorageBusHandle(StorageBusId id, IPart part, IPartHost host, TileEntity hostTile) {
        this.id = id;
        this.part = part;
        this.host = host;
        this.hostTile = hostTile;
    }

    @Override
    public StorageBusId getId() {
        return id;
    }

    public IPart getPart() {
        return part;
    }

    public IPartHost getHost() {
        return host;
    }

    public TileEntity getHostTile() {
        return hostTile;
    }
}
