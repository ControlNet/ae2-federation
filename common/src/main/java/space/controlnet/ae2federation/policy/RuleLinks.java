package space.controlnet.ae2federation.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The rules one switch changes together. A crafting rule needs the same direction's storage rule, because the crafting
 * network's CPU takes the other network's materials through it: turning crafting on turns that storage on, and
 * turning storage off turns that crafting off. Energy is one pool per pair, so turning it off turns off both ways.
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
        var consumer = key.consumerNetworkId();
        var provider = key.providerNetworkId();
        switch (key.capability()) {
            case CRAFTING -> {
                var storage = new PolicyKey(consumer, provider, PolicyCapability.STORAGE);
                if (mode.enabled() && !current.apply(storage).enabled()) {
                    changes.add(new Change(storage, RuleMode.ENABLED));
                }
            }
            case STORAGE -> {
                var crafting = new PolicyKey(consumer, provider, PolicyCapability.CRAFTING);
                if (!mode.enabled() && current.apply(crafting).enabled()) {
                    changes.add(new Change(crafting, RuleMode.DISABLED));
                }
            }
            case ME_POWER -> {
                var reverse = new PolicyKey(provider, consumer, PolicyCapability.ME_POWER);
                if (!mode.enabled() && current.apply(reverse).enabled()) {
                    changes.add(new Change(reverse, RuleMode.DISABLED));
                }
            }
        }
        return List.copyOf(changes);
    }
}
