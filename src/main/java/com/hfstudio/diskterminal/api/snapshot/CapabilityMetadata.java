package com.hfstudio.diskterminal.api.snapshot;

import java.util.Set;

import net.minecraft.util.ResourceLocation;

/**
 * Capability availability carried alongside a {@link Snapshot} so the client can decide what to show or
 * disable without an RPC round-trip and without inspecting concrete runtime types.
 * <p>
 * The server must still re-check the actual capability before executing any behavior.
 */
public interface CapabilityMetadata {

    /**
     * The set of capability ids currently available for the snapshotted target.
     */
    Set<ResourceLocation> getAvailableCapabilities();
}
