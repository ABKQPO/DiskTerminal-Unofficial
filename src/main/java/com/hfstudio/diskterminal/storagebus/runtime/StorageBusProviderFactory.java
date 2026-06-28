package com.hfstudio.diskterminal.storagebus.runtime;

import com.hfstudio.diskterminal.api.capability.ICapabilityProvider;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.provider.AEStorageBusProvider;
import com.hfstudio.diskterminal.storagebus.provider.GTStorageBusProvider;
import com.hfstudio.diskterminal.storagebus.provider.GenericItemBusProvider;
import com.hfstudio.diskterminal.storagebus.provider.StockReplenisherProvider;

/**
 * Creates the capability provider for a storage bus from its identity and source family. Provider
 * creation belongs to the runtime layer, never to a scanner. Each provider is paired with the resolver
 * that knows how to re-locate that source's real object.
 */
public class StorageBusProviderFactory {

    private final StorageBusResolver aeResolver = new AEStorageBusResolver();
    private final StorageBusResolver gtResolver = new GTStorageBusResolver();
    private final StorageBusResolver stockReplenisherResolver = new StockReplenisherResolver();

    /**
     * Build a provider for the given identity and source family.
     */
    public ICapabilityProvider<StorageBusId> create(StorageBusId id, StorageBusSource source) {
        return switch (source) {
            case AE_STORAGE_BUS -> new AEStorageBusProvider(id, aeResolver);
            case AE_SHARED_BUS -> new GenericItemBusProvider(id, aeResolver);
            case GREGTECH -> new GTStorageBusProvider(id, gtResolver);
            case MIXED_CONFIG_TARGET -> new StockReplenisherProvider(id, stockReplenisherResolver);
        };
    }
}
