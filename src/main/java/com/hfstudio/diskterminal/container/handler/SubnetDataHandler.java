package com.hfstudio.diskterminal.container.handler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IInterfaceHost;
import appeng.parts.misc.PartInterface;
import appeng.tile.misc.TileInterface;

import com.hfstudio.diskterminal.integration.subnet.SubnetScannerRegistry;
import com.hfstudio.diskterminal.util.PosUtil;

/**
 * Server-side handler for subnet data collection and management.
 * <p>
 * A subnet is a separate ME grid that connects to the main network via Storage Bus -&gt; Interface
 * pattern (ME Passthrough). This allows the main network to access the subnet's storage without
 * merging the grids.
 */
public class SubnetDataHandler {

    /**
     * Session-scoped set of favorited subnet ids (see {@link #handleToggleFavorite}).
     */
    private static final Set<Long> FAVORITED_SUBNETS = new HashSet<>();

    /**
     * Tracker for subnet instances on the server side.
     * Stores references to the subnet's grid and connection points.
     */
    public static class SubnetTracker {

        public final long id;
        public final IGrid targetGrid;
        public final List<Object> connectionParts; // Storage Buses and Interfaces that connect to this subnet
        public final List<TileEntity> hostTiles;
        public final List<Boolean> isOutbound; // Whether each connection is outbound vs inbound
        public final List<ForgeDirection> connectionSides; // The facing of each connection

        public SubnetTracker(long id, IGrid targetGrid) {
            this.id = id;
            this.targetGrid = targetGrid;
            this.connectionParts = new ArrayList<>();
            this.hostTiles = new ArrayList<>();
            this.isOutbound = new ArrayList<>();
            this.connectionSides = new ArrayList<>();
        }

        /**
         * Add an outbound connection (Storage Bus on main -&gt; Interface on subnet).
         */
        public void addConnection(Object part, TileEntity hostTile) {
            connectionParts.add(part);
            hostTiles.add(hostTile);
            isOutbound.add(true);
            connectionSides.add(null);
        }

        /**
         * Add an inbound connection (Interface on main &lt;- Storage Bus on subnet).
         */
        public void addInboundConnection(Object part, TileEntity hostTile, ForgeDirection side) {
            connectionParts.add(part);
            hostTiles.add(hostTile);
            isOutbound.add(false);
            connectionSides.add(side);
        }
    }

    /**
     * Collect all subnets connected to the given grid.
     *
     * @param grid       The main ME network grid to scan
     * @param trackerMap Map to populate with subnet trackers (keyed by subnet ID)
     * @param playerId   The player ID for security permission checks
     * @param slotLimit  Maximum number of inventory item types to include per subnet
     * @return NBTTagList containing all subnet data
     */
    public static NBTTagList collectSubnets(IGrid grid, Map<Long, SubnetTracker> trackerMap, int playerId,
        int slotLimit) {
        NBTTagList subnetList = new NBTTagList();

        if (grid == null) return subnetList;

        SubnetScannerRegistry.scanAll(grid, subnetList, trackerMap, playerId, slotLimit);

        return subnetList;
    }

    /**
     * Create a unique subnet ID from the grid's identity.
     * Uses System.identityHashCode combined with primary position for stability.
     */
    public static long createSubnetId(IGrid grid, IGridNode primaryNode) {
        if (grid == null) return 0;

        long gridHash = System.identityHashCode(grid);

        if (primaryNode != null && primaryNode.getGridBlock() != null) {
            DimensionalCoord loc = primaryNode.getGridBlock()
                .getLocation();
            if (loc != null) {
                gridHash ^= PosUtil.toLong(loc.x, loc.y, loc.z);
                gridHash ^= ((long) loc.getWorld().provider.dimensionId) << 48;
            }
        }

        return gridHash;
    }

    /**
     * Handle subnet action from client (rename, favorite toggle).
     */
    public static boolean handleSubnetAction(Map<Long, SubnetTracker> trackerMap, long subnetId, SubnetAction action,
        NBTTagCompound data, EntityPlayer player) {
        SubnetTracker tracker = trackerMap.get(subnetId);
        if (tracker == null) return false;

        return switch (action) {
            case RENAME -> handleRename(tracker, data.getString("name"));
            case TOGGLE_FAVORITE -> handleToggleFavorite(tracker, data.getBoolean("favorite"));
        };
    }

    private static boolean handleRename(SubnetTracker tracker, String newName) {
        IInterfaceHost interfaceHost = findPrimaryInterfaceHost(tracker.targetGrid);
        if (interfaceHost == null) return false;

        if (!(interfaceHost instanceof ICustomNameObject)) return false;

        ICustomNameObject nameable = (ICustomNameObject) interfaceHost;

        if (newName == null || newName.trim()
            .isEmpty()) {
            nameable.setCustomName(null);
        } else {
            nameable.setCustomName(newName.trim());
        }

        TileEntity tile = interfaceHost.getTileEntity();
        if (tile != null) tile.markDirty();

        return true;
    }

    private static boolean handleToggleFavorite(SubnetTracker tracker, boolean favorite) {
        IInterfaceHost interfaceHost = findPrimaryInterfaceHost(tracker.targetGrid);
        if (interfaceHost == null) return false;

        TileEntity tile = interfaceHost.getTileEntity();
        if (tile == null) return false;

        // 1.7.10 Forge in this toolchain does not expose TileEntity#getTileData(), so favorites are
        // tracked in a server-side set keyed by subnet id. This persists for the session; a future
        // WorldSavedData-backed store can replace this without touching callers.
        if (favorite) {
            FAVORITED_SUBNETS.add(tracker.id);
        } else {
            FAVORITED_SUBNETS.remove(tracker.id);
        }

        tile.markDirty();

        return true;
    }

    /**
     * Whether the given subnet id is currently favorited (session-scoped).
     */
    public static boolean isFavorited(long subnetId) {
        return FAVORITED_SUBNETS.contains(subnetId);
    }

    /**
     * Find the primary interface host (TileInterface or PartInterface) in a subnet's grid.
     * This is the interface where we store subnet metadata (name, favorite).
     */
    public static IInterfaceHost findPrimaryInterfaceHost(IGrid grid) {
        if (grid == null) return null;

        for (IGridNode node : grid.getMachines(TileInterface.class)) {
            if (node.getMachine() instanceof TileInterface) {
                return (IInterfaceHost) node.getMachine();
            }
        }

        for (IGridNode node : grid.getMachines(PartInterface.class)) {
            if (node.getMachine() instanceof PartInterface) {
                return (IInterfaceHost) node.getMachine();
            }
        }

        return null;
    }

    /**
     * Actions that can be performed on subnets.
     */
    public enum SubnetAction {

        RENAME,
        TOGGLE_FAVORITE
    }
}
