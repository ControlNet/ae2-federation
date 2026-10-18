package space.controlnet.ae2federation.policy;

import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * A rule change as the Federation screen makes it: the rules linked to it change in the same edit (energy is one pool
 * per pair), and a rule that was never set starts from its capability's defaults.
 */
public final class PolicyChanges {
    private PolicyChanges() {
    }

    public static PolicyRule defaults(PolicyCapability capability) {
        return switch (capability) {
            case STORAGE -> PolicyRule.storageDefaults();
            case CRAFTING -> PolicyRule.enabled(Set.of(PolicyOperation.REQUEST));
            case ME_POWER -> PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY));
        };
    }

    public static RuleMode mode(PolicyService service, PolicyKey key) {
        return service.configured(key).map(record -> RuleMode.of(record.rule())).orElse(RuleMode.DISABLED);
    }

    /**
     * Sets {@code key} to {@code mode}, with {@code edit} applied to its own rule, and the linked rules with it. Empty
     * when nothing would change; otherwise the store's answer.
     */
    public static java.util.Optional<PolicyMutationResult> apply(PolicyService service, PolicyKey key, RuleMode mode,
            UnaryOperator<PolicyRule> edit) {
        var current = service.configured(key).map(record -> record.rule());
        var own = edit.apply(current.orElseGet(() -> defaults(key.capability())).withMode(mode));
        var edits = new java.util.ArrayList<PolicyEdit>();
        if (current.isEmpty() || !current.get().equals(own)) {
            edits.add(new PolicyEdit(key, service.revision(key), own));
        }
        var linked = RuleLinks.of(key, mode, other -> mode(service, other));
        for (var change : linked.subList(1, linked.size())) {
            if (mode(service, change.key()) != change.mode()) {
                edits.add(new PolicyEdit(change.key(), service.revision(change.key()), service.configured(change.key())
                        .map(record -> record.rule()).orElseGet(() -> defaults(change.key().capability()))
                        .withMode(change.mode())));
            }
        }
        return edits.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(service.editAll(List.copyOf(edits)));
    }
}
