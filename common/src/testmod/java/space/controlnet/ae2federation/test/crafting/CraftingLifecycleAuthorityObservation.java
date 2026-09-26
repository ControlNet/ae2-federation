package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.crafting.terminal.NativeTerminalSession;
import space.controlnet.ae2federation.identity.NetworkIdentityService;

public final class CraftingLifecycleAuthorityObservation {
    private static final Logger LOGGER = LoggerFactory.getLogger(CraftingLifecycleAuthorityObservation.class);
    private static RequestAuthority request;
    private static CycleAuthority cycle;
    private static int backendDiscoveries;

    private CraftingLifecycleAuthorityObservation() {
    }

    public static synchronized void authorizeRequest(String testId, CraftingBindingFixture fixture,
            NativeCraftingRequester requester, NativeTerminalSession session, AEKey key, long amount) {
        requireSelected(testId);
        var capability = fixture.binding();
        var snapshot = capability.submissionSnapshot().orElseThrow();
        var node = requester.getActionableNode();
        var nodeId = snapshot.sourceGrid().getService(NetworkIdentityService.class).lineage(node).nodeId();
        request = new RequestAuthority(testId, fixture, capability.relationship().key().toString().replace(" ", ""),
                capability.revision().policyRevision().value(), capability.revision().topologyRevision(),
                capability.revision().providerGeneration().value(), requester, nodeId, snapshot.service(),
                snapshot.sourceGrid(), snapshot.providerSources().getFirst().provider(), fixture.sourcePhysicalStorage(),
                session, key, amount, fixture.physicalMaterialAmount(), fixture.physicalOutputAmount());
        cycle = null;
        emitRequest("authorized");
    }

    public static synchronized void authorizeCycle(String testId, CraftingBindingFixture fixture,
            int discoveries) {
        requireSelected(testId);
        cycle = new CycleAuthority(testId, fixture.key().toString().replace(" ", ""),
                fixture.reverseKey().toString().replace(" ", ""),
                discoveries);
        request = null;
        emitCycle("cycle-authorized", discoveries);
    }

    public static synchronized void recordBackendDiscovery() {
        if ("craftingrejectcycle".equals(selectedTest())) {
            backendDiscoveries++;
        }
    }

    public static synchronized int backendDiscoveries() {
        return backendDiscoveries;
    }

    public static synchronized void recordTrackerCall(ICraftingRequester requester, AEKey key, long amount,
            ICraftingService service) {
        if (!requestSelected() || requester != request.requester || service != request.service
                || !request.key.equals(key) || amount != request.amount) {
            return;
        }
        request.trackerCalls++;
    }

    public static synchronized void recordTrackerResult(ICraftingRequester requester, boolean submitted) {
        if (!requestSelected() || requester != request.requester) {
            return;
        }
        if (submitted) {
            var links = requester.getRequestedJobs();
            if (links.size() != 1) {
                throw new IllegalStateException("Authorized native submission must expose exactly one link");
            }
            request.link = links.iterator().next();
            request.craftingId = request.link.getCraftingID();
            request.trackerSubmissions++;
        }
        request.inputAfter = request.fixture.physicalMaterialAmount();
        if (submitted || request.link != null) {
            emitRequest(submitted ? "submitted" : "duplicate-rejected");
        }
    }

    public static synchronized void recordCpuResult(CraftingCpuLogic logic, AEKey key, long amount, Actionable mode) {
        if (!requestSelected() || mode != Actionable.MODULATE || request.link == null || !request.key.equals(key)
                || amount <= 0 || logic.getLastLink() == null
                || !request.craftingId.equals(logic.getLastLink().getCraftingID())) {
            return;
        }
        request.cpuLogic = logic;
        if (!request.cpuRecorded) {
            request.cpuRecorded = true;
            emitRequest("cpu-result");
        }
    }

    public static synchronized void recordCallbackStart(NativeCraftingRequester requester, ICraftingLink link,
            AEKey key, long amount, Actionable mode) {
        if (!matchesDelivery(requester, link, key, amount, mode)) {
            return;
        }
        if (!request.callbackRecorded) {
            request.callbackRecorded = true;
            emitRequest("callback");
        }
    }

    public static synchronized void recordCallbackAccepted(NativeCraftingRequester requester, ICraftingLink link,
            AEKey key, long amount, Actionable mode, long accepted) {
        if (!matchesDelivery(requester, link, key, amount, mode)) {
            return;
        }
        request.callbackAccepted += accepted;
        if (request.callbackAccepted == request.amount) {
            emitRequest("callback-accepted");
        }
    }

