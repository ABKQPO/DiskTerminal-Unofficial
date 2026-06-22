package com.hfstudio.diskterminal.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * API contract for subnet proxies.
 */
public interface ISubnetProxy extends IFilterHost {

    /**
     * Whether this proxy lives on the main-network side of the connection.
     */
    boolean isOutboundConnection();

    /**
     * Stable facing used when a single side must be selected.
     */
    @Nonnull
    ForgeDirection getPrimaryFacing();

    /**
     * The grid reached through the proxy.
     */
    @Nullable
    Object getTargetGrid();

    /**
     * Display stack representing the linked peer.
     */
    ItemStack getRemoteDisplayStack();
}
