package space.controlnet.ae2federation.test.processing;

import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.processing.endpoint.EndpointRuntime;
import space.controlnet.ae2federation.processing.provider.ProviderLogicProvenance;
import space.controlnet.ae2federation.processing.provider.ProviderRuntime;
import space.controlnet.ae2federation.processing.provider.ProviderTargetResolution;

public final class ProviderRuntimeReplayControl {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderRuntimeReplayControl.class);
    private static final Map<ProviderRuntime, State> STATES = new IdentityHashMap<>();

    private ProviderRuntimeReplayControl() {
    }

    public static synchronized ProviderTargetResolution observeResolution(ProviderRuntime runtime,
            ProviderLogicProvenance provenance, ProviderTargetResolution issued) {
        var state = STATES.computeIfAbsent(runtime, ignored -> new State());
        state.provenances.put(provenance.logic(), provenance);
        var pending = state.pending;
        if (pending != null && !pending.resolverSubstituted && provenance.logic() == pending.targetLogic) {
            pending.resolverSubstituted = true;
            pending.resolverOwnerIdentity = identity(runtime);
            pending.resolverInvocationCount++;
            return pending.authorization;
        }
        if (issued instanceof ProviderTargetResolution.Authorized authorized) {
            state.authorizations.put(provenance.logic(), authorized);
        }
        return issued;
    }

    public static synchronized void arm(ProviderRuntime runtime, PatternProviderLogic sourceLogic,
            PatternProviderLogic targetLogic, EndpointRuntime endpoint) {
        var state = requireState(runtime);
        if (state.pending != null) {
            throw new IllegalStateException("A Provider replay observation is already armed");
        }
        var authorization = state.authorizations.get(sourceLogic);
        var expectedProvenance = state.provenances.get(targetLogic);
        if (authorization == null || expectedProvenance == null) {
            throw new IllegalStateException("Source authorization and target Lane provenance must be observed first");
        }
        state.pending = new Pending(authorization, targetLogic, identity(expectedProvenance.logic()),
                identity(expectedProvenance), identity(authorization.target().provenance()),
                authorization.target().mode().generation(), contextIdentity(endpoint.itemReturnContext().orElse(null)),
                contextIdentity(endpoint.fluidReturnContext().orElse(null)));
    }

    public static synchronized void observeMixinOwner(PatternProviderLogic logic) {
        var pending = pendingFor(logic);
        if (pending != null) {
            pending.mixinOwnerIdentity = identity(logic);
            pending.mixinLookupCount++;
        }
    }

    public static synchronized void observeCacheOwner(PatternProviderLogic logic) {
        var pending = pendingFor(logic);
        if (pending != null) {
            pending.cacheOwnerIdentity = identity(logic);
            pending.cacheLookupCount++;
        }
    }

    public static synchronized void observeCacheResult(PatternProviderLogic logic,
            space.controlnet.ae2federation.ae2.processing.FederationPatternProviderTargetCache.Lookup lookup) {
        var pending = pendingFor(logic);
        if (pending != null && lookup.target() != null) {
            pending.cacheTargetFoundCount++;
        }
    }

    public static synchronized ReplayTrace finish(ProviderRuntime runtime, EndpointRuntime endpoint, String phase) {
        var state = requireState(runtime);
        var pending = state.pending;
        if (pending == null) {
            throw new IllegalStateException("No Provider replay observation is armed");
        }
        var trace = new ReplayTrace(pending.expectedLogicIdentity, pending.expectedProvenanceIdentity,
                pending.actualProvenanceIdentity, pending.authorizedGeneration, pending.resolverOwnerIdentity,
                pending.resolverInvocationCount, pending.mixinOwnerIdentity, pending.mixinLookupCount,
                pending.cacheOwnerIdentity, pending.cacheLookupCount, pending.cacheTargetFoundCount,
                pending.itemContextIdentityBefore, contextIdentity(endpoint.itemReturnContext().orElse(null)),
                pending.fluidContextIdentityBefore, contextIdentity(endpoint.fluidReturnContext().orElse(null)));
        state.pending = null;
        trace.facts().forEach((fact, value) -> LOGGER.info(
                "AE2F_ENDPOINT_NEGATIVE_TRACE testId={} phase={} fact={} value={}",
                System.getProperty("ae2federation.testId", ""), phase, fact, value));
        return trace;
    }

    public static synchronized void clear(ProviderRuntime runtime) {
        STATES.remove(runtime);
    }

    private static State requireState(ProviderRuntime runtime) {
        var state = STATES.get(runtime);
        if (state == null) {
            throw new IllegalStateException("Provider runtime has not issued an observed resolution");
        }
        return state;
    }

    private static Pending pendingFor(PatternProviderLogic logic) {
        Pending found = null;
        for (var state : STATES.values()) {
            if (state.pending != null && state.pending.targetLogic == logic) {
                if (found != null) {
                    throw new IllegalStateException("Multiple Provider replay observations target one Lane");
                }
                found = state.pending;
            }
        }
        return found;
    }

    private static String contextIdentity(Object context) {
        return context == null ? "0" : identity(context);
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    public record ReplayTrace(String expectedLogicIdentity, String expectedProvenanceIdentity,
            String actualProvenanceIdentity, long authorizedGeneration, String resolverOwnerIdentity,
            int resolverInvocationCount, String mixinOwnerIdentity, int mixinLookupCount,
            String cacheOwnerIdentity, int cacheLookupCount, int cacheTargetFoundCount,
            String itemContextIdentityBefore, String itemContextIdentityAfter,
            String fluidContextIdentityBefore, String fluidContextIdentityAfter) {
        public Map<String, String> facts() {
            var facts = new TreeMap<String, String>();
            facts.put("actualProvenanceIdentity", actualProvenanceIdentity);
            facts.put("authorizedGeneration", Long.toString(authorizedGeneration));
            facts.put("cacheLookupCount", Integer.toString(cacheLookupCount));
            facts.put("cacheOwnerIdentity", cacheOwnerIdentity);
            facts.put("cacheTargetFoundCount", Integer.toString(cacheTargetFoundCount));
            facts.put("expectedLogicIdentity", expectedLogicIdentity);
            facts.put("expectedProvenanceIdentity", expectedProvenanceIdentity);
            facts.put("fluidContextIdentityAfter", fluidContextIdentityAfter);
            facts.put("fluidContextIdentityBefore", fluidContextIdentityBefore);
            facts.put("itemContextIdentityAfter", itemContextIdentityAfter);
            facts.put("itemContextIdentityBefore", itemContextIdentityBefore);
            facts.put("mixinLookupCount", Integer.toString(mixinLookupCount));
            facts.put("mixinOwnerIdentity", mixinOwnerIdentity);
            facts.put("resolverInvocationCount", Integer.toString(resolverInvocationCount));
            facts.put("resolverOwnerIdentity", resolverOwnerIdentity);
            return Map.copyOf(facts);
        }
    }

    private static final class State {
        private final Map<PatternProviderLogic, ProviderLogicProvenance> provenances = new IdentityHashMap<>();
        private final Map<PatternProviderLogic, ProviderTargetResolution.Authorized> authorizations =
                new IdentityHashMap<>();
        private Pending pending;
    }

    private static final class Pending {
        private final ProviderTargetResolution.Authorized authorization;
        private final PatternProviderLogic targetLogic;
        private final String expectedLogicIdentity;
        private final String expectedProvenanceIdentity;
        private final String actualProvenanceIdentity;
        private final long authorizedGeneration;
        private final String itemContextIdentityBefore;
        private final String fluidContextIdentityBefore;
        private boolean resolverSubstituted;
        private String resolverOwnerIdentity = "0";
        private int resolverInvocationCount;
        private String mixinOwnerIdentity = "0";
        private int mixinLookupCount;
        private String cacheOwnerIdentity = "0";
        private int cacheLookupCount;
        private int cacheTargetFoundCount;

        private Pending(ProviderTargetResolution.Authorized authorization, PatternProviderLogic targetLogic,
                String expectedLogicIdentity, String expectedProvenanceIdentity, String actualProvenanceIdentity,
                long authorizedGeneration, String itemContextIdentityBefore, String fluidContextIdentityBefore) {
            this.authorization = authorization;
            this.targetLogic = targetLogic;
            this.expectedLogicIdentity = expectedLogicIdentity;
            this.expectedProvenanceIdentity = expectedProvenanceIdentity;
            this.actualProvenanceIdentity = actualProvenanceIdentity;
            this.authorizedGeneration = authorizedGeneration;
            this.itemContextIdentityBefore = itemContextIdentityBefore;
            this.fluidContextIdentityBefore = fluidContextIdentityBefore;
        }
    }
}
