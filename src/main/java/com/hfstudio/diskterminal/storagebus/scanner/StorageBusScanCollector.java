package com.hfstudio.diskterminal.storagebus.scanner;

import java.util.Map;

import net.minecraft.nbt.NBTTagList;

import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusCapabilityProviderRegistry;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusProviderFactory;

import appeng.api.networking.IGrid;

/**
 * Collects scanner output and registers a capability provider per discovered bus. Scanners only
 * discover objects and produce read-model NBT plus a tracker carrying the bus's stable identity and
 * source family; provider creation and registration belong here, in the runtime layer.
 */
public class StorageBusScanCollector {

    private final StorageBusProviderFactory providerFactory = new StorageBusProviderFactory();

    /**
     * Run all registered scanners, populate the tracker map and bus NBT, then (re)build the provider
     * registry from the freshly discovered trackers.
     *
     * @return the bus read-model NBT list for the client
     */
    public NBTTagList collect(IGrid grid, Map<Long, StorageBusTracker> trackerMap,
        StorageBusCapabilityProviderRegistry registry, int contentLimit) {
        NBTTagList busList = StorageBusDataHandler.collectStorageBuses(grid, trackerMap, contentLimit);

        registry.clear();
        for (StorageBusTracker tracker : trackerMap.values()) {
            if (tracker.targetId == null || tracker.source == null) continue;

            registry.register(providerFactory.create(tracker.targetId, tracker.source));
        }

        return busList;
    }
}
