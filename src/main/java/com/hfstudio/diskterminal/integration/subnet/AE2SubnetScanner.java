package com.hfstudio.diskterminal.integration.subnet;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.hfstudio.diskterminal.container.handler.SubnetDataHandler.SubnetTracker;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.parts.misc.PartInterface;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.misc.TileInterface;
import appeng.util.IterationCounter;

/**
 * Scanner for detecting subnets connected via Storage Bus -> Interface pattern in 1.7.10.
 * Adapted from 1.12 version to use 1.7.10 AE2-Unofficial API.
 */
public class AE2SubnetScanner extends AbstractSubnetScanner {

    public static final AE2SubnetScanner INSTANCE = new AE2SubnetScanner();

    private AE2SubnetScanner() {}

    @Override
    public String getId() {
        return "appliedenergistics2";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void scanSubnets(IGrid grid, NBTTagList out, Map<Long, SubnetTracker> trackerMap, int playerId,
        int slotLimit) {
        if (grid == null) return;

        // Temporary map to group connections by target subnet grid
        Map<IGrid, SubnetTracker> subnetsByGrid = new HashMap<>();

        // Scan for outbound connections: our Storage Buses -> remote Interfaces
        scanStorageBuses(grid, subnetsByGrid, playerId);

        // Scan for inbound connections: our Interfaces <- remote Storage Buses
        scanInboundConnections(grid, subnetsByGrid, playerId);

        // Convert to NBT and populate tracker map
        for (Map.Entry<IGrid, SubnetTracker> entry : subnetsByGrid.entrySet()) {
            IGrid subnetGrid = entry.getKey();
            SubnetTracker tracker = entry.getValue();
            tracker.targetGrid = subnetGrid;

            NBTTagCompound subnetNbt = createSubnetNBT(subnetGrid, tracker, playerId, slotLimit);
            out.appendTag(subnetNbt);
            trackerMap.put(tracker.id, tracker);
        }
    }

    /**
     * Scan for outbound connections where our Storage Bus points at a remote Interface.
     */
    private void scanStorageBuses(IGrid mainGrid, Map<IGrid, SubnetTracker> subnetsByGrid, int playerId) {
        // Scan all storage buses on the main grid
        for (IGridNode node : mainGrid.getMachines(PartStorageBus.class)) {
            if (!node.isActive()) continue;

            PartStorageBus bus = (PartStorageBus) node.getMachine();
            IPartHost host = bus.getHost();
            if (host == null) continue;

            TileEntity hostTile = host.getTile();
            if (hostTile == null) continue;

            // Get the direction the storage bus is facing
            ForgeDirection facing = bus.getSide();
            if (facing == null || facing == ForgeDirection.UNKNOWN) continue;

            // Get the target tile the storage bus is pointing at
            World world = hostTile.getWorldObj();
            int targetX = hostTile.xCoord + facing.offsetX;
            int targetY = hostTile.yCoord + facing.offsetY;
            int targetZ = hostTile.zCoord + facing.offsetZ;
            TileEntity targetTile = world.getTileEntity(targetX, targetY, targetZ);
            if (targetTile == null) continue;

            // Check if target is an interface from a different grid
            IGrid targetGrid = getGridFromTile(targetTile);
            if (targetGrid == null || targetGrid == mainGrid) continue;

            // Found a subnet connection!
            SubnetTracker tracker = getOrCreateTracker(subnetsByGrid, targetGrid);
            tracker.addConnection(bus, hostTile);
        }
    }

    /**
     * Scan for inbound connections where a remote Storage Bus points at our Interface.
     */
    private void scanInboundConnections(IGrid mainGrid, Map<IGrid, SubnetTracker> subnetsByGrid, int playerId) {
        // Scan full-block TileInterface
        for (IGridNode node : mainGrid.getMachines(TileInterface.class)) {
            if (!node.isActive()) continue;

            TileInterface iface = (TileInterface) node.getMachine();
            TileEntity ifaceTile = iface.getTileEntity();
            if (ifaceTile == null) continue;

            // Check all 6 adjacent sides for remote storage buses
            scanAdjacentForRemoteBus(ifaceTile, mainGrid, subnetsByGrid, iface);
        }

        // Scan cable-attached PartInterface
        for (IGridNode node : mainGrid.getMachines(PartInterface.class)) {
            if (!node.isActive()) continue;

            PartInterface iface = (PartInterface) node.getMachine();
            TileEntity ifaceTile = iface.getTile();
            if (ifaceTile == null) continue;

            // PartInterface faces one specific direction
            ForgeDirection facing = iface.getSide();
            if (facing == null || facing == ForgeDirection.UNKNOWN) continue;

            // Check the adjacent tile in that direction
            World world = ifaceTile.getWorldObj();
            int adjX = ifaceTile.xCoord + facing.offsetX;
            int adjY = ifaceTile.yCoord + facing.offsetY;
            int adjZ = ifaceTile.zCoord + facing.offsetZ;
            TileEntity adjacentTile = world.getTileEntity(adjX, adjY, adjZ);
            if (adjacentTile == null) continue;

            IGrid remoteGrid = checkForRemoteStorageBus(adjacentTile, facing.getOpposite(), mainGrid);
            if (remoteGrid != null) {
                SubnetTracker tracker = getOrCreateTracker(subnetsByGrid, remoteGrid);
                tracker.addInboundConnection(iface, ifaceTile, facing);
            }
        }
    }

    /**
     * Check all 6 sides of a tile for remote storage buses.
     */
    private void scanAdjacentForRemoteBus(TileEntity centerTile, IGrid mainGrid,
        Map<IGrid, SubnetTracker> subnetsByGrid, Object interfacePart) {
        World world = centerTile.getWorldObj();

        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            int adjX = centerTile.xCoord + dir.offsetX;
            int adjY = centerTile.yCoord + dir.offsetY;
            int adjZ = centerTile.zCoord + dir.offsetZ;
            TileEntity adjacentTile = world.getTileEntity(adjX, adjY, adjZ);
            if (adjacentTile == null) continue;

            IGrid remoteGrid = checkForRemoteStorageBus(adjacentTile, dir.getOpposite(), mainGrid);
            if (remoteGrid != null) {
                SubnetTracker tracker = getOrCreateTracker(subnetsByGrid, remoteGrid);
                tracker.addInboundConnection(interfacePart, centerTile, dir);
            }
        }
    }

