package com.hfstudio.diskterminal.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.hfstudio.diskterminal.items.ItemWirelessCellTerminal;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.WirelessTerminalGuiObject;
import baubles.api.BaublesApi;
import cpw.mods.fml.common.Loader;

/**
 * Container for the Wireless Cell Terminal GUI. Works over a wireless connection, draining power
 * and enforcing range like AE2's wireless terminal.
 */
public class ContainerWirelessCellTerminal extends ContainerCellTerminalBase {

    private static final String BAUBLES_MODID = "Baubles|Expanded";

    private final WirelessTerminalGuiObject wirelessTerminalGuiObject;
    private final int slot;
    private final boolean isBauble;
    private final WirelessTempCellInventory tempCellInventory;
    private double powerMultiplier = 0.5;
    private int ticks = 0;

    public ContainerWirelessCellTerminal(InventoryPlayer ip, WirelessTerminalGuiObject wth) {
        super(ip, wth);

        this.wirelessTerminalGuiObject = wth;
        this.slot = wth.getInventorySlot();
        this.isBauble = detectBauble(ip, this.slot);

        this.tempCellInventory = new WirelessTempCellInventory(ip.player, slot, isBauble);

        if (!isBauble && slot >= 0 && slot < ip.mainInventory.length) this.lockPlayerInventorySlot(slot);

        this.bindPlayerInventory(ip, 0, 0);
    }

    private static boolean detectBauble(InventoryPlayer ip, int slot) {
        if (slot >= 0 && slot < ip.mainInventory.length) {
            ItemStack inMain = ip.getStackInSlot(slot);
            if (!ItemStacks.isEmpty(inMain) && inMain.getItem() instanceof ItemWirelessCellTerminal) return false;
        }

        if (!Loader.isModLoaded(BAUBLES_MODID)) return false;

        IInventory baubles = BaublesApi.getBaubles(ip.player);
        if (baubles == null) return false;

        ItemStack inBauble = baubles.getStackInSlot(slot);

        return !ItemStacks.isEmpty(inBauble) && inBauble.getItem() instanceof ItemWirelessCellTerminal;
    }

    public WirelessTerminalGuiObject getWirelessTerminalGuiObject() {
        return this.wirelessTerminalGuiObject;
    }

    @Override
    public IInventory getTempCellInventory() {
        return this.tempCellInventory;
    }

    private ItemStack getTerminalStack() {
        if (isBauble) {
            if (!Loader.isModLoaded(BAUBLES_MODID)) return null;
            IInventory baubles = BaublesApi.getBaubles(getPlayerInv().player);

            return baubles == null ? null : baubles.getStackInSlot(slot);
        }

        return getPlayerInv().getStackInSlot(slot);
    }

    @Override
    protected boolean canSendUpdates() {
        ItemStack currentStack = getTerminalStack();

        if (ItemStacks.isEmpty(currentStack) || !(currentStack.getItem() instanceof ItemWirelessCellTerminal)) {
            this.setValidContainer(false);

            return false;
        }

        EntityPlayer player = getPlayerInv().player;

        this.ticks++;
        if (this.ticks >= 20) {
            double powerDrain = this.getPowerMultiplier() * this.ticks;
            double powerUsed = this.wirelessTerminalGuiObject
                .extractAEPower(powerDrain, Actionable.MODULATE, PowerMultiplier.CONFIG);

            if (powerDrain != powerUsed) {
                if (this.isValidContainer()) player.addChatMessage(PlayerMessages.DeviceNotPowered.get());
                this.setValidContainer(false);

                return false;
            }
            this.ticks = 0;
        }

        if (!this.wirelessTerminalGuiObject.rangeCheck()) {
            if (this.isValidContainer()) player.addChatMessage(PlayerMessages.OutOfRange.get());

            this.setValidContainer(false);
        } else {
            this.setPowerMultiplier(AEConfig.instance.wireless_getDrainRate(this.wirelessTerminalGuiObject.getRange()));
        }

        if (this.grid == null) {
            if (this.isValidContainer()) player.addChatMessage(PlayerMessages.StationCanNotBeLocated.get());
            this.setValidContainer(false);

            return false;
        }

        return true;
    }

    public double getPowerMultiplier() {
        return powerMultiplier;
    }

    public void setPowerMultiplier(double powerMultiplier) {
        this.powerMultiplier = powerMultiplier;
    }
}
