package com.hfstudio.diskterminal.integration;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.config.DiskTerminalServerConfig;
import com.hfstudio.diskterminal.integration.storagebus.GregTechMEInputBusScanner;
import com.hfstudio.diskterminal.integration.storagebus.StorageBusScannerRegistry;
import com.hfstudio.diskterminal.util.ItemStacks;

import cpw.mods.fml.common.Optional;
import gregtech.common.items.ItemFluidDisplay;

public class GregTechIntegration {

    private GregTechIntegration() {}

    public static void registerStorageBusScanner() {
        if (!isEnabled()) return;

        registerStorageBusScannerInternal();
    }

    public static boolean isEnabled() {
        return Mods.GregTech.isModLoaded()
            && (!DiskTerminalServerConfig.isInitialized() || DiskTerminalServerConfig.getInstance()
                .isIntegrationGregTechEnabled());
    }

    public static ItemStack prepareDisplayStack(ItemStack stack) {
        if (!Mods.GregTech.isModLoaded() || ItemStacks.isEmpty(stack)) return stack;

        return hideFluidDisplayStackSizeOverlay(stack);
    }

    @Optional.Method(modid = "gregtech")
    private static void registerStorageBusScannerInternal() {
        StorageBusScannerRegistry.register(GregTechMEInputBusScanner.INSTANCE);
    }

    @Optional.Method(modid = "gregtech")
    private static ItemStack hideFluidDisplayStackSizeOverlay(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemFluidDisplay)) return stack;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setBoolean("mHideStackSize", true);
        return stack;
    }
}
