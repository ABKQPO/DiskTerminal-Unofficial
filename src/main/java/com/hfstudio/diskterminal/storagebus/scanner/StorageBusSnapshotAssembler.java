package com.hfstudio.diskterminal.storagebus.scanner;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.diskterminal.api.capability.ICapabilityProvider;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusCapabilityProviderRegistry;

/**
 * Writes capability availability into each bus read-model entry. Availability is computed once on the
 * server by asking the freshly registered provider which capabilities it can currently resolve, so the
 * client can decide what to show without an RPC round-trip or any concrete type knowledge.
 * <p>
 * The metadata is advisory for the GUI only; the server still re-checks the capability before executing
 * any action.
 */
public class StorageBusSnapshotAssembler {

    public static final String CAPABILITIES_KEY = "availableCapabilities";

    /**
     * Append capability metadata to every bus entry in {@code busList} for which a provider exists.
     */
    public void writeCapabilityMetadata(NBTTagList busList, Map<Long, StorageBusTracker> trackerMap,
        StorageBusCapabilityProviderRegistry registry) {
        for (int i = 0; i < busList.tagCount(); i++) {
            NBTTagCompound busData = busList.getCompoundTagAt(i);
            StorageBusTracker tracker = trackerMap.get(busData.getLong("id"));
            if (tracker == null || tracker.targetId == null) continue;

            Set<ResourceLocation> capabilities = resolveCapabilities(tracker.targetId, registry);
            writeCapabilities(busData, capabilities);
        }
    }

    private Set<ResourceLocation> resolveCapabilities(StorageBusId id, StorageBusCapabilityProviderRegistry registry) {
        return registry.find(id)
            .map(ICapabilityProvider::availableCapabilities)
            .orElseGet(Collections::emptySet);
    }

    private void writeCapabilities(NBTTagCompound busData, Set<ResourceLocation> capabilities) {
        NBTTagList list = new NBTTagList();
        for (ResourceLocation capability : capabilities) {
            list.appendTag(new NBTTagString(capability.toString()));
        }

        busData.setTag(CAPABILITIES_KEY, list);
    }
}
