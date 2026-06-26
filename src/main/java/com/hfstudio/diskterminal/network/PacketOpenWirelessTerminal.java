package com.hfstudio.diskterminal.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.hfstudio.diskterminal.ItemRegistry;
import com.hfstudio.diskterminal.gui.GuiHandler;
import com.hfstudio.diskterminal.integration.BaublesIntegration;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.core.localization.PlayerMessages;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Packet sent from client to server to open the wireless cell terminal via keybind.
 * Searches the player's main inventory and (if present) Baubles slots for a linked terminal.
 */
public class PacketOpenWirelessTerminal implements IMessage {

    public PacketOpenWirelessTerminal() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketOpenWirelessTerminal, IMessage> {

        @Override
        public IMessage onMessage(PacketOpenWirelessTerminal message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;

            NetUtil.run(player, () -> tryOpenTerminal(player));

            return null;
        }

        private void tryOpenTerminal(EntityPlayerMP player) {
            ItemStack[] mainInventory = player.inventory.mainInventory;
            for (int i = 0; i < mainInventory.length; i++) {
                ItemStack is = mainInventory[i];
                if (!ItemStacks.isEmpty(is) && is.getItem() == ItemRegistry.WIRELESS_CELL_TERMINAL) {
                    if (tryOpenWirelessCellTerminal(is, player, i, false)) return;
                }
            }

            if (BaublesIntegration.isModLoaded()) tryOpenFromBaubles(player);
        }

        private boolean tryOpenWirelessCellTerminal(ItemStack is, EntityPlayerMP player, int slot, boolean isBauble) {
            IWirelessTermHandler handler = AEApi.instance()
                .registries()
                .wireless()
                .getWirelessTerminalHandler(is);
            if (handler == null) return false;

            String encKey = handler.getEncryptionKey(is);
            if (encKey == null || encKey.isEmpty()) {
                player.addChatMessage(PlayerMessages.DeviceNotLinked.get());
                return true;
            }

            try {
                long parsedKey = Long.parseLong(encKey);
                ILocatable securityStation = AEApi.instance()
                    .registries()
                    .locatable()
                    .getLocatableBy(parsedKey);
                if (securityStation == null) {
                    player.addChatMessage(PlayerMessages.StationCanNotBeLocated.get());
                    return true;
                }
            } catch (NumberFormatException e) {
                player.addChatMessage(PlayerMessages.DeviceNotLinked.get());
                return true;
            }

            if (!handler.hasPower(player, 0.5, is)) {
                player.addChatMessage(PlayerMessages.DeviceNotPowered.get());
                return true;
            }

            GuiHandler.openWirelessCellTerminalGui(player, slot, isBauble);
            return true;
        }

        private boolean tryOpenFromBaubles(EntityPlayerMP player) {
            IInventory baubles = BaublesIntegration.getInventory(player);
            if (baubles == null) return false;

            for (int i = 0; i < baubles.getSizeInventory(); i++) {
                ItemStack is = baubles.getStackInSlot(i);
                if (ItemStacks.isEmpty(is)) continue;

                if (is.getItem() == ItemRegistry.WIRELESS_CELL_TERMINAL) {
                    if (tryOpenWirelessCellTerminal(is, player, i, true)) return true;
                }
            }

            return false;
        }
    }
}
