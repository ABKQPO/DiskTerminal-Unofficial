package com.hfstudio.diskterminal.storagebus.model;

import java.util.List;

import com.hfstudio.diskterminal.api.scanner.ScanResult;

/**
 * Immutable {@link ScanResult} backed by a fixed list of entries.
 *
 * @param <T> the entry type
 */
public class SimpleScanResult<T> implements ScanResult<T> {

    private final List<T> entries;

    public SimpleScanResult(List<T> entries) {
        this.entries = entries;
    }

    @Override
    public List<T> getEntries() {
        return entries;
    }
}
