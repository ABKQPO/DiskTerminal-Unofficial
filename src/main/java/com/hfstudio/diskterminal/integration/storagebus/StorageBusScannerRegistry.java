package com.hfstudio.diskterminal.integration.storagebus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.IGrid;

import com.hfstudio.diskterminal.DiskTerminal;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;

/**
 * Registry for storage bus scanners.
 */
public final class StorageBusScannerRegistry {

    private static final List<IStorageBusScanner> scanners = new ArrayList<>();

    private StorageBusScannerRegistry() {}

    public static void register(IStorageBusScanner scanner) {
        if (scanner == null) {
            DiskTerminal.LOG.warn("Attempted to register null storage bus scanner");
            return;
        }
        scanners.add(scanner);
        DiskTerminal.LOG.info("Registered storage bus scanner: {}", scanner.getId());
    }

    public static void scanAll(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap) {
        for (IStorageBusScanner scanner : scanners) {
            if (!scanner.isAvailable()) continue;
            try {
                scanner.scanStorageBuses(grid, out, trackerMap);
            } catch (Exception e) {
                DiskTerminal.LOG.error("Error scanning storage buses with {}: {}", scanner.getId(), e.getMessage());
            }
        }
    }
}
