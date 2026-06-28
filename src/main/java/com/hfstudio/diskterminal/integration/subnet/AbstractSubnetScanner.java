package com.hfstudio.diskterminal.integration.subnet;

import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.hfstudio.diskterminal.container.handler.SubnetDataHandler;
import com.hfstudio.diskterminal.container.handler.SubnetDataHandler.SubnetTracker;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IInterfaceHost;

/**
 * Abstract base class for subnet scanners providing common utility methods.
 * Adapted for 1.7.10 AE2-Unofficial API.
 */
public abstract class AbstractSubnetScanner implements ISubnetScanner {

    /**
     * Get the ME grid from a tile entity (if it's part of one).
     * Handles both full-block interfaces and part interfaces on cables.
     */
    protected IGrid getGridFromTile(TileEntity tile) {
        if (tile == null) return null;

        // Handle IInterfaceHost (TileInterface implements this)
        if (tile instanceof IInterfaceHost iface) {
            IGridNode node = iface.getActionableNode();
            if (node != null && node.getGrid() != null) return node.getGrid();
        }

        // Handle cable bus with parts
        if (tile instanceof IPartHost host) {
            // Check all sides for parts with grid nodes
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                IPart part = host.getPart(dir);
                if (part != null) {
                    IGridNode node = part.getGridNode();
                    if (node != null && node.getGrid() != null) return node.getGrid();
                }
            }
        }

