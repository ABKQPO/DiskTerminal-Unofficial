package com.hfstudio.diskterminal.network;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Packet for modifying partition of a storage bus connection in the subnet overview.
 * Identifies the target by subnet ID + connection position + side.
 */
public class PacketSubnetPartitionAction implements IMessage {

    public enum Action {
        ADD_ITEM,
        REMOVE_ITEM,
        SET_ALL_FROM_CONTENTS,
        CLEAR_ALL,
        TOGGLE_ITEM,
        /** Set partition from the subnet's entire ME storage inventory. */
        SET_ALL_FROM_SUBNET_INVENTORY
    }

    private long subnetId;
    private long pos;
    private int side;
    private Action action;
    private int partitionSlot;
    private ItemStack itemStack;

    public PacketSubnetPartitionAction() {
        this.itemStack = null;
    }

    /**
     * Actions that don't need a specific partition slot or item (CLEAR_ALL, SET_ALL_FROM_CONTENTS).
     */
    public PacketSubnetPartitionAction(long subnetId, long pos, int side, Action action) {
        this(subnetId, pos, side, action, -1, null);
    }

    /**
     * REMOVE_ITEM action on a specific partition slot.
     */
    public PacketSubnetPartitionAction(long subnetId, long pos, int side, Action action, int partitionSlot) {
        this(subnetId, pos, side, action, partitionSlot, null);
    }

    /**
     * ADD_ITEM action on a specific partition slot with an item.
     */
    public PacketSubnetPartitionAction(long subnetId, long pos, int side, Action action, int partitionSlot,
        ItemStack itemStack) {
        this.subnetId = subnetId;
        this.pos = pos;
        this.side = side;
        this.action = action;
        this.partitionSlot = partitionSlot;
        this.itemStack = itemStack != null ? itemStack : null;
    }

    /**
     * TOGGLE_ITEM action without specific partition slot.
     */
    public PacketSubnetPartitionAction(long subnetId, long pos, int side, Action action, ItemStack itemStack) {
        this(subnetId, pos, side, action, -1, itemStack);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.subnetId = buf.readLong();
        this.pos = buf.readLong();
        this.side = buf.readInt();
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
        buf.writeLong(subnetId);
        buf.writeLong(pos);
        buf.writeInt(side);
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

    public static class Handler implements IMessageHandler<PacketSubnetPartitionAction, IMessage> {

        @Override
        public IMessage onMessage(PacketSubnetPartitionAction message, MessageContext ctx) {
            NetUtil.run(ctx.getServerHandler().playerEntity, () -> {
                if (ctx.getServerHandler().playerEntity.openContainer instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase container = (ContainerCellTerminalBase) ctx
                        .getServerHandler().playerEntity.openContainer;
                    container.handleSubnetPartitionAction(
                        message.subnetId,
                        message.pos,
                        message.side,
                        message.action,
                        message.partitionSlot,
                        message.itemStack);
                }
            });

            return null;
        }
    }
}
