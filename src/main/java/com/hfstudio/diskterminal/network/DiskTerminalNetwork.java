package com.hfstudio.diskterminal.network;

import com.hfstudio.diskterminal.Tags;
import com.hfstudio.diskterminal.network.chunked.PacketNBTChunk;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

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

        // Client -> Server: action packets
        INSTANCE
            .registerMessage(PacketPartitionAction.Handler.class, PacketPartitionAction.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketEjectCell.Handler.class, PacketEjectCell.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketInsertCell.Handler.class, PacketInsertCell.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketPickupCell.Handler.class, PacketPickupCell.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetPriority.Handler.class, PacketSetPriority.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketUpgradeCell.Handler.class, PacketUpgradeCell.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketTabChange.Handler.class, PacketTabChange.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(
            PacketStorageBusPartitionAction.Handler.class,
            PacketStorageBusPartitionAction.class,
            packetId++,
            Side.SERVER);
        INSTANCE.registerMessage(
            PacketStorageBusIOMode.Handler.class,
            PacketStorageBusIOMode.class,
            packetId++,
            Side.SERVER);
        INSTANCE.registerMessage(
            PacketUpgradeStorageBus.Handler.class,
            PacketUpgradeStorageBus.class,
            packetId++,
            Side.SERVER);
        INSTANCE
            .registerMessage(PacketExtractUpgrade.Handler.class, PacketExtractUpgrade.class, packetId++, Side.SERVER);
        INSTANCE
            .registerMessage(PacketSlotLimitChange.Handler.class, PacketSlotLimitChange.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(
            PacketNetworkToolAction.Handler.class,
            PacketNetworkToolAction.class,
            packetId++,
            Side.SERVER);
        INSTANCE.registerMessage(
            PacketSubnetListRequest.Handler.class,
            PacketSubnetListRequest.class,
            packetId++,
            Side.SERVER);
        INSTANCE.registerMessage(PacketSubnetAction.Handler.class, PacketSubnetAction.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSwitchNetwork.Handler.class, PacketSwitchNetwork.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketRenameAction.Handler.class, PacketRenameAction.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(
            PacketOpenWirelessTerminal.Handler.class,
            PacketOpenWirelessTerminal.class,
            packetId++,
            Side.SERVER);
        INSTANCE
            .registerMessage(PacketTempCellAction.Handler.class, PacketTempCellAction.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(
            PacketTempCellPartitionAction.Handler.class,
            PacketTempCellPartitionAction.class,
            packetId++,
            Side.SERVER);
        INSTANCE.registerMessage(
            PacketSubnetPartitionAction.Handler.class,
            PacketSubnetPartitionAction.class,
            packetId++,
            Side.SERVER);

        // Server -> Client: GUI-safe feedback messages (overlay + chat)
        INSTANCE
            .registerMessage(PacketPlayerFeedback.Handler.class, PacketPlayerFeedback.class, packetId++, Side.CLIENT);

        // World block highlight: client requests (C2S), server echoes to the requester (S2C)
        INSTANCE
            .registerMessage(PacketHighlightBlock.Handler.class, PacketHighlightBlock.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(
            PacketHighlightBlockClient.Handler.class,
            PacketHighlightBlockClient.class,
            packetId++,
            Side.CLIENT);
    }
}
