package com.hfstudio.diskterminal.api;

import javax.annotation.Nonnull;

import net.minecraft.inventory.IInventory;

/**
 * API contract for devices with a mutable upgrade inventory.
 */
public interface IUpgradeable {

    /**
     * Upgrade inventory exposed by this device.
     */
    @Nonnull
    IInventory getUpgradeInventory();
}
