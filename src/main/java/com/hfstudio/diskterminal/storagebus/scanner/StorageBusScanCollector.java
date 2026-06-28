package com.hfstudio.diskterminal.storagebus.scanner;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusCapabilityProviderRegistry;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusProviderFactory;

import appeng.api.networking.IGrid;

/**
 * Collects storage bus read-model NBT and rebuilds the capability-provider registry. Scanners only
 * discover objects and emit NBT plus a tracker carrying the bus's stable identity and source
 * family; provider creation and registration belong here, in the runtime layer.
 */
public class StorageBusScanCollector {

    private final StorageBusProviderFactory providerFactory = new StorageBusProviderFactory();

    /**
     * Run all registered scanners, populate the tracker map and bus NBT, and rebuild the provider
     * registry.
     */
    public NBTTagList collect(IGrid grid, Map<Long, StorageBusTracker> trackerMap,
        StorageBusCapabilityProviderRegistry registry, int contentLimit) {
        NBTTagList busList = StorageBusDataHandler.collectStorageBuses(grid, trackerMap, contentLimit);
        Set<StorageBusId> activeIds = new LinkedHashSet<>();

        for (StorageBusTracker tracker : trackerMap.values()) {
            if (tracker.targetId == null || tracker.source == null) continue;

            activeIds.add(tracker.targetId);
            if (registry.find(tracker.targetId)
                .isEmpty()) {
                registry.register(providerFactory.create(tracker.targetId, tracker.source));
            }

            tracker.availableCapabilities = registry.find(tracker.targetId)
                .map(provider -> provider.availableCapabilities())
                .orElseGet(Collections::emptySet);
        }
        registry.retainOnly(activeIds);

        for (int i = 0; i < busList.tagCount(); i++) {
            NBTTagCompound busData = busList.getCompoundTagAt(i);
            StorageBusTracker tracker = trackerMap.get(busData.getLong("id"));
            if (tracker == null) continue;
            tracker.hasPartitionConfigured = StorageBusDataHandler.readAndStripPartitionSummary(busData);
            tracker.hasConnectedContents = StorageBusDataHandler.readAndStripContentSummary(busData);
            tracker.partitionSummaryKnown = true;
            tracker.contentSummaryKnown = true;
        }

        return busList;
    }
}
