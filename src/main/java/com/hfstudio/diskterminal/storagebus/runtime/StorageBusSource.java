package com.hfstudio.diskterminal.storagebus.runtime;

/**
 * Source family a storage bus belongs to. Used by the provider factory to build the matching provider
 * and resolver. The action chain never branches on this value; it is only consulted once, at scan time,
 * when constructing providers.
 */
public enum StorageBusSource {

    /** AE2 part-based storage bus ({@code PartStorageBus} and subclasses). */
    AE_STORAGE_BUS,

    /** AE2 shared import/export bus ({@code PartSharedItemBus}), including fluid and essentia variants. */
    AE_SHARED_BUS,

    /** GregTech ME input bus/hatch meta tile. */
    GREGTECH
}
