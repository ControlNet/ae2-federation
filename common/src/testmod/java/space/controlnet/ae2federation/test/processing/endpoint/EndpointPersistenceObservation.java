package space.controlnet.ae2federation.test.processing.endpoint;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.ae2.processing.endpoint.EndpointMode;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;

public final class EndpointPersistenceObservation {
    private static final Logger LOGGER = LoggerFactory.getLogger(EndpointPersistenceObservation.class);
    private static State active;

    private EndpointPersistenceObservation() {
    }

    public static synchronized void begin(String testId, EndpointBlockEntity endpoint) {
        if (active != null) {
            throw new IllegalStateException("Endpoint persistence observation is already active");
        }
        active = new State(testId, endpoint.getBlockPos().immutable(), endpoint);
    }

    public static synchronized void observeSave(EndpointBlockEntity endpoint, CompoundTag tag) {
        if (!matches(endpoint)) {
            return;
        }
        active.save = fromTag(tag, identity(endpoint.binding()));
        emit(active.testId, "save", active.save);
    }

    public static synchronized void observeUnload(EndpointBlockEntity endpoint) {
        if (!matches(endpoint) || endpoint != active.original) {
            return;
        }
        active.unloadCount++;
        emit(active.testId, "unload", Map.of("bindingIdentity", identity(endpoint.binding())));
    }

    public static synchronized void observeLoad(EndpointBlockEntity endpoint, CompoundTag tag) {
        if (!matches(endpoint) || endpoint == active.original) {
            return;
        }
        active.load = fromEndpoint(endpoint, tag.getString("endpointMode"), tag.getLong("endpointModeGeneration"), "0");
        emit(active.testId, "load", active.load);
    }

    public static synchronized void observeReady(EndpointBlockEntity endpoint) {
        if (!matches(endpoint) || endpoint == active.original || endpoint.binding() == null) {
            return;
        }
        active.ready = fromEndpoint(endpoint, endpoint.binding().runtime().configuredMode().name(),
                endpoint.binding().runtime().generation(), identity(endpoint.binding()));
        emit(active.testId, "ready", active.ready);
    }

    public static synchronized Snapshot finish(EndpointBlockEntity endpoint) {
        if (!matches(endpoint) || endpoint == active.original || active.save == null || active.load == null
                || active.ready == null) {
            throw new IllegalStateException("Endpoint persistence lifecycle observation is incomplete");
        }
        var snapshot = new Snapshot(active.save, active.load, active.ready, active.unloadCount);
        active = null;
        return snapshot;
    }

    public static synchronized void clear() {
        active = null;
    }

    private static boolean matches(EndpointBlockEntity endpoint) {
        return active != null && active.position.equals(endpoint.getBlockPos());
    }

    private static Map<String, String> fromTag(CompoundTag tag, String bindingIdentity) {
        var claim = tag.getCompound("endpointClaim");
        var facts = new LinkedHashMap<String, String>();
        facts.put("endpointId", claim.getUUID("endpoint").toString());
        facts.put("endpointEpoch", Long.toString(claim.getLong("endpointEpoch")));
        facts.put("providerId", claim.getUUID("provider").toString());
        facts.put("providerEpoch", Long.toString(claim.getLong("providerEpoch")));
        facts.put("claimEpoch", Long.toString(claim.getLong("claimEpoch")));
        facts.put("mode", tag.getString("endpointMode"));
        facts.put("generation", Long.toString(tag.getLong("endpointModeGeneration")));
        facts.put("bindingIdentity", bindingIdentity);
        return Map.copyOf(facts);
    }

    private static Map<String, String> fromEndpoint(EndpointBlockEntity endpoint, String mode, long generation,
            String bindingIdentity) {
        if (!(endpoint.claimState() instanceof ClaimState.Owned owned)) {
            throw new IllegalStateException("Observed Endpoint does not retain an owned Claim");
        }
        var identity = endpoint.endpointIdentity();
        var provider = owned.ownerIdentity().provider();
        var facts = new LinkedHashMap<String, String>();
        facts.put("endpointId", identity.id().value().toString());
        facts.put("endpointEpoch", Long.toString(identity.instanceEpoch().value()));
        facts.put("providerId", provider.id().value().toString());
        facts.put("providerEpoch", Long.toString(provider.instanceEpoch().value()));
        facts.put("claimEpoch", Long.toString(owned.epoch().value()));
        facts.put("mode", mode);
        facts.put("generation", Long.toString(generation));
        facts.put("bindingIdentity", bindingIdentity);
        return Map.copyOf(facts);
    }

    private static void emit(String testId, String step, Map<String, String> facts) {
        facts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> LOGGER.info(
                "AE2F_ENDPOINT_PERSISTENCE_TRACE testId={} step={} fact={} value={}",
                testId, step, entry.getKey(), entry.getValue()));
    }

    private static String identity(Object value) {
        return value == null ? "0" : Integer.toUnsignedString(System.identityHashCode(value));
    }

    public record Snapshot(Map<String, String> saved, Map<String, String> loaded, Map<String, String> ready,
            int unloadCount) {
        public Snapshot {
            saved = Map.copyOf(Objects.requireNonNull(saved));
            loaded = Map.copyOf(Objects.requireNonNull(loaded));
            ready = Map.copyOf(Objects.requireNonNull(ready));
        }

        public Map<String, String> evidenceFacts() {
            var facts = new LinkedHashMap<String, String>();
            copy(facts, "persisted", saved);
            copy(facts, "loaded", loaded);
            copy(facts, "restored", ready);
            facts.put("saveLifecycleCount", "1");
            facts.put("unloadLifecycleCount", Integer.toString(unloadCount));
            facts.put("loadLifecycleCount", "1");
            facts.put("readyLifecycleCount", "1");
            facts.put("ownedClaimRestored", Boolean.toString(saved.get("providerId").equals(ready.get("providerId"))));
            facts.put("configuredModeRestored", Boolean.toString(EndpointMode.FEDERATED.name().equals(ready.get("mode"))));
            facts.put("generationAdvancedAfterLoad", Boolean.toString(
                    Long.parseLong(ready.get("generation")) > Long.parseLong(saved.get("generation"))));
            facts.put("bindingRecreated", Boolean.toString(!saved.get("bindingIdentity").equals(ready.get("bindingIdentity"))));
            return Map.copyOf(facts);
        }

        private static void copy(Map<String, String> target, String prefix, Map<String, String> source) {
            source.forEach((name, value) -> target.put(prefix + Character.toUpperCase(name.charAt(0))
                    + name.substring(1), value));
        }
    }

    private static final class State {
        private final String testId;
        private final BlockPos position;
        private final EndpointBlockEntity original;
        private Map<String, String> save;
        private Map<String, String> load;
        private Map<String, String> ready;
        private int unloadCount;

        private State(String testId, BlockPos position, EndpointBlockEntity original) {
            this.testId = Objects.requireNonNull(testId);
            this.position = Objects.requireNonNull(position);
            this.original = Objects.requireNonNull(original);
        }
    }
}