        return null;
    }

    /**
     * Find the "primary" node of a grid for consistent ID generation.
     */
    protected IGridNode findPrimaryNode(IGrid grid) {
        if (grid == null) return null;

        // Prefer the interface-host node with the lowest stable world position.
        // Grid machine iteration order is not guaranteed, so picking the first node
        // causes subnet ids to drift between scans.
        IGridNode bestInterfaceNode = null;
        for (IGridNode node : grid.getMachines(IInterfaceHost.class)) {
            if (isBetterPrimaryNode(node, bestInterfaceNode)) {
                bestInterfaceNode = node;
            }
        }
        if (bestInterfaceNode != null) return bestInterfaceNode;

        // Fall back to the lowest-position node in the whole grid.
        IGridNode bestNode = null;
        for (IGridNode node : grid.getNodes()) {
            if (isBetterPrimaryNode(node, bestNode)) {
                bestNode = node;
            }
        }

        return bestNode;
    }

    /**
     * Check if the subnet has a security station.
     */
    protected boolean checkHasSecurity(IGrid grid) {
        try {
            ISecurityGrid securityGrid = grid.getCache(ISecurityGrid.class);
            return securityGrid != null && securityGrid.isAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if the player can access the subnet (has security permissions).
     */
    protected boolean checkIsAccessible(IGrid grid, int playerId) {
        try {
            ISecurityGrid securityGrid = grid.getCache(ISecurityGrid.class);
            if (securityGrid == null || !securityGrid.isAvailable()) return true;
            return securityGrid.hasPermission(playerId, SecurityPermissions.BUILD);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Check if the subnet has power.
     */
    protected boolean checkHasPower(IGrid grid) {
        try {
            IEnergyGrid energyGrid = grid.getCache(IEnergyGrid.class);
            if (energyGrid == null) return false;
            return energyGrid.getAvgPowerInjection() > 0 || energyGrid.getStoredPower() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get or create a subnet tracker for the given grid.
     */
    protected SubnetTracker getOrCreateTracker(Map<IGrid, SubnetTracker> subnetsByGrid, IGrid subnetGrid) {
        return subnetsByGrid.computeIfAbsent(subnetGrid, grid -> {
            long id = generateSubnetId(grid);
            return new SubnetTracker(id);
        });
    }

    /**
     * Generate a unique ID for a subnet based on its primary node position.
     */
    protected long generateSubnetId(IGrid grid) {
        IGridNode primaryNode = findPrimaryNode(grid);
        if (primaryNode == null) return grid.hashCode();

        DimensionalCoord loc = primaryNode.getGridBlock()
            .getLocation();
        if (loc == null) return grid.hashCode();

        int dim = loc.getDimension();
        long pos = PosUtil.toLong(loc.x, loc.y, loc.z);
        return pos ^ ((long) dim << 48);
    }

    /**
     * Create NBT data for a subnet.
     */
    protected NBTTagCompound createSubnetNBT(IGrid subnetGrid, SubnetTracker tracker, int playerId, int slotLimit) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("id", tracker.id);

        // Get primary node for subnet info
        IGridNode primaryNode = findPrimaryNode(subnetGrid);
        if (primaryNode != null) {
            DimensionalCoord loc = primaryNode.getGridBlock()
                .getLocation();
            if (loc != null) {
                nbt.setLong("pos", PosUtil.toLong(loc.x, loc.y, loc.z));
                nbt.setInteger("dim", loc.getDimension());

                // Get block item representation from world
                TileEntity tile = loc.getWorld()
                    .getTileEntity(loc.x, loc.y, loc.z);
                if (tile != null) {
                    ItemStack blockItem = getBlockItem(tile);
                    if (!ItemStacks.isEmpty(blockItem)) {
                        NBTTagCompound blockNbt = new NBTTagCompound();
                        blockItem.writeToNBT(blockNbt);
                        nbt.setTag("blockItem", blockNbt);
                    }
                }
            }
        }

        nbt.setLong("primaryPos", nbt.getLong("pos"));
        nbt.setInteger("posX", locX(primaryNode));
        nbt.setInteger("posY", locY(primaryNode));
        nbt.setInteger("posZ", locZ(primaryNode));

        IInterfaceHost interfaceHost = SubnetDataHandler.findPrimaryInterfaceHost(subnetGrid);
        if (interfaceHost instanceof ICustomNameObject nameable) {
            if (nameable.hasCustomName()) {
                String customName = nameable.getCustomName();
                if (customName != null && !customName.isEmpty()) nbt.setString("customName", customName);
            }
        }

        nbt.setBoolean("favorite", SubnetDataHandler.isFavorited(tracker.id));

        // Subnet properties
        nbt.setBoolean("hasSecurity", checkHasSecurity(subnetGrid));
        nbt.setBoolean("accessible", checkIsAccessible(subnetGrid, playerId));
        nbt.setBoolean("hasPower", checkHasPower(subnetGrid));

        // Add connection data
        tracker.writeConnectionsToNBT(nbt);

        return nbt;
    }

    private int locX(IGridNode node) {
        DimensionalCoord loc = getNodeLocation(node);
        return loc == null ? 0 : loc.x;
    }

    private int locY(IGridNode node) {
        DimensionalCoord loc = getNodeLocation(node);
        return loc == null ? 0 : loc.y;
    }

    private int locZ(IGridNode node) {
        DimensionalCoord loc = getNodeLocation(node);
        return loc == null ? 0 : loc.z;
    }

    private DimensionalCoord getNodeLocation(IGridNode node) {
        if (node == null || node.getGridBlock() == null) return null;
        return node.getGridBlock()
            .getLocation();
    }

    private boolean isBetterPrimaryNode(IGridNode candidate, IGridNode currentBest) {
        if (candidate == null) return false;
        if (currentBest == null) return true;

        DimensionalCoord candidateLoc = getNodeLocation(candidate);
        DimensionalCoord currentLoc = getNodeLocation(currentBest);

        if (candidateLoc == null) return false;
        if (currentLoc == null) return true;

        int dimCompare = Integer.compare(candidateLoc.getDimension(), currentLoc.getDimension());
        if (dimCompare != 0) return dimCompare < 0;

        int xCompare = Integer.compare(candidateLoc.x, currentLoc.x);
        if (xCompare != 0) return xCompare < 0;

        int yCompare = Integer.compare(candidateLoc.y, currentLoc.y);
        if (yCompare != 0) return yCompare < 0;

        return candidateLoc.z < currentLoc.z;
    }

    /**
     * Get an ItemStack representing the block at this tile entity.
     */
    protected ItemStack getBlockItem(TileEntity tile) {
        if (tile == null || tile.getWorldObj() == null) return null;

        World world = tile.getWorldObj();
        int x = tile.xCoord;
        int y = tile.yCoord;
        int z = tile.zCoord;

        try {
            ItemStack stack = world.getBlock(x, y, z)
                .getPickBlock(null, world, x, y, z);
            if (!ItemStacks.isEmpty(stack)) return stack;
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }
}
