package com.hfstudio.diskterminal.integration.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagList;

import com.hfstudio.diskterminal.DiskTerminal;
import com.hfstudio.diskterminal.container.handler.CellDataHandler;

import appeng.api.networking.IGrid;

/**
 * Registry for storage scanners.
 * Allows mods to register their own storage device types for scanning.
 * <p>
 * Built-in scanners for AE2 (ME Drive, ME Chest) are registered automatically.
 * Optional mod integrations register their scanners during init if the mods are present.
 */
public final class StorageScannerRegistry {

    private static final List<IStorageScanner> scanners = new ArrayList<>();

    private StorageScannerRegistry() {}

    /**
     * Register a storage scanner. Scanners are called in registration order.
     *
     * @param scanner the scanner to register
     */
    public static void register(IStorageScanner scanner) {
        if (scanner == null) {
            DiskTerminal.LOG.warn("Attempted to register null storage scanner");

            return;
        }

        scanners.add(scanner);
        DiskTerminal.LOG.info("Registered storage scanner: {}", scanner.getId());
    }

    /**
     * Scan all storage devices from all registered scanners with a slot limit.
     *
     * @param grid        the ME network grid to scan
     * @param storageList the list to append storage data to
     * @param callback    callback to register storage trackers
     * @param slotLimit   maximum number of item types to include per cell
     */
    public static void scanAllStorages(IGrid grid, NBTTagList storageList,
        CellDataHandler.StorageTrackerCallback callback, int slotLimit) {
        for (IStorageScanner scanner : scanners) {
            if (!scanner.isAvailable()) continue;

            try {
                scanner.scanStorages(grid, storageList, callback, slotLimit);
            } catch (Exception e) {
                DiskTerminal.LOG.error("Error scanning storage with {}: {}", scanner.getId(), e.getMessage());
            }
        }
    }
}
