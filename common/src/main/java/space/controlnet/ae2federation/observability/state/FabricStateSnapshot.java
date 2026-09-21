package space.controlnet.ae2federation.observability.state;

import java.util.List;
import java.util.Objects;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.ObservationLimits;

public record FabricStateSnapshot(FabricReference scope, long topologyRevision, long policyRevision, long dataRevision,
        List<MemberState> members, List<ProviderState> providers, List<EndpointState> endpoints,
        List<PolicyState> policies, List<LockState> locks, List<TaskState> tasks, List<FlowState> flows) {
    public FabricStateSnapshot {
        Objects.requireNonNull(scope);
        if (topologyRevision < 0 || policyRevision < 0 || dataRevision < 0) {
            throw new IllegalArgumentException("Observation revisions cannot be negative");
        }
        members = ObservationStateSupport.canonical(scope, members, ObservationLimits.MAX_MEMBERS, "members");
        providers = ObservationStateSupport.canonical(scope, providers, ObservationLimits.MAX_PROVIDERS, "providers");
        endpoints = ObservationStateSupport.canonical(scope, endpoints, ObservationLimits.MAX_ENDPOINTS, "endpoints");
        policies = ObservationStateSupport.canonical(scope, policies, ObservationLimits.MAX_POLICIES, "policies");
        locks = ObservationStateSupport.canonical(scope, locks, ObservationLimits.MAX_LOCKS, "locks");
        tasks = ObservationStateSupport.canonical(scope, tasks, ObservationLimits.MAX_TASKS, "tasks");
        flows = ObservationStateSupport.canonical(scope, flows, ObservationLimits.MAX_FLOWS, "flows");
    }
}
