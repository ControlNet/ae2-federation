package space.controlnet.ae2federation.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

class RuleLinksTest {
    private static final NetworkId A = new NetworkId(new UUID(0, 1));
    private static final NetworkId B = new NetworkId(new UUID(0, 2));
    private static final PolicyKey CRAFTING = new PolicyKey(A, B, PolicyCapability.CRAFTING);
    private static final PolicyKey STORAGE = new PolicyKey(A, B, PolicyCapability.STORAGE);
    private static final PolicyKey REVERSE_STORAGE = new PolicyKey(B, A, PolicyCapability.STORAGE);
    private static final PolicyKey ENERGY = new PolicyKey(A, B, PolicyCapability.ME_POWER);
    private static final PolicyKey REVERSE_ENERGY = new PolicyKey(B, A, PolicyCapability.ME_POWER);

    private final Map<PolicyKey, RuleMode> rules = new HashMap<>();

    private List<RuleLinks.Change> switchTo(PolicyKey key, RuleMode mode) {
        return RuleLinks.of(key, mode, other -> rules.getOrDefault(other, RuleMode.DISABLED));
    }

    @Test
    void craftingOnBringsTheSamePairsStorage() {
        assertEquals(List.of(new RuleLinks.Change(CRAFTING, RuleMode.ENABLED),
                new RuleLinks.Change(STORAGE, RuleMode.ENABLED)), switchTo(CRAFTING, RuleMode.ENABLED));
        assertEquals(List.of(new RuleLinks.Change(CRAFTING, RuleMode.REEXPORT),
                new RuleLinks.Change(STORAGE, RuleMode.ENABLED)), switchTo(CRAFTING, RuleMode.REEXPORT));
    }

    @Test
    void craftingOnKeepsStorageAsItIs() {
        rules.put(STORAGE, RuleMode.REEXPORT);
        assertEquals(List.of(new RuleLinks.Change(CRAFTING, RuleMode.ENABLED)), switchTo(CRAFTING, RuleMode.ENABLED));
    }

    @Test
    void storageOffTakesCraftingOff() {
        rules.put(STORAGE, RuleMode.ENABLED);
        rules.put(CRAFTING, RuleMode.REEXPORT);
        assertEquals(List.of(new RuleLinks.Change(STORAGE, RuleMode.DISABLED),
                new RuleLinks.Change(CRAFTING, RuleMode.DISABLED)), switchTo(STORAGE, RuleMode.DISABLED));
        // Stepping storage between its on states leaves crafting alone.
        assertEquals(List.of(new RuleLinks.Change(STORAGE, RuleMode.REEXPORT)), switchTo(STORAGE, RuleMode.REEXPORT));
    }

    @Test
    void linksStayWithinOneDirection() {
        rules.put(REVERSE_STORAGE, RuleMode.DISABLED);
        assertEquals(List.of(new RuleLinks.Change(CRAFTING, RuleMode.DISABLED)), switchTo(CRAFTING, RuleMode.DISABLED));
        assertEquals(List.of(new RuleLinks.Change(STORAGE, RuleMode.ENABLED)), switchTo(STORAGE, RuleMode.ENABLED));
    }

    @Test
    void energyOffTurnsOffBothWays() {
        rules.put(ENERGY, RuleMode.ENABLED);
        rules.put(REVERSE_ENERGY, RuleMode.ENABLED);
        assertEquals(List.of(new RuleLinks.Change(ENERGY, RuleMode.DISABLED),
                new RuleLinks.Change(REVERSE_ENERGY, RuleMode.DISABLED)), switchTo(ENERGY, RuleMode.DISABLED));
        rules.put(REVERSE_ENERGY, RuleMode.DISABLED);
        assertEquals(List.of(new RuleLinks.Change(ENERGY, RuleMode.DISABLED)), switchTo(ENERGY, RuleMode.DISABLED));
    }
}
