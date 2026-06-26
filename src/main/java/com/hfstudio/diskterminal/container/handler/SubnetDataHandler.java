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

import com.hfstudio.diskterminal.integration.subnet.SubnetScannerRegistry;
import com.hfstudio.diskterminal.network.PacketSubnetPartitionAction.Action;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IInterfaceHost;
import appeng.parts.misc.PartInterface;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.misc.TileInterface;
import appeng.util.IterationCounter;

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
        public IGrid targetGrid;
        public final List<Object> connectionParts; // Storage Buses and Interfaces that connect to this subnet
        public final List<TileEntity> hostTiles;
        public final List<Boolean> isOutbound; // Whether each connection is outbound vs inbound
        public final List<ForgeDirection> connectionSides; // The facing of each connection

        public SubnetTracker(long id) {
            this.id = id;
            this.targetGrid = null;
            this.connectionParts = new ArrayList<>();
            this.hostTiles = new ArrayList<>();
            this.isOutbound = new ArrayList<>();
            this.connectionSides = new ArrayList<>();
        }

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

        /**
         * Write connection info to NBT (for client sync).
         */
        public void writeConnectionsToNBT(NBTTagCompound nbt) {
            nbt.setInteger("connectionCount", connectionParts.size());
            // Connection details can be expanded later if needed for GUI display
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

    public static boolean handleSubnetPartitionAction(Map<Long, SubnetTracker> trackerMap, long subnetId, long pos,
        int side, Action action, int partitionSlot, NBTTagCompound stackData) {
        SubnetTracker tracker = trackerMap.get(subnetId);
        if (tracker == null) return false;

        PartStorageBus bus = findBusForConnection(tracker, pos, side);
        if (bus == null) return false;

        IAEStackInventory config = bus.getAEInventoryByName(StorageName.CONFIG);
        if (config == null) return false;

        IAEStack<?> partitionStack = AEStackUtil.readPartitionStack(stackData, bus.getStackType());

        switch (action) {
            case ADD_ITEM:
                if (partitionSlot >= 0 && partitionSlot < config.getSizeInventory() && partitionStack != null) {
                    setConfigSlot(config, partitionSlot, partitionStack);
                }
                break;
            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < config.getSizeInventory())
                    setConfigSlot(config, partitionSlot, null);
                break;
            case TOGGLE_ITEM:
                togglePartitionStack(config, partitionStack);
                break;
            case CLEAR_ALL:
                clearConfig(config);
                break;
            case SET_ALL_FROM_CONTENTS:
                setPartitionFromBusContents(config, bus);
                break;
            case SET_ALL_FROM_SUBNET_INVENTORY:
                setPartitionFromSubnetInventory(config, bus, tracker.targetGrid);
                break;
        }

        TileEntity hostTile = bus.getHost() == null ? null
            : bus.getHost()
                .getTile();
        if (hostTile != null) hostTile.markDirty();

        return true;
    }

    private static PartStorageBus findBusForConnection(SubnetTracker tracker, long pos, int side) {
        for (int i = 0; i < tracker.connectionParts.size(); i++) {
            Object part = tracker.connectionParts.get(i);
            TileEntity hostTile = i < tracker.hostTiles.size() ? tracker.hostTiles.get(i) : null;
            boolean outbound = i < tracker.isOutbound.size() && tracker.isOutbound.get(i);
            ForgeDirection connectionSide = i < tracker.connectionSides.size() ? tracker.connectionSides.get(i) : null;

            if (outbound && part instanceof PartStorageBus) {
                PartStorageBus bus = (PartStorageBus) part;
                TileEntity busHost = bus.getHost() == null ? null
                    : bus.getHost()
                        .getTile();
                if (matchesConnection(busHost, bus.getSide(), pos, side)) return bus;
            } else if (!outbound && hostTile != null && connectionSide != null) {
                if (matchesConnection(hostTile, connectionSide, pos, side)) {
                    return findAdjacentBus(hostTile, connectionSide);
                }
            }
        }

        return null;
    }

    private static boolean matchesConnection(TileEntity hostTile, ForgeDirection side, long pos, int sideOrdinal) {
        if (hostTile == null || side == null) return false;

        return PosUtil.toLong(hostTile.xCoord, hostTile.yCoord, hostTile.zCoord) == pos
            && side.ordinal() == sideOrdinal;
    }

    private static PartStorageBus findAdjacentBus(TileEntity hostTile, ForgeDirection connectionSide) {
        TileEntity targetTile = hostTile.getWorldObj()
            .getTileEntity(
                hostTile.xCoord + connectionSide.offsetX,
                hostTile.yCoord + connectionSide.offsetY,
                hostTile.zCoord + connectionSide.offsetZ);
        if (!(targetTile instanceof IPartHost)) return null;

        IPart part = ((IPartHost) targetTile).getPart(connectionSide.getOpposite());
        return part instanceof PartStorageBus ? (PartStorageBus) part : null;
    }

    private static void togglePartitionStack(IAEStackInventory config, IAEStack<?> stack) {
        if (stack == null) return;

        int existing = findStackInConfig(config, stack);
        if (existing >= 0) {
            setConfigSlot(config, existing, null);
            return;
        }

        int empty = findEmptySlot(config);
        if (empty >= 0) setConfigSlot(config, empty, stack);
    }

    private static int findStackInConfig(IAEStackInventory config, IAEStack<?> stack) {
        if (stack == null) return -1;

        for (int i = 0; i < config.getSizeInventory(); i++) {
            IAEStack<?> slotStack = config.getAEStackInSlot(i);
            if (slotStack != null && slotStack.isSameType(stack)) return i;
        }

        return -1;
    }

    private static int findEmptySlot(IAEStackInventory config) {
        for (int i = 0; i < config.getSizeInventory(); i++) {
            if (config.getAEStackInSlot(i) == null) return i;
        }

        return -1;
    }

    private static void clearConfig(IAEStackInventory config) {
        for (int i = 0; i < config.getSizeInventory(); i++) setConfigSlot(config, i, null);
    }

    private static void setConfigSlot(IAEStackInventory config, int slot, IAEStack<?> stack) {
        if (stack != null) stack.setStackSize(1);
        config.putAEStackInSlot(slot, stack);
        config.markDirty();
    }

    private static void setPartitionFromBusContents(IAEStackInventory config, PartStorageBus bus) {
        clearConfig(config);

        IMEInventoryHandler<?> handler = bus.getInternalHandler();
        if (handler == null) return;

        IAEStackType<?> type = handler.getStackType();
        if (type == null) return;

        fillFromHandler(config, handler, type);
    }

    private static void setPartitionFromSubnetInventory(IAEStackInventory config, PartStorageBus bus,
        IGrid subnetGrid) {
        clearConfig(config);
        if (subnetGrid == null) return;

        IStorageGrid storageGrid;
        try {
            storageGrid = subnetGrid.getCache(IStorageGrid.class);
        } catch (RuntimeException e) {
            return;
        }

        if (storageGrid == null) return;

        IAEStackType<?> type = bus.getStackType();
        IMEMonitor<?> monitor = storageGrid.getMEMonitor(type);
        if (monitor == null) return;

        fillFromMonitor(config, monitor, type);
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> void fillFromHandler(IAEStackInventory config,
        IMEInventoryHandler<?> handler, IAEStackType<?> type) {
        fillFromHandlerForType(config, (IMEInventoryHandler<T>) handler, (IAEStackType<T>) type);
    }

    private static <T extends IAEStack<T>> void fillFromHandlerForType(IAEStackInventory config,
        IMEInventoryHandler<T> handler, IAEStackType<T> type) {
        IItemList<T> contents = type.createList();
        handler.getAvailableItems(contents, IterationCounter.fetchNewId());
        fillFromList(config, contents);
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> void fillFromMonitor(IAEStackInventory config, IMEMonitor<?> monitor,
        IAEStackType<?> type) {
        fillFromMonitorForType(config, (IMEMonitor<T>) monitor, (IAEStackType<T>) type);
    }

    private static <T extends IAEStack<T>> void fillFromMonitorForType(IAEStackInventory config, IMEMonitor<T> monitor,
        IAEStackType<T> type) {
        IItemList<T> contents = type.createList();
        monitor.getAvailableItems(contents, IterationCounter.fetchNewId());
        fillFromList(config, contents);
    }

    private static <T extends IAEStack<T>> void fillFromList(IAEStackInventory config, IItemList<T> contents) {
        int slot = 0;
        for (T stack : contents) {
            if (slot >= config.getSizeInventory()) break;
            if (stack == null || stack.getStackSize() <= 0 || ItemStacks.isEmpty(AEStackUtil.getDisplayStack(stack))) {
                continue;
            }

            T partitionStack = stack.copy();
            partitionStack.setStackSize(1);
            setConfigSlot(config, slot++, partitionStack);
        }
    }

    /**
     * Actions that can be performed on subnets.
     */
    public enum SubnetAction {

        RENAME,
        TOGGLE_FAVORITE
    }
}
