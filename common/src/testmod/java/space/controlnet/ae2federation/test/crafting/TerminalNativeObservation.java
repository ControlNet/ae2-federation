package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TerminalNativeObservation {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalNativeObservation.class);
    private static String activeTest = "";
    private static int beginCalls;
    private static int submitCalls;
    private static int providerPushes;
    private static int cpuSubmissions;
    private static int resultCallbacks;
    private static int physicalInserts;
    private static int requesterActionCalls;
    private static int requesterNodeCalls;
    private static String serviceIdentity = "none";
    private static String requesterIdentity = "none";
    private static String requesterNodeIdentity = "none";
    private static String providerIdentity = "none";
    private static String cpuIdentity = "none";
    private static String cpuLogicIdentity = "none";
    private static String resultCallbackOwner = "none";
    private static String physicalResultOwner = "none";
    private static String plannerThread = "none";
    private static final Set<String> jobIds = new LinkedHashSet<>();
    private static final Set<String> rejectedMutableAccess = new LinkedHashSet<>();

    private TerminalNativeObservation() {
    }

    public static synchronized void begin(String testId) {
        activeTest = testId;
        beginCalls = 0;
        submitCalls = 0;
        providerPushes = 0;
        cpuSubmissions = 0;
        resultCallbacks = 0;
        physicalInserts = 0;
        requesterActionCalls = 0;
        requesterNodeCalls = 0;
        serviceIdentity = "none";
        requesterIdentity = "none";
        requesterNodeIdentity = "none";
        providerIdentity = "none";
        cpuIdentity = "none";
        cpuLogicIdentity = "none";
        resultCallbackOwner = "none";
        physicalResultOwner = "none";
        plannerThread = "none";
        jobIds.clear();
        rejectedMutableAccess.clear();
    }

    public static synchronized void recordBegin(Object service, ICraftingSimulationRequester requester) {
        if (!selected()) return;
        beginCalls++;
        serviceIdentity = identity(service);
        requesterIdentity = identity(requester);
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=begin service={} requester={}",
                activeTest, serviceIdentity, requesterIdentity);
    }

    public static synchronized void recordSubmit(Object service) {
        if (!selected()) return;
        submitCalls++;
        serviceIdentity = identity(service);
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=submit service={}", activeTest, serviceIdentity);
    }

    public static synchronized void recordRequesterAction(Object requester) {
        if (!selected() || !plannerThread()) return;
        requesterActionCalls++;
        requesterIdentity = identity(requester);
        plannerThread = Thread.currentThread().getName();
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=planner-action requester={} thread={}",
                activeTest, requesterIdentity, threadToken());
    }

    public static synchronized void recordRequesterNode(Object requester, IGridNode node) {
        if (!selected() || !plannerThread()) return;
        requesterNodeCalls++;
        requesterIdentity = identity(requester);
        requesterNodeIdentity = identity(node);
        plannerThread = Thread.currentThread().getName();
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=planner-node requester={} node={} thread={}",
                activeTest, requesterIdentity, requesterNodeIdentity, threadToken());
    }

    public static synchronized void recordProviderPush(Object provider) {
        if (!selected()) return;
        providerPushes++;
        providerIdentity = identity(provider);
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=provider-push provider={}", activeTest, providerIdentity);
    }

    public static synchronized void recordCpuSubmission(CraftingCPUCluster cpu, ICraftingSubmitResult result) {
        if (!selected() || !result.successful()) return;
        cpuSubmissions++;
        cpuIdentity = identity(cpu);
        cpuLogicIdentity = identity(cpu.craftingLogic);
        var link = cpu.craftingLogic.getLastLink();
        if (link != null) jobIds.add(link.getCraftingID().toString());
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=cpu-submit cpu={} cpuLogic={} job={}", activeTest,
                cpuIdentity, cpuLogicIdentity, link == null ? "none" : link.getCraftingID().toString());
    }

    public static synchronized void recordResultCallback(CraftingCpuLogic logic, AEKey key, long amount,
            Actionable mode) {
        if (!selected() || mode != Actionable.MODULATE || !key.equals(TerminalCraftingFixture.outputKey())) return;
        resultCallbacks++;
        resultCallbackOwner = identity(logic);
        var link = logic.getLastLink();
        if (link != null) jobIds.add(link.getCraftingID().toString());
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=result-callback owner={} job={} key={} amount={}", activeTest,
                resultCallbackOwner, link == null ? "none" : link.getCraftingID().toString(), key.getId(), amount);
    }

    public static synchronized void recordPhysicalInsert(Object owner, AEKey key, long amount, Actionable mode,
            long inserted) {
        if (!selected() || mode != Actionable.MODULATE || inserted <= 0
                || !key.equals(TerminalCraftingFixture.outputKey())) return;
        physicalInserts++;
        physicalResultOwner = identity(owner);
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=physical-insert owner={} key={} amount={} inserted={}",
                activeTest, physicalResultOwner, key.getId(), amount, inserted);
    }

    public static synchronized void guardMutableAccess(String boundary) {
        if (!selected() || !plannerThread()) return;
        rejectedMutableAccess.add(boundary);
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=planner-reject boundary={} thread={}", activeTest,
                boundary, threadToken());
        throw new IllegalStateException("Planner thread mutable access rejected: " + boundary);
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(beginCalls, submitCalls, providerPushes, cpuSubmissions, resultCallbacks, physicalInserts,
                requesterActionCalls, requesterNodeCalls, serviceIdentity, requesterIdentity, requesterNodeIdentity,
                providerIdentity, cpuIdentity, cpuLogicIdentity, resultCallbackOwner, physicalResultOwner, plannerThread,
                List.copyOf(jobIds), Set.copyOf(rejectedMutableAccess));
    }

    public static synchronized void close() {
        activeTest = "";
    }

    private static boolean selected() {
        return !activeTest.isEmpty() && activeTest.equals(System.getProperty("ae2federation.testId", ""));
    }

    private static boolean plannerThread() {
        return Thread.currentThread().getName().startsWith("AE Crafting Calculator");
    }

    private static String threadToken() {
        return Thread.currentThread().getName().replace(' ', '_');
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    public record Snapshot(int beginCalls, int submitCalls, int providerPushes, int cpuSubmissions,
            int resultCallbacks, int physicalInserts, int requesterActionCalls, int requesterNodeCalls,
            String serviceIdentity, String requesterIdentity, String requesterNodeIdentity, String providerIdentity,
            String cpuIdentity, String cpuLogicIdentity, String resultCallbackOwner, String physicalResultOwner,
            String plannerThread,
            List<String> jobIds, Set<String> rejectedMutableAccess) {
        public String joinedJobIds() {
            var ordered = new ArrayList<>(jobIds);
            ordered.sort(String::compareTo);
            return String.join(",", ordered);
        }

        public String plannerThreadToken() {
            return plannerThread.replace(' ', '_');
        }
    }
}
