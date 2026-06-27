package com.hfstudio.diskterminal.gui.handler;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.gui.overlay.MessageHelper;
import com.hfstudio.diskterminal.integration.ThaumicEnergisticsIntegration;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.FluidStacks;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

/**
 * Converts external ghost ingredients into the ItemStack representation expected by AE2 config inventories.
 */
public class GhostIngredientHandler {

    private GhostIngredientHandler() {}

    public static ItemStack convertIngredientForCell(Object ingredient, StorageType cellType) {
        return convert(ingredient, cellType, false);
    }

    public static ItemStack convertIngredientForStorageBus(Object ingredient, StorageType busType) {
        return convert(ingredient, busType, true);
    }

    public static ItemStack convertIngredientForType(Object ingredient, String stackTypeId, boolean bus) {
        if (ingredient instanceof ItemStack itemStack) {
            ItemStack stack = convertItemForType(itemStack, stackTypeId);
            if (!ItemStacks.isEmpty(stack)) return stack;
        }

        return convert(ingredient, storageTypeFrom(stackTypeId), bus);
    }

    private static ItemStack convert(Object ingredient, StorageType type, boolean bus) {
        if (ingredient == null) return null;

        if (ingredient instanceof ItemStack itemStack) {

            if (type.isEssentia()) {
                ItemStack essentiaRep = ThaumicEnergisticsIntegration.tryConvertEssentiaContainerToAspect(itemStack);
                if (!ItemStacks.isEmpty(essentiaRep)) return essentiaRep;

                MessageHelper
                    .error(bus ? "disk_terminal.error.essentia_bus_item" : "disk_terminal.error.essentia_cell_item");
                return null;
            }

            if (type.isFluid()) {
                FluidStack contained = FluidStacks.extract(itemStack);
                if (contained == null) {
                    MessageHelper
                        .error(bus ? "disk_terminal.error.fluid_bus_item" : "disk_terminal.error.fluid_cell_item");
                    return null;
                }

                return FluidStacks.toDisplayStack(contained);
            }

            return itemStack;
        }

        if (ingredient instanceof FluidStack) {
            if (type.isEssentia()) {
                MessageHelper
                    .error(bus ? "disk_terminal.error.essentia_bus_fluid" : "disk_terminal.error.essentia_cell_fluid");
                return null;
            }

            if (!type.isFluid()) {
                MessageHelper.error(bus ? "disk_terminal.error.item_bus_fluid" : "disk_terminal.error.item_cell_fluid");
                return null;
            }

            return FluidStacks.toDisplayStack((FluidStack) ingredient);
        }

        return null;
    }

    private static ItemStack convertItemForType(ItemStack itemStack, String stackTypeId) {
        IAEStackType<?> type = AEStackTypeRegistry.getType(stackTypeId);
        IAEStack<?> stack = AEStackUtil.convertItemForType(itemStack, type);
        return AEStackUtil.getDisplayStack(stack);
    }

    private static StorageType storageTypeFrom(String stackTypeId) {
        if ("fluid".equals(stackTypeId)) return StorageType.FLUID;
        if ("essentia".equals(stackTypeId)) return StorageType.ESSENTIA;

        return StorageType.ITEM;
    }
}
