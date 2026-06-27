package com.hfstudio.diskterminal.storagebus.runtime;

import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.storagebus.model.StorageBusId;

import gregtech.api.metatileentity.MetaTileEntity;

/**
 * Handle wrapping a freshly resolved GregTech ME input bus/hatch meta tile. Only the storage bus
 * domain's GT capabilities use {@link #getMetaTileEntity()}.
 */
public class GTStorageBusHandle implements StorageBusHandle {

    private final StorageBusId id;
    private final MetaTileEntity metaTileEntity;
    private final TileEntity hostTile;

    public GTStorageBusHandle(StorageBusId id, MetaTileEntity metaTileEntity, TileEntity hostTile) {
        this.id = id;
        this.metaTileEntity = metaTileEntity;
        this.hostTile = hostTile;
    }

    @Override
    public StorageBusId getId() {
        return id;
    }

    public MetaTileEntity getMetaTileEntity() {
        return metaTileEntity;
    }

    public TileEntity getHostTile() {
        return hostTile;
    }
}
