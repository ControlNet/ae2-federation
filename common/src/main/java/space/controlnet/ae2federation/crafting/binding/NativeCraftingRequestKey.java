package space.controlnet.ae2federation.crafting.binding;

import java.util.Objects;
import java.util.UUID;
import space.controlnet.ae2federation.policy.PolicyKey;

public record NativeCraftingRequestKey(PolicyKey policyKey, UUID requesterNodeId, int slot) {
    public NativeCraftingRequestKey {
        Objects.requireNonNull(policyKey);
        Objects.requireNonNull(requesterNodeId);
        if (slot < 0) {
            throw new IllegalArgumentException("Native crafting request slot must not be negative");
        }
    }
}
