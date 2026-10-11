package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;

/** Real addon CPU chunk unload; the remote processing machine is explicitly hand-operated by this test. */
final class IndependentCpuChunkScene {
    private static final BlockPos CPU = new BlockPos(1, 0, 3);
    private static final String TYPE = "com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU";

    static void run(GameTestHelper helper) {
        if ("resume".equals(System.getProperty("ae2federation.compat.cpuRestartPhase"))) {
            IndependentCpuRestart.resume(helper);
            return;
        }
        var fixture = new PatternProjectionFixture(helper, PatternProjectionFixture.ConsumerCpus.NETHER);
        int[] stage = {0};
        long[] sweeps = {0};
        helper.succeedWhen(() -> {
            if (stage[0] == 0) {
                helper.assertTrue(fixture.ready(), "Waiting for the native quantum-linked fixture");
                fixture.site().setBlock(CPU, AddonCraftingScene.block("ae2lt:pigmee_mentalmath_unit").defaultBlockState());
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerService().getCpus().stream().anyMatch(cpu -> cpu.getClass().getName().equals(TYPE) || cpu.getClass().getName().equals("com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool")),
                        "The actual independent CPU must join through the native Quantum Network Bridge: "
                                + fixture.consumerService().getCpus().stream().map(cpu -> cpu.getClass().getName()).toList());
                helper.assertValueEqual(fixture.visibleConsumerCpus(), 1, "No native CPU substitute remains");
                fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
                fixture.putInConsumer(AEItemKey.of(Items.COBBLESTONE), 2);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.consumerService().isCraftable(PatternProjectionFixture.stone()),
                        "Waiting for Crafting-only projection");
                fixture.begin(PatternProjectionFixture.stone(), 2);
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertTrue(fixture.planReady(), "Waiting for actual native planning");
                helper.assertTrue(fixture.submitOn(fixture.consumerService().getCpus().iterator().next()),
                        "The independent addon CPU takes the real job");
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 2L, "Both inputs reached the processing machine");
                var jobs = CraftingReturnLedger.get(helper.getLevel()).jobs(fixture.consumerNetwork());
                helper.assertValueEqual(jobs.size(), 1, "One native job id is tracked");
                helper.assertFalse(jobs.getFirst().nativeCpu(), "The CPU must be an independent implementation");
                helper.assertTrue(jobs.getFirst().dimension().equals("minecraft:the_nether"), "The CPU anchor includes its dimension");
                fixture.site().releaseTickets();
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertFalse(fixture.site().loaded(), "Waiting for actual CPU chunk unload and block entity removal");
                helper.assertValueEqual(fixture.visibleConsumerCpus(), 0, "Only the CPU disappears; consumer Grid remains available");
                helper.assertValueEqual(fixture.runMachine(2), 2L, "Both actual outputs enter provider storage while the CPU is absent");
                sweeps[0] = fixture.ledgerSweeps();
                stage[0] = 6;
            }
            if (stage[0] == 6) {
                helper.assertTrue(fixture.ledgerSweeps() - sweeps[0] >= 4, "Four sweeps must pass while the CPU remains unloaded");
                helper.assertFalse(fixture.site().loaded(), "CPU chunk remains unloaded throughout all four sweeps");
                helper.assertValueEqual(fixture.owed(PatternProjectionFixture.stone()), 2L, "Unloaded independent CPU retains debt");
                helper.assertValueEqual(fixture.heldForConsumer(PatternProjectionFixture.stone()), 2L, "Exactly two proven arrivals are held");
                helper.assertValueEqual(fixture.held(fixture.consumerChest(), PatternProjectionFixture.stone()), 0L,
                        "No held output may leak into ordinary consumer storage before CPU reload");
                if ("prepare".equals(System.getProperty("ae2federation.compat.cpuRestartPhase"))) {
                    IndependentCpuRestart.prepare(helper, fixture, CPU);
                    return;
                }
                fixture.site().forceAgain();
                stage[0] = 7;
            }
            helper.assertTrue(stage[0] == 7 && fixture.site().ticking(), "Waiting for actual CPU chunk reload");
            helper.assertTrue(fixture.consumerService().getCpus().stream().anyMatch(cpu -> cpu.getClass().getName().equals(TYPE) || cpu.getClass().getName().equals("com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool")),
                    "The reloaded independent CPU rejoins the same consumer");
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "The original job completes without a replacement request");
            helper.assertValueEqual(fixture.owed(PatternProjectionFixture.stone()), 0L, "Actual delivery settles both owed outputs");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), PatternProjectionFixture.stone()), 2L,
                    "Both outputs reach the consumer after CPU reload");
            helper.assertValueEqual(fixture.held(fixture.providerChest(), PatternProjectionFixture.stone()), 0L,
                    "Provider retains no duplicate outputs");
            fixture.close();
        });
    }
}
