package space.controlnet.ae2federation.test;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.terminal.NativeTerminalSubmission;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.crafting.TerminalCraftingEvidence;
import space.controlnet.ae2federation.test.crafting.TerminalCraftingFixture;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;

@PrefixGameTestTemplate(false)
public final class TerminalCraftingFailureGameTests {
    private TerminalCraftingFailureGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 800, required = true, manualOnly = true)
    public static void terminalMissingMaterial(GameTestHelper helper) {
        var fixture = new TerminalCraftingFixture(helper);
        var initialized = new boolean[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for missing-material topology: " + fixture.readinessState());
            if (!initialized[0]) {
                TerminalNativeObservation.begin("terminalmissingmaterial");
                fixture.authorizeAndDiscover();
                fixture.captureAuthority("terminalmissingmaterial");
                fixture.begin(4);
                initialized[0] = true;
                helper.assertTrue(false, "Waiting for missing-material plan");
            }
            helper.assertTrue(fixture.planReady(), "Waiting for missing-material plan");
            helper.assertTrue(fixture.plan().simulation(), "Missing source material must remain a native simulation");
            helper.assertValueEqual(fixture.plan().missingItems().get(TerminalCraftingFixture.inputKey()), 2L,
                    "Native plan must report two missing planks");
            fixture.submit();
            helper.assertTrue(fixture.submitError(CraftingSubmitErrorCode.INCOMPLETE_PLAN),
                    "Native service must return INCOMPLETE_PLAN");
            helper.assertValueEqual(fixture.materialAmount(), 0L, "Missing plan must not extract material");
            var observation = TerminalNativeObservation.snapshot();
            helper.assertValueEqual(observation.cpuSubmissions(), 0, "Missing plan must not reach a native CPU");
            helper.assertValueEqual(observation.jobIds().size(), 0, "Missing plan must create no native job");
            var facts = new java.util.TreeMap<>(authorityFacts(fixture));
            facts.putAll(Map.ofEntries(
                    Map.entry("planSimulation", "true"), Map.entry("missingMaterial", "2"),
                    Map.entry("submitError", "INCOMPLETE_PLAN"), Map.entry("submitted", "false"),
                    Map.entry("cannedError", "false"),
                    Map.entry("materialBefore", "0"), Map.entry("materialAfter", "0"),
                    Map.entry("jobCardinality", "0"), Map.entry("jobIds", "none"),
                    Map.entry("resultAmount", "0")));
            TerminalCraftingEvidence.write("terminalmissingmaterial", 10, facts);
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 800, required = true, manualOnly = true)
    public static void terminalNoCpu(GameTestHelper helper) {
        var fixture = new TerminalCraftingFixture(helper);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] < 2) {
                helper.assertTrue(fixture.ready(), "Waiting for no-CPU topology: " + fixture.readinessState());
            }
            if (phase[0] == 0) {
                TerminalNativeObservation.begin("terminalnocpu");
                fixture.authorizeAndDiscover();
                fixture.captureAuthority("terminalnocpu");
                fixture.insertMaterials(2);
                fixture.begin(4);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for executable no-CPU plan");
            }
            if (phase[0] == 1) {
                helper.assertTrue(fixture.planReady(), "Waiting for executable no-CPU plan");
                helper.assertTrue(!fixture.plan().simulation(), "Available material must produce an executable plan");
                fixture.removeCpu();
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for native CPU removal");
            }
            helper.assertValueEqual(fixture.cpuCount(), 0, "Native service must expose no CPU at submission");
            fixture.submit();
            helper.assertTrue(fixture.submitError(CraftingSubmitErrorCode.NO_CPU_FOUND),
                    "Native service must return NO_CPU_FOUND");
            helper.assertValueEqual(fixture.materialAmount(), 2L, "No-CPU rejection must leave material untouched");
            var observation = TerminalNativeObservation.snapshot();
            helper.assertValueEqual(observation.cpuSubmissions(), 0, "No-CPU rejection must not reach a CPU");
            helper.assertValueEqual(observation.jobIds().size(), 0, "No-CPU rejection must create no job");
            var facts = new java.util.TreeMap<>(authorityFacts(fixture));
            facts.putAll(Map.ofEntries(
                    Map.entry("planSimulation", "false"), Map.entry("cpuCount", "0"),
                    Map.entry("submitError", "NO_CPU_FOUND"), Map.entry("submitted", "false"),
                    Map.entry("cannedError", "false"),
                    Map.entry("materialBefore", "2"), Map.entry("materialAfter", "2"),
                    Map.entry("jobCardinality", "0"), Map.entry("jobIds", "none"),
                    Map.entry("resultAmount", "0"), Map.entry("authorityCurrent", "true")));
            TerminalCraftingEvidence.write("terminalnocpu", 10, facts);
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1000, required = true, manualOnly = true)
    public static void terminalRejectAsyncWorldAccess(GameTestHelper helper) {
        var fixture = new TerminalCraftingFixture(helper);
        var forbiddenPlan = new AtomicReference<Future<ICraftingPlan>>();
        var phase = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for planner boundary topology: " + fixture.readinessState());
            if (phase[0] == 0) {
                TerminalNativeObservation.begin("terminalrejectasyncworldaccess");
                fixture.authorizeAndDiscover();
                fixture.captureAuthority("terminalrejectasyncworldaccess");
                fixture.insertMaterials(2);
                var level = fixture.binding().level();
                var policy = PolicyService.get(level);
                var key = fixture.binding().key();
                var grid = fixture.binding().providerGrid();
                var binding = fixture.session().snapshot().binding();
                var node = fixture.session().snapshot().providerSources().getFirst().node();
                var worldPosition = helper.absolutePos(BlockPos.ZERO);
                ICraftingSimulationRequester probe = new ICraftingSimulationRequester() {
                    @Override
                    public appeng.api.networking.security.IActionSource getActionSource() {
                        return fixture.actionSource();
                    }

                    @Override
                    public appeng.api.networking.IGridNode getGridNode() {
                        expectRejected(() -> helper.getLevel());
                        expectRejected(() -> level.getBlockEntity(worldPosition));
                        expectRejected(() -> policy.configured(key));
                        expectRejected(() -> FederationDomainRegistryAccess.confirmedNetworkId(grid));
                        expectRejected(binding::isCurrent);
                        return node;
                    }
                };
                forbiddenPlan.set(fixture.session().snapshot().service().beginCraftingCalculation(level, probe,
                        TerminalCraftingFixture.outputKey(), 4, CalculationStrategy.REPORT_MISSING_ITEMS));
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for guarded planner probe");
            }
            if (phase[0] == 1) {
                if (!forbiddenPlan.get().isDone()) helper.assertTrue(false, "Waiting for guarded planner probe");
                helper.assertTrue(completed(forbiddenPlan.get()) != null,
                        "Guarded planner must retain the captured native path");
                fixture.begin(4);
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for valid captured terminal plan");
            }
            if (phase[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for valid captured terminal plan");
                fixture.revoke();
                helper.assertTrue(fixture.submit() instanceof NativeTerminalSubmission.StaleBinding,
                        "Policy change must fail closed before stale submission");
                helper.assertValueEqual(fixture.materialAmount(), 2L, "Stale plan must not extract material");
                phase[0] = 3;
            }
            var observation = TerminalNativeObservation.snapshot();
            helper.assertValueEqual(observation.rejectedMutableAccess(), java.util.Set.of(
                    "gametest-helper", "world-block", "policy", "domain", "backend"),
                    "Every forbidden planner-thread boundary must reject");
            helper.assertTrue(observation.requesterNodeCalls() > 0,
                    "Valid planning must use the captured requester node");
            helper.assertValueEqual(observation.submitCalls(), 0, "Stale work must never call native submitJob");
            helper.assertValueEqual(observation.jobIds().size(), 0, "Stale work must create no native job");
            var facts = new java.util.TreeMap<>(authorityFacts(fixture));
            facts.putAll(Map.ofEntries(
                    Map.entry("plannerThread", observation.plannerThreadToken()),
                    Map.entry("rejectedMutableAccess", observation.rejectedMutableAccess().stream().sorted()
                            .collect(java.util.stream.Collectors.joining(","))),
                    Map.entry("capturedPlanUsable", "true"), Map.entry("staleBindingRejected", "true"),
                    Map.entry("plannerWorldAccess", "false"), Map.entry("staleBindingSubmitted", "false"),
                    Map.entry("nativeSubmitCalls", "0"), Map.entry("jobCardinality", "0"),
                    Map.entry("jobIds", "none"), Map.entry("materialBefore", "2"),
                    Map.entry("materialAfter", "2"), Map.entry("resultAmount", "0")));
            TerminalCraftingEvidence.write("terminalrejectasyncworldaccess", 10, facts);
            fixture.close();
        });
    }

    private static ICraftingPlan completed(Future<ICraftingPlan> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Planner boundary probe interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Planner boundary probe failed", exception);
        }
    }

    private static void expectRejected(Runnable access) {
        try {
            access.run();
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Planner thread mutable access rejected")) {
                return;
            }
            throw exception;
        }
        throw new IllegalStateException("Planner-thread mutable access was not rejected");
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    private static Map<String, String> authorityFacts(TerminalCraftingFixture fixture) {
        var snapshot = fixture.session().snapshot();
        var provider = snapshot.providerSources().getFirst();
        var cpu = snapshot.nativeCpus().stream().findFirst().orElse(null);
        return Map.ofEntries(
                Map.entry("sourceNetworkId", fixture.binding().key().providerNetworkId().toString()),
                Map.entry("sourceGrid", identity(snapshot.sourceGrid())),
                Map.entry("service", identity(snapshot.service())),
                Map.entry("provider", identity(provider.provider())),
                Map.entry("providerNode", identity(provider.node())),
                Map.entry("providerNodeId", provider.registrationNodeId().toString()),
                Map.entry("cpu", cpu == null ? "none" : identity(cpu)),
                Map.entry("binding", identity(snapshot.binding())));
    }
}
