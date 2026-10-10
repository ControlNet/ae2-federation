package space.controlnet.ae2federation.ae2.processing;

import appeng.api.networking.IGridNode;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.helpers.MachineSource;
import appeng.util.ConfigManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.processing.provider.AuthorizedNativeTarget;
import space.controlnet.ae2federation.processing.provider.ProviderLogicProvenance;
import space.controlnet.ae2federation.processing.provider.ProviderTargetResolution;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;

public final class FederationPatternProviderTargetCache {
    private static final Map<PatternProviderLogic, Binding> BINDINGS = new WeakHashMap<>();
    /**
     * ExpandedAE replaces the targets of every AE2 provider logic with its own, which carry its blocking modes, so a Lane
     * builds its Endpoint target the same way and blocks as a native provider does with that mod. Absent without it.
     */
    private static final @Nullable Constructor<?> EXPANDED_AE_TARGETS = expandedAeTargets();
    private static final @Nullable Method EXPANDED_AE_FIND = EXPANDED_AE_TARGETS == null ? null
            : method(EXPANDED_AE_TARGETS.getDeclaringClass(), "find");

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

    /**
     * The Endpoint target {@code logic} may reach now, authorized exactly as for its pushes; empty for a logic that is
     * not a bound Lane or whose target is paused.
     */
    public static synchronized java.util.Optional<AuthorizedNativeTarget> authorized(PatternProviderLogic logic) {
        var binding = BINDINGS.get(logic);
        if (binding == null || !(binding.resolver.get() instanceof ProviderTargetResolution.Authorized authorized)
                || authorized.target().provenance() != binding.provenance) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(authorized.target());
    }

    public record Lookup(boolean bound, @Nullable Object target) {
    }

    private static @Nullable Constructor<?> expandedAeTargets() {
        try {
            return Class.forName("lu.kolja.expandedae.helper.pattern.PatternProviderTargetCache").getConstructor(
                    ServerLevel.class, BlockPos.class, Direction.class, appeng.api.networking.security.IActionSource.class,
                    ConfigManager.class);
        } catch (ReflectiveOperationException | LinkageError absent) {
            return null;
        }
    }

    private static @Nullable Method method(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    private static final class Binding {
        private final ProviderLogicProvenance provenance;
        private final Supplier<ProviderTargetResolution> resolver;
        private final Supplier<IGridNode> sourceNode;
        private @Nullable AuthorizedNativeTarget addonTargetKey;
        private @Nullable Object addonTargets;

        private Binding(ProviderLogicProvenance provenance, Supplier<ProviderTargetResolution> resolver,
                Supplier<IGridNode> sourceNode) {
            this.provenance = provenance;
            this.resolver = resolver;
            this.sourceNode = sourceNode;
        }

        private @Nullable PatternProviderTarget find(AuthorizedNativeTarget authorized) {
            var source = new MachineSource(sourceNode::get);
            if (EXPANDED_AE_FIND != null
                    && provenance.logic().getConfigManager() instanceof ConfigManager config) {
                try {
                    if (addonTargets == null || !sameTarget(addonTargetKey, authorized)) {
                        addonTargets = EXPANDED_AE_TARGETS.newInstance(authorized.level(), authorized.position(),
                                authorized.side(), source, config);
                        addonTargetKey = authorized;
                    }
                    return (PatternProviderTarget) EXPANDED_AE_FIND.invoke(addonTargets);
                } catch (ReflectiveOperationException | ClassCastException | LinkageError unusable) {
                    addonTargets = null;
                }
            }
            return PatternProviderTarget.get(authorized.level(), authorized.position(), null, authorized.side(), source);
        }

        private static boolean sameTarget(@Nullable AuthorizedNativeTarget cached, AuthorizedNativeTarget current) {
            return cached != null && cached.level() == current.level() && cached.position().equals(current.position())
                    && cached.side() == current.side();
        }
    }
}
