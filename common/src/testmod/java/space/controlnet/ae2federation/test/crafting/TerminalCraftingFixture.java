package space.controlnet.ae2federation.test.crafting;

import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEItemKey;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.PlayerSource;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.crafting.terminal.NativeTerminalAdapter;
import space.controlnet.ae2federation.crafting.terminal.NativeTerminalRequest;
import space.controlnet.ae2federation.crafting.terminal.NativeTerminalSession;
import space.controlnet.ae2federation.crafting.terminal.NativeTerminalSubmission;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyFilterMode;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyResource;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;

public final class TerminalCraftingFixture implements AutoCloseable {
    private final CraftingBindingFixture binding;
    private final PlayerSource actionSource;
    private NativeTerminalSession session;
    private NativeTerminalRequest request;
    private ICraftingPlan plan;
    private NativeTerminalSubmission submission;
    private PolicyRevision policyRevision;

    public TerminalCraftingFixture(GameTestHelper helper) {
        binding = new CraftingBindingFixture(helper, true, true);
        actionSource = new PlayerSource(helper.makeMockPlayer(GameType.CREATIVE));
    }

    public boolean ready() {
        return binding.ready();
    }

    public String readinessState() {
        return binding.readinessState();
    }

    public void authorizeAndDiscover() {
        policyRevision = setPolicy(PolicyRevision.NONE, true);
        discover();
    }

    public void captureAuthority(String testId) {
        NativeCraftingAuthorityReceipt.captureCurrent(
                new NativeCraftingAuthorityReceipt.AuthorityLabel(testId, "terminal-discovery"),
                binding.level(), session.snapshot().binding());
        TerminalDiscoveryAuthorityReceipt.captureCurrent(testId, this);
    }

    public void captureResultAuthority(String testId, long amount) {
        TerminalResultAuthorityReceipt.captureDestination(testId, this, outputKey(), amount);
    }

    public void revoke() {
        policyRevision = setPolicy(policyRevision, false);
    }

    public void reauthorizeAndDiscover() {
        policyRevision = setPolicy(policyRevision, true);
        discover();
    }

    private void discover() {
        session = NativeTerminalAdapter.discover(binding.level(), binding.consumerGrid(),
                binding.key().providerNetworkId(), actionSource).orElseThrow();
    }

    public Set<appeng.api.stacks.AEKey> craftables() {
        return session.craftables();
    }

    public void insertMaterials(long amount) {
        binding.insertMaterials(amount);
    }

    public void begin(long amount) {
        request = session.begin(outputKey(), amount).orElseThrow();
    }

    public boolean planReady() {
        var completed = request.completedPlan();
        completed.ifPresent(value -> plan = value);
        return completed.isPresent();
    }

    public ICraftingPlan plan() {
        return plan;
    }

    public NativeTerminalSubmission submit() {
        submission = request.submit(null);
        return submission;
    }

    public ICraftingSubmitResult nativeSubmitResult() {
        return ((NativeTerminalSubmission.Native) submission).result();
    }

    public boolean submittedSuccessfully() {
        return submission instanceof NativeTerminalSubmission.Native nativeResult
                && nativeResult.result().successful();
    }

    public boolean submitError(CraftingSubmitErrorCode expected) {
        return submission instanceof NativeTerminalSubmission.Native nativeResult
                && nativeResult.result().errorCode() == expected;
    }

    public void removeCpu() {
        binding.removeCpu();
    }

    public int cpuCount() {
        return binding.sourceService().getCpus().size();
    }

    public int uniqueNativeJobCount() {
        return nativeJobIds().size();
    }

    public Set<String> nativeJobIds() {
        var result = new TreeSet<String>();
        for (var cpu : request.snapshot().binding().nativeCpus()) {
            if (cpu instanceof CraftingCPUCluster cluster && cluster.craftingLogic.getLastLink() != null) {
                result.add(cluster.craftingLogic.getLastLink().getCraftingID().toString());
            }
        }
        return Set.copyOf(result);
    }

    public long materialAmount() {
        return binding.materialAmount();
    }

    public long outputAmount() {
        return binding.outputAmount();
    }

    public CraftingBindingFixture binding() {
        return binding;
    }

    public NativeTerminalSession session() {
        return session;
    }

    public NativeTerminalRequest request() {
        return request;
    }

    public PlayerSource actionSource() {
        return actionSource;
    }

    public static AEItemKey inputKey() {
        return AEItemKey.of(Items.OAK_PLANKS);
    }

    public static AEItemKey outputKey() {
        return AEItemKey.of(Items.STICK);
    }

    public static AEItemKey forbiddenOutputKey() {
        return AEItemKey.of(Items.CRAFTING_TABLE);
    }

    private PolicyRevision setPolicy(PolicyRevision expected, boolean enabled) {
        var allowed = outputKey();
        var filter = new PolicyFilter(PolicyFilterMode.ALLOW_LIST,
                Set.of(new PolicyResource(allowed.getType().getId(), allowed.getId())));
        var rule = new PolicyRule(enabled, Set.of(PolicyOperation.REQUEST), filter, false);
        return ((PolicyMutationResult.Accepted) PolicyService.get(binding.level())
                .edit(new PolicyEdit(binding.key(), expected, rule))).revision();
    }

    @Override
    public void close() {
        TerminalResultAuthorityReceipt.close();
        binding.close();
    }
}
