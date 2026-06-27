package com.hfstudio.diskterminal.storagebus.model;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;

/**
 * Immutable {@link StorageBusDescriptor} implementation. Holds only stable data; never a runtime object
 * reference.
 */
public class SimpleStorageBusDescriptor implements StorageBusDescriptor {

    private final StorageBusId id;
    private final BlockPos position;
    private final int dimension;
    private final ItemStack icon;
    private final ForgeDirection side;
    private final BusRole role;
    private final StorageType storageType;

    public SimpleStorageBusDescriptor(StorageBusId id, BlockPos position, int dimension, ItemStack icon,
        ForgeDirection side, BusRole role, StorageType storageType) {
        this.id = id;
        this.position = position;
        this.dimension = dimension;
        this.icon = icon;
        this.side = side;
        this.role = role;
        this.storageType = storageType;
    }

    @Override
    public StorageBusId getId() {
        return id;
    }

    @Override
    public BlockPos getPosition() {
        return position;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public ItemStack getIcon() {
        return icon;
    }

    @Override
    public ForgeDirection getSide() {
        return side;
    }

    @Override
    public BusRole getRole() {
        return role;
    }

    @Override
    public StorageType getStorageType() {
        return storageType;
    }
}
