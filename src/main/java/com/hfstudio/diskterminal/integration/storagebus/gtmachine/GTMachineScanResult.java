package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;

public record GTMachineScanResult(NBTTagCompound busData, StorageBusTracker tracker) {}
