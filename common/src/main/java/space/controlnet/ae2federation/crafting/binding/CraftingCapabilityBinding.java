package space.controlnet.ae2federation.crafting.binding;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class CraftingCapabilityBinding {
    private final CraftingRelationship relationship;
    private final NativeCraftingBackend backend;
    private final CraftingBindingRevision revision;
    private final BooleanSupplier current;

    CraftingCapabilityBinding(CraftingRelationship relationship, NativeCraftingBackend backend,
            CraftingBindingRevision revision, BooleanSupplier current) {
        this.relationship = relationship;
        this.backend = backend;
        this.revision = revision;
        this.current = current;
    }

    public CraftingRelationship relationship() {
        return relationship;
    }

    public CraftingBindingRevision revision() {
        return revision;
    }

    public boolean isCurrent() {
        return current.getAsBoolean();
    }

    public Optional<ICraftingService> nativeService() {
        return isCurrent() ? Optional.of(backend.service()) : Optional.empty();
    }

    public Optional<IGrid> sourceGrid() {
        return isCurrent() ? Optional.of(backend.sourceGrid()) : Optional.empty();
    }

    public List<ICraftingProvider> nativeProviders() {
        return isCurrent() ? backend.providers().stream().map(NativeCraftingProviderSource::provider).toList()
                : List.of();
    }

    public List<NativeCraftingProviderSource> providerSources() {
        return isCurrent() ? backend.providers() : List.of();
    }

    public Set<ICraftingCPU> nativeCpus() {
        return isCurrent() ? backend.cpus() : Set.of();
    }

    public Optional<CraftingSubmissionSnapshot> submissionSnapshot() {
        if (!isCurrent()) {
            return Optional.empty();
        }
        var snapshot = new CraftingSubmissionSnapshot(this, backend.service(), backend.sourceGrid(), backend.providers(),
                backend.cpus());
        return isCurrent() ? Optional.of(snapshot) : Optional.empty();
    }
}