    /**
     * Check if a tile has a Storage Bus on the given side that belongs to a different grid.
     */
    private IGrid checkForRemoteStorageBus(TileEntity tile, ForgeDirection side, IGrid mainGrid) {
        if (!(tile instanceof IPartHost host)) return null;

        IPart part = host.getPart(side);
        if (!(part instanceof PartStorageBus bus)) return null;

        IGridNode busNode = bus.getGridNode();
        if (busNode == null || busNode.getGrid() == null) return null;

        IGrid busGrid = busNode.getGrid();
        if (busGrid == mainGrid) return null;

        return busGrid;
    }

    @Override
    protected NBTTagCompound createSubnetNBT(IGrid subnetGrid, SubnetTracker tracker, int playerId, int slotLimit) {
        NBTTagCompound nbt = super.createSubnetNBT(subnetGrid, tracker, playerId, slotLimit);
        nbt.setTag("inventory", collectSubnetInventory(subnetGrid, slotLimit));

        NBTTagList connections = new NBTTagList();
        for (int i = 0; i < tracker.connectionParts.size(); i++) {
            Object part = tracker.connectionParts.get(i);
            TileEntity hostTile = i < tracker.hostTiles.size() ? tracker.hostTiles.get(i) : null;
            boolean outbound = i < tracker.isOutbound.size() && tracker.isOutbound.get(i);
            ForgeDirection side = i < tracker.connectionSides.size() ? tracker.connectionSides.get(i) : null;
            NBTTagCompound connection = createConnectionNBT(part, hostTile, outbound, side, slotLimit);
            if (connection != null) connections.appendTag(connection);
        }

        nbt.setTag("connections", connections);
        return nbt;
    }

    private NBTTagList collectSubnetInventory(IGrid subnetGrid, int slotLimit) {
        NBTTagList inventory = new NBTTagList();
        IStorageGrid storageGrid;
        try {
            storageGrid = subnetGrid.getCache(IStorageGrid.class);
        } catch (RuntimeException e) {
            return inventory;
        }

        if (storageGrid == null) return inventory;

        int count = 0;
        for (IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
            if (count >= slotLimit) break;

            IMEMonitor<?> monitor = storageGrid.getMEMonitor(type);
            if (monitor == null) continue;

            count = appendMonitorContents(inventory, monitor, type, slotLimit, count);
        }

        return inventory;
    }

    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> int appendMonitorContents(NBTTagList out, IMEMonitor<?> monitor,
        IAEStackType<?> type, int slotLimit, int startCount) {
        return appendContents(out, (IMEMonitor<T>) monitor, (IAEStackType<T>) type, slotLimit, startCount);
    }

