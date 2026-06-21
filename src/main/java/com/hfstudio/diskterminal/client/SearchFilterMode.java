package com.hfstudio.diskterminal.client;

/**
 * Enum representing the search filter mode for the Cell Terminal.
 * Determines whether to search in inventory contents, partition filters, or both.
 */
public enum SearchFilterMode {

    /**
     * Search in cell inventory contents only (grey dot).
     */
    INVENTORY,

    /**
     * Search in cell partition filters only (orange dot).
     */
    PARTITION,

    /**
     * Search in both inventory and partition (split diagonal dot).
     */
    MIXED;

    /**
     * Cycle to the next mode in order: INVENTORY -> PARTITION -> MIXED -> INVENTORY
     */
    public SearchFilterMode next() {
        return switch (this) {
            case INVENTORY -> PARTITION;
            case PARTITION -> MIXED;
            default -> INVENTORY;
        };
    }

    /**
     * Parse a mode from its name string.
     *
     * @param name The name to parse
     * @return The matching mode, or MIXED if not found
     */
    public static SearchFilterMode fromName(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MIXED;
        }
    }
}
