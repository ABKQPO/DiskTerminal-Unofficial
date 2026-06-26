package com.hfstudio.diskterminal.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;

/**
 * Keybindings for the Disk Terminal. All default to NONE (unbound) to avoid conflicts.
 * <p>
 * 1.7.10 has no KeyConflictContext / KeyModifier (those are Forge 1.8+), so bindings are plain
 * description/keyCode/category entries.
 */
public enum KeyBindings {

    OPEN_WIRELESS_TERMINAL(new KeyBinding(
        "key.disk_terminal.open_wireless_terminal.desc",
        Keyboard.KEY_NONE,
        "key.disk_terminal.category")),

    SUBNET_OVERVIEW_TOGGLE(new KeyBinding(
        "key.disk_terminal.subnet_overview_toggle.desc",
        Keyboard.KEY_NONE,
        "key.disk_terminal.category")),

    QUICK_PARTITION_AUTO(
        new KeyBinding("key.disk_terminal.quick_partition_auto.desc", Keyboard.KEY_NONE, "key.disk_terminal.category")),

    QUICK_PARTITION_ITEM(
        new KeyBinding("key.disk_terminal.quick_partition_item.desc", Keyboard.KEY_NONE, "key.disk_terminal.category")),

    QUICK_PARTITION_FLUID(new KeyBinding(
        "key.disk_terminal.quick_partition_fluid.desc",
        Keyboard.KEY_NONE,
        "key.disk_terminal.category")),

    QUICK_PARTITION_ESSENTIA(new KeyBinding(
        "key.disk_terminal.quick_partition_essentia.desc",
        Keyboard.KEY_NONE,
        "key.disk_terminal.category")),

    ADD_TO_STORAGE_BUS(
        new KeyBinding("key.disk_terminal.add_to_storage_bus.desc", Keyboard.KEY_NONE, "key.disk_terminal.category"));

    private final KeyBinding keyBinding;

    KeyBindings(KeyBinding keyBinding) {
        this.keyBinding = keyBinding;
    }

    public KeyBinding getKeyBinding() {
        return keyBinding;
    }

    /**
     * Check if this keybinding matches the given key code.
     */
    public boolean isActiveAndMatches(int keyCode) {
        return keyCode != Keyboard.KEY_NONE && keyBinding.getKeyCode() == keyCode;
    }

    /**
     * Check if this keybinding is bound (not NONE).
     */
    public boolean isBound() {
        return keyBinding.getKeyCode() != Keyboard.KEY_NONE;
    }

    /**
     * Get the display name (key label) for this keybinding.
     */
    public String getDisplayName() {
        return Keyboard.getKeyName(keyBinding.getKeyCode());
    }

    /**
     * Register all keybindings with Forge.
     */
    public static void registerAll() {
        for (KeyBindings kb : values()) ClientRegistry.registerKeyBinding(kb.getKeyBinding());
    }
}
