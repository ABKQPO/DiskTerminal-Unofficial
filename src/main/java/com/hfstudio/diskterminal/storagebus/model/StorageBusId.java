package com.hfstudio.diskterminal.storagebus.model;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.DiskTerminal;
import com.hfstudio.diskterminal.api.identity.TargetId;
import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.util.PosUtil;

/**
 * Stable identity of a storage bus within the {@code StorageBus} domain. Identity is derived from the
 * host position, dimension, side, bus role and storage type, none of which depend on a transient
 * runtime instance. A precomputed legacy {@code long} key is also carried so existing lookup maps keep
 * working, but the identity source remains the stable tuple above.
 */
public class StorageBusId implements TargetId {

    public static final ResourceLocation TYPE = new ResourceLocation(DiskTerminal.MODID, "storage_bus");

    private final int dimension;
    private final BlockPos position;
    private final int sideOrdinal;
    private final BusRole role;
    private final StorageType storageType;
    private final long legacyKey;

    public StorageBusId(int dimension, BlockPos position, int sideOrdinal, BusRole role, StorageType storageType) {
        this.dimension = dimension;
        this.position = position;
        this.sideOrdinal = sideOrdinal;
        this.role = role;
        this.storageType = storageType;
        this.legacyKey = computeLegacyKey(dimension, position, sideOrdinal, role, storageType);
    }

    /**
     * Recreate the synthetic {@code long} key the legacy data path used so the new identity and the old
     * tracker maps agree on the same value.
     */
    public static long computeLegacyKey(int dimension, BlockPos position, int sideOrdinal, BusRole role,
        StorageType storageType) {
        long pos = PosUtil.toLong(position.getX(), position.getY(), position.getZ());
        int typeFlag = storageType.ordinal() + role.ordinal() * 16;

        return pos ^ ((long) dimension << 48) ^ ((long) sideOrdinal << 40) ^ ((long) typeFlag << 39);
    }

    /**
     * Build an identity from a host tile and the bus's side, role and storage type.
     */
    public static StorageBusId of(TileEntity hostTile, int sideOrdinal, BusRole role, StorageType storageType) {
        int dim = hostTile.getWorldObj().provider.dimensionId;
        BlockPos pos = new BlockPos(hostTile.xCoord, hostTile.yCoord, hostTile.zCoord);

        return new StorageBusId(dim, pos, sideOrdinal, role, storageType);
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    public int getDimension() {
        return dimension;
    }

    public BlockPos getPosition() {
        return position;
    }

    public int getSideOrdinal() {
        return sideOrdinal;
    }

    public BusRole getRole() {
        return role;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    /**
     * The precomputed key matching the legacy bus id, used for fast map lookup only.
     */
    public long getLegacyKey() {
        return legacyKey;
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("dim", dimension);
        tag.setLong("pos", PosUtil.toLong(position.getX(), position.getY(), position.getZ()));
        tag.setInteger("side", sideOrdinal);
        tag.setInteger("role", role.ordinal());
        tag.setInteger("storageType", storageType.ordinal());
    }

    public static StorageBusId readFromNBT(NBTTagCompound tag) {
        int dim = tag.getInteger("dim");
        BlockPos pos = PosUtil.fromLong(tag.getLong("pos"));
        int side = tag.getInteger("side");
        BusRole role = roleFromOrdinal(tag.getInteger("role"));
        StorageType storageType = storageTypeFromOrdinal(tag.getInteger("storageType"));

        return new StorageBusId(dim, pos, side, role, storageType);
    }

    private static BusRole roleFromOrdinal(int ordinal) {
        BusRole[] values = BusRole.values();

        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : BusRole.STORAGE;
    }

    private static StorageType storageTypeFromOrdinal(int ordinal) {
        StorageType[] values = StorageType.values();

        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : StorageType.ITEM;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StorageBusId id)) return false;

        return dimension == id.dimension && sideOrdinal == id.sideOrdinal
            && legacyKey == id.legacyKey
            && role == id.role
            && storageType == id.storageType
            && position.equals(id.position);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(legacyKey);
    }

    @Override
    public String toString() {
        return "StorageBusId[dim=" + dimension
            + ", pos="
            + position
            + ", side="
            + sideOrdinal
            + ", role="
            + role
            + ", type="
            + storageType
            + "]";
    }
}
