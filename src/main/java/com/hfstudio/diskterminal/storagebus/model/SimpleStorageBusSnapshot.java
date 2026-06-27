package com.hfstudio.diskterminal.storagebus.model;

import java.util.List;

import com.hfstudio.diskterminal.api.snapshot.CapabilityMetadata;
import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;

/**
 * Immutable {@link StorageBusSnapshot} implementation.
 */
public class SimpleStorageBusSnapshot implements StorageBusSnapshot {

    private final StorageBusId id;
    private final String displayName;
    private final int priority;
    private final List<FilterSnapshot> filters;
    private final CapabilityMetadata capabilityMetadata;

    public SimpleStorageBusSnapshot(StorageBusId id, String displayName, int priority, List<FilterSnapshot> filters,
        CapabilityMetadata capabilityMetadata) {
        this.id = id;
        this.displayName = displayName;
        this.priority = priority;
        this.filters = filters;
        this.capabilityMetadata = capabilityMetadata;
    }

    @Override
    public StorageBusId getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public List<FilterSnapshot> getFilters() {
        return filters;
    }

    @Override
    public CapabilityMetadata getCapabilityMetadata() {
        return capabilityMetadata;
    }
}
