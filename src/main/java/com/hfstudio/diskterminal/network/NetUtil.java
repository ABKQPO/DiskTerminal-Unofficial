package com.hfstudio.diskterminal.network;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Small helper for running packet-handler logic on the server.
 * <p>
 * The 1.12 source scheduled handler bodies onto the server thread via
 * {@code player.getServerWorld().addScheduledTask(...)}. In this 1.7.10 toolchain SimpleImpl
 * handlers already run in a context where directly touching the open container is safe (matching
 * the wider GTNH ecosystem), so this simply runs the task inline.
 */
public class NetUtil {

    private NetUtil() {}

    public static void run(EntityPlayerMP player, Runnable task) {
        task.run();
    }
}
