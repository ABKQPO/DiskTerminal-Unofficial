package com.hfstudio.diskterminal.storagebus.runtime;

import net.minecraft.util.ResourceLocation;

import com.hfstudio.diskterminal.DiskTerminal;

/**
 * Capability identifiers for the storage bus domain. These ids appear in {@code CapabilityMetadata} and
 * in the unified capability action packet, keeping the GUI and network layers free of concrete type
 * knowledge.
 */
public class StorageBusCapabilityIds {

    public static final ResourceLocation RENAME = new ResourceLocation(DiskTerminal.MODID, "rename");
    public static final ResourceLocation PRIORITY = new ResourceLocation(DiskTerminal.MODID, "priority");
    public static final ResourceLocation FILTER = new ResourceLocation(DiskTerminal.MODID, "filter");

    private StorageBusCapabilityIds() {}
}
