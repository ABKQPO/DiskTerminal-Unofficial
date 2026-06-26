package com.hfstudio.diskterminal.util;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.glodblock.github.common.item.ItemFluidDrop;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Utility class for working with IAEStack types.
 * Handles serialization, deserialization, and rendering of AE2 storage stacks.
 */
public class AEStackUtil {

    /**
     * Write an IAEStack to NBT, preserving type information.
     */
    public static void writeStackToNBT(NBTTagCompound nbt, IAEStack<?> stack) {
        if (stack == null) return;

        if (stack instanceof IAEItemStack) {
            nbt.setString("type", "item");
            ((IAEItemStack) stack).writeToNBT(nbt);
        } else if (stack instanceof IAEFluidStack) {
            nbt.setString("type", "fluid");
            ((IAEFluidStack) stack).writeToNBT(nbt);
        }
    }

    /**
     * Read an IAEStack from NBT.
     */
    public static IAEStack<?> readStackFromNBT(NBTTagCompound nbt) {
        if (!nbt.hasKey("type")) {
            // Legacy format - try to detect type from keys
            if (nbt.hasKey("FluidName")) {
                return AEFluidStack.loadFluidStackFromNBT(nbt);
            } else if (nbt.hasKey("id")) {
                return AEItemStack.loadItemStackFromNBT(nbt);
            }
            return null;
        }

        String type = nbt.getString("type");
        switch (type) {
            case "item":
                return AEItemStack.loadItemStackFromNBT(nbt);
            case "fluid":
                return AEFluidStack.loadFluidStackFromNBT(nbt);
            default:
                return null;
        }
    }

    /**
     * Get a display ItemStack for an IAEStack.
     * Used for compatibility with code that needs ItemStack representation.
     */
    public static ItemStack getDisplayStack(IAEStack<?> stack) {
        if (stack == null) return null;

        if (stack instanceof IAEItemStack) {
            return ((IAEItemStack) stack).getItemStack();
        } else if (stack instanceof IAEFluidStack) {
            return ItemFluidDrop.newStack(((IAEFluidStack) stack).getFluidStack());
        }

        return null;
    }

    /**
     * Render an IAEStack in GUI using AE2's native rendering.
     * This properly handles all stack types (items, fluids, essentia, etc.).
     */
    @SideOnly(Side.CLIENT)
    public static void drawStackInGui(IAEStack<?> stack, int x, int y) {
        if (stack == null) return;
        stack.drawInGui(Minecraft.getMinecraft(), x, y);
    }

    /**
     * Create an IAEItemStack from a regular ItemStack.
     */
    public static IAEItemStack createItemStack(ItemStack stack) {
        if (ItemStacks.isEmpty(stack)) return null;
        return AEItemStack.create(stack);
    }

    /**
     * Get the stack size/amount for display.
     */
    public static long getStackSize(IAEStack<?> stack) {
        return stack != null ? stack.getStackSize() : 0;
    }

    /**
     * Check if two IAEStacks are equal (ignoring stack size).
     */
    public static boolean isSameType(IAEStack<?> a, IAEStack<?> b) {
        if (a == null || b == null) return a == b;
        return a.isSameType(b);
    }

    /**
     * Create a copy of an IAEStack.
     */
    public static IAEStack<?> copy(IAEStack<?> stack) {
        return stack != null ? stack.copy() : null;
    }
}
