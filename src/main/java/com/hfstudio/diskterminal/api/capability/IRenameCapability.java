package com.hfstudio.diskterminal.api.capability;

/**
 * Rename behavior for a target that can carry a custom display name.
 */
public interface IRenameCapability extends ICapability {

    /**
     * Whether this target currently supports renaming.
     */
    boolean canRename();

    /**
     * The current custom name, or an empty string when none is set.
     */
    String getCustomName();

    /**
     * Apply a new custom name.
     */
    void rename(String newName);

    /**
     * Clear any custom name, reverting to the default display name.
     */
    void clearCustomName();
}
