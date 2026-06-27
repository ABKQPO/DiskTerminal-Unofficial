package com.hfstudio.diskterminal.storagebus.model;

import java.util.List;

import com.hfstudio.diskterminal.api.snapshot.CapabilityMetadata;
import com.hfstudio.diskterminal.api.snapshot.FilterSnapshot;
import com.hfstudio.diskterminal.api.snapshot.Snapshot;

/**
 * Read model of a storage bus: <em>what should currently be displayed</em>. Carries the display name,
 * priority, filters and capability availability. Holds no runtime reference and resolves no behavior.
 */
public interface StorageBusSnapshot extends Snapshot<StorageBusId> {

    /**
     * Name to display for the bus.
     */
    String getDisplayName();

    /**
     * Current priority value.
     */
    int getPriority();

    /**
     * Current filter entries.
     */
    List<FilterSnapshot> getFilters();

    /**
     * Capability availability for the bus.
     */
    CapabilityMetadata getCapabilityMetadata();
}
