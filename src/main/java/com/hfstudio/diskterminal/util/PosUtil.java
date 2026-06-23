package com.hfstudio.diskterminal.util;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

/**
 * Helpers for packing/unpacking a block position into a single long and back.
 * <p>
 * 1.7.10 has no vanilla {@code BlockPos.toLong()/fromLong()}; this mirrors the vanilla 1.12 bit
 * layout (26 bits X, 12 bits Y, 26 bits Z) so the wire format matches the original mod and stays
 * stable across the client/server boundary. Uses GTNHLib's {@link BlockPos} as the position type.
 */
public class PosUtil {

    private static final int NUM_X_BITS = 26;
    private static final int NUM_Z_BITS = 26;
    private static final int NUM_Y_BITS = 64 - NUM_X_BITS - NUM_Z_BITS; // 12
    private static final int Y_SHIFT = NUM_Z_BITS;
    private static final int X_SHIFT = Y_SHIFT + NUM_Y_BITS;
    private static final long X_MASK = (1L << NUM_X_BITS) - 1L;
    private static final long Y_MASK = (1L << NUM_Y_BITS) - 1L;
    private static final long Z_MASK = (1L << NUM_Z_BITS) - 1L;

    private PosUtil() {}

    public static long toLong(int x, int y, int z) {
        return ((long) x & X_MASK) << X_SHIFT | ((long) y & Y_MASK) << Y_SHIFT | ((long) z & Z_MASK);
    }

    public static long toLong(BlockPos pos) {
        return toLong(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos fromLong(long serialized) {
        int x = (int) (serialized << (64 - X_SHIFT - NUM_X_BITS) >> (64 - NUM_X_BITS));
        int y = (int) (serialized << (64 - Y_SHIFT - NUM_Y_BITS) >> (64 - NUM_Y_BITS));
        int z = (int) (serialized << (64 - NUM_Z_BITS) >> (64 - NUM_Z_BITS));

        return new BlockPos(x, y, z);
    }

    /**
     * Squared euclidean distance between two positions. GTNHLib's {@link BlockPos} has no
     * {@code distanceSq}, so this mirrors the vanilla 1.12 helper used by the source mod for
     * distance-based sorting.
     */
    public static double distSq(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();

        return dx * dx + dy * dy + dz * dz;
    }
}
