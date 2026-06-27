package com.hfstudio.diskterminal.storagebus.runtime;

import com.hfstudio.diskterminal.storagebus.model.StorageBusId;

/**
 * Opaque reference to a freshly resolved storage bus. A handle is produced by a
 * {@link StorageBusResolver} at execution time and lives only for the duration of a single action, so
 * no long-lived runtime object is ever retained. Concrete handle types hide the underlying AE part or
 * GT meta tile from the rest of the system.
 */
public interface StorageBusHandle {

    /**
     * Identity of the resolved bus.
     */
    StorageBusId getId();
}