    private <T extends IAEStack<T>> int appendContents(NBTTagList out, IMEMonitor<T> monitor, IAEStackType<T> type,
        int slotLimit, int startCount) {
        IItemList<T> list = type.createList();
        monitor.getAvailableItems(list, IterationCounter.fetchNewId());

        int count = startCount;
        for (T stack : list) {
            if (count >= slotLimit) break;
            if (stack.getStackSize() <= 0) continue;

            ItemStack displayStack = AEStackUtil.getDisplayStack(stack);
            if (ItemStacks.isEmpty(displayStack)) continue;

            ItemStack single = displayStack.copy();
            single.stackSize = 1;
            NBTTagCompound stackNbt = new NBTTagCompound();
            single.writeToNBT(stackNbt);
            AEStackUtil.writeStackToNBT(stackNbt, stack);
            stackNbt.setLong("Cnt", stack.getStackSize());
            out.appendTag(stackNbt);
            count++;
        }

        return count;
    }

    private NBTTagCompound createConnectionNBT(Object part, TileEntity hostTile, boolean outbound,
        ForgeDirection connectionSide, int slotLimit) {
        if (hostTile == null || hostTile.getWorldObj() == null) return null;

        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("pos", PosUtil.toLong(hostTile.xCoord, hostTile.yCoord, hostTile.zCoord));
        nbt.setInteger("dim", hostTile.getWorldObj().provider.dimensionId);
        nbt.setBoolean("outbound", outbound);
        nbt.setBoolean("usesSubnetInventory", !outbound);

        if (part instanceof PartStorageBus bus) {
            ForgeDirection side = bus.getSide();
            nbt.setInteger("side", side == null ? 0 : side.ordinal());
            writeStackType(nbt, bus);
            writeIcon(
                nbt,
                "localIcon",
                AEApi.instance()
                    .definitions()
                    .parts()
                    .storageBus()
                    .maybeStack(1)
                    .orNull());
            writeRemoteIcon(nbt, hostTile, side);
            addStorageBusFilter(nbt, bus);
            addStorageBusContents(nbt, bus, slotLimit);
            return nbt;
        }

        if (part instanceof TileInterface) {
            nbt.setInteger("side", connectionSide == null ? 0 : connectionSide.ordinal());
            writeInboundStackType(nbt, hostTile, connectionSide);
            writeIcon(
                nbt,
                "localIcon",
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .iface()
                    .maybeStack(1)
                    .orNull());
            writeRemoteIcon(nbt, hostTile, connectionSide);
            addInboundFilter(nbt, hostTile, connectionSide);
            return nbt;
        }

        if (part instanceof PartInterface iface) {
            ForgeDirection side = connectionSide == null ? iface.getSide() : connectionSide;
            nbt.setInteger("side", side == null ? 0 : side.ordinal());
            writeInboundStackType(nbt, hostTile, side);
            writeIcon(
                nbt,
                "localIcon",
                AEApi.instance()
                    .definitions()
                    .parts()
                    .iface()
                    .maybeStack(1)
                    .orNull());
            writeRemoteIcon(nbt, hostTile, side);
            addInboundFilter(nbt, hostTile, side);
            return nbt;
        }

        return null;
    }

    private void addStorageBusContents(NBTTagCompound nbt, PartStorageBus bus, int slotLimit) {
        IMEInventoryHandler<?> handler = bus.getInternalHandler();
        if (handler == null) return;

        IAEStackType<?> type = handler.getStackType();
        if (type == null) return;

        NBTTagList contents = new NBTTagList();
        appendHandlerContents(contents, handler, type, slotLimit, 0);
        nbt.setTag("content", contents);
    }

    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> int appendHandlerContents(NBTTagList out, IMEInventoryHandler<?> handler,
        IAEStackType<?> type, int slotLimit, int startCount) {
        return appendHandlerContentsForType(
            out,
            (IMEInventoryHandler<T>) handler,
            (IAEStackType<T>) type,
            slotLimit,
            startCount);
    }

