package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TerminalResultAuthorityReceipt {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalResultAuthorityReceipt.class);
    private static Expected expected;
    private static String job = "none";
    private static Object cpuLogic;

    private TerminalResultAuthorityReceipt() {
    }

    public static synchronized void captureDestination(String testId, TerminalCraftingFixture fixture, AEKey key,
            long amount) {
        requireSelected(testId);
        var snapshot = fixture.session().snapshot();
        var provider = snapshot.providerSources().getFirst().provider();
        var cpu = snapshot.nativeCpus().stream().findFirst().orElseThrow();
        expected = new Expected(testId, provider, cpu, fixture.binding().sourcePhysicalStorage(), key, amount);
        job = "none";
        cpuLogic = null;
        emit("destination", "none", cpu, "none", "none", 0);
    }

    public static synchronized void recordSubmission(CraftingCPUCluster cpu, ICraftingSubmitResult result) {
        if (!selected() || !result.successful()) return;
        var link = cpu.craftingLogic.getLastLink();
        if (cpu != expected.cpu() || link == null) return;
        job = link.getCraftingID().toString();
        cpuLogic = cpu.craftingLogic;
        emit("submission", job, cpu, identity(cpuLogic), "none", 0);
    }

    public static synchronized void recordCallback(CraftingCpuLogic logic, AEKey key, long amount, Actionable mode) {
        if (!selected() || mode != Actionable.MODULATE || logic != cpuLogic || !expected.key().equals(key)
                || amount != expected.amount() || logic.getLastLink() == null
                || !job.equals(logic.getLastLink().getCraftingID().toString())) return;
        emit("callback", job, expected.cpu(), identity(logic), identity(logic), 0);
    }

    public static synchronized void recordPhysicalInsert(Object owner, AEKey key, long amount, Actionable mode,
            long inserted) {
        if (!selected() || mode != Actionable.MODULATE || owner != expected.destination()
                || !expected.key().equals(key) || amount != expected.amount() || inserted != expected.amount()) return;
        emit("physical", job, expected.cpu(), identity(cpuLogic), identity(cpuLogic), inserted);
    }

    public static synchronized void close() {
        expected = null;
        job = "none";
        cpuLogic = null;
    }

    private static void emit(String phase, String jobId, Object cpu, String logic, String callback, long inserted) {
        LOGGER.info("AE2F_TERMINAL_RESULT_AUTHORITY testId={} selected={} phase={} job={} cpu={} cpuLogic={} "
                        + "callbackOwner={} provider={} destination={} key={} amount={} inserted={}",
                expected.testId(), selectedTest(), phase, jobId, identity(cpu), logic, callback,
                identity(expected.provider()), identity(expected.destination()), expected.key().getId(), expected.amount(),
                inserted);
    }

    private static boolean selected() {
        return expected != null && selectedTest().equals(expected.testId());
    }

    private static void requireSelected(String testId) {
        if (!selectedTest().equals(testId)) {
            throw new IllegalArgumentException("Result authority test ID does not match the selected child");
        }
    }

    private static String selectedTest() {
        return System.getProperty("ae2federation.testId", "");
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    private record Expected(String testId, Object provider, Object cpu, Object destination, AEKey key, long amount) {
    }
}
