package com.hfstudio.diskterminal.storagebus.runtime;

import net.minecraft.util.ResourceLocation;

import com.hfstudio.diskterminal.DiskTerminal;

/**
 * Action identifiers used by the unified capability action packet. Action ids are namespaced by their
 * capability and carried as {@link ResourceLocation}s on the wire.
 */
public class StorageBusActionIds {

    public static final ResourceLocation RENAME_SET_NAME = new ResourceLocation(DiskTerminal.MODID, "set_name");
    public static final ResourceLocation RENAME_CLEAR_NAME = new ResourceLocation(DiskTerminal.MODID, "clear_name");

    public static final ResourceLocation PRIORITY_SET_VALUE = new ResourceLocation(DiskTerminal.MODID, "set_value");

    public static final ResourceLocation FILTER_SET_SLOT = new ResourceLocation(DiskTerminal.MODID, "set_slot");
    public static final ResourceLocation FILTER_CLEAR_SLOT = new ResourceLocation(DiskTerminal.MODID, "clear_slot");
    public static final ResourceLocation FILTER_CLEAR_ALL = new ResourceLocation(DiskTerminal.MODID, "clear_all");
    public static final ResourceLocation FILTER_TOGGLE = new ResourceLocation(DiskTerminal.MODID, "toggle");
    public static final ResourceLocation FILTER_FILL_FROM_PREVIEW = new ResourceLocation(
        DiskTerminal.MODID,
        "fill_from_preview");

    private StorageBusActionIds() {}
}
