package com.hfstudio.diskterminal.container.handler;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

/**
 * Per-channel server-side snapshot tracker for delta updates.
 * <p>
 * Holds the last full payload we sent for a given channel, keyed by entry ID. On the next regen,
 * the new entries are diffed against the snapshot and an incremental payload is produced
 * containing:
 * <ul>
 * <li>{@code added}: NBTTagList of compounds present in the new state but not in the old</li>
 * <li>{@code updated}: NBTTagList of compounds present in both with changed serialization</li>
 * <li>{@code removed}: NBTTagList of long IDs present in old but not new</li>
 * </ul>
 * Static (non-list) keys (e.g. {@code networkId}, {@code terminalPos}) are always included in
 * every payload so the client always has them in sync.
 * <p>
 * Reset whenever the underlying network/grid identity changes (e.g. switching subnets) so the
 * next payload is forced to be a full rebuild on the client.
 */
public class DeltaSnapshot {

    private record SnapshotEntry(NBTTagCompound data, int fingerprint) {}

    /**
     * Wrapper for the result of {@link #buildDelta(String, NBTTagCompound, String, String)}.
     */
    public static class DeltaResult {

        public final NBTTagCompound payload;
        public final boolean isFull;
        public final boolean isEmpty;

        public DeltaResult(NBTTagCompound payload, boolean isFull) {
            this(payload, isFull, false);
        }

        public DeltaResult(NBTTagCompound payload, boolean isFull, boolean isEmpty) {
            this.payload = payload;
            this.isFull = isFull;
            this.isEmpty = isEmpty;
        }
    }

    /**
     * Map: channel -> (entryId -> last-sent NBT compound for that entry).
     * Empty map means we have never sent a full payload yet (next send must be FULL).
     */
    private final Map<String, Map<Long, SnapshotEntry>> snapshots = new HashMap<>();

    /**
     * Reset the snapshot for one channel (next send on that channel will be FULL).
     */
    public void reset(String channel) {
        snapshots.remove(channel);
    }

    /**
     * Reset all snapshots. Use when network/grid identity changes underneath us.
     */
    public void resetAll() {
        snapshots.clear();
    }