    public static synchronized void observePhysicalInsertion(Object destination, AEKey key, long amount, Actionable mode,
            long inserted) {
        if (!requestSelected() || mode != Actionable.MODULATE || destination != request.destination
                || !request.key.equals(key) || amount <= 0 || inserted != amount
                || request.physicalInserted + inserted > request.amount) {
            return;
        }
        request.physicalInserted += inserted;
        request.outputAfter = request.fixture.physicalOutputAmount();
        if (request.physicalInserted == request.amount) {
            emitRequest("physical");
        }
    }

    public static synchronized void recordTerminal(NativeCraftingRequester requester, ICraftingLink link) {
        if (!requestSelected() || requester != request.requester || !sameLink(link) || request.terminalRecorded) {
            return;
        }
        request.terminalRecorded = true;
        emitRequest(link.isCanceled() ? "canceled" : "completed");
    }

    public static synchronized void recordReload(NativeCraftingRequester previous, NativeCraftingRequester loaded,
            ICraftingLink loadedLink, UUID loadedNodeId) {
        if (!requestSelected() || previous != request.requester || !sameUuid(loadedLink)
                || !request.requesterNode.equals(loadedNodeId) || !loaded.getRequestedJobs().contains(loadedLink)) {
            throw new IllegalStateException("Reloaded requester does not preserve authorized native authority");
        }
        request.requester = loaded;
        request.link = loadedLink;
        emitRequest("reloaded");
    }

    public static synchronized void recordReplacement(NativeCraftingRequester replacement, UUID replacementNodeId) {
        if (!requestSelected() || replacement == request.requester || request.requesterNode.equals(replacementNodeId)) {
            throw new IllegalStateException("Requester replacement must use a distinct native node lineage");
        }
        request.replacementRequester = replacement;
        request.replacementNode = replacementNodeId;
        emitRequest("replacement-rejected");
    }

    public static synchronized void recordSessionClosed(NativeTerminalSession session) {
        if (!requestSelected() || session != request.session || !session.isClosed()) {
            throw new IllegalStateException("Authorized native terminal session was not closed");
        }
        emitRequest("session-closed");
    }

    public static synchronized void recordLateWindow(long callbacks, long insertions, int trackerSubmissions) {
        if (!requestSelected() || callbacks != request.callbackAccepted || insertions != request.physicalInserted
                || trackerSubmissions != request.trackerSubmissions) {
            throw new IllegalStateException("Native lifecycle changed during the bounded late window: callbacks="
                    + callbacks + "/" + request.callbackAccepted + ", insertions=" + insertions + "/"
                    + request.physicalInserted + ", submissions=" + trackerSubmissions + "/"
                    + request.trackerSubmissions);
        }
        emitRequest("late-window");
    }

    public static synchronized void recordRetired(int requests, int owners) {
        if (!requestSelected() || requests != 0 || owners != 0) {
            throw new IllegalStateException("Native request authority was not retired atomically");
        }
        emitRequest("retired");
    }

    public static synchronized void recordCycleRejected(int backendDiscoveries) {
        if (!cycleSelected() || backendDiscoveries != cycle.backendDiscoveries) {
            throw new IllegalStateException("Cyclic Crafting relationships reached native backend discovery");
        }
        emitCycle("cycle-rejected", backendDiscoveries);
    }

    public static synchronized long callbackAccepted() {
        return request == null ? 0 : request.callbackAccepted;
    }

    public static synchronized long physicalInserted() {
        return request == null ? 0 : request.physicalInserted;
    }

    public static synchronized int trackerSubmissions() {
        return request == null ? 0 : request.trackerSubmissions;
    }

    public static synchronized void close() {
        request = null;
        cycle = null;
        backendDiscoveries = 0;
    }

    private static boolean matchesDelivery(NativeCraftingRequester requester, ICraftingLink link, AEKey key, long amount,
            Actionable mode) {
        return requestSelected() && mode == Actionable.MODULATE && requester == request.requester && sameLink(link)
                && request.key.equals(key) && amount > 0 && request.callbackAccepted + amount <= request.amount;
    }

    private static boolean sameLink(ICraftingLink link) {
        return request.link == link && sameUuid(link);
    }

    private static boolean sameUuid(ICraftingLink link) {
        return request.craftingId != null && request.craftingId.equals(link.getCraftingID());
    }

