package com.hfstudio.diskterminal.gui;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.hfstudio.diskterminal.util.FluidStacks;
import com.hfstudio.diskterminal.util.ItemStacks;

/**
 * Utility methods for stack comparison in GUI code.
 */
public final class ComparisonUtils {

    private ComparisonUtils() {}

    /**
     * Check if an item stack is in the partition list.
     * For fluid items, compares by fluid type only (ignoring amount and NBT). For other items, uses
     * item and NBT comparison (ignoring count), since partitioning normalizes stacks to count 1.
     */
    public static boolean isInPartition(ItemStack stack, List<ItemStack> partition) {
        if (ItemStacks.isEmpty(stack)) return false;

        FluidStack targetFluid = FluidStacks.extract(stack);

        if (targetFluid != null && targetFluid.getFluid() != null) {
            for (ItemStack partItem : partition) {
                if (ItemStacks.isEmpty(partItem)) continue;

                FluidStack partFluid = FluidStacks.extract(partItem);

                if (partFluid != null && partFluid.getFluid() == targetFluid.getFluid()) return true;
            }

            return false;
        }

        for (ItemStack partItem : partition) {
            if (ItemStacks.isEmpty(partItem)) continue;

            if (stack.isItemEqual(partItem) && ItemStack.areItemStackTagsEqual(stack, partItem)) {
                return true;
            }
        }

        return false;
    }
}
