package space.controlnet.ae2federation.crafting.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;

final class CraftingDependencyCycleGuardTest {
    @Test
    void acceptsAcyclicDependencies() {
        var first = NetworkId.create();
        var second = NetworkId.create();
        var third = NetworkId.create();
        assertEquals(Set.of(), CraftingDependencyCycleGuard.cyclicKeys(Set.of(
                key(first, second), key(second, third))));
    }

    @Test
    void rejectsEveryRelationshipInCyclesWithoutRejectingAcyclicBranches() {
        var first = NetworkId.create();
        var second = NetworkId.create();
        var third = NetworkId.create();
        var branch = NetworkId.create();
        var firstSecond = key(first, second);
        var secondThird = key(second, third);
        var thirdFirst = key(third, first);

        assertEquals(Set.of(firstSecond, secondThird, thirdFirst), CraftingDependencyCycleGuard.cyclicKeys(Set.of(
                firstSecond, secondThird, thirdFirst, key(first, branch))));
    }

    private static PolicyKey key(NetworkId consumer, NetworkId provider) {
        return new PolicyKey(consumer, provider, PolicyCapability.CRAFTING);
    }
}
