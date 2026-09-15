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
import space.controlnet.ae2federation.processing.provider.ProviderTargetResolution;

public final class FederationPatternProviderTargetCache {
    private static final Map<PatternProviderLogic, Binding> BINDINGS = new WeakHashMap<>();

    private FederationPatternProviderTargetCache() {
    }

    public static synchronized void bind(PatternProviderLogic logic, Supplier<ProviderTargetResolution> resolver,
            Supplier<IGridNode> sourceNode) {
        BINDINGS.put(logic, new Binding(resolver, sourceNode));
        ProviderTargetTrace.recordBinding(logic);
    }

    public static synchronized void unbind(PatternProviderLogic logic) {
        BINDINGS.remove(logic);
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
        var target = binding.find(authorized.target());
        ProviderTargetTrace.recordNativeTarget(target);
        return new Lookup(true, target);
    }

    public record Lookup(boolean bound, @Nullable Object target) {
    }

    private static final class Binding {
        private final Supplier<ProviderTargetResolution> resolver;
        private final Supplier<IGridNode> sourceNode;
        private Binding(Supplier<ProviderTargetResolution> resolver, Supplier<IGridNode> sourceNode) {
            this.resolver = resolver;
            this.sourceNode = sourceNode;
        }

        private @Nullable PatternProviderTarget find(AuthorizedNativeTarget authorized) {
            return PatternProviderTarget.get(authorized.level(), authorized.position(), null, authorized.side(),
                    new MachineSource(sourceNode::get));
        }
    }
}
