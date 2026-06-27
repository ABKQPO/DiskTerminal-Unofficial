package com.hfstudio.diskterminal.storagebus.model;

/**
 * Binds a descriptor and snapshot for the same bus so descriptor/snapshot pairs can never drift apart.
 * A scan entry is the unit a scanner produces.
 */
public record StorageBusScanEntry(StorageBusDescriptor descriptor, StorageBusSnapshot snapshot) {

    /**
     * Convenience accessor returning the bound identity (both descriptor and snapshot agree on it).
     */
    public StorageBusId id() {
        return descriptor.getId();
    }
}