    private <T extends IAEStack<T>> int appendHandlerContentsForType(NBTTagList out, IMEInventoryHandler<T> handler,
        IAEStackType<T> type, int slotLimit, int startCount) {
        IItemList<T> list = type.createList();
        handler.getAvailableItems(list, IterationCounter.fetchNewId());

        int count = startCount;
        for (T stack : list) {
            if (count >= slotLimit) break;
            if (stack.getStackSize() <= 0) continue;

            ItemStack displayStack = AEStackUtil.getDisplayStack(stack);
            if (ItemStacks.isEmpty(displayStack)) continue;

            ItemStack single = displayStack.copy();
            single.stackSize = 1;
            NBTTagCompound stackNbt = new NBTTagCompound();
            single.writeToNBT(stackNbt);
            AEStackUtil.writeStackToNBT(stackNbt, stack);
            stackNbt.setLong("Cnt", stack.getStackSize());
            out.appendTag(stackNbt);
            count++;
        }

        return count;
    }

    private void addStorageBusFilter(NBTTagCompound nbt, PartStorageBus bus) {
        IAEStackInventory config = bus.getAEInventoryByName(StorageName.CONFIG);
        if (config == null) return;

        NBTTagList filter = new NBTTagList();
        for (int i = 0; i < config.getSizeInventory(); i++) {
            NBTTagCompound itemNbt = new NBTTagCompound();
            IAEStack<?> stack = config.getAEStackInSlot(i);
            if (stack != null) {
                ItemStack displayStack = AEStackUtil.getDisplayStack(stack);
                if (!ItemStacks.isEmpty(displayStack)) {
                    ItemStack single = displayStack.copy();
                    single.stackSize = 1;
                    single.writeToNBT(itemNbt);
                }
                AEStackUtil.writeStackToNBT(itemNbt, stack);
            }
            filter.appendTag(itemNbt);
        }

        nbt.setTag("filter", filter);
        nbt.setInteger("maxPartitionSlots", config.getSizeInventory());
    }

    private void addInboundFilter(NBTTagCompound nbt, TileEntity hostTile, ForgeDirection connectionSide) {
        PartStorageBus bus = findAdjacentStorageBus(hostTile, connectionSide);
        if (bus != null) addStorageBusFilter(nbt, bus);
    }

    private void writeInboundStackType(NBTTagCompound nbt, TileEntity hostTile, ForgeDirection connectionSide) {
        PartStorageBus bus = findAdjacentStorageBus(hostTile, connectionSide);
        if (bus != null) writeStackType(nbt, bus);
    }

    private void writeStackType(NBTTagCompound nbt, PartStorageBus bus) {
        IAEStackType<?> type = bus.getStackType();
        if (type != null) nbt.setString("stackType", type.getId());
    }

    private PartStorageBus findAdjacentStorageBus(TileEntity hostTile, ForgeDirection connectionSide) {
        if (hostTile == null || connectionSide == null) return null;

        TileEntity targetTile = hostTile.getWorldObj()
            .getTileEntity(
                hostTile.xCoord + connectionSide.offsetX,
                hostTile.yCoord + connectionSide.offsetY,
                hostTile.zCoord + connectionSide.offsetZ);
        if (!(targetTile instanceof IPartHost)) return null;

        IPart part = ((IPartHost) targetTile).getPart(connectionSide.getOpposite());
        return part instanceof PartStorageBus ? (PartStorageBus) part : null;
    }

    private void writeRemoteIcon(NBTTagCompound nbt, TileEntity hostTile, ForgeDirection side) {
        if (hostTile == null || side == null) return;

        TileEntity targetTile = hostTile.getWorldObj()
            .getTileEntity(
                hostTile.xCoord + side.offsetX,
                hostTile.yCoord + side.offsetY,
                hostTile.zCoord + side.offsetZ);
        if (targetTile == null) return;

        writeIcon(nbt, "remoteIcon", getBlockItem(targetTile));
    }

    private void writeIcon(NBTTagCompound nbt, String key, ItemStack stack) {
        if (ItemStacks.isEmpty(stack)) return;

        NBTTagCompound iconNbt = new NBTTagCompound();
        stack.writeToNBT(iconNbt);
        nbt.setTag(key, iconNbt);
    }
}
