package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;

/** Real addon CPUs and real submissions; the chest deliberately holds processing inputs for lifecycle observation. */
final class CpuLifecycleAuditScene {
    static void sharedAnchor(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest", List.of(), true)
                .consumerCpuStructure(test -> NeoEcoCompatGameTests.placeComputationSystem(test, NeoEcoCompatGameTests.Tier.L4),
                        () -> NeoEcoCompatGameTests.fitComputationCell(helper, NeoEcoCompatGameTests.Tier.L4))
                .requesting(1).craftingOnly();
        int[] stage = {0};
        var secondPlan = new java.util.concurrent.atomic.AtomicReference<
                java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan>>();
        helper.succeedWhen(() -> {
            if (!scene.submitted()) scene.tick();
            var grid = scene.consumerGrid();
            if (stage[0] == 0) {
                helper.assertValueEqual(grid.getStorageService().getInventory().insert(AEItemKey.of(Items.COBBLESTONE),
                        1, Actionable.MODULATE, IActionSource.empty()), 1L, "Second real job receives its own input");
                var node = scene.bridge().consumerChest().getMainNode().getNode();
                secondPlan.set(grid.getCraftingService().beginCraftingCalculation(helper.getLevel(),
                        new appeng.api.networking.crafting.ICraftingSimulationRequester() {
                            @Override public IActionSource getActionSource() { return IActionSource.empty(); }
                            @Override public appeng.api.networking.IGridNode getGridNode() {
                                return node;
                            }
                        }, AEItemKey.of(Items.STONE), 1,
                        appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS));
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(secondPlan.get().isDone(), "Waiting for the independent second calculation");
                try {
                    helper.assertTrue(grid.getCraftingService().submitJob(secondPlan.get().get(), null, null, true,
                            IActionSource.empty()).successful(), "Same threading core must accept a second real job");
                } catch (java.util.concurrent.ExecutionException | InterruptedException exception) {
                    throw new IllegalStateException(exception);
                }
                stage[0] = 2;
            }
            var ledger = CraftingReturnLedger.get(helper.getLevel());
            var consumer = scene.bridge().mainNetwork();
            var jobs = ledger.jobs(consumer);
            helper.assertValueEqual(jobs.size(), 2, "Both dispatched jobs must be observed");
            helper.assertTrue(jobs.get(0).cpu().equals(jobs.get(1).cpu()), "Both jobs share the real threading core anchor");
            helper.assertValueEqual(grid.getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).count(),
                    2L, "Both real CPUs are still waiting for their processing output");
            // Resolve each recorded job directly: this is the exact lifecycle check used by the return sweep.
            try {
                var end = CraftingProjectionService.class.getDeclaredMethod("end", CraftingReturnLedger.class,
                        space.controlnet.ae2federation.identity.NetworkId.class, CraftingReturnLedger.Job.class);
                end.setAccessible(true);
                for (var job : jobs) {
                    var actual = end.invoke(CraftingProjectionService.get(helper.getLevel()), ledger, consumer, job);
                    helper.assertTrue(actual == CraftingReturnLedger.JobEnd.RUNNING,
                            "A different CPU at the same anchor must not finish a running job: " + actual);
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
            for (var cpu : grid.getCraftingService().getCpus()) if (cpu.isBusy()) cpu.cancelJob();
        });
    }
    static void completed(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest", List.of(), true)
                .consumerCpuStructure(test -> NeoEcoCompatGameTests.placeComputationSystem(test, NeoEcoCompatGameTests.Tier.L4),
                        () -> NeoEcoCompatGameTests.fitComputationCell(helper, NeoEcoCompatGameTests.Tier.L4))
                .requesting(1).craftingOnly();
        var saved = new CraftingReturnLedger[1];
        var recorded = new CraftingReturnLedger.Job[1];
        helper.succeedWhen(() -> {
            if (!scene.submitted()) scene.tick();
            var consumer = scene.bridge().mainNetwork();
            var grid = scene.consumerGrid();
            if (saved[0] == null) {
                var ledger = CraftingReturnLedger.get(helper.getLevel());
                helper.assertValueEqual(ledger.owed(scene.bridge().outerNetwork(), consumer,
                        AEItemKey.of(Items.STONE)), 1L, "The real job has dispatched its input");
                recorded[0] = ledger.jobs(consumer).getFirst();
                saved[0] = CraftingReturnLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(),
                        helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
                var machine = helper.<net.minecraft.world.level.block.entity.ChestBlockEntity>getBlockEntity(
                        AddonCraftingScene.PROVIDER.east());
                helper.assertTrue(machine.countItem(Items.COBBLESTONE) == 1, "The test machine holds one real input");
                machine.clearContent();
                helper.assertValueEqual(scene.bridge().outerGrid().getStorageService().getInventory().insert(
                        AEItemKey.of(Items.STONE), 1, Actionable.MODULATE, IActionSource.empty()), 1L,
                        "The test machine emits exactly one output into the actual return path");
                helper.fail("Waiting for the real CPU to retire after completion");
            }
            helper.assertTrue(grid.getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "The real Neo ECO job must complete");
            var owner = helper.getLevel().getBlockEntity(recorded[0].cpu());
            try {
                var cpus = (Object[]) owner.getClass().getMethod("getCpus").invoke(owner);
                helper.assertTrue(java.util.Arrays.stream(cpus).allMatch(cpu -> cpu == null),
                        "The threading core must remove the completed CPU from all slots");
                var service = CraftingProjectionService.get(helper.getLevel());
                var links = CraftingProjectionService.class.getDeclaredField("jobLinks");
                links.setAccessible(true);
                ((java.util.Map<?, ?>) links.get(service)).remove(recorded[0].craftingId());
                var end = CraftingProjectionService.class.getDeclaredMethod("end", CraftingReturnLedger.class,
                        space.controlnet.ae2federation.identity.NetworkId.class, CraftingReturnLedger.Job.class);
                end.setAccessible(true);
                // Fault injection: a quarantined deferred slot and an unformed owner must suppress terminal proof.
                // No item is introduced; the empty tag represents an unreadable pending restore for this guard test.
                var deferredField = owner.getClass().getDeclaredField("deferredInit");
                deferredField.setAccessible(true);
                var deferred = (net.minecraft.nbt.CompoundTag[]) deferredField.get(owner);
                var original = deferred[0];
                try {
                    deferred[0] = new net.minecraft.nbt.CompoundTag();
                    helper.assertTrue(end.invoke(service, saved[0], consumer, recorded[0]) == null,
                            "A quarantined deferred slot must retain unknown lifecycle");
                } finally {
                    deferred[0] = original;
                }
                var formed = owner.getClass().getMethod("isFormed");
                var setFormed = owner.getClass().getMethod("setFormed", boolean.class);
                boolean wasFormed = (boolean) formed.invoke(owner);
                try {
                    setFormed.invoke(owner, false);
                    helper.assertTrue(end.invoke(service, saved[0], consumer, recorded[0]) == null,
                            "An unformed owner must retain unknown lifecycle");
                } finally {
                    setFormed.invoke(owner, wasFormed);
                }
                var actual = end.invoke(service, saved[0], consumer, recorded[0]);
                helper.assertTrue(actual == CraftingReturnLedger.JobEnd.TERMINATED,
                        "Addon persisted terminal evidence must resolve a retired CPU without transient links: " + actual);
                var reloaded = CraftingReturnLedger.load(saved[0].save(new net.minecraft.nbt.CompoundTag(),
                        helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
                helper.assertTrue(reloaded.jobs(consumer).getFirst().end() == CraftingReturnLedger.JobEnd.TERMINATED,
                        "The explicit native terminal outcome survives Federation SavedData roundtrip");
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

}
