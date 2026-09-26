package space.controlnet.ae2federation.ae2.processing;

import appeng.api.networking.IGridNode;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.helpers.MachineSource;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.processing.provider.AuthorizedNativeTarget;
import space.controlnet.ae2federation.processing.provider.ProviderLogicProvenance;
import space.controlnet.ae2federation.processing.provider.ProviderTargetResolution;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;

public final class FederationPatternProviderTargetCache {
    private static final Map<PatternProviderLogic, Binding> BINDINGS = new WeakHashMap<>();

    private FederationPatternProviderTargetCache() {
    }

    public static synchronized void bind(PatternProviderLogic logic, ProviderLogicProvenance provenance,
            Supplier<ProviderTargetResolution> resolver, Supplier<IGridNode> sourceNode) {
        if (provenance.logic() != logic) {
            throw new IllegalArgumentException("Native Lane provenance must identify the exact bound logic");
        }
        BINDINGS.put(logic, new Binding(provenance, resolver, sourceNode));
    }

    public static synchronized void unbind(PatternProviderLogic logic) {
        BINDINGS.remove(logic);
    }

    public static synchronized boolean isBound(PatternProviderLogic logic) {
        return BINDINGS.containsKey(logic);
    }

    public static synchronized Lookup find(PatternProviderLogic logic) {
        var binding = BINDINGS.get(logic);
        if (binding == null) {
            return new Lookup(false, null);
        }
        var resolution = binding.resolver.get();
        if (!(resolution instanceof ProviderTargetResolution.Authorized authorized)) {
            return new Lookup(true, null);
        }
        if (authorized.target().provenance() != binding.provenance) {
            return new Lookup(true, null);
        }
        var target = binding.find(authorized.target());
        if (target != null && !EndpointTargetBinding.captureFederatedReturn(authorized.target())) {
            target = null;
        }
        return new Lookup(true, target);
    }

    public record Lookup(boolean bound, @Nullable Object target) {
    }

    private static final class Binding {
        private final ProviderLogicProvenance provenance;
        private final Supplier<ProviderTargetResolution> resolver;
        private final Supplier<IGridNode> sourceNode;
        private Binding(ProviderLogicProvenance provenance, Supplier<ProviderTargetResolution> resolver,
                Supplier<IGridNode> sourceNode) {
            this.provenance = provenance;
            this.resolver = resolver;
            this.sourceNode = sourceNode;
        }

        private @Nullable PatternProviderTarget find(AuthorizedNativeTarget authorized) {
            return PatternProviderTarget.get(authorized.level(), authorized.position(), null, authorized.side(),
                    new MachineSource(sourceNode::get));
        }
    }
}
