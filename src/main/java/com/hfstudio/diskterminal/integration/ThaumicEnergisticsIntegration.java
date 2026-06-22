package com.hfstudio.diskterminal.integration;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.storage.ICellHandler;

import cpw.mods.fml.common.Loader;

/**
 * Soft integration hook for Thaumic Energistics essentia cells.
 * <p>
 * Essentia is a third storage channel registered into AE2's {@code AEStackTypeRegistry} by
 * Thaumic Energistics. This class isolates all reflective/optional access so the core data
 * handler stays decoupled. When Thaumic Energistics is absent, every method is a no-op.
 */
public final class ThaumicEnergisticsIntegration {

    public static final String MOD_ID = "thaumicenergistics";

    private static Boolean loaded = null;

    private ThaumicEnergisticsIntegration() {}

    public static boolean isModLoaded() {
        if (loaded == null) loaded = Loader.isModLoaded(MOD_ID);

        return loaded;
    }

    /**
     * Try to populate essentia cell data for the given cell stack.
     *
     * @return an NBT compound with essentia content/stats, or null if this is not an essentia cell
     *         or Thaumic Energistics is not present.
     */
    public static NBTTagCompound tryPopulateEssentiaCell(ICellHandler cellHandler, ItemStack cellStack, int slotLimit) {
        if (!isModLoaded()) return null;

        // Filled in by the full Thaumic Energistics integration (Phase 5).
        return null;
    }
}
