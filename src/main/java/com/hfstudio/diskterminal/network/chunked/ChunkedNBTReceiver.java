package com.hfstudio.diskterminal.network.chunked;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import com.hfstudio.diskterminal.DiskTerminal;

/**
 * Client-side reassembler for chunked NBT payloads.
 * <p>
 * Maintains one in-flight buffer per ({@code channel}). When a chunk arrives with a session ID
 * different from the one currently buffered for that channel, the previous buffer is discarded
 * (old session aborted). When all chunks of a session are present, the byte slices are concatenated,
 * gzip-decompressed, parsed as NBT and dispatched to the channel's
 * {@link PayloadHandler}.
 * <p>
 * Threading: all calls go through {@link #acceptChunk} on the client main thread (scheduled by
 * {@link PacketNBTChunk.Handler}), so no synchronization is needed.
 */
@SideOnly(Side.CLIENT)
public class ChunkedNBTReceiver {

    private static final Map<String, Assembler> inflight = new HashMap<>();

    private ChunkedNBTReceiver() {}

    public static void acceptChunk(PacketNBTChunk chunk) {
        String channel = chunk.getChannel();
        PayloadHandler handler = PayloadDispatcher.get(channel);

        // If the GUI that owns this channel is closed, drop any late chunks immediately so a
        // reopened GUI does not inherit stale partial assemblies from the previous instance.
        if (handler == null) {
            inflight.remove(channel);
            return;
        }

        Assembler assembler = inflight.get(channel);
        int chunkIndex = chunk.getChunkIndex();

        // New session: start fresh. This abandons any partially-received older session.
        if (assembler == null || assembler.sessionId != chunk.getSessionId()) {
            assembler = new Assembler(chunk.getSessionId(), chunk.getTotalChunks(), chunk.getMode());
            inflight.put(channel, assembler);
        }

        if (chunkIndex < 0 || chunkIndex >= assembler.totalChunks) {
            DiskTerminal.LOG.warn(
                "Discarding out-of-range chunk {} for channel {} (total={})",
                chunkIndex,
                channel,
                assembler.totalChunks);
            return;
        }

        if (assembler.parts[chunkIndex] != null) return;

        assembler.parts[chunkIndex] = chunk.getPayload();
        assembler.received++;

        if (assembler.received < assembler.totalChunks) return;

        // All chunks present: assemble and dispatch.
        inflight.remove(channel);

        try {
            NBTTagCompound nbt = decode(assembler);
            handler.onPayload(assembler.mode, nbt);
        } catch (IOException e) {
            DiskTerminal.LOG.error("Failed to decode chunked payload for channel " + channel, e);
        }
    }

    private static NBTTagCompound decode(Assembler assembler) throws IOException {
        // Concatenate all chunk byte arrays then gzip-decompress + read NBT.
        int total = 0;
        for (byte[] part : assembler.parts) total += part.length;

        byte[] joined = new byte[total];
        int offset = 0;
        for (byte[] part : assembler.parts) {
            System.arraycopy(part, 0, joined, offset, part.length);
            offset += part.length;
        }

        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(joined));
            DataInputStream dis = new DataInputStream(gzis)) {
            return CompressedStreamTools.read(dis);
        }
    }

    private static class Assembler {

        private final long sessionId;
        private final int totalChunks;
        private final PayloadMode mode;
        private final byte[][] parts;
        private int received;

        Assembler(long sessionId, int totalChunks, PayloadMode mode) {
            this.sessionId = sessionId;
            this.totalChunks = totalChunks;
            this.mode = mode;
            this.parts = new byte[totalChunks][];
        }
    }
}
