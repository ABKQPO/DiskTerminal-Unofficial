package com.hfstudio.diskterminal.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Null-safe ItemStack helpers for the 1.7.10 environment, where there is no
 * {@code ItemStack.EMPTY} sentinel and stacks are represented by {@code null}.
 * <p>
 * Centralizes the empty/load conventions so the rest of the port can read like
 * the 1.12 source while compiling against 1.7.10 semantics.
 */
public class ItemStacks {

    private ItemStacks() {}

    /**
     * Returns true when the stack is absent or has no item/size.
     */
    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getItem() == null || stack.stackSize <= 0;
    }

    /**
     * Load an ItemStack from NBT, returning null (the 1.7.10 empty form) when absent.
     */
    public static ItemStack load(NBTTagCompound nbt) {
        if (nbt == null) return null;

        return ItemStack.loadItemStackFromNBT(nbt);
    }

    /**
     * Load an ItemStack for GUI display and normalize its count to one.
     */
    public static ItemStack loadDisplay(NBTTagCompound nbt) {
        if (nbt == null) return null;

        NBTTagCompound copy = (NBTTagCompound) nbt.copy();
        copy.setByte("Count", (byte) 1);
        return ItemStack.loadItemStackFromNBT(copy);
    }

    /**
     * Clear an ItemStack's custom display name (no clearCustomName in 1.7.10).
     */
    public static void clearCustomName(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag.hasKey("display")) {
            NBTTagCompound display = tag.getCompoundTag("display");
            display.removeTag("Name");
            if (display.hasNoTags()) tag.removeTag("display");
        }
    }
}
