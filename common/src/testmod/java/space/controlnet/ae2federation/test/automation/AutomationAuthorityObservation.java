package space.controlnet.ae2federation.test.automation;

import appeng.api.stacks.AEKey;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.storage.mount.StorageMountService;

public final class AutomationAuthorityObservation {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutomationAuthorityObservation.class);
    private static Authority active;

    private AutomationAuthorityObservation() {
    }

    public static synchronized void begin(String testId, NativeAutomationFixture fixture) {
        active = new Authority(testId, fixture.storageKey().toString().replace(' ', '_'));
        captureProjection(fixture);
    }

    public static synchronized void captureProjection(NativeAutomationFixture fixture) {
        var service = StorageMountService.get(fixture.binding().level());
        var key = fixture.storageKey();
        var projection = service.projection(key);
        var mountGeneration = service.mountGeneration(key);
        var domain = service.sourceDomain(key);
        var relationship = service.effectiveRelationship(key);
        if (projection == null || mountGeneration == null || domain == null || relationship == null) {
            throw new IllegalStateException("Native automation authority is not ready");
        }
        var authority = authority();
        authority.projections.add(projection);
        authority.projectionIdentities.add(identity(projection));
        authority.mountGeneration = mountGeneration.value();
        authority.sourceGeneration = domain.generation().value();
        authority.sourceNetwork = domain.origin().value().toString();
        authority.sourceGrid = identity(domain.runtimeGrid());
        authority.relationshipRevision = identity(relationship.revision());
    }

    public static synchronized void authorizeInterface(Object owner) {
        authority().interfaceOwners.add(owner);
        authority().interfaceOwnerIdentities.add(identity(owner));
    }

    public static synchronized void authorizeProjectionOperation(String operation, AEKey key) {
        authority().projectionOperations.add(operationKey(operation, key));
    }

    public static synchronized boolean acceptsInterfaceOwner(Object owner) {
        return active != null && active.interfaceOwners.contains(owner);
    }

    public static synchronized boolean acceptsProjectionOperation(Object projection, String operation, AEKey key) {
        return active != null && active.projections.contains(projection)
                && active.projectionOperations.contains(operationKey(operation, key));
    }

    public static synchronized void emit(String testId) {
        var authority = authority();
        if (!authority.testId.equals(testId)) {
            throw new IllegalStateException("Native automation authority child does not match");
        }
        LOGGER.info("AE2F_AUTOMATION_PREOP_AUTHORITY testId={} selected={} phase=pre-operation interfaceOwners={} "
                        + "projection={} storageKey={} mountGeneration={} sourceGeneration={} sourceNetwork={} "
                        + "sourceGrid={} relationshipRevision={} operations={}",
                testId, selectedTest(), joined(authority.interfaceOwnerIdentities), joined(authority.projectionIdentities),
                authority.storageKey, authority.mountGeneration, authority.sourceGeneration, authority.sourceNetwork,
                authority.sourceGrid, authority.relationshipRevision, joined(authority.projectionOperations));
    }

    public static synchronized void close() {
        active = null;
    }

    private static Authority authority() {
        if (active == null) {
            throw new IllegalStateException("No native automation authority is active");
        }
        return active;
    }

    private static String operationKey(String operation, AEKey key) {
        return operation + ":" + key.getType().getId() + "|" + key.getId();
    }

    private static String joined(Set<String> values) {
        return values.isEmpty() ? "none" : String.join(",", values);
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    private static String selectedTest() {
        return System.getProperty("ae2federation.testId", "");
    }

    private static final class Authority {
        private final String testId;
        private final String storageKey;
        private long mountGeneration;
        private long sourceGeneration;
        private String sourceNetwork;
        private String sourceGrid;
        private String relationshipRevision;
        private final Set<Object> projections = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<String> projectionIdentities = new TreeSet<>();
        private final Set<Object> interfaceOwners = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<String> interfaceOwnerIdentities = new TreeSet<>();
        private final Set<String> projectionOperations = new TreeSet<>();

        private Authority(String testId, String storageKey) {
            this.testId = testId;
            this.storageKey = storageKey;
        }
    }
}