    /**
     * Compute a delta payload for one channel.
     *
     * @param channel     the channel being sent on (used as the snapshot key)
     * @param fullPayload the freshly-built full NBT (must contain a list of compounds at {@code listKey})
     * @param listKey     the NBT key of the entry list inside {@code fullPayload}
     * @param idKey       the field name inside each entry that uniquely identifies it (typically {@code "id"})
     */
    public DeltaResult buildDelta(String channel, NBTTagCompound fullPayload, String listKey, String idKey) {
        NBTTagList list = fullPayload.getTagList(listKey, Constants.NBT.TAG_COMPOUND);
        Map<Long, SnapshotEntry> oldSnapshot = snapshots.get(channel);

        // Build new snapshot map first (we always commit it after sending, regardless of mode).
        Map<Long, SnapshotEntry> newSnapshot = new HashMap<>(list.tagCount() * 2);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.hasKey(idKey)) continue;
            newSnapshot.put(entry.getLong(idKey), new SnapshotEntry(entry, fingerprint(entry)));
        }

        // First send on this channel: full snapshot.
        if (oldSnapshot == null) {
            snapshots.put(channel, newSnapshot);
            return new DeltaResult(fullPayload, true);
        }

        // Compute diff
        NBTTagList added = new NBTTagList();
        NBTTagList updated = new NBTTagList();
        NBTTagList removedIds = new NBTTagList();

        for (Map.Entry<Long, SnapshotEntry> e : newSnapshot.entrySet()) {
            SnapshotEntry oldEntry = oldSnapshot.get(e.getKey());
            if (oldEntry == null) {
                added.appendTag(
                    e.getValue()
                        .data());
            } else if (oldEntry.fingerprint() != e.getValue()
                .fingerprint()) {
                    updated.appendTag(
                        e.getValue()
                            .data());
                }
        }

        for (Long oldId : oldSnapshot.keySet()) {
            if (!newSnapshot.containsKey(oldId)) {
                NBTTagCompound idTag = new NBTTagCompound();
                idTag.setLong(idKey, oldId);
                removedIds.appendTag(idTag);
            }
        }

        // Commit new snapshot.
        snapshots.put(channel, newSnapshot);

        // Build delta payload: copy all non-list static keys from full payload, then attach diff lists.
        NBTTagCompound delta = new NBTTagCompound();
        for (String key : fullPayload.func_150296_c()) {
            if (key.equals(listKey)) continue;
            delta.setTag(key, fullPayload.getTag(key));
        }
        delta.setString("listKey", listKey);
        delta.setString("idKey", idKey);
        delta.setTag("added", added);
        delta.setTag("updated", updated);
        delta.setTag("removed", removedIds);

        boolean empty = added.tagCount() == 0 && updated.tagCount() == 0 && removedIds.tagCount() == 0;

        return new DeltaResult(delta, false, empty);
    }

    private static int fingerprint(NBTTagCompound entry) {
        return fingerprintTag(entry);
    }

    private static int fingerprintTag(NBTBase tag) {
        if (tag == null) return 0;

        int hash = tag.getId();

        if (tag instanceof NBTTagCompound compound) {
            for (Object keyObject : compound.func_150296_c()) {
                String key = (String) keyObject;
                hash = 31 * hash + key.hashCode();
                hash = 31 * hash + fingerprintTag(compound.getTag(key));
            }

            return hash;
        }

        if (tag instanceof NBTTagList list) {
            for (int i = 0; i < list.tagCount(); i++) {
                hash = 31 * hash + fingerprintTag(getListElement(list, i));
            }

            return hash;
        }

        if (tag instanceof NBTTagString stringTag) {
            return 31 * hash + stringTag.func_150285_a_()
                .hashCode();
        }

        if (tag instanceof NBTTagInt intTag) {
            return 31 * hash + intTag.func_150287_d();
        }

        if (tag instanceof NBTTagLong longTag) {
            long value = longTag.func_150291_c();
            return 31 * hash + (int) (value ^ (value >>> 32));
        }

        if (tag instanceof NBTTagShort shortTag) {
            return 31 * hash + shortTag.func_150289_e();
        }

        if (tag instanceof NBTTagByte byteTag) {
            return 31 * hash + byteTag.func_150290_f();
        }

        if (tag instanceof NBTTagFloat floatTag) {
            return 31 * hash + Float.floatToIntBits(floatTag.func_150288_h());
        }

        if (tag instanceof NBTTagDouble doubleTag) {
            long value = Double.doubleToLongBits(doubleTag.func_150286_g());
            return 31 * hash + (int) (value ^ (value >>> 32));
        }

        if (tag instanceof NBTTagByteArray byteArrayTag) {
            for (byte value : byteArrayTag.func_150292_c()) {
                hash = 31 * hash + value;
            }

            return hash;
        }

        if (tag instanceof NBTTagIntArray intArrayTag) {
            for (int value : intArrayTag.func_150302_c()) {
                hash = 31 * hash + value;
            }

            return hash;
        }

        return 31 * hash + tag.toString()
            .hashCode();
    }

    private static NBTBase getListElement(NBTTagList list, int index) {
        if (list == null || index < 0 || index >= list.tagCount()) return null;

        int tagType = list.func_150303_d();

        return switch (tagType) {
            case Constants.NBT.TAG_COMPOUND -> list.getCompoundTagAt(index);
            case Constants.NBT.TAG_STRING -> new NBTTagString(list.getStringTagAt(index));
            case Constants.NBT.TAG_INT_ARRAY -> new NBTTagIntArray(list.func_150306_c(index));
            default -> null;
        };
    }
}
