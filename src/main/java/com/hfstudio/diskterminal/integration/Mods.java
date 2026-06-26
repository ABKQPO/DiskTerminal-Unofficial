package com.hfstudio.diskterminal.integration;

import java.util.Locale;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.util.data.IMod;
import com.gtnewhorizon.gtnhmixins.builders.ITargetMod;
import com.gtnewhorizon.gtnhmixins.builders.TargetModBuilder;

import appeng.api.storage.data.AEStackTypeRegistry;
import cpw.mods.fml.common.Loader;

public enum Mods implements IMod, ITargetMod {

    // spotless:off
    NotEnoughItems("NotEnoughItems"),
    AE2FluidCraft("ae2fc"),
    ThaumicEnergistics("thaumicenergistics"),
    Baubles("Baubles", () -> Loader.isModLoaded("Baubles") || Loader.isModLoaded("Baubles|Expanded"), null),
    WirelessCraftingTerminal("ae2wct"),
    Thaumcraft("thaumcraft"),
    AE2("appliedenergistics2"),
    GregTech("gregtech"),
    ;
    // spotless:on

    public static final String AE2_FLUID_CRAFT = "ae2fc";
    public static final String THAUMIC_ENERGISTICS = "thaumicenergistics";
    public static final String BAUBLES = "Baubles";
    public static final String BAUBLES_EXPANDED = "Baubles|Expanded";
    public static final String ITEM_STACK_TYPE = "item";
    public static final String FLUID_STACK_TYPE = "fluid";
    public static final String ESSENTIA_STACK_TYPE = "essentia";

    public final String modid;
    public final String resourceDomain;
    private final Supplier<Boolean> supplier;
    private final TargetModBuilder targetBuilder;
    private Boolean loaded;

    Mods(String modid) {
        this(modid, null, null);
    }

    Mods(Supplier<Boolean> supplier) {
        this(null, supplier, null);
    }

    Mods(String modid, Supplier<Boolean> supplier, String coreModClass) {
        this.modid = modid;
        this.resourceDomain = modid != null ? modid.toLowerCase(Locale.ENGLISH) : null;
        this.supplier = supplier;
        this.targetBuilder = new TargetModBuilder().setModId(modid)
            .setCoreModClass(coreModClass);
    }

    @NotNull
    @Override
    public TargetModBuilder getBuilder() {
        return targetBuilder;
    }

    @Override
    public boolean isModLoaded() {
        if (loaded == null) {
            if (supplier != null) {
                loaded = supplier.get();
            } else if (modid != null) {
                loaded = Loader.isModLoaded(modid);
            } else loaded = false;
        }
        return loaded;
    }

    @Override
    public String getID() {
        return modid;
    }

    @Override
    public String getResourceLocation() {
        return resourceDomain;
    }

    public static boolean hasItemStorage() {
        return isStackTypeRegistered(ITEM_STACK_TYPE);
    }

    public static boolean hasFluidStorage() {
        return isStackTypeRegistered(FLUID_STACK_TYPE) && AE2FluidCraft.isModLoaded();
    }

    public static boolean hasEssentiaStorage() {
        return isStackTypeRegistered(ESSENTIA_STACK_TYPE) && ThaumicEnergistics.isModLoaded();
    }

    public static boolean isStackTypeRegistered(String id) {
        return AEStackTypeRegistry.getType(id) != null;
    }
}
