package com.hfstudio.diskterminal.integration;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.common.item.ItemFluidDrop;
import com.hfstudio.diskterminal.integration.storagebus.AE2FluidCraftStorageBusScanner;
import com.hfstudio.diskterminal.integration.storagebus.StorageBusScannerRegistry;

import cpw.mods.fml.common.Optional;

public class AE2FluidCraftIntegration {

    private AE2FluidCraftIntegration() {}

    public static FluidStack tryExtractFluidDrop(ItemStack stack) {
        if (!Mods.AE2FluidCraft.isModLoaded()) return null;

        return extractFluidDrop(stack);
    }

    public static ItemStack tryCreateFluidDrop(FluidStack fluid) {
        if (!Mods.AE2FluidCraft.isModLoaded()) return null;

        return createFluidDrop(fluid);
    }

    public static void registerStorageBusScanner() {
        if (!Mods.AE2FluidCraft.isModLoaded()) return;

        registerStorageBusScannerInternal();
    }

    @Optional.Method(modid = Mods.AE2_FLUID_CRAFT)
    private static FluidStack extractFluidDrop(ItemStack stack) {
        if (!ItemFluidDrop.isFluidStack(stack)) return null;

        return ItemFluidDrop.getFluidStack(stack);
    }

    @Optional.Method(modid = Mods.AE2_FLUID_CRAFT)
    private static ItemStack createFluidDrop(FluidStack fluid) {
        return ItemFluidDrop.newStack(fluid);
    }

    @Optional.Method(modid = Mods.AE2_FLUID_CRAFT)
    private static void registerStorageBusScannerInternal() {
        StorageBusScannerRegistry.register(AE2FluidCraftStorageBusScanner.INSTANCE);
    }
}
