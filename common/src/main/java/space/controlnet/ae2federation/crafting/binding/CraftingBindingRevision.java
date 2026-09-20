package space.controlnet.ae2federation.crafting.binding;

import java.util.Objects;
import java.util.Set;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.policy.PolicyRevision;

public record CraftingBindingRevision(PolicyRevision policyRevision, long topologyRevision,
        Set<FabricReference> fabrics, ProviderGeneration providerGeneration) {
    public CraftingBindingRevision {
        Objects.requireNonNull(policyRevision);
        Objects.requireNonNull(providerGeneration);
        fabrics = Set.copyOf(fabrics);
        if (policyRevision.equals(PolicyRevision.NONE) || topologyRevision < 1 || fabrics.isEmpty()) {
            throw new IllegalArgumentException("Crafting binding revision requires current Policy and Fabric evidence");
        }
    }
}
