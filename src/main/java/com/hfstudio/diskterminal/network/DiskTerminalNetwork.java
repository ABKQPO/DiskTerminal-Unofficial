package com.hfstudio.diskterminal.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

import com.hfstudio.diskterminal.Tags;
import com.hfstudio.diskterminal.network.chunked.PacketNBTChunk;

/**
 * Network handler for Disk Terminal packets.
 * <p>
 * Packets are registered with stable, incrementing discriminator IDs. As more packets are ported,
 * they are appended here; existing IDs must not be reordered to preserve client/server compatibility.
 */
public class DiskTerminalNetwork {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MODID);

    private static int packetId = 0;

    public static void init() {
        // Server -> Client: Generic chunked NBT payload (terminal data, subnet data, etc).
        INSTANCE.registerMessage(PacketNBTChunk.Handler.class, PacketNBTChunk.class, packetId++, Side.CLIENT);

        // Server -> Client: GUI-safe feedback messages (overlay + chat)
        INSTANCE
            .registerMessage(PacketPlayerFeedback.Handler.class, PacketPlayerFeedback.class, packetId++, Side.CLIENT);
    }
}
