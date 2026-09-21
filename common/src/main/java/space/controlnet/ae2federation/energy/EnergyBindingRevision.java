package space.controlnet.ae2federation.energy;

import java.util.Set;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.policy.PolicyRevision;

public record EnergyBindingRevision(PolicyRevision policyRevision, long topologyRevision,
        Set<FabricReference> fabrics, EnergyProviderGeneration providerGeneration) {
    public EnergyBindingRevision {
        fabrics = Set.copyOf(fabrics);
    }
}
