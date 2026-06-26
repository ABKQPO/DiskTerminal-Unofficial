package com.hfstudio.diskterminal.util;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.hfstudio.diskterminal.integration.AE2FluidCraftIntegration;

/**
 * Helpers for extracting and representing fluids as item stacks.
 * <p>
 * The 1.12 source used AE2's {@code FluidDummyItem} plus Forge 1.12 {@code FluidUtil}. In 1.7.10 the in-network fluid
 * representation is provided by AE2FluidCraft when installed, and fluid containers are read through
 * {@link FluidContainerRegistry}.
 */
public class FluidStacks {

    private FluidStacks() {}

    /**
     * Extract a {@link FluidStack} from an item stack: an AE2FC fluid-drop, or a vanilla/Forge
     * fluid container. Returns null if the stack carries no fluid.
     */
    public static FluidStack extract(ItemStack stack) {
        if (ItemStacks.isEmpty(stack)) return null;

        FluidStack dropFluid = AE2FluidCraftIntegration.tryExtractFluidDrop(stack);
        if (dropFluid != null) return dropFluid;

        return FluidContainerRegistry.getFluidForFilledItem(stack);
    }

    /**
     * Create the in-network item representation of a fluid.
     */
    public static ItemStack toDisplayStack(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return null;

        return AE2FluidCraftIntegration.tryCreateFluidDrop(fluid);
    }
}
