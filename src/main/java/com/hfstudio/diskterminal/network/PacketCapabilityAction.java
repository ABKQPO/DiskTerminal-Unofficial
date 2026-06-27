package com.hfstudio.diskterminal.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.diskterminal.client.StorageBusInfo;
import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusActionIds;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusCapabilityIds;
import com.hfstudio.diskterminal.util.AEStackUtil;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Unified client-to-server packet for any storage bus capability action. Carries the target identity,
 * the capability id, the action id and an opaque payload, so adding a capability or a bus type never
 * requires a new packet class.
 */
public class PacketCapabilityAction implements IMessage {

    private NBTTagCompound targetIdData;
    private String capability;
    private String action;
    private NBTTagCompound payload;

    public PacketCapabilityAction() {}

    public PacketCapabilityAction(StorageBusId targetId, ResourceLocation capability, ResourceLocation action,
        NBTTagCompound payload) {
        this.targetIdData = new NBTTagCompound();
        targetId.writeToNBT(this.targetIdData);
        this.capability = capability.toString();
        this.action = action.toString();
        this.payload = payload != null ? payload : new NBTTagCompound();
    }

    /**
     * Build a filter "set slot" action carrying the given stack at the given filter slot.
     */
    public static PacketCapabilityAction filterSetSlot(StorageBusInfo bus, int slot, ItemStack stack) {
        NBTTagCompound payload = filterPayload(bus, stack);
        payload.setInteger("slot", slot);

        return new PacketCapabilityAction(
            bus.toTargetId(),
            StorageBusCapabilityIds.FILTER,
            StorageBusActionIds.FILTER_SET_SLOT,
            payload);
    }

    /**
     * Build a filter "clear slot" action for the given filter slot.
     */
    public static PacketCapabilityAction filterClearSlot(StorageBusInfo bus, int slot) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger("slot", slot);

        return new PacketCapabilityAction(
            bus.toTargetId(),
            StorageBusCapabilityIds.FILTER,
            StorageBusActionIds.FILTER_CLEAR_SLOT,
            payload);
    }

    /**
     * Build a filter "toggle" action for the given stack.
     */
    public static PacketCapabilityAction filterToggle(StorageBusInfo bus, ItemStack stack) {
        return new PacketCapabilityAction(
            bus.toTargetId(),
            StorageBusCapabilityIds.FILTER,
            StorageBusActionIds.FILTER_TOGGLE,
            filterPayload(bus, stack));
    }

    /**
     * Build a filter "clear all" action.
     */
    public static PacketCapabilityAction filterClearAll(StorageBusInfo bus) {
        return new PacketCapabilityAction(
            bus.toTargetId(),
            StorageBusCapabilityIds.FILTER,
            StorageBusActionIds.FILTER_CLEAR_ALL,
            new NBTTagCompound());
    }

    /**
     * Build a filter "fill from preview" action.
     */
    public static PacketCapabilityAction filterFillFromPreview(StorageBusInfo bus) {
        return new PacketCapabilityAction(
            bus.toTargetId(),
            StorageBusCapabilityIds.FILTER,
            StorageBusActionIds.FILTER_FILL_FROM_PREVIEW,
            new NBTTagCompound());
    }

    private static NBTTagCompound filterPayload(StorageBusInfo bus, ItemStack stack) {
        NBTTagCompound payload = new NBTTagCompound();
        NBTTagCompound filterData = AEStackUtil.writeItemLikePartitionStack(stack);
        if (filterData != null) payload.setTag("filter", filterData);
        payload.setInteger(
            "filterType",
            bus.getStorageType()
                .ordinal());

        return payload;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.targetIdData = ByteBufUtils.readTag(buf);
        this.capability = ByteBufUtils.readUTF8String(buf);
        this.action = ByteBufUtils.readUTF8String(buf);
        this.payload = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, targetIdData);
        ByteBufUtils.writeUTF8String(buf, capability);
        ByteBufUtils.writeUTF8String(buf, action);
        ByteBufUtils.writeTag(buf, payload);
    }

    public static class Handler implements IMessageHandler<PacketCapabilityAction, IMessage> {

        @Override
        public IMessage onMessage(PacketCapabilityAction message, MessageContext ctx) {
            EntityPlayer player = ctx.getServerHandler().playerEntity;

            NetUtil.run(ctx.getServerHandler().playerEntity, () -> {
                if (!(player.openContainer instanceof ContainerCellTerminalBase container)) return;

                StorageBusId targetId = StorageBusId.readFromNBT(message.targetIdData);
                ResourceLocation capability = new ResourceLocation(message.capability);
                ResourceLocation action = new ResourceLocation(message.action);

                container.handleCapabilityAction(targetId, capability, action, message.payload);
            });

            return null;
        }
    }
}
