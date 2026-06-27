package com.hfstudio.diskterminal.api.capability;

import java.util.Optional;

import com.hfstudio.diskterminal.api.identity.TargetId;

/**
 * Server-side runtime resolution entry point for capability providers. A provider knows its own
 * {@link TargetId}, so registration takes only the provider and never an id/provider pair that could
 * disagree.
 */
public interface ICapabilityProviderRegistry {

    /**
     * Register a provider, keyed by its own {@link ICapabilityProvider#getTargetId()}.
     */
    void register(ICapabilityProvider<?> provider);

    /**
     * Find the provider currently registered for the given identity, if any.
     */
    Optional<ICapabilityProvider<?>> find(TargetId id);

    /**
     * Drop the provider registered for the given identity.
     */
    void unregister(TargetId id);
}
