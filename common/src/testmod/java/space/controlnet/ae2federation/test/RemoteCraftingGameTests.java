package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.crafting.remote.RemoteCraftingService;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.crafting.CraftingBindingFixture;
import space.controlnet.ae2federation.test.crafting.NativeCraftingEvidence;
import space.controlnet.ae2federation.test.crafting.RemoteCraftingChainFixture;

/**
 * A Crafting rule as a player meets it: the consumer's own ME crafting service lists what the provider can craft, and a
 * job planned and run there on the consumer's CPU is fulfilled by a native job on the provider's patterns, CPU and
 * materials, whose output arrives in the consumer's storage.
 */
@PrefixGameTestTemplate(false)
public final class RemoteCraftingGameTests {
    private RemoteCraftingGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void craftingRemoteRequest(GameTestHelper helper) {
        var fixture = new CraftingBindingFixture(helper, true);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native Crafting topology: " + fixture.readinessState());
            if (stage[0] == 0) {
                fixture.enable();
                fixture.insertMaterials(2);
                fixture.addConsumerCpu();
                stage[0] = 1;
                helper.assertTrue(false, "Waiting for the consumer CPU");
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerCpuReady(), "Waiting for the consumer CPU to join its Grid");
                helper.assertTrue(fixture.consumerService().getCraftingFor(CraftingBindingFixture.outputKey()).isEmpty(),
                        "The consumer must have no pattern of its own for the output");
                // The terminal lists getCraftables, which includes emitable keys; isCraftable counts patterns only.
                helper.assertTrue(fixture.consumerService().getCraftables(key -> true)
                                .contains(CraftingBindingFixture.outputKey()),
                        "Waiting for the consumer to list the provider's craftable output");
                fixture.beginOnConsumer(4);
                stage[0] = 2;
                helper.assertTrue(false, "Waiting for the consumer's plan");
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertTrue(!fixture.plan().simulation(), "The consumer's plan must be executable");
                helper.assertTrue(fixture.submitOnConsumer(), "The consumer must start the job on its own CPU");
                stage[0] = 3;
                helper.assertTrue(false, "Waiting for the remote job");
            }
            helper.assertValueEqual(fixture.consumerPhysicalOutputAmount(), 4L,
                    "Four sticks must arrive in the consumer's chest");
            helper.assertValueEqual(fixture.physicalMaterialAmount(), 0L,
                    "The provider's two planks must be used");
            helper.assertValueEqual(fixture.physicalOutputAmount(), 0L,
                    "No sticks may stay in the provider's storage");
            helper.assertValueEqual(fixture.busyConsumerCpuCount() + fixture.busyCpuCount(), 0L,
                    "Both jobs must finish");
            var submissions = RemoteCraftingService.get(helper.getLevel()).submissionCount(fixture.key());
            helper.assertValueEqual(submissions, 1, "Exactly one provider job must run");
            NativeCraftingEvidence.write("craftingremoterequest", 12, Map.of(
                    "consumerPatterns", Integer.toString(fixture.consumerService()
                            .getCraftingFor(CraftingBindingFixture.outputKey()).size()),
                    "resultInserted", Long.toString(fixture.consumerPhysicalOutputAmount()),
                    "providerMaterialAfter", Long.toString(fixture.physicalMaterialAmount()),
                    "providerResidue", Long.toString(fixture.physicalOutputAmount()),
                    "providerSubmissions", Integer.toString(submissions),
                    "busyCpus", Long.toString(fixture.busyConsumerCpuCount() + fixture.busyCpuCount())));
            fixture.close();
        });
    }

    /**
     * Rules run consumer to middle and middle to source only. The consumer's sticks come from the middle, whose own
     * plan requests the planks its pattern needs from the source in turn: a request recurses along Crafting rules.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = true, manualOnly = true)
    public static void craftingRemoteChain(GameTestHelper helper) {
        var fixture = new RemoteCraftingChainFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for the three-Grid chain: " + fixture.readinessState());
            if (stage[0] == 0) {
                fixture.enableChain();
                fixture.insertLogs(1);
                stage[0] = 1;
                helper.assertTrue(false, "Waiting for the chain's projections");
            }
            if (stage[0] == 1) {
                helper.assertTrue(PolicyService.get(helper.getLevel()).configured(fixture.consumerToSource()).isEmpty(),
                        "The consumer must hold no rule with the source");
                helper.assertTrue(!fixture.consumerHasPattern(RemoteCraftingChainFixture.stick()),
                        "The consumer must have no stick pattern of its own");
                helper.assertTrue(fixture.consumerLists(RemoteCraftingChainFixture.stick()),
                        "Waiting for the consumer to list the middle's sticks");
                fixture.beginOnConsumer(4);
                stage[0] = 2;
                helper.assertTrue(false, "Waiting for the consumer's plan");
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertTrue(!fixture.plan().simulation(), "The consumer's plan must be executable");
                helper.assertTrue(fixture.submitOnConsumer(), "The consumer must start the job on its own CPU");
                stage[0] = 3;
                helper.assertTrue(false, "Waiting for the chained jobs");
            }
            helper.assertValueEqual(fixture.consumerAmount(RemoteCraftingChainFixture.stick()), 4L,
                    "Four sticks must arrive in the consumer's chest");
            helper.assertValueEqual(fixture.sourceAmount(RemoteCraftingChainFixture.log()), 0L,
                    "The source's log must be used");
            helper.assertValueEqual(fixture.sourceAmount(RemoteCraftingChainFixture.planks()), 2L,
                    "The two planks the middle did not ask for must stay with the source");
            helper.assertValueEqual(fixture.middleAmount(RemoteCraftingChainFixture.planks())
                            + fixture.middleAmount(RemoteCraftingChainFixture.stick()), 0L,
                    "Nothing may stay in the middle's storage");
            helper.assertValueEqual(fixture.busyCpuCount(), 0L, "All three jobs must finish");
            var remote = RemoteCraftingService.get(helper.getLevel());
            helper.assertValueEqual(remote.submissionCount(fixture.consumerToMiddle()), 1,
                    "Exactly one provider job on the middle");
            helper.assertValueEqual(remote.submissionCount(fixture.middleToSource()), 1,
                    "Exactly one provider job on the source, requested by the middle");
            NativeCraftingEvidence.write("craftingremotechain", 14, Map.of(
                    "consumerSourceRule", PolicyService.get(helper.getLevel()).configured(fixture.consumerToSource())
                            .isPresent() ? "present" : "absent",
                    "resultInserted", Long.toString(fixture.consumerAmount(RemoteCraftingChainFixture.stick())),
                    "sourceLogsAfter", Long.toString(fixture.sourceAmount(RemoteCraftingChainFixture.log())),
                    "sourcePlanksAfter", Long.toString(fixture.sourceAmount(RemoteCraftingChainFixture.planks())),
                    "middleResidue", Long.toString(fixture.middleAmount(RemoteCraftingChainFixture.planks())
                            + fixture.middleAmount(RemoteCraftingChainFixture.stick())),
                    "middleSubmissions", Integer.toString(remote.submissionCount(fixture.consumerToMiddle())),
                    "sourceSubmissions", Integer.toString(remote.submissionCount(fixture.middleToSource())),
                    "busyCpus", Long.toString(fixture.busyCpuCount())));
            fixture.close();
        });
    }

    /** Cancelling the consumer's job cancels the provider job serving it, which returns its materials. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void craftingRemoteCancel(GameTestHelper helper) {
        var fixture = new CraftingBindingFixture(helper, true);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native Crafting topology: " + fixture.readinessState());
            if (stage[0] == 0) {
                fixture.enable();
                fixture.insertMaterials(2);
                fixture.addConsumerCpu();
                stage[0] = 1;
                helper.assertTrue(false, "Waiting for the consumer CPU");
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerCpuReady(), "Waiting for the consumer CPU to join its Grid");
                helper.assertTrue(fixture.consumerService().getCraftables(key -> true)
                                .contains(CraftingBindingFixture.outputKey()),
                        "Waiting for the consumer to list the provider's craftable output");
                fixture.beginOnConsumer(4);
                stage[0] = 2;
                helper.assertTrue(false, "Waiting for the consumer's plan");
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertTrue(fixture.submitOnConsumer(), "The consumer must start the job on its own CPU");
                stage[0] = 3;
                helper.assertTrue(false, "Waiting for the provider job");
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.busyCpuCount(), 1L, "Waiting for the provider job to start");
                // Hold the provider's job, so it is still running when the consumer cancels.
                fixture.suspendCpu();
                fixture.cancelConsumerJob();
                stage[0] = 4;
                helper.assertTrue(false, "Waiting for the provider job to be cancelled");
            }
            helper.assertValueEqual(fixture.busyCpuCount() + fixture.busyConsumerCpuCount(), 0L,
                    "The provider job must be cancelled with the consumer's");
            helper.assertValueEqual(fixture.physicalMaterialAmount(), 2L,
                    "The cancelled provider job must return its two planks");
            helper.assertValueEqual(fixture.consumerPhysicalOutputAmount(), 0L, "No sticks may be delivered");
            var submissions = RemoteCraftingService.get(helper.getLevel()).submissionCount(fixture.key());
            helper.assertValueEqual(submissions, 1, "Exactly one provider job must have run");
            NativeCraftingEvidence.write("craftingremotecancel", 10, Map.of(
                    "providerMaterialAfter", Long.toString(fixture.physicalMaterialAmount()),
                    "consumerResult", Long.toString(fixture.consumerPhysicalOutputAmount()),
                    "providerSubmissions", Integer.toString(submissions),
                    "busyCpus", Long.toString(fixture.busyCpuCount() + fixture.busyConsumerCpuCount())));
            fixture.close();
        });
    }

    /**
     * A provider without the materials runs nothing and the consumer's CPU keeps waiting, as for a vanilla crafting
     * emitter; once the materials arrive the provider's plan is tried again and the job completes.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = true, manualOnly = true)
    public static void craftingRemoteMissingRetry(GameTestHelper helper) {
        var fixture = new CraftingBindingFixture(helper, true);
        var stage = new int[1];
        var waitedUntil = new long[1];
        var whileMissing = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native Crafting topology: " + fixture.readinessState());
            if (stage[0] == 0) {
                fixture.enable();
                fixture.addConsumerCpu();
                stage[0] = 1;
                helper.assertTrue(false, "Waiting for the consumer CPU");
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerCpuReady(), "Waiting for the consumer CPU to join its Grid");
                helper.assertTrue(fixture.consumerService().getCraftables(key -> true)
                                .contains(CraftingBindingFixture.outputKey()),
                        "Waiting for the consumer to list the provider's craftable output");
                fixture.beginOnConsumer(4);
                stage[0] = 2;
                helper.assertTrue(false, "Waiting for the consumer's plan");
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertTrue(fixture.submitOnConsumer(), "The consumer must start the job on its own CPU");
                waitedUntil[0] = helper.getTick() + 120;
                stage[0] = 3;
                helper.assertTrue(false, "Waiting while the provider lacks planks");
            }
            if (stage[0] == 3) {
                helper.assertTrue(helper.getTick() >= waitedUntil[0], "Waiting while the provider lacks planks");
                helper.assertValueEqual(RemoteCraftingService.get(helper.getLevel()).submissionCount(fixture.key()), 0,
                        "A provider plan missing materials must not be submitted");
                helper.assertValueEqual(fixture.busyConsumerCpuCount(), 1L, "The consumer's CPU must keep waiting");
                whileMissing[0] = RemoteCraftingService.get(helper.getLevel()).submissionCount(fixture.key());
                fixture.insertMaterials(2);
                stage[0] = 4;
                helper.assertTrue(false, "Waiting for the retried provider job");
            }
            helper.assertValueEqual(fixture.consumerPhysicalOutputAmount(), 4L,
                    "Four sticks must arrive once the provider has the planks");
            helper.assertValueEqual(fixture.busyConsumerCpuCount() + fixture.busyCpuCount(), 0L,
                    "Both jobs must finish");
            helper.assertValueEqual(RemoteCraftingService.get(helper.getLevel()).submissionCount(fixture.key()), 1,
                    "Exactly one provider job must run");
            NativeCraftingEvidence.write("craftingremotemissingretry", 11, Map.of(
                    "submissionsWhileMissing", Integer.toString(whileMissing[0]),
                    "resultInserted", Long.toString(fixture.consumerPhysicalOutputAmount()),
                    "providerSubmissions", Integer.toString(
                            RemoteCraftingService.get(helper.getLevel()).submissionCount(fixture.key())),
                    "busyCpus", Long.toString(fixture.busyConsumerCpuCount() + fixture.busyCpuCount())));
            fixture.close();
        });
    }

    /**
     * A server restart while a provider job runs: the provider's requester is saved with its Bridge and the service
     * starts empty. The reloaded Bridge's requester reconnects AE2's job, which then delivers to the consumer, and
     * nothing is requested a second time. The CPUs stay loaded; only the requester's host and the service restart.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = true, manualOnly = true)
    public static void craftingRemoteReload(GameTestHelper helper) {
        var fixture = new CraftingBindingFixture(helper, true);
        var stage = new int[1];
        var reloadedAt = new long[1];
        var savedLink = new boolean[1];
        helper.succeedWhen(() -> {
            if (stage[0] < 4) {
                helper.assertTrue(fixture.ready(), "Waiting for native Crafting topology: " + fixture.readinessState());
            }
            if (stage[0] == 0) {
                fixture.enable();
                fixture.insertMaterials(2);
                fixture.addConsumerCpu();
                stage[0] = 1;
                helper.assertTrue(false, "Waiting for the consumer CPU");
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerCpuReady(), "Waiting for the consumer CPU to join its Grid");
                helper.assertTrue(fixture.consumerService().getCraftables(key -> true)
                                .contains(CraftingBindingFixture.outputKey()),
                        "Waiting for the consumer to list the provider's craftable output");
                fixture.beginOnConsumer(4);
                stage[0] = 2;
                helper.assertTrue(false, "Waiting for the consumer's plan");
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertTrue(fixture.submitOnConsumer(), "The consumer must start the job on its own CPU");
                stage[0] = 3;
                helper.assertTrue(false, "Waiting for the provider job");
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.busyCpuCount(), 1L, "Waiting for the provider job to start");
                // Hold the provider's job, so it is still running when its requester's host reloads.
                fixture.suspendCpu();
                RemoteCraftingService.closeLevel(helper.getLevel());
                var saved = fixture.reloadBridgeHost();
                savedLink[0] = saved.toString().contains("ae2federation_crafting_");
                helper.assertTrue(savedLink[0], "The Bridge must save the provider job's link with its host");
                reloadedAt[0] = helper.getTick();
                stage[0] = 4;
                helper.assertTrue(false, "Waiting for the reloaded Bridge");
            }
            if (stage[0] == 4) {
                // Past AE2's 60-tick window for a missing requester, and long enough for the service to settle.
                helper.assertTrue(helper.getTick() >= reloadedAt[0] + 200, "Waiting through the reload window");
                helper.assertValueEqual(fixture.busyCpuCount(), 1L,
                        "The provider job must survive its requester's reload");
                helper.assertValueEqual(fixture.busyConsumerCpuCount(), 1L, "The consumer's CPU must keep waiting");
                fixture.resumeCpu();
                stage[0] = 5;
                helper.assertTrue(false, "Waiting for the resumed provider job");
            }
            helper.assertValueEqual(fixture.consumerPhysicalOutputAmount(), 4L,
                    "Four sticks must arrive through the reloaded requester");
            helper.assertValueEqual(fixture.physicalMaterialAmount(), 0L, "Only the job's two planks may be used");
            helper.assertValueEqual(fixture.busyConsumerCpuCount() + fixture.busyCpuCount(), 0L,
                    "Both jobs must finish");
            helper.assertValueEqual(RemoteCraftingService.get(helper.getLevel()).submissionCount(fixture.key()), 0,
                    "The restarted service must not request the job again");
            NativeCraftingEvidence.write("craftingremotereload", 14, Map.of(
                    "savedLink", Boolean.toString(savedLink[0]),
                    "resultInserted", Long.toString(fixture.consumerPhysicalOutputAmount()),
                    "providerMaterialAfter", Long.toString(fixture.physicalMaterialAmount()),
                    "resubmissions", Integer.toString(
                            RemoteCraftingService.get(helper.getLevel()).submissionCount(fixture.key())),
                    "busyCpus", Long.toString(fixture.busyConsumerCpuCount() + fixture.busyCpuCount())));
            fixture.close();
        });
    }

    /**
     * A restart of both networks while a provider job runs: every block of both Grids is unloaded and loaded again
     * from its saved data in one tick, and the service starts empty, so AE2 restores both CPUs' jobs and builds both
     * Grids and their crafting services anew, and the job's two links meet again. The job then delivers to the consumer
     * and nothing is requested a second time.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = true, manualOnly = true)
    public static void craftingRemoteWorldReload(GameTestHelper helper) {
        var fixture = new CraftingBindingFixture(helper, true);
        var stage = new int[1];
        var reloadedAt = new long[1];
        var saved = new int[2];
        var before = new Object[2];
        helper.succeedWhen(() -> {
            if (stage[0] < 4) {
                helper.assertTrue(fixture.ready(), "Waiting for native Crafting topology: " + fixture.readinessState());
            }
            if (stage[0] == 0) {
                fixture.enable();
                fixture.insertMaterials(2);
                fixture.addConsumerCpu();
                stage[0] = 1;
                helper.assertTrue(false, "Waiting for the consumer CPU");
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerCpuReady(), "Waiting for the consumer CPU to join its Grid");
                helper.assertTrue(fixture.consumerService().getCraftables(key -> true)
                                .contains(CraftingBindingFixture.outputKey()),
                        "Waiting for the consumer to list the provider's craftable output");
                fixture.beginOnConsumer(4);
                stage[0] = 2;
                helper.assertTrue(false, "Waiting for the consumer's plan");
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertTrue(fixture.submitOnConsumer(), "The consumer must start the job on its own CPU");
                stage[0] = 3;
                helper.assertTrue(false, "Waiting for the provider job");
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.busyCpuCount(), 1L, "Waiting for the provider job to start");
                // AE2 saves a suspended job as suspended, so the job is still running after the reload.
                fixture.suspendCpu();
                RemoteCraftingService.closeLevel(helper.getLevel());
                before[0] = fixture.consumerGrid();
                before[1] = fixture.providerGrid();
                var tags = fixture.reloadAll().values();
                saved[0] = (int) tags.stream().filter(tag -> tag.contains("job")).count();
                saved[1] = (int) tags.stream().filter(tag -> tag.toString().contains("ae2federation_crafting_"))
                        .count();
                helper.assertValueEqual(saved[0], 2, "Both CPUs must save their running jobs");
                helper.assertValueEqual(saved[1], 1, "The Bridge must save the provider job's link");
                reloadedAt[0] = helper.getTick();
                stage[0] = 4;
                helper.assertTrue(false, "Waiting for the reloaded networks");
            }
            if (stage[0] == 4) {
                helper.assertTrue(helper.getTick() >= reloadedAt[0] + 200, "Waiting through the reload window");
                helper.assertTrue(fixture.consumerGrid() != before[0] && fixture.providerGrid() != before[1],
                        "Both Grids and their crafting services must be built anew");
                helper.assertValueEqual(fixture.busyCpuCount(), 1L, "The provider CPU must restore its job");
                helper.assertValueEqual(fixture.busyConsumerCpuCount(), 1L, "The consumer CPU must restore its job");
                fixture.resumeCpu();
                stage[0] = 5;
                helper.assertTrue(false, "Waiting for the resumed provider job");
            }
            helper.assertValueEqual(fixture.consumerPhysicalOutputAmount(), 4L,
                    "Four sticks must arrive after both networks reloaded");
            helper.assertValueEqual(fixture.physicalMaterialAmount(), 0L, "Only the job's two planks may be used");
            helper.assertValueEqual(fixture.physicalOutputAmount(), 0L, "No sticks may stay with the provider");
            helper.assertValueEqual(fixture.busyConsumerCpuCount() + fixture.busyCpuCount(), 0L,
                    "Both jobs must finish");
            var resubmissions = RemoteCraftingService.get(helper.getLevel()).submissionCount(fixture.key());
            helper.assertValueEqual(resubmissions, 0, "The restarted service must not request the job again");
            NativeCraftingEvidence.write("craftingremoteworldreload", 17, Map.of(
                    "savedCpuJobs", Integer.toString(saved[0]),
                    "savedLinks", Integer.toString(saved[1]),
                    "resultInserted", Long.toString(fixture.consumerPhysicalOutputAmount()),
                    "providerMaterialAfter", Long.toString(fixture.physicalMaterialAmount()),
                    "providerResidue", Long.toString(fixture.physicalOutputAmount()),
                    "resubmissions", Integer.toString(resubmissions),
                    "busyCpus", Long.toString(fixture.busyConsumerCpuCount() + fixture.busyCpuCount())));
            fixture.close();
        });
    }

    /**
     * The rule is switched on before the provider has a CPU, so no binding can be published yet. A player then adds
     * the CPU: nothing else changes (no rule edit, no topology change), and the consumer must still come to list the
     * provider's craftable output, as it must after a restart, where the domain forms before the provider's CPUs and
     * patterns are loaded.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void craftingRemoteLateProvider(GameTestHelper helper) {
        var fixture = new CraftingBindingFixture(helper, false);
        var stage = new int[1];
        var cpuPlacedAt = new long[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native Crafting topology: " + fixture.readinessState());
            if (stage[0] == 0) {
                fixture.enable();
                stage[0] = 1;
                helper.assertTrue(false, "Waiting for the rule without a provider CPU");
            }
            if (stage[0] == 1) {
                helper.assertTrue(CraftingBindingService.publishedBindingsIfPresent(helper.getLevel()).stream()
                                .noneMatch(binding -> binding.relationship().key().equals(fixture.key())),
                        "No binding can be published while the provider has no CPU");
                fixture.placeProviderCpu();
                cpuPlacedAt[0] = helper.getTick();
                stage[0] = 2;
                helper.assertTrue(false, "Waiting for the provider CPU");
            }
            helper.assertTrue(fixture.consumerService().getCraftables(key -> true)
                            .contains(CraftingBindingFixture.outputKey()),
                    "The consumer must list the provider's craftable output once the provider has a CPU");
            helper.assertTrue(helper.getTick() - cpuPlacedAt[0] <= 100,
                    "The binding must follow the provider's CPU within 100 ticks");
            NativeCraftingEvidence.write("craftingremotelateprovider", 4, Map.of(
                    "bindingBeforeCpu", "absent",
                    "consumerListsAfterCpu", "true",
                    "withinTicks", "true"));
            // Leaves no live binding behind for tests that count the shared level's bindings or discoveries.
            fixture.setEnabled(PolicyService.get(helper.getLevel()).revision(fixture.key()), false);
            fixture.close();
        });
    }
}
