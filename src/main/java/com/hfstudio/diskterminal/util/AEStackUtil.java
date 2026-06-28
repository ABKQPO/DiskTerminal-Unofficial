package com.hfstudio.diskterminal.util;

import java.util.Collection;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.opengl.GL11;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Utility methods for AE stack serialization, conversion, and GUI rendering.
 */
public class AEStackUtil {

    private static final String LEGACY_ITEM_KEY = "id";
    private static final String DISPLAY_STACK_KEY = "display";

    /**
     * Writes a stack using AE2's generic stack NBT format.
     */
    public static void writeStackToNBT(NBTTagCompound nbt, IAEStack<?> stack) {
        if (nbt == null || stack == null) return;

        NBTTagCompound generic = stack.toNBTGeneric();
        for (String key : generic.func_150296_c()) {
            nbt.setTag(key, generic.getTag(key));
        }
    }

    /**
     * Reads a stack using AE2's registered stack types, with legacy fallback for older payloads.
     */
    public static IAEStack<?> readStackFromNBT(NBTTagCompound nbt) {
        if (nbt == null || nbt.hasNoTags()) return null;

        if (nbt.hasKey("StackType")) {
            IAEStack<?> stack = IAEStack.fromNBTGeneric(nbt);
            if (stack != null) return stack;
        }

        return readLegacyStackFromNBT(nbt);
    }

    public static Collection<IAEStackType<?>> getRegisteredTypes() {
        return AEStackTypeRegistry.getAllTypes();
    }

    /**
     * Converts an item-like ingredient into the first registered AE stack type that accepts it.
     */
    public static IAEStack<?> convertItemUsingRegisteredTypes(ItemStack stack) {
        if (ItemStacks.isEmpty(stack)) return null;

        ItemStack single = stack.copy();
        single.stackSize = 1;

        for (IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
            IAEStack<?> containerStack = type.getStackFromContainerItem(single);
            if (containerStack != null) return containerStack;

            IAEStack<?> convertedStack = type.convertStackFromItem(single);
            if (convertedStack != null) return convertedStack;
        }

        return AEItemStack.create(single);
    }

    public static IAEStack<?> convertItemForType(ItemStack stack, IAEStackType<?> type) {
        if (ItemStacks.isEmpty(stack) || type == null) return null;

        ItemStack single = stack.copy();
        single.stackSize = 1;

        IAEStack<?> containerStack = type.getStackFromContainerItem(single);
        if (containerStack != null) return containerStack;

        IAEStack<?> convertedStack = type.convertStackFromItem(single);
        if (convertedStack != null) return convertedStack;

        return "item".equals(type.getId()) ? AEItemStack.create(single) : null;
    }

    public static NBTTagCompound writeItemLikePartitionStack(ItemStack stack) {
        if (ItemStacks.isEmpty(stack)) return null;

        NBTTagCompound data = new NBTTagCompound();
        IAEStack<?> aeStack = convertItemUsingRegisteredTypes(stack);
        if (aeStack != null) {
            aeStack.setStackSize(1);
            writeStackToNBT(data, aeStack);
        }

        ItemStack displayStack = stack.copy();
        displayStack.stackSize = 1;
        NBTTagCompound display = new NBTTagCompound();
        displayStack.writeToNBT(display);
        data.setTag(DISPLAY_STACK_KEY, display);

        return data;
    }

    public static IAEStack<?> readPartitionStack(NBTTagCompound data, IAEStackType<?> targetType) {
        if (data == null || data.hasNoTags()) return null;

        IAEStack<?> genericStack = readStackFromNBT(data);
        if (genericStack != null && (targetType == null || genericStack.getStackType() == targetType)) {
            genericStack.setStackSize(1);
            return genericStack;
        }

        ItemStack displayStack = readDisplayStack(data);
        if (ItemStacks.isEmpty(displayStack)) return null;

        IAEStack<?> convertedStack = targetType != null ? convertItemForType(displayStack, targetType)
            : convertItemUsingRegisteredTypes(displayStack);
        if (convertedStack != null) convertedStack.setStackSize(1);

        return convertedStack;
    }

    public static ItemStack readDisplayStack(NBTTagCompound data) {
        if (data == null || !data.hasKey(DISPLAY_STACK_KEY)) return null;

        return ItemStack.loadItemStackFromNBT(data.getCompoundTag(DISPLAY_STACK_KEY));
    }

    /**
     * Gets the display ItemStack exposed by a stack type for NEI and GUI compatibility.
     */
    public static ItemStack getDisplayStack(IAEStack<?> stack) {
        if (stack == null) return null;

        ItemStack displayStack = stack.getItemStackForNEI();
        if (!ItemStacks.isEmpty(displayStack)) return normalizeDisplayStack(displayStack);

        if (stack instanceof IAEItemStack itemStack) return normalizeDisplayStack(itemStack.getItemStack());

        return null;
    }

    public static ItemStack normalizeDisplayStack(ItemStack stack) {
        if (ItemStacks.isEmpty(stack)) return null;

        ItemStack normalized = stack.copy();
        normalized.stackSize = 1;
        return normalized;
    }

    @SideOnly(Side.CLIENT)
    public static void drawStackInGui(IAEStack<?> stack, int x, int y) {
        if (stack == null) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        try {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            stack.drawInGui(Minecraft.getMinecraft(), x, y);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    public static IAEItemStack createItemStack(ItemStack stack) {
        if (ItemStacks.isEmpty(stack)) return null;
        return AEItemStack.create(stack);
    }

    public static long getStackSize(IAEStack<?> stack) {
        return stack != null ? stack.getStackSize() : 0;
    }

    public static boolean isSameType(IAEStack<?> a, IAEStack<?> b) {
        if (a == null || b == null) return a == b;
        return a.isSameType(b);
    }

    public static IAEStack<?> copy(IAEStack<?> stack) {
        return stack != null ? stack.copy() : null;
    }

    @SideOnly(Side.CLIENT)
    public static String getDisplayName(IAEStack<?> stack) {
        if (stack == null) return "";

        String displayName = stack.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) return displayName;

        ItemStack displayStack = getDisplayStack(stack);
        return ItemStacks.isEmpty(displayStack) ? "" : displayStack.getDisplayName();
    }

    private static IAEStack<?> readLegacyStackFromNBT(NBTTagCompound nbt) {
        if (nbt.hasKey(LEGACY_ITEM_KEY)) {
            ItemStack itemStack = ItemStack.loadItemStackFromNBT(nbt);
            if (ItemStacks.isEmpty(itemStack)) return null;

            IAEStack<?> converted = convertItemUsingRegisteredTypes(itemStack);
            if (converted != null) {
                long count = nbt.hasKey("Cnt") ? nbt.getLong("Cnt") : itemStack.stackSize;
                converted.setStackSize(count);
                return converted;
            }
        }

        for (IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
            try {
                IAEStack<?> stack = type.loadStackFromNBT(nbt);
                if (stack != null) return stack;
            } catch (RuntimeException ignored) {
                // Incompatible legacy payload for this registered type.
            }
        }

        return null;
    }
}
