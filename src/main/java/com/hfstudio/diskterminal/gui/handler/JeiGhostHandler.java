package com.hfstudio.diskterminal.gui.handler;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.gui.overlay.MessageHelper;
import com.hfstudio.diskterminal.integration.ThaumicEnergisticsIntegration;
import com.hfstudio.diskterminal.util.FluidStacks;
import com.hfstudio.diskterminal.util.ItemStacks;

/**
 * Converts a dragged ingredient (from NEI, or any provider) into the ItemStack representation a
 * cell/storage-bus partition expects.
 * <p>
 * The 1.12 source bound this to JEI's ghost-ingredient API and Mekanism gas. Here it is
 * provider-neutral: it accepts {@link ItemStack} and {@link FluidStack} ingredients (NEI hands
 * both), maps fluids to the AE2FluidCraft drop representation, and routes essentia containers
 * through Thaumic Energistics. Gas support is dropped (no Mekanism).
 */
public class JeiGhostHandler {

    private JeiGhostHandler() {}

    /**
     * Convert an ingredient to the ItemStack a cell partition of {@code cellType} expects.
     * Returns null when the ingredient is incompatible.
     */
    public static ItemStack convertJeiIngredientToItemStack(Object ingredient, StorageType cellType) {
        return convert(ingredient, cellType, false);
    }

    /**
     * Convert an ingredient to the ItemStack a storage bus partition of {@code busType} expects.
     */
    public static ItemStack convertJeiIngredientForStorageBus(Object ingredient, StorageType busType) {
        return convert(ingredient, busType, true);
    }

    private static ItemStack convert(Object ingredient, StorageType type, boolean bus) {
        if (ingredient == null) return null;

        if (ingredient instanceof ItemStack) {
            ItemStack itemStack = (ItemStack) ingredient;

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
}
