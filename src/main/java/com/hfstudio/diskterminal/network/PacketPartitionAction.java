package com.hfstudio.diskterminal.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
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
 * Packet sent from client to server to modify cell partition.
 */
public class PacketPartitionAction implements IMessage {

    public enum Action {
        ADD_ITEM,
        REMOVE_ITEM,
        SET_ALL_FROM_CONTENTS,
        CLEAR_ALL,
        TOGGLE_ITEM
    }

    private long storageId;
    private int cellSlot;
    private Action action;
    private int partitionSlot;
    private NBTTagCompound stackData;

    public PacketPartitionAction() {
        this.stackData = null;
    }

    public PacketPartitionAction(long storageId, int cellSlot, Action action) {
        this.storageId = storageId;
        this.cellSlot = cellSlot;
        this.action = action;
        this.partitionSlot = -1;
        this.stackData = null;
    }

    public PacketPartitionAction(long storageId, int cellSlot, Action action, int partitionSlot) {
        this.storageId = storageId;
        this.cellSlot = cellSlot;
        this.action = action;
        this.partitionSlot = partitionSlot;
        this.stackData = null;
    }

    public PacketPartitionAction(long storageId, int cellSlot, Action action, int partitionSlot, ItemStack itemStack) {
        this.storageId = storageId;
        this.cellSlot = cellSlot;
        this.action = action;
        this.partitionSlot = partitionSlot;
        this.stackData = AEStackUtil.writeItemLikePartitionStack(itemStack);
    }

    public PacketPartitionAction(long storageId, int cellSlot, Action action, ItemStack itemStack) {
        this.storageId = storageId;
        this.cellSlot = cellSlot;
        this.action = action;
        this.partitionSlot = -1;
        this.stackData = AEStackUtil.writeItemLikePartitionStack(itemStack);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageId = buf.readLong();
        this.cellSlot = buf.readInt();
        this.action = Action.values()[buf.readByte()];
        this.partitionSlot = buf.readInt();

        this.stackData = buf.readBoolean() ? ByteBufUtils.readTag(buf) : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageId);
        buf.writeInt(cellSlot);
        buf.writeByte(action.ordinal());
        buf.writeInt(partitionSlot);

        if (stackData != null && !stackData.hasNoTags()) {
            buf.writeBoolean(true);
            ByteBufUtils.writeTag(buf, stackData);
        } else {
            buf.writeBoolean(false);
        }
    }

    public static class Handler implements IMessageHandler<PacketPartitionAction, IMessage> {

        @Override
        public IMessage onMessage(PacketPartitionAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;

            NetUtil.run(player, () -> {
                Container container = player.openContainer;

                if (container instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase cellContainer = (ContainerCellTerminalBase) container;
                    cellContainer.handlePartitionAction(
                        message.storageId,
                        message.cellSlot,
                        message.action,
                        message.partitionSlot,
                        message.stackData);
                }
            });

            return null;
        }
    }
}
