package space.controlnet.ae2federation.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The rules one switch changes together. Energy is one pool per pair, so turning it off turns off both ways. Crafting
 * and storage change alone: the consumer's CPU pushes to the provider's pattern providers and gets the results back
 * without the storage rule, which only adds the provider's items to what the CPU can use.
 */
public final class RuleLinks {
    public record Change(PolicyKey key, RuleMode mode) {
    }

    private RuleLinks() {
    }

    /** The requested change first, then each linked rule whose mode must change with it. */
    public static List<Change> of(PolicyKey key, RuleMode mode, Function<PolicyKey, RuleMode> current) {
        var changes = new ArrayList<Change>();
        changes.add(new Change(key, mode));
        if (key.capability() == PolicyCapability.ME_POWER) {
            var reverse = new PolicyKey(key.providerNetworkId(), key.consumerNetworkId(), PolicyCapability.ME_POWER);
            if (!mode.enabled() && current.apply(reverse).enabled()) {
                changes.add(new Change(reverse, RuleMode.DISABLED));
            }
        }
        return List.copyOf(changes);
    }
}
