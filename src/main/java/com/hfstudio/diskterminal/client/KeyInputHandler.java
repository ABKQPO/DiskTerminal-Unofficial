package com.hfstudio.diskterminal.client;

import com.hfstudio.diskterminal.network.DiskTerminalNetwork;
import com.hfstudio.diskterminal.network.PacketOpenWirelessTerminal;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

/**
 * Handles key input events for keybinds that work outside of GUIs.
 * Registered on the Forge event bus in ClientProxy.
 */
public class KeyInputHandler {

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!KeyBindings.OPEN_WIRELESS_TERMINAL.getKeyBinding()
            .isPressed()) {
            return;
        }

        DiskTerminalNetwork.INSTANCE.sendToServer(new PacketOpenWirelessTerminal());
    }
}
