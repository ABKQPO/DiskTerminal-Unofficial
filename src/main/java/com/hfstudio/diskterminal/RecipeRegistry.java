package com.hfstudio.diskterminal;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.definitions.IBlocks;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IMaterials;
import appeng.api.definitions.IParts;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Registers crafting recipes for Disk Terminal items.
 * Called during init (after items and AE2 definitions are available).
 */
public class RecipeRegistry {

    private RecipeRegistry() {}

    public static void init() {
        IDefinitions defs = AEApi.instance()
            .definitions();
        IMaterials mats = defs.materials();
        IParts parts = defs.parts();
        IBlocks blocks = defs.blocks();

        ItemStack terminal = parts.terminal()
            .maybeStack(1)
            .orNull();
        ItemStack logicProcessor = mats.logicProcessor()
            .maybeStack(1)
            .orNull();
        ItemStack emptyCell = mats.emptyStorageCell()
            .maybeStack(1)
            .orNull();
        ItemStack wirelessPart = mats.wireless()
            .maybeStack(1)
            .orNull();
        ItemStack denseEnergyCell = blocks.energyCellDense()
            .maybeStack(1)
            .orNull();

        ItemStack cellTerminal = new ItemStack(ItemRegistry.CELL_TERMINAL);
        ItemStack wirelessCellTerminal = new ItemStack(ItemRegistry.WIRELESS_CELL_TERMINAL);

        // Cell Terminal: shapeless [Terminal + Logic Processor + Empty Storage Cell]
        if (terminal != null && logicProcessor != null && emptyCell != null) {
            GameRegistry.addShapelessRecipe(cellTerminal, terminal, logicProcessor, emptyCell);
        }

        // Wireless Cell Terminal: shaped [Wireless / CellTerminal / Dense Energy Cell]
        if (wirelessPart != null && denseEnergyCell != null) {
            GameRegistry.addRecipe(
                wirelessCellTerminal,
                "a",
                "b",
                "c",
                'a',
                wirelessPart,
                'b',
                cellTerminal,
                'c',
                denseEnergyCell);
        }
    }
}
