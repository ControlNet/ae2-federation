package space.controlnet.ae2federation.crafting.binding;

import appeng.api.stacks.AEKey;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyResource;

/** Whether a Crafting rule's resource filter lets the consumer request a key. */
public final class CraftingPolicyFilter {
    private CraftingPolicyFilter() {
    }

    public static boolean permits(PolicyFilter filter, AEKey key) {
        var resource = new PolicyResource(key.getType().getId(), key.getId());
        return switch (filter.mode()) {
            case ALL -> true;
            case ALLOW_LIST -> filter.entries().contains(resource);
            case DENY_LIST -> !filter.entries().contains(resource);
        };
    }
}
