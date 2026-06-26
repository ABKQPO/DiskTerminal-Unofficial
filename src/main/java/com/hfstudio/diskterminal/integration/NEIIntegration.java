package com.hfstudio.diskterminal.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;

/**
 * Soft integration with NotEnoughItems. Provides the item stack currently hovered in the NEI
 * item-list overlay, used by quick-partition and ghost-drag. All access is guarded by
 * {@link Optional.Method}; when NEI is absent the methods return null.
 */
public class NEIIntegration {

    public static final String MOD_ID = "NotEnoughItems";

    private static Boolean loaded = null;

    private NEIIntegration() {}

    public static boolean isModLoaded() {
        if (loaded == null) loaded = Loader.isModLoaded(MOD_ID);

        return loaded;
    }

    /**
     * Get the stack hovered in the NEI overlay (item list / bookmarks), or null.
     */
    public static ItemStack getStackUnderMouse() {
        if (!isModLoaded()) return null;

        return queryNei();
    }

    @Optional.Method(modid = MOD_ID)
    private static ItemStack queryNei() {
        try {
            if (!(Minecraft.getMinecraft().currentScreen instanceof GuiContainer)) return null;

            return GuiContainerManager.getStackMouseOver((GuiContainer) Minecraft.getMinecraft().currentScreen);
        } catch (Exception e) {
            return null;
        }
    }
}
