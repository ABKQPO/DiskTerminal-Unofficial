package com.hfstudio.diskterminal.gui;

import java.util.List;
import java.util.function.IntFunction;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.FluidStacks;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.storage.data.IAEStack;

/**
 * Utility methods for stack comparison in GUI code.
 */
public class ComparisonUtils {

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

            if (isSamePartitionType(stack, partItem)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isInPartition(ItemStack stack, String contentTypeId, List<ItemStack> partition,
        IntFunction<String> partitionTypeProvider) {
        if (ItemStacks.isEmpty(stack)) return false;

        String normalizedType = normalizeStackTypeId(contentTypeId);
        if ("fluid".equals(normalizedType)) {
            FluidStack targetFluid = FluidStacks.extract(stack);
            if (targetFluid == null || targetFluid.getFluid() == null) return false;

            for (int i = 0; i < partition.size(); i++) {
                ItemStack partItem = partition.get(i);
                if (ItemStacks.isEmpty(partItem)) continue;
                if (!normalizedType.equals(normalizeStackTypeId(partitionTypeProvider.apply(i)))) continue;

                FluidStack partFluid = FluidStacks.extract(partItem);
                if (partFluid != null && partFluid.getFluid() == targetFluid.getFluid()) return true;
            }

            return false;
        }

        for (int i = 0; i < partition.size(); i++) {
            ItemStack partItem = partition.get(i);
            if (ItemStacks.isEmpty(partItem)) continue;
            if (!normalizedType.equals(normalizeStackTypeId(partitionTypeProvider.apply(i)))) continue;

            if (isSamePartitionType(stack, partItem)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isSamePartitionType(ItemStack left, ItemStack right) {
        IAEStack<?> leftStack = AEStackUtil.convertItemUsingRegisteredTypes(left);
        IAEStack<?> rightStack = AEStackUtil.convertItemUsingRegisteredTypes(right);
        if (leftStack != null && rightStack != null) {
            return AEStackUtil.isSameType(leftStack, rightStack);
        }

        return left.isItemEqual(right) && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static String normalizeStackTypeId(String stackTypeId) {
        if ("fluid".equals(stackTypeId)) return "fluid";
        if ("essentia".equals(stackTypeId)) return "essentia";

        return "item";
    }
}
