package com.hfstudio.diskterminal.integration;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import baubles.api.BaublesApi;
import cpw.mods.fml.common.Optional;

public class BaublesIntegration {

    private BaublesIntegration() {}

    public static boolean isModLoaded() {
        return Mods.Baubles.isModLoaded();
    }

    public static IInventory getInventory(EntityPlayer player) {
        if (!isModLoaded()) return null;

        return getBaublesInventory(player);
    }

    public static ItemStack getStackInSlot(EntityPlayer player, int slot) {
        IInventory inventory = getInventory(player);
        if (inventory == null || slot < 0 || slot >= inventory.getSizeInventory()) return null;

        return inventory.getStackInSlot(slot);
    }

    @Optional.Method(modid = Mods.BAUBLES_EXPANDED)
    private static IInventory getBaublesInventory(EntityPlayer player) {
        return BaublesApi.getBaubles(player);
    }
}
