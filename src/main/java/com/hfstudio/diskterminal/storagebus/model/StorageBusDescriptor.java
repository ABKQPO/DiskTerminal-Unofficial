package com.hfstudio.diskterminal.storagebus.model;

import net.minecraftforge.common.util.ForgeDirection;

import com.hfstudio.diskterminal.api.descriptor.Descriptor;
import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;

/**
 * Static description of a storage bus: <em>what it is</em>. Carries only stable location and
 * presentation data, never priority, custom name or filter state.
 */
public interface StorageBusDescriptor extends Descriptor<StorageBusId> {

    /**
     * Side of the host the bus is attached to.
     */
    ForgeDirection getSide();

    /**
     * Role of the bus (storage / import / export).
     */
    BusRole getRole();

    /**
     * Storage channel the bus operates on.
     */
    StorageType getStorageType();
}
