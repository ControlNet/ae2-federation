package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.test.mixed.MixedFactoryRuntimeReceipt;

public final class TerminalNativeObservation {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalNativeObservation.class);
    /** The crafted item whose results are counted: the sticks every crafting fixture makes. */
    private static final AEKey OUTPUT = AEItemKey.of(Items.STICK);
    private static String activeTest = "";
    private static int beginCalls;
    private static int submitCalls;
    private static int providerPushes;
    private static int cpuSubmissions;
    private static int resultCallbacks;
    private static int physicalInserts;
    private static String serviceIdentity = "none";
    private static String requesterIdentity = "none";
    private static String providerIdentity = "none";
    private static String cpuIdentity = "none";
    private static String cpuLogicIdentity = "none";
    private static String resultCallbackOwner = "none";
    private static String physicalResultOwner = "none";
    private static final Set<String> jobIds = new LinkedHashSet<>();

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
        serviceIdentity = "none";
        requesterIdentity = "none";
        providerIdentity = "none";
        cpuIdentity = "none";
        cpuLogicIdentity = "none";
        resultCallbackOwner = "none";
        physicalResultOwner = "none";
        jobIds.clear();
    }

    public static synchronized void recordBegin(Object service, ICraftingSimulationRequester requester) {
        if (!selected()) return;
        beginCalls++;
        serviceIdentity = identity(service);
        requesterIdentity = identity(requester);
        MixedFactoryRuntimeReceipt.terminal("BEGIN", serviceIdentity, requesterIdentity);
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=begin service={} requester={}",
                activeTest, serviceIdentity, requesterIdentity);
    }

    public static synchronized void recordSubmit(Object service) {
        if (!selected()) return;
        submitCalls++;
        serviceIdentity = identity(service);
        MixedFactoryRuntimeReceipt.terminal("SUBMIT", serviceIdentity);
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=submit service={}", activeTest, serviceIdentity);
    }

    public static synchronized void recordProviderPush(Object provider) {
        if (!selected()) return;
        providerPushes++;
        providerIdentity = identity(provider);
        MixedFactoryRuntimeReceipt.terminal("PROVIDER_PUSH", providerIdentity);
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=provider-push provider={}", activeTest, providerIdentity);
    }

    public static synchronized void recordCpuSubmission(CraftingCPUCluster cpu, ICraftingSubmitResult result) {
        if (!selected() || !result.successful()) return;
        cpuSubmissions++;
        cpuIdentity = identity(cpu);
        cpuLogicIdentity = identity(cpu.craftingLogic);
        var link = cpu.craftingLogic.getLastLink();
        if (link != null) jobIds.add(link.getCraftingID().toString());
        MixedFactoryRuntimeReceipt.terminal("CPU_SUBMIT", cpuIdentity, cpuLogicIdentity,
                link == null ? "none" : link.getCraftingID().toString());
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=cpu-submit cpu={} cpuLogic={} job={}", activeTest,
                cpuIdentity, cpuLogicIdentity, link == null ? "none" : link.getCraftingID().toString());
    }

    public static synchronized void recordResultCallback(CraftingCpuLogic logic, AEKey key, long amount,
            Actionable mode) {
        if (!selected() || mode != Actionable.MODULATE || !key.equals(OUTPUT)) return;
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
                || !key.equals(OUTPUT)) return;
        physicalInserts++;
        physicalResultOwner = identity(owner);
        LOGGER.info("AE2F_TERMINAL_RUNTIME testId={} event=physical-insert owner={} key={} amount={} inserted={}",
                activeTest, physicalResultOwner, key.getId(), amount, inserted);
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(beginCalls, submitCalls, providerPushes, cpuSubmissions, resultCallbacks, physicalInserts,
                serviceIdentity, requesterIdentity, providerIdentity, cpuIdentity, cpuLogicIdentity, resultCallbackOwner,
                physicalResultOwner, List.copyOf(jobIds));
    }

    public static synchronized void close() {
        activeTest = "";
    }

    private static boolean selected() {
        return !activeTest.isEmpty() && activeTest.equals(System.getProperty("ae2federation.testId", ""));
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    public record Snapshot(int beginCalls, int submitCalls, int providerPushes, int cpuSubmissions,
            int resultCallbacks, int physicalInserts, String serviceIdentity, String requesterIdentity,
            String providerIdentity, String cpuIdentity, String cpuLogicIdentity, String resultCallbackOwner,
            String physicalResultOwner, List<String> jobIds) {
        public String joinedJobIds() {
            var ordered = new ArrayList<>(jobIds);
            ordered.sort(String::compareTo);
            return String.join(",", ordered);
        }
    }
}
