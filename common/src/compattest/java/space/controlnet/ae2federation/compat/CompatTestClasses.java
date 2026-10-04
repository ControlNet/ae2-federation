package space.controlnet.ae2federation.compat;

import java.util.LinkedHashMap;
import java.util.Map;

/** The GameTest classes a compatibility run can select from, by group name; a run may name a group or a test. */
final class CompatTestClasses {
    private CompatTestClasses() {
    }

    static Map<String, Class<?>> groups() {
        var groups = new LinkedHashMap<String, Class<?>>();
        groups.put("core", CoreCompatGameTests.class);
        groups.put("extendedae", ExtendedAECompatGameTests.class);
        groups.put("extendedae-plus", ExtendedAEPlusCompatGameTests.class);
        groups.put("data-energistics", DataEnergisticsCompatGameTests.class);
        groups.put("ae2-lightning-tech", LightningTechCompatGameTests.class);
        groups.put("ae2-pattern-disk", PatternDiskCompatGameTests.class);
        groups.put("ae2extras", AE2ExtrasCompatGameTests.class);
        groups.put("appmek", AppliedMekanisticsCompatGameTests.class);
        groups.put("megacells", MegaCellsCompatGameTests.class);
        groups.put("advanced-ae", AdvancedAECompatGameTests.class);
        groups.put("create", CreateCompatGameTests.class);
        groups.put("mekanism", MekanismCompatGameTests.class);
        return groups;
    }
}
