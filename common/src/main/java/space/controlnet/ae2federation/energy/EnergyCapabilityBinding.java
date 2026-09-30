package space.controlnet.ae2federation.energy;

import appeng.api.config.Actionable;
import java.util.function.BooleanSupplier;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.policy.PolicyKey;

public final class EnergyCapabilityBinding {
    private final EnergyRelationship relationship;
    private final NativeEnergyBackend backend;
    private final EnergyBindingRevision revision;
    private final DirectionalEnergySource source;
    private final BooleanSupplier current;
    /** A Grid's services are fixed for its lifetime, so the binding keeps the identity services of its two Grids. */
    private final NetworkIdentityService consumerIdentity;
    private final NetworkIdentityService providerIdentity;
    /** Set once when the service drops this binding; a withdrawn binding is never published again. */
    private boolean withdrawn;

    EnergyCapabilityBinding(EnergyRelationship relationship, NativeEnergyBackend backend,
            EnergyBindingRevision revision, DirectionalEnergySource source, BooleanSupplier current) {
        this.relationship = relationship;
        this.backend = backend;
        this.revision = revision;
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

    double extract(double amount, Actionable mode) {
        if (!isCurrent() || !EnergyRouteGuard.visit(relationship.providerGrid())) {
            return 0;
        }
        return DirectionalEnergyTransfer.extract(backend.service(), amount, mode);
    }
}
