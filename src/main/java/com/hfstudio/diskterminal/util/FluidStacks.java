package com.hfstudio.diskterminal.util;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.common.item.ItemFluidDrop;

/**
 * Helpers for extracting and representing fluids as item stacks.
 * <p>
 * The 1.12 source used AE2's {@code FluidDummyItem} plus Forge 1.12 {@code FluidUtil}. In 1.7.10
 * the in-network fluid representation is AE2FluidCraft's {@link ItemFluidDrop}, and fluid containers
 * are read through {@link FluidContainerRegistry}.
 */
public final class FluidStacks {

    private FluidStacks() {}

    /**
     * Extract a {@link FluidStack} from an item stack: an AE2FC fluid-drop, or a vanilla/Forge
     * fluid container. Returns null if the stack carries no fluid.
     */
    public static FluidStack extract(ItemStack stack) {
        if (ItemStacks.isEmpty(stack)) return null;

        if (ItemFluidDrop.isFluidStack(stack)) return ItemFluidDrop.getFluidStack(stack);

        return FluidContainerRegistry.getFluidForFilledItem(stack);
    }

    /**
     * Create the in-network item representation of a fluid (AE2FC fluid drop).
     */
    public static ItemStack toDisplayStack(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return null;

        return ItemFluidDrop.newStack(fluid);
    }
}
