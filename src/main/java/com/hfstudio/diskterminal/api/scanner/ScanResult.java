package com.hfstudio.diskterminal.api.scanner;

import java.util.List;

/**
 * Result of a scan: a single list of bound entries rather than parallel descriptor/snapshot lists, so
 * indices can never drift apart.
 *
 * @param <T> the entry type produced by the scan
 */
public interface ScanResult<T> {

    /**
     * The scanned entries.
     */
    List<T> getEntries();
}
