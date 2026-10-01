package space.controlnet.ae2federation.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.remote.RemoteCraftingService;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.crafting.CraftingBindingFixture;
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
            fixture.close();
        });
    }
}
