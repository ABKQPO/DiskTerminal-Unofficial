package com.hfstudio.diskterminal.storagebus.runtime;

import java.util.Optional;

import com.hfstudio.diskterminal.storagebus.model.StorageBusId;

/**
 * Locates the current real object for a {@link StorageBusId} and wraps it in a {@link StorageBusHandle}.
 * The world is derived from the id's dimension, so resolution happens fresh on every call and object
 * lifetime details (chunk unload, replacement) never leak into the rest of the system.
 */
public interface StorageBusResolver {

    /**
     * Resolve the bus identified by {@code id}, if it currently exists and is valid.
     */
    Optional<StorageBusHandle> resolve(StorageBusId id);
}
