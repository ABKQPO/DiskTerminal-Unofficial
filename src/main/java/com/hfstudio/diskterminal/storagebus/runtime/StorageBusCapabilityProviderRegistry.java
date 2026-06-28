package com.hfstudio.diskterminal.storagebus.runtime;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.hfstudio.diskterminal.api.capability.ICapabilityProvider;
import com.hfstudio.diskterminal.api.capability.ICapabilityProviderRegistry;
import com.hfstudio.diskterminal.api.identity.TargetId;

/**
 * Per-container registry of storage bus capability providers, keyed by {@link TargetId}. Rebuilt on each
 * scan; providers are short-lived and never outlive the container's view of the network.
 */
public class StorageBusCapabilityProviderRegistry implements ICapabilityProviderRegistry {

    private final Map<TargetId, ICapabilityProvider<?>> providers = new LinkedHashMap<>();

    @Override
    public void register(ICapabilityProvider<?> provider) {
        if (provider == null) return;

        providers.put(provider.getTargetId(), provider);
    }

    @Override
    public Optional<ICapabilityProvider<?>> find(TargetId id) {
        return Optional.ofNullable(providers.get(id));
    }

    @Override
    public void unregister(TargetId id) {
        providers.remove(id);
    }

    /**
     * Drop all registered providers. Called before a fresh scan repopulates the registry.
     */
    public void clear() {
        providers.clear();
    }

    /**
     * Remove any providers whose target ids are not present in the latest scan result while keeping
     * reusable providers for still-live targets.
     */
    public void retainOnly(Set<? extends TargetId> activeIds) {
        if (activeIds == null || activeIds.isEmpty()) {
            providers.clear();
            return;
        }

        Set<TargetId> staleIds = new LinkedHashSet<>(providers.keySet());
        staleIds.removeAll(activeIds);
        for (TargetId staleId : staleIds) {
            providers.remove(staleId);
        }
    }
}
