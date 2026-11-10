package space.controlnet.ae2federation.observability.state;

import java.util.List;
import java.util.Objects;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.ObservationLimits;

/**
 * One domain's observed state. The constructor rejects lists over their bounds, as a decoded payload must; a projection
 * of a live domain, which has no size limit, goes through {@link #bounded} and records what it left out in
 * {@link #omitted}.
 */
public record FederationDomainStateSnapshot(FederationDomainReference scope, long topologyRevision, long policyRevision, long dataRevision,
        List<MemberState> members, List<ProviderState> providers, List<EndpointState> endpoints,
        List<PolicyState> policies, List<LockState> locks, List<TaskState> tasks, List<FlowState> flows, Omitted omitted) {
    public FederationDomainStateSnapshot {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(omitted);
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

    /** A snapshot that left nothing out. */
    public FederationDomainStateSnapshot(FederationDomainReference scope, long topologyRevision, long policyRevision,
            long dataRevision, List<MemberState> members, List<ProviderState> providers, List<EndpointState> endpoints,
            List<PolicyState> policies, List<LockState> locks, List<TaskState> tasks, List<FlowState> flows) {
        this(scope, topologyRevision, policyRevision, dataRevision, members, providers, endpoints, policies, locks, tasks,
                flows, Omitted.NONE);
    }

    /**
     * A snapshot of lists of any length: each keeps its first entries in canonical order up to its bound and while the
     * payload stays under {@link ObservationLimits#MAX_PAYLOAD_BYTES}; the rest are counted in {@link #omitted}.
     */
    public static FederationDomainStateSnapshot bounded(FederationDomainReference scope, long topologyRevision,
            long policyRevision, long dataRevision, List<MemberState> members, List<ProviderState> providers,
            List<EndpointState> endpoints, List<PolicyState> policies, List<LockState> locks, List<TaskState> tasks,
            List<FlowState> flows) {
        var budget = new ObservationStateSupport.PayloadBudget(
                ObservationLimits.MAX_PAYLOAD_BYTES - ObservationLimits.PAYLOAD_HEADER_RESERVE);
        var keptMembers = ObservationStateSupport.bounded(members, ObservationLimits.MAX_MEMBERS, budget);
        var keptProviders = ObservationStateSupport.bounded(providers, ObservationLimits.MAX_PROVIDERS, budget);
        var keptEndpoints = ObservationStateSupport.bounded(endpoints, ObservationLimits.MAX_ENDPOINTS, budget);
        var keptPolicies = ObservationStateSupport.bounded(policies, ObservationLimits.MAX_POLICIES, budget);
        var keptLocks = ObservationStateSupport.bounded(locks, ObservationLimits.MAX_LOCKS, budget);
        var keptTasks = ObservationStateSupport.bounded(tasks, ObservationLimits.MAX_TASKS, budget);
        var keptFlows = ObservationStateSupport.bounded(flows, ObservationLimits.MAX_FLOWS, budget);
        return new FederationDomainStateSnapshot(scope, topologyRevision, policyRevision, dataRevision, keptMembers,
                keptProviders, keptEndpoints, keptPolicies, keptLocks, keptTasks, keptFlows, new Omitted(
                        members.size() - keptMembers.size(), providers.size() - keptProviders.size(),
                        endpoints.size() - keptEndpoints.size(), policies.size() - keptPolicies.size(),
                        locks.size() - keptLocks.size(), tasks.size() - keptTasks.size(), flows.size() - keptFlows.size()));
    }

    /** How many entries of each list a bounded snapshot left out. */
    public record Omitted(int members, int providers, int endpoints, int policies, int locks, int tasks, int flows) {
        public static final Omitted NONE = new Omitted(0, 0, 0, 0, 0, 0, 0);

        public Omitted {
            if (members < 0 || providers < 0 || endpoints < 0 || policies < 0 || locks < 0 || tasks < 0 || flows < 0) {
                throw new IllegalArgumentException("Omitted observation counts cannot be negative");
            }
        }
    }
}
