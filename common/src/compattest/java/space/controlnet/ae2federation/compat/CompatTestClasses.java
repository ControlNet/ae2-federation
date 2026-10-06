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
        groups.put("sophisticated-storage", SophisticatedStorageCompatGameTests.class);
        groups.put("functional-storage", FunctionalStorageCompatGameTests.class);
        groups.put("appflux", AppliedFluxCompatGameTests.class);
        groups.put("appflux-mekanism", AppliedFluxMekanismCompatGameTests.class);
        groups.put("appflux-createaddition", AppliedFluxCreateAdditionCompatGameTests.class);
        groups.put("appflux-enderio", AppliedFluxEnderIOCompatGameTests.class);
        groups.put("appflux-industrialforegoing", AppliedFluxIndustrialForegoingCompatGameTests.class);
        groups.put("omnisequence", OmniSequenceCompatGameTests.class);
        groups.put("expandedae", ExpandedAECompatGameTests.class);
        groups.put("neoecoae", NeoEcoCompatGameTests.class);
        return groups;
    }
}
