package com.hfstudio.diskterminal.integration;

import cpw.mods.fml.common.Loader;

/**
 * Central registry for mod integration status.
 * Provides cached checks for whether optional mods are loaded.
 */
public final class ModIntegration {

    private ModIntegration() {}

    // Mod IDs
    public static final String AE2_FLUID_CRAFT = "ae2fc";
    public static final String THAUMIC_ENERGISTICS = "thaumicenergistics";
    public static final String MEKANISM = "Mekanism";
    public static final String GREGTECH = "gregtech";
    public static final String NEI = "NotEnoughItems";
    public static final String BAUBLES = "Baubles";
    public static final String WCT = "ae2wct";

    // Cached status (initialized on first access)
    private static Boolean ae2FluidCraftLoaded;
    private static Boolean thaumicEnergisticsLoaded;
    private static Boolean mekanismLoaded;
    private static Boolean gregtechLoaded;
    private static Boolean neiLoaded;
    private static Boolean baublesLoaded;
    private static Boolean wctLoaded;

    /**
     * Check if AE2FluidCraft (fluid storage) is loaded.
     */
    public static boolean isAE2FluidCraftLoaded() {
        if (ae2FluidCraftLoaded == null) {
            ae2FluidCraftLoaded = Loader.isModLoaded(AE2_FLUID_CRAFT);
        }
        return ae2FluidCraftLoaded;
    }

    /**
     * Check if Thaumic Energistics (essentia storage) is loaded.
     */
    public static boolean isThaumicEnergisticsLoaded() {
        if (thaumicEnergisticsLoaded == null) {
            thaumicEnergisticsLoaded = Loader.isModLoaded(THAUMIC_ENERGISTICS);
        }
        return thaumicEnergisticsLoaded;
    }

    /**
     * Check if Mekanism (gas storage) is loaded.
     */
    public static boolean isMekanismLoaded() {
        if (mekanismLoaded == null) {
            mekanismLoaded = Loader.isModLoaded(MEKANISM);
        }
        return mekanismLoaded;
    }

    /**
     * Check if GregTech 5 is loaded.
     */
    public static boolean isGregTechLoaded() {
        if (gregtechLoaded == null) {
            gregtechLoaded = Loader.isModLoaded(GREGTECH);
        }
        return gregtechLoaded;
    }

    /**
     * Check if Not Enough Items is loaded.
     */
    public static boolean isNEILoaded() {
        if (neiLoaded == null) {
            neiLoaded = Loader.isModLoaded(NEI);
        }
        return neiLoaded;
    }

    /**
     * Check if Baubles is loaded.
     */
    public static boolean isBaublesLoaded() {
        if (baublesLoaded == null) {
            baublesLoaded = Loader.isModLoaded(BAUBLES);
        }
        return baublesLoaded;
    }

    /**
     * Check if Wireless Crafting Terminal is loaded.
     */
    public static boolean isWCTLoaded() {
        if (wctLoaded == null) {
            wctLoaded = Loader.isModLoaded(WCT);
        }
        return wctLoaded;
    }

    /**
     * Check if any fluid storage mod is loaded.
     * This determines whether fluid-related UI elements should be shown.
     */
    public static boolean hasFluidStorage() {
        return isAE2FluidCraftLoaded();
    }

    /**
     * Check if any essentia storage mod is loaded.
     * This determines whether essentia-related UI elements should be shown.
     */
    public static boolean hasEssentiaStorage() {
        return isThaumicEnergisticsLoaded();
    }

    /**
     * Check if any gas storage mod is loaded.
     * This determines whether gas-related UI elements should be shown.
     */
    public static boolean hasGasStorage() {
        return isMekanismLoaded();
    }
}