    private static void emitRequest(String phase) {
        LOGGER.info("AE2F_CRAFT_LIFECYCLE_AUTHORITY testId={} selected={} phase={} policy={} policyRevision={} "
                        + "topologyRevision={} providerGeneration={} requester={} requesterNode={} link={} linkOwner={} service={} "
                        + "sourceGrid={} provider={} destination={} session={} key={} amount={} inputBefore={} "
                        + "inputAfter={} outputBefore={} outputAfter={} trackerCalls={} trackerSubmissions={} cpuLogic={} "
                        + "callbackAccepted={} physicalInserted={} replacementRequester={} replacementNode={}",
                request.testId, selectedTest(), phase, request.policy, request.policyRevision, request.topologyRevision,
                request.providerGeneration, identity(request.requester), request.requesterNode,
                request.craftingId == null ? "none" : request.craftingId,
                request.link == null ? "none" : identity(request.link), identity(request.service),
                identity(request.sourceGrid), identity(request.provider), identity(request.destination),
                identity(request.session), request.key.getId(), request.amount, request.inputBefore, request.inputAfter,
                request.outputBefore, request.outputAfter, request.trackerCalls, request.trackerSubmissions,
                request.cpuLogic == null ? "none" : identity(request.cpuLogic), request.callbackAccepted,
                request.physicalInserted,
                request.replacementRequester == null ? "none" : identity(request.replacementRequester),
                request.replacementNode == null ? "none" : request.replacementNode);
    }

    private static void emitCycle(String phase, int backendDiscoveries) {
        LOGGER.info("AE2F_CRAFT_LIFECYCLE_AUTHORITY testId={} selected={} phase={} forward={} reverse={} cycleEdges=2 "
                        + "backendDiscoveries={}", cycle.testId, selectedTest(), phase, cycle.forward, cycle.reverse,
                backendDiscoveries);
    }

    private static boolean requestSelected() {
        return request != null && selectedTest().equals(request.testId);
    }

    private static boolean cycleSelected() {
        return cycle != null && selectedTest().equals(cycle.testId);
    }

    private static void requireSelected(String testId) {
        if (!selectedTest().equals(testId)) {
            throw new IllegalArgumentException("Lifecycle authority test ID does not match the selected child");
        }
    }

    private static String selectedTest() {
        return System.getProperty("ae2federation.testId", "");
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(Objects.requireNonNull(value)));
    }

    private static final class RequestAuthority {
        private final String testId;
        private final CraftingBindingFixture fixture;
        private final String policy;
        private final long policyRevision;
        private final long topologyRevision;
        private final long providerGeneration;
        private final UUID requesterNode;
        private final Object service;
        private final Object sourceGrid;
        private final Object provider;
        private final Object destination;
        private final NativeTerminalSession session;
        private final AEKey key;
        private final long amount;
        private final long inputBefore;
        private final long outputBefore;
        private NativeCraftingRequester requester;
        private ICraftingLink link;
        private UUID craftingId;
        private Object cpuLogic;
        private long inputAfter;
        private long outputAfter;
        private int trackerCalls;
        private int trackerSubmissions;
        private long callbackAccepted;
        private long physicalInserted;
        private NativeCraftingRequester replacementRequester;
        private UUID replacementNode;
        private boolean terminalRecorded;
        private boolean cpuRecorded;
        private boolean callbackRecorded;

        private RequestAuthority(String testId, CraftingBindingFixture fixture, String policy, long policyRevision,
                long topologyRevision, long providerGeneration, NativeCraftingRequester requester, UUID requesterNode,
                Object service, Object sourceGrid, Object provider, Object destination, NativeTerminalSession session,
                AEKey key, long amount, long inputBefore, long outputBefore) {
            this.testId = testId;
            this.fixture = fixture;
            this.policy = policy;
            this.policyRevision = policyRevision;
            this.topologyRevision = topologyRevision;
            this.providerGeneration = providerGeneration;
            this.requester = requester;
            this.requesterNode = requesterNode;
            this.service = service;
            this.sourceGrid = sourceGrid;
            this.provider = provider;
            this.destination = destination;
            this.session = session;
            this.key = key;
            this.amount = amount;
            this.inputBefore = inputBefore;
            this.outputBefore = outputBefore;
            inputAfter = inputBefore;
            outputAfter = outputBefore;
        }
    }

    private record CycleAuthority(String testId, String forward, String reverse, int backendDiscoveries) {
    }
}
