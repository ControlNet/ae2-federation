package space.controlnet.ae2federation.energy;

import appeng.api.config.Actionable;
import java.util.function.BooleanSupplier;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.policy.PolicyKey;

public final class EnergyCapabilityBinding {
    private final EnergyRelationship relationship;
    private final NativeEnergyBackend backend;
    private final EnergyBindingRevision revision;
    /** The revision's domain references in the set's own order, as an indexed list each accepted demand records into. */
    private final java.util.List<space.controlnet.ae2federation.domain.FederationDomainReference> scopes;
    private final DirectionalEnergySource source;
    private final BooleanSupplier current;
    /** A Grid's services are fixed for its lifetime, so the binding keeps the identity services of its two Grids. */
    private final NetworkIdentityService consumerIdentity;
    private final NetworkIdentityService providerIdentity;
    /** Set once when the service drops this binding; a withdrawn binding is never published again. */
    private boolean withdrawn;
    /**
     * The level registry and its topology revision, and the policy service and its rule watermark, at the last full
     * authority check this binding passed. Rules change only with the watermark and domains only with the topology
     * revision, so while all four are unchanged the rule and domain parts of that check still hold.
     */
    private Object checkedRegistry;
    private long checkedTopology;
    private Object checkedPolicies;
    private long checkedWatermark;
    /**
     * The {@link space.controlnet.ae2federation.identity.IdentityEpoch} when both Grids' settlements last matched
     * this binding's key: while it is unchanged they would return what matched.
     */
    private long matchedEpoch = -1;

    EnergyCapabilityBinding(EnergyRelationship relationship, NativeEnergyBackend backend,
            EnergyBindingRevision revision, DirectionalEnergySource source, BooleanSupplier current) {
        this.relationship = relationship;
        this.backend = backend;
        this.revision = revision;
        this.scopes = java.util.List.copyOf(revision.federationDomains());
        this.source = source;
        this.current = current;
        consumerIdentity = relationship.consumerGrid().getService(NetworkIdentityService.class);
        providerIdentity = relationship.providerGrid().getService(NetworkIdentityService.class);
    }

    NetworkIdentityService consumerIdentity() {
        return consumerIdentity;
    }

    NetworkIdentityService providerIdentity() {
        return providerIdentity;
    }

    boolean matchedAt(long epoch) {
        return epoch == matchedEpoch;
    }

    void matched(long epoch) {
        matchedEpoch = epoch;
    }

    boolean passedAt(Object registry, long topology, Object policies, long watermark) {
        return registry == checkedRegistry && topology == checkedTopology && policies == checkedPolicies
                && watermark == checkedWatermark;
    }

    void passed(Object registry, long topology, Object policies, long watermark) {
        checkedRegistry = registry;
        checkedTopology = topology;
        checkedPolicies = policies;
        checkedWatermark = watermark;
    }

    boolean withdrawn() {
        return withdrawn;
    }

    void withdraw() {
        withdrawn = true;
    }

    public boolean isCurrent() {
        return current.getAsBoolean();
    }

    PolicyKey key() {
        return relationship.key();
    }

    public java.util.List<space.controlnet.ae2federation.domain.FederationDomainReference> scopes() {
        return scopes;
    }

    public EnergyBindingRevision revision() {
        return revision;
    }

    public appeng.api.networking.IGrid consumerGrid() {
        return relationship.consumerGrid();
    }

    public appeng.api.networking.IGrid providerGrid() {
        return relationship.providerGrid();
    }

    public appeng.api.networking.energy.IEnergyService providerService() {
        return backend.service();
    }

    public java.util.List<NativeEnergySource> providerSources() {
        return isCurrent() ? backend.sources() : java.util.List.of();
    }

    public DirectionalEnergySource consumerSource() {
        return source;
    }

    double extract(double amount, Actionable mode, EnergyRouteGuard.Demand guard) {
        if (!isCurrent() || !guard.visit(relationship.providerGrid())) {
            return 0;
        }
        return DirectionalEnergyTransfer.extract(backend.service(), amount, mode);
    }
}
