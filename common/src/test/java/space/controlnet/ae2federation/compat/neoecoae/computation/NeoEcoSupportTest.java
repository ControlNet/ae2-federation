package space.controlnet.ae2federation.compat.neoecoae.computation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.client.guide.GuideExamplePacks;

/**
 * The computation package registers only beside the Neo ECO and Prototype versions it is compiled against: 21.2.15
 * changed the builder screen's API, and an older one crashed the server when an ECO - N controller was opened
 * (2026-11-24). The guide's ECO - N examples require the same versions, so they never show blocks that are missing.
 */
final class NeoEcoSupportTest {
    @Test
    void olderNeoEcoAndPrototypeAreLeftOut() {
        assertFalse(NeoEcoSupport.atLeast("21.2.11", NeoEcoSupport.MINIMUM));
        assertFalse(NeoEcoSupport.atLeast("21.2.14", NeoEcoSupport.MINIMUM));
        assertFalse(NeoEcoSupport.atLeast("21.2.15-beta1", NeoEcoSupport.MINIMUM));
        assertTrue(NeoEcoSupport.atLeast("21.2.15", NeoEcoSupport.MINIMUM));
        assertTrue(NeoEcoSupport.atLeast("21.3.0", NeoEcoSupport.MINIMUM));
        assertFalse(NeoEcoSupport.atLeast("1.4.2", NeoEcoSupport.PROTOTYPE_MINIMUM));
        assertTrue(NeoEcoSupport.atLeast("1.4.6", NeoEcoSupport.PROTOTYPE_MINIMUM));
    }

    @Test
    void theGuideExamplesRequireTheSameVersions() {
        var neoEco = NeoEcoSupport.MOD_ID + "@" + NeoEcoSupport.MINIMUM;
        var prototype = NeoEcoSupport.PROTOTYPE_MOD_ID + "@" + NeoEcoSupport.PROTOTYPE_MINIMUM;
        var packs = Map.of("guide_neoecoae", new String[] {neoEco},
                "guide_neoecoprototype_computing", new String[] {neoEco, prototype});
        packs.forEach((id, required) -> {
            var pack = GuideExamplePacks.ALL.stream().filter(each -> each.id().equals(id)).findFirst().orElseThrow();
            for (var requirement : required) {
                assertTrue(pack.requiredMods().contains(requirement), id + " requires " + requirement);
            }
        });
    }
}
