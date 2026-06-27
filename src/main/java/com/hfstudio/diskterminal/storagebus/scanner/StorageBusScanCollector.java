package com.hfstudio.diskterminal.storagebus.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hfstudio.diskterminal.api.scanner.ScanResult;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.storagebus.model.SimpleScanResult;
import com.hfstudio.diskterminal.storagebus.model.StorageBusScanEntry;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusCapabilityProviderRegistry;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusProviderFactory;

import appeng.api.networking.IGrid;

/**
 * Collects scanner output into bound {@link StorageBusScanEntry} entries and registers a capability
 * provider per discovered bus. Scanners only discover objects and produce read-model NBT plus a
 * tracker carrying the bus's stable identity and source family; provider creation, registration and
 * descriptor/snapshot assembly belong here, in the runtime layer.
 */
public class StorageBusScanCollector {

    private final StorageBusProviderFactory providerFactory = new StorageBusProviderFactory();
    private final StorageBusSnapshotAssembler assembler = new StorageBusSnapshotAssembler();

    /**
     * Holds both products of a scan: the read-model NBT sent to the client and the assembled entries.
     */
    public record CollectResult(NBTTagList busList, ScanResult<StorageBusScanEntry> entries) {}

    /**
     * Run all registered scanners, populate the tracker map and bus NBT, rebuild the provider registry,
     * and assemble one {@link StorageBusScanEntry} per discovered bus.
     */
    public CollectResult collect(IGrid grid, Map<Long, StorageBusTracker> trackerMap,
        StorageBusCapabilityProviderRegistry registry, int contentLimit) {
        NBTTagList busList = StorageBusDataHandler.collectStorageBuses(grid, trackerMap, contentLimit);

        registry.clear();
        for (StorageBusTracker tracker : trackerMap.values()) {
            if (tracker.targetId == null || tracker.source == null) continue;

            registry.register(providerFactory.create(tracker.targetId, tracker.source));
        }

        List<StorageBusScanEntry> entries = new ArrayList<>();
        for (int i = 0; i < busList.tagCount(); i++) {
            NBTTagCompound busData = busList.getCompoundTagAt(i);
            StorageBusTracker tracker = trackerMap.get(busData.getLong("id"));
            if (tracker == null) continue;
            tracker.hasConnectedContents = busData.hasKey("contents") && busData.getTagList("contents", 10)
                .tagCount() > 0;

            StorageBusScanEntry entry = assembler.assemble(tracker, busData, registry);
            if (entry != null) entries.add(entry);
        }

        return new CollectResult(busList, new SimpleScanResult<>(entries));
    }
}
