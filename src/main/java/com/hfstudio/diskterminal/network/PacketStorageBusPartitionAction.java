package com.hfstudio.diskterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;


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
    private ItemStack itemStack;

    public PacketStorageBusPartitionAction() {
        this.itemStack = null;
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
        this.itemStack = itemStack != null ? itemStack : null;
    }

    public PacketStorageBusPartitionAction(long storageBusId, Action action, ItemStack itemStack) {
        this(storageBusId, action, -1, itemStack);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageBusId = buf.readLong();
        this.action = Action.values()[buf.readByte()];
        this.partitionSlot = buf.readInt();

        if (buf.readBoolean()) {
            NBTTagCompound nbt = ByteBufUtils.readTag(buf);
            this.itemStack = ItemStack.loadItemStackFromNBT(nbt);
        } else {
            this.itemStack = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageBusId);
        buf.writeByte(action.ordinal());
        buf.writeInt(partitionSlot);

        if (itemStack != null && itemStack.stackSize > 0) {
            buf.writeBoolean(true);
            NBTTagCompound nbt = new NBTTagCompound();
            itemStack.writeToNBT(nbt);
            ByteBufUtils.writeTag(buf, nbt);
        } else {
            buf.writeBoolean(false);
        }
    }

    public static class Handler implements IMessageHandler<PacketStorageBusPartitionAction, IMessage> {
        @Override
        public IMessage onMessage(PacketStorageBusPartitionAction message, MessageContext ctx) {
            NetUtil.run(ctx.getServerHandler().playerEntity, () -> {
                if (ctx.getServerHandler().playerEntity.openContainer instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase container = (ContainerCellTerminalBase) ctx.getServerHandler().playerEntity.openContainer;
                    container.handleStorageBusPartitionAction(
                        message.storageBusId,
                        message.action,
                        message.partitionSlot,
                        message.itemStack
                    );
                }
            });

            return null;
        }
    }
}
