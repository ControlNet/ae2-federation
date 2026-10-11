package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;

/** Real retired TimeWheel CPU; saved ledger copy isolates lifecycle observation from already delivered stock. */
final class IndependentCpuRetirementScene {
    static void run(GameTestHelper helper) {
        run(helper, false);
    }

    static void run(GameTestHelper helper, boolean advanced) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest",
                List.of(advanced ? "advanced_ae:quantum_core" : "ae2lt:pigmee_mentalmath_unit"), true)
                .requesting(1).craftingOnly();
        if (advanced) scene.cpuOnCable();
        var saved = new CraftingReturnLedger[1];
        var recorded = new CraftingReturnLedger.Job[1];
        helper.succeedWhen(() -> {
            if (!scene.submitted()) scene.tick();
            var consumer = scene.bridge().mainNetwork();
            var grid = scene.consumerGrid();
            if (saved[0] == null) {
                var ledger = CraftingReturnLedger.get(helper.getLevel());
                helper.assertValueEqual(ledger.owed(scene.bridge().outerNetwork(), consumer, AEItemKey.of(Items.STONE)),
                        1L, "The real independent job dispatched its processing input");
                recorded[0] = ledger.jobs(consumer).getFirst();
                helper.assertFalse(recorded[0].nativeCpu(), "An independent CPU must own the job");
                saved[0] = CraftingReturnLedger.load(ledger.save(new CompoundTag(), helper.getLevel().registryAccess()),
                        helper.getLevel().registryAccess());
                var machine = helper.<net.minecraft.world.level.block.entity.ChestBlockEntity>getBlockEntity(
                        AddonCraftingScene.PROVIDER.east());
                helper.assertValueEqual(machine.countItem(Items.COBBLESTONE), 1, "The test machine consumes one real input");
                machine.clearContent();
                helper.assertValueEqual(scene.bridge().outerGrid().getStorageService().getInventory().insert(
                        AEItemKey.of(Items.STONE), 1, Actionable.MODULATE, IActionSource.empty()), 1L,
                        "The test machine emits exactly one real output");
                helper.fail("Waiting for the real CPU to retire");
            }
            helper.assertTrue(grid.getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "The actual independent job completes");
            var owner = helper.getLevel().getBlockEntity(recorded[0].cpu());
            try {
                var pool = owner.getClass().getMethod(advanced ? "getCluster" : "getTimeWheelCraftingCpuPool").invoke(owner);
                var cpus = (java.util.Collection<?>) pool.getClass().getMethod(advanced ? "getActiveCPUs" : "getActiveCpus").invoke(pool);
                helper.assertTrue(cpus.isEmpty(), "The native host has removed the completed CPU and retains no pending CPU state");
                var service = CraftingProjectionService.get(helper.getLevel());
                var links = CraftingProjectionService.class.getDeclaredField("jobLinks");
                links.setAccessible(true);
                ((java.util.Map<?, ?>) links.get(service)).remove(recorded[0].craftingId());
                var end = CraftingProjectionService.class.getDeclaredMethod("end", CraftingReturnLedger.class,
                        space.controlnet.ae2federation.identity.NetworkId.class, CraftingReturnLedger.Job.class);
                end.setAccessible(true);
                var actual = end.invoke(service, saved[0], consumer, recorded[0]);
                helper.assertTrue(actual == CraftingReturnLedger.JobEnd.TERMINATED,
                        "A loaded native host with no live or pending CPU must resolve the retired job: " + actual);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }
}
