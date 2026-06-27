package com.hfstudio.diskterminal.storagebus.model;

import java.util.Set;

import net.minecraft.util.ResourceLocation;

import com.hfstudio.diskterminal.api.snapshot.CapabilityMetadata;

/**
 * Immutable {@link CapabilityMetadata} backed by a fixed set of capability ids.
 */
public class SimpleCapabilityMetadata implements CapabilityMetadata {

    private final Set<ResourceLocation> capabilities;

    public SimpleCapabilityMetadata(Set<ResourceLocation> capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public Set<ResourceLocation> getAvailableCapabilities() {
        return capabilities;
    }
}
