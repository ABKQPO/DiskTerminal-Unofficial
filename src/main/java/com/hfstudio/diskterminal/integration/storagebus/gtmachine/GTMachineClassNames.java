package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

import java.util.Set;

public class GTMachineClassNames {

    public static final String SUPER_INPUT_BUS_ME = "com.science.gtnl.common.machine.hatch.SuperInputBusME";
    public static final String SUPER_INPUT_HATCH_ME = "com.science.gtnl.common.machine.hatch.SuperInputHatchME";
    public static final String OREDICT_INPUT_BUS_ME = "com.science.gtnl.common.machine.hatch.OredictInputBusME";
    public static final String TYPE_FILTERED_INPUT_BUS_ME = "com.science.gtnl.common.machine.hatch.TypeFilteredInputBusME";
    public static final String SUPER_DUAL_INPUT_HATCH_ME = "com.science.gtnl.common.machine.hatch.SuperDualInputHatchME";

    public static final String RESTRICTED_INPUT_BUS_ME = "reobf.proghatches.gt.metatileentity.meinput.RestrictedInputBusME";
    public static final String PRIORITY_FILTER_INPUT_BUS_ME = "reobf.proghatches.gt.metatileentity.meinput.PriorityFilterInputBusME";
    public static final String DECOY_INPUT_BUS_ME = "reobf.proghatches.gt.metatileentity.meinput.DecoyInputBusME";

    public static final String RESTRICTED_INPUT_HATCH_ME = "reobf.proghatches.gt.metatileentity.meinput.RestrictedInputHatchME";
    public static final String PRIORITY_FILTER_INPUT_HATCH_ME = "reobf.proghatches.gt.metatileentity.meinput.PriorityFilterInputHatchME";
    public static final String DECOY_INPUT_HATCH_ME = "reobf.proghatches.gt.metatileentity.meinput.DecoyInputHatchME";

    public static final Set<String> GTNL_ITEM_INPUT_CLASSES = Set
        .of(SUPER_INPUT_BUS_ME, OREDICT_INPUT_BUS_ME, TYPE_FILTERED_INPUT_BUS_ME);

    public static final Set<String> GTNL_SUPER_ITEM_INPUT_CLASSES = Set
        .of(OREDICT_INPUT_BUS_ME, TYPE_FILTERED_INPUT_BUS_ME);

    public static final Set<String> GTNL_FLUID_INPUT_CLASSES = Set.of(SUPER_INPUT_HATCH_ME);

    public static final Set<String> PROGRAMMABLE_ITEM_INPUT_CLASSES = Set
        .of(RESTRICTED_INPUT_BUS_ME, PRIORITY_FILTER_INPUT_BUS_ME, DECOY_INPUT_BUS_ME);

    public static final Set<String> PROGRAMMABLE_FLUID_INPUT_CLASSES = Set
        .of(RESTRICTED_INPUT_HATCH_ME, PRIORITY_FILTER_INPUT_HATCH_ME, DECOY_INPUT_HATCH_ME);

    private GTMachineClassNames() {}
}
