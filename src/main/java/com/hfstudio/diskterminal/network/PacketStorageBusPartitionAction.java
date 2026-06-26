package com.hfstudio.diskterminal.network;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;
import com.hfstudio.diskterminal.util.AEStackUtil;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Packet sent from client to server to modify storage bus partition.
 */
public class PacketStorageBusPartitionAction implements IMessage {

    public enum Action {
        ADD_ITEM,
        REMOVE_ITEM,
        TOGGLE_ITEM,
        SET_ALL_FROM_CONTENTS,
        CLEAR_ALL
    }

    private long storageBusId;
    private Action action;
    private int partitionSlot;
    private NBTTagCompound stackData;

    public PacketStorageBusPartitionAction() {
        this.stackData = null;
    }

    public PacketStorageBusPartitionAction(long storageBusId, Action action) {
        this(storageBusId, action, -1, null);
    }

    public PacketStorageBusPartitionAction(long storageBusId, Action action, int partitionSlot) {
        this(storageBusId, action, partitionSlot, null);
    }

    public PacketStorageBusPartitionAction(long storageBusId, Action action, int partitionSlot, ItemStack itemStack) {
        this.storageBusId = storageBusId;
        this.action = action;
        this.partitionSlot = partitionSlot;
        this.stackData = AEStackUtil.writeItemLikePartitionStack(itemStack);
    }

    public PacketStorageBusPartitionAction(long storageBusId, Action action, ItemStack itemStack) {
        this(storageBusId, action, -1, itemStack);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageBusId = buf.readLong();
        this.action = Action.values()[buf.readByte()];
        this.partitionSlot = buf.readInt();

        this.stackData = buf.readBoolean() ? ByteBufUtils.readTag(buf) : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageBusId);
        buf.writeByte(action.ordinal());
        buf.writeInt(partitionSlot);

        if (stackData != null && !stackData.hasNoTags()) {
            buf.writeBoolean(true);
            ByteBufUtils.writeTag(buf, stackData);
        } else {
            buf.writeBoolean(false);
        }
    }

    public static class Handler implements IMessageHandler<PacketStorageBusPartitionAction, IMessage> {

        @Override
        public IMessage onMessage(PacketStorageBusPartitionAction message, MessageContext ctx) {
            NetUtil.run(ctx.getServerHandler().playerEntity, () -> {
                if (ctx.getServerHandler().playerEntity.openContainer instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase container = (ContainerCellTerminalBase) ctx
                        .getServerHandler().playerEntity.openContainer;
                    container.handleStorageBusPartitionAction(
                        message.storageBusId,
                        message.action,
                        message.partitionSlot,
                        message.stackData);
                }
            });

            return null;
        }
    }
}
