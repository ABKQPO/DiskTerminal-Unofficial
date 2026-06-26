package com.hfstudio.diskterminal.integration.subnet;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.container.handler.SubnetDataHandler.SubnetTracker;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.helpers.IInterfaceHost;
import appeng.parts.misc.PartInterface;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.misc.TileInterface;
import appeng.tile.networking.TileCableBus;

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
    private void scanAdjacentForRemoteBus(TileEntity centerTile, IGrid mainGrid, Map<IGrid, SubnetTracker> subnetsByGrid,
        Object interfacePart) {
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
        if (!(tile instanceof IPartHost)) return null;

        IPartHost host = (IPartHost) tile;
        IPart part = host.getPart(side);
        if (!(part instanceof PartStorageBus)) return null;

        PartStorageBus bus = (PartStorageBus) part;
        IGridNode busNode = bus.getGridNode();
        if (busNode == null || busNode.getGrid() == null) return null;

        IGrid busGrid = busNode.getGrid();
        if (busGrid == mainGrid) return null;

        return busGrid;
    }
}
