package com.hfstudio.diskterminal.api;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.inventory.IInventory;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * API contract for interface views.
 */
public interface IInterfaceHost extends IFilterHost {

    /**
     * Resource channel represented by this interface view.
     */
    @Nonnull
    ResourceType getResourceType();

    /**
     * Whether this view exports from the ME network into adjacent targets.
     */
    boolean isExport();

    /**
     * Whether this host is part of a paired import/export I/O view and needs
     * direction-based disambiguation when displayed.
     */
    boolean isDirectionalView();

    /**
     * Stable facing used when a single side must be selected.
     */
    @Nonnull
    ForgeDirection getPrimaryFacing();

    /**
     * All facings currently exposed by this interface view.
     */
    @Nonnull
    Collection<ForgeDirection> getTargetFacings();

    /**
     * Preview entries visible through the primary facing.
     */
    @Nonnull
    List<ResourcePreviewEntry> getPreviewEntries(int limit);

    /**
     * Preview entries visible through a specific facing.
     */
    @Nonnull
    List<ResourcePreviewEntry> getPreviewEntries(@Nonnull ForgeDirection facing, int limit);

    /**
     * Whether this host represents one resource type within a multi-type combined interface
     * and needs a resource-type prefix label when displayed.
     */
    default boolean isTypeLabeled() {
        return false;
    }

    /**
     * Upgrade inventory for this specific interface direction, or {@code null} if not applicable.
     */
    @Nullable
    default IInventory getUpgradeInventory() {
        return null;
    }
}
