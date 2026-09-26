package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.crafting.CraftingBindingFixture;
import space.controlnet.ae2federation.test.crafting.CraftingLifecycleFixture;
import space.controlnet.ae2federation.test.crafting.CraftingLifecycleAuthorityObservation;
import space.controlnet.ae2federation.test.crafting.NativeCraftingEvidence;
import space.controlnet.ae2federation.test.crafting.NativeCraftingRequester;

@PrefixGameTestTemplate(false)
public final class CraftingLifecycleGameTests {
    private CraftingLifecycleGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 4000, required = true, manualOnly = true)
    public static void craftingCancelNative(GameTestHelper helper) {
        var state = new LifecycleState(helper, "craftingcancelnative");
        helper.succeedWhen(() -> {
            state.prepare();
            if (state.stage == 0) {
                state.submit(128);
                state.linkId = state.requester.activeLink().getCraftingID().toString();
                helper.assertTrue(state.fixture.binding().physicalMaterialAmount() < 64,
                        "Native CPU must consume physical input before cancellation");
                state.requester.activeLink().cancel();
                state.fixture.closeTerminalSession();
                CraftingLifecycleAuthorityObservation.recordSessionClosed(state.fixture.terminalSession());
                state.stage = 1;
                helper.assertTrue(false, "Waiting for native cancellation callback and material return");
            }
            if (state.stage == 1 && (!state.requester.observedCanceled()
                    || state.fixture.binding().physicalMaterialAmount() != 64)) {
                helper.assertTrue(false, "Waiting for native cancellation callback and material return");
            }
            helper.assertTrue(state.binding.canceled(), "Native link must own canceled state");
            helper.assertTrue(state.requester.observedCanceled(), "Native requester must observe cancellation");
            helper.assertValueEqual(state.fixture.binding().physicalMaterialAmount(), 64L,
                    "Native CPU must return consumed input");
            helper.assertValueEqual(state.requester.acceptedAmount(), 0L, "Canceled work must return no output");
            if (state.stage == 1) {
                state.requester.close();
                state.recordRetired();
                state.fixture.removeBridges();
                state.stage = 2;
                helper.assertTrue(false, "Waiting for withdrawn capability");
            }
            if (state.stage == 2) {
                helper.assertTrue(state.fixture.binding().bindings().capability(state.fixture.binding().key()).isEmpty(),
                        "Visibility withdrawal must remove only the capability");
                state.fixture.restoreBridge();
                state.stage = 3;
                helper.assertTrue(false, "Waiting for capability reactivation");
            }
            if (!state.fixture.binding().restoredBridgeReady() || state.lateTicks++ < 40) {
                helper.assertTrue(false, "Waiting through bounded late-event window");
            }
            CraftingLifecycleAuthorityObservation.recordLateWindow(state.requester.acceptedAmount(),
                    CraftingLifecycleAuthorityObservation.physicalInserted(), state.requester.uniqueNativeJobCount());
            write("craftingcancelnative", 16, state, Map.of("linkCanceled", "true", "resultOwner", "native-requester",
                    "closedUiResult", "0", "materialAfterCancel", "64", "lateWindowTicks", "40"));
            state.finish();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 4000, required = true, manualOnly = true)
    public static void craftingDisconnectRestart(GameTestHelper helper) {
        var state = new LifecycleState(helper, "craftingdisconnectrestart");
        helper.succeedWhen(() -> {
            state.prepare();
            if (state.stage == 0) {
                state.submit(128);
                state.linkId = state.requester.activeLink().getCraftingID().toString();
                state.persisted = state.fixture.writeState();
                state.fixture.closeTerminalSession();
                state.fixture.assertTerminalSessionRejectsUse();
                CraftingLifecycleAuthorityObservation.recordSessionClosed(state.fixture.terminalSession());
                state.fixture.removeBridges();
                state.originalRequester = state.requester;
                state.requester.close();
                state.requester = state.fixture.replaceRequester(state.persisted);
                state.stage = 1;
                helper.assertTrue(false, "Waiting for capability withdrawal");
            }
            if (state.stage == 1) {
                helper.assertTrue(state.fixture.binding().bindings().capability(state.fixture.binding().key()).isEmpty(),
                        "Disconnect must withdraw capability visibility");
                helper.assertTrue(!state.binding.canceled(), "Disconnect must not cancel native work");
                helper.assertValueEqual(state.fixture.binding().bindings().nativeRequestCount(), 1,
                        "Disconnect must retain native link authority");
                helper.assertTrue(state.requester.activeLink() != null
                        && state.requester.activeLink().getCraftingID().toString().equals(state.linkId),
                        "Requester restart must reload the same native link UUID");
                CraftingLifecycleAuthorityObservation.recordReload(state.originalRequester, state.requester,
                        state.requester.activeLink(), state.fixture.requesterNodeId(state.requester));
                state.binding = state.request.synchronizeTracked(0, state.requester)
                        .orElseThrow();
                state.fixture.restoreBridge();
                state.stage = 2;
                helper.assertTrue(false, "Waiting for restored bridge");
            }
            if (!state.fixture.binding().restoredBridgeReady()) {
                helper.assertTrue(false, "Waiting for restored capability");
            }
            if (state.stage == 2) {
                helper.assertTrue(!state.requester.handleCrafting(CraftingBindingFixture.outputKey(), 128,
                        helper.getLevel(), state.fixture.binding().sourceService()),
                        "Reactivation must not resubmit existing native work");
                state.stage = 3;
            }
            if (!state.requester.observedDone() || state.requester.acceptedAmount() != 128) {
                helper.assertTrue(false, "Waiting for reloaded requester completion after terminal close");
            }
            helper.assertValueEqual(state.fixture.binding().outputAmount(), 128L,
                    "Closed terminal session must not own or lose the physical native result");
            state.recordRetired();
            write("craftingdisconnectrestart", 18, state, Map.of("disconnected", "true",
                    "restored", "true", "resubmitted", "false", "linkRetained", "true",
                    "restartReloaded", "true", "closedUi", "true", "acceptedResult", "128",
                    "physicalResult", "128"));
            state.finish();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 4000, required = true, manualOnly = true)
    public static void craftingRejectCycle(GameTestHelper helper) {
        var fixture = new CraftingLifecycleFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            if (!fixture.binding().ready()) {
                helper.assertTrue(false, fixture.binding().readinessState());
            }
            if (stage[0] == 0) {
                fixture.binding().enable();
                stage[0] = 1;
                helper.assertTrue(false, "Waiting for forward capability");
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.binding().bindings().capability(fixture.binding().key()).isPresent(),
                        "Acyclic forward relationship must publish before cycle attempt");
                CraftingLifecycleAuthorityObservation.authorizeCycle("craftingrejectcycle", fixture.binding(),
                        CraftingLifecycleAuthorityObservation.backendDiscoveries());
                fixture.binding().enableReverse();
                stage[0] = 2;
                helper.assertTrue(false, "Waiting for cycle reconciliation");
            }
            helper.assertTrue(fixture.binding().bindings().capability(fixture.binding().key()).isEmpty(),
                    "Forward cyclic dependency must not publish");
            helper.assertTrue(fixture.binding().bindings().capability(fixture.binding().reverseKey()).isEmpty(),
                    "Reverse cyclic dependency must not publish");
            helper.assertValueEqual(fixture.binding().bindings().nativeRequestCount(), 0,
                    "Cycle rejection must precede native submission");
            CraftingLifecycleAuthorityObservation.recordCycleRejected(
                    CraftingLifecycleAuthorityObservation.backendDiscoveries());
            NativeCraftingEvidence.write("craftingrejectcycle", 12, Map.of("cycle", "native-policy",
                    "forwardPublished", "false", "reversePublished", "false", "nativeSubmitCalls", "0",
                    "jobCardinality", "0", "scheduler", "false", "cycleEdges", "2",
                    "backendDiscoveryDelta", "0"));
            fixture.close();
            CraftingLifecycleAuthorityObservation.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 4000, required = true, manualOnly = true)
    public static void craftingRejectReplay(GameTestHelper helper) {
        var state = new LifecycleState(helper, "craftingrejectreplay");
        helper.succeedWhen(() -> {
            state.prepare();
            if (state.stage == 0) {
                state.submit(128);
                var link = state.requester.activeLink();
                state.linkId = link.getCraftingID().toString();
                state.fixture.binding().suspendCpu();
                state.persisted = state.fixture.writeState();
                state.fixture.removeBridges();
                state.originalRequester = state.requester;
                state.requester.close();
                state.requester = state.fixture.replaceRequester(state.persisted);
                state.stage = 1;
                helper.assertTrue(false, "Waiting for serialized requester reload");
            }
            if (state.stage == 1) {
                helper.assertTrue(state.requester.activeLink() != null, "Reloaded tracker must contain native link");
                CraftingLifecycleAuthorityObservation.recordReload(state.originalRequester, state.requester,
                        state.requester.activeLink(), state.fixture.requesterNodeId(state.requester));
                state.binding = state.request.synchronizeTracked(0, state.requester)
                        .orElseThrow();
                state.fixture.restoreBridge();
                state.stage = 2;
                helper.assertTrue(false, "Waiting for replay reactivation");
            }
            helper.assertTrue(state.fixture.binding().restoredBridgeReady(), "Capability must reactivate");
            if (state.stage == 2) {
                helper.assertTrue(!state.requester.handleCrafting(CraftingBindingFixture.outputKey(), 128,
                        helper.getLevel(), state.fixture.binding().sourceService()),
                        "Reloaded existing request must reject duplicate submission");
                state.binding.link().cancel();
                state.fixture.binding().resumeCpu();
                state.stage = 3;
            }
            if (!state.requester.observedCanceled()) {
                helper.assertTrue(false, "Waiting for canceled replay authority");
            }
            helper.assertTrue(state.binding.canceled(), "Canceled native state must remain authoritative");
            if (state.stage == 3 && state.fixture.binding().busyCpuCount() != 0) {
                helper.assertTrue(false, "Waiting for canceled CPU to return partial native work");
            }
            if (state.stage == 3) {
                state.recordRetired();
                state.stage = 4;
            }
            if (state.lateTicks++ < 40) {
                helper.assertTrue(false, "Waiting through replay late-event window");
            }
            CraftingLifecycleAuthorityObservation.recordLateWindow(state.requester.acceptedAmount(),
                    CraftingLifecycleAuthorityObservation.physicalInserted(), state.requester.uniqueNativeJobCount());
            helper.assertValueEqual(state.requester.uniqueNativeJobCount(), 1, "Replay must retain one native UUID");
            write("craftingrejectreplay", 15, state, Map.of("duplicateSubmitted", "false", "replayRejected", "true",
                    "jobCardinality", "1", "nativeState", "canceled"));
            state.finish();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 4000, required = true, manualOnly = true)
    public static void craftingReplaceRequester(GameTestHelper helper) {
        var state = new LifecycleState(helper, "craftingreplacerequester");
        helper.succeedWhen(() -> {
            state.prepare();
            if (state.stage == 0) {
                state.submit(128);
                var old = state.requester;
                var link = old.activeLink();
                state.linkId = link.getCraftingID().toString();
                state.fixture.binding().suspendCpu();
                state.persisted = state.fixture.writeState();
                state.originalRequester = old;
                old.close();
                state.requester = state.fixture.replaceRequester(state.persisted);
                state.replacementRequester = state.fixture.createReplacementRequester();
                state.stage = 1;
                helper.assertTrue(false, "Waiting for replacement requester Grid attachment");
            }
            if (!state.requester.isReady(state.fixture.binding().sourceChest().getMainNode().getNode())
                    || !state.replacementRequester.isReady(state.fixture.binding().sourceChest().getMainNode().getNode())) {
                helper.assertTrue(false, "Waiting for replacement requester");
            }
            if (state.stage == 1) {
                CraftingLifecycleAuthorityObservation.recordReload(state.originalRequester, state.requester,
                        state.requester.activeLink(), state.fixture.requesterNodeId(state.requester));
                state.binding = state.request.synchronizeTracked(0, state.requester)
                        .orElseThrow();
                CraftingLifecycleAuthorityObservation.recordReplacement(state.replacementRequester,
                        state.fixture.requesterNodeId(state.replacementRequester));
                helper.assertTrue(state.request.synchronizeTracked(0, state.replacementRequester).isEmpty(),
                    "Same-coordinate replacement must not inherit an old native link");
                state.fixture.binding().resumeCpu();
                state.stage = 2;
                helper.assertTrue(false, "Waiting for authorized requester completion");
            }
            if (!state.requester.observedDone() || state.requester.acceptedAmount() != 128) {
                helper.assertTrue(false, "Waiting for authorized requester completion");
            }
            helper.assertValueEqual(state.replacementRequester.acceptedAmount(), 0L,
                    "Same-coordinate replacement must receive no result");
            state.recordRetired();
            var receipt = space.controlnet.ae2federation.crafting.binding.CraftingBindingService
                    .closeLevel(helper.getLevel());
            helper.assertValueEqual(receipt.nativeRequestsRetired(), 0,
                    "Level close must find no completed terminal native request");
            write("craftingreplacerequester", 16, state, Map.of("sameCoordinate", "true",
                    "requesterIdentityReused", "false", "oldLinkInherited", "false", "nativeState", "done",
                    "authorizedResult", "128", "replacementResult", "0"));
            state.finish();
        });
    }

    private static void write(String testId, int assertions, LifecycleState state, Map<String, String> facts) {
        var evidence = new java.util.HashMap<>(facts);
        evidence.put("linkId", state.linkId);
        evidence.put("nativeJobIds", state.linkId);
        evidence.put("uniqueNativeTasks", "1");
        evidence.put("requester", identity(state.requester));
        evidence.put("nativeLink", identity(state.binding.link()));
        evidence.put("craftingService", identity(state.fixture.binding().sourceService()));
        evidence.put("provider", identity(state.fixture.authorityProvider()));
        evidence.put("physicalDestination", identity(state.fixture.binding().sourcePhysicalStorage()));
        evidence.put("federationScheduler", "false");
        NativeCraftingEvidence.write(testId, assertions, evidence);
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    private static final class LifecycleState {
        private final GameTestHelper helper;
        private final CraftingLifecycleFixture fixture;
        private final String testId;
        private NativeCraftingRequester requester;
        private NativeCraftingRequester originalRequester;
        private NativeCraftingRequester replacementRequester;
        private space.controlnet.ae2federation.crafting.terminal.NativeTerminalRequest request;
        private space.controlnet.ae2federation.crafting.binding.NativeCraftingRequestBinding binding;
        private CompoundTag persisted;
        private boolean enabled;
        private boolean submitted;
        private boolean armed;
        private boolean authorityAuthorized;
        private String linkId;
        private int stage;
        private int lateTicks;

        private LifecycleState(GameTestHelper helper, String testId) {
            this.helper = helper;
            this.testId = testId;
            fixture = new CraftingLifecycleFixture(helper);
        }

        private void prepare() {
            if (!enabled) {
                if (!fixture.binding().ready()) {
                    helper.assertTrue(false, fixture.binding().readinessState());
                }
                fixture.binding().enable();
                fixture.binding().insertMaterials(64);
                requester = fixture.createRequester();
                fixture.openTerminalSession();
                enabled = true;
                helper.assertTrue(false, "Waiting for requester and storage cache");
            }
            if (!submitted) {
                helper.assertTrue(requester.isReady(fixture.binding().sourceChest().getMainNode().getNode()),
                        "Waiting for native requester Grid attachment");
                if (!armed) {
                    armed = true;
                    helper.assertTrue(false, "Waiting one tick for native crafting cache stabilization");
                }
            }
        }

        private void submit(long amount) {
            if (!authorityAuthorized) {
                CraftingLifecycleAuthorityObservation.authorizeRequest(testId, fixture.binding(), requester,
                        fixture.terminalSession(), CraftingBindingFixture.outputKey(), amount);
                authorityAuthorized = true;
            }
            if (request == null) {
                request = fixture.beginTerminalRequest(CraftingBindingFixture.outputKey(), amount);
            }
            var tracked = request.submitTracked(0, requester, requester::handleCrafting);
            submitted = tracked.isPresent();
            tracked.ifPresent(value -> binding = value);
            helper.assertTrue(submitted && requester.activeLink() != null, "Native tracker must submit one request");
        }

        private void recordRetired() {
            CraftingLifecycleAuthorityObservation.recordRetired(
                    fixture.binding().bindings().nativeRequestCount(),
                    fixture.binding().bindings().nativeLinkOwnerCount());
        }

        private void finish() {
            fixture.close();
            CraftingLifecycleAuthorityObservation.close();
        }
    }
}
