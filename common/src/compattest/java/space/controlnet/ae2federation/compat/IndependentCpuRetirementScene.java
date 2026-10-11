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
                advanced ? List.of("advanced_ae:quantum_core", "advanced_ae:quantum_storage_256")
                        : List.of("ae2lt:pigmee_mentalmath_unit"), true)
                .requesting(1).craftingOnly();
        if (advanced) scene.consumerCpuStructure(IndependentCpuRetirementScene::placeAdvancedCluster, () -> true);
        var saved = new CraftingReturnLedger[1];
        var recorded = new CraftingReturnLedger.Job[1];
        var pendingState = new CompoundTag[1];
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
                if (advanced) {
                    var runningOwner = helper.getLevel().getBlockEntity(recorded[0].cpu());
                    pendingState[0] = runningOwner.saveWithFullMetadata(helper.getLevel().registryAccess());
                    helper.assertFalse(terminal(runningOwner, java.util.UUID.randomUUID()),
                            "Another live native job prevents historical terminal inference at the shared host");
                }
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
                if (advanced) {
                    // Explicit fault injection of actual native job NBT into its real deferred-restore slot.
                    var getter = owner.getClass().getMethod("getPreviousState");
                    var setter = owner.getClass().getMethod("setPreviousState", CompoundTag.class);
                    var members = (java.util.Iterator<?>) pool.getClass().getMethod("getBlockEntities").invoke(pool);
                    boolean checkedOtherMember = false;
                    while (members.hasNext()) {
                        var member = members.next();
                        var previous = getter.invoke(member);
                        try {
                            setter.invoke(member, pendingState[0]);
                            helper.assertFalse(terminal(owner, recorded[0].craftingId()),
                                    "Any member's deferred native restore prevents terminal inference with an empty pool");
                        } finally {
                            setter.invoke(member, previous);
                        }
                        checkedOtherMember |= member != owner;
                    }
                    helper.assertTrue(checkedOtherMember, "A different multiblock member also exercises the pending-state guard");
                    var destroyed = pool.getClass().getDeclaredField("isDestroyed");
                    destroyed.setAccessible(true);
                    var oldDestroyed = destroyed.getBoolean(pool);
                    try {
                        destroyed.setBoolean(pool, true);
                        helper.assertFalse(terminal(owner, recorded[0].craftingId()),
                                "A destroyed cluster cannot prove termination");
                    } finally {
                        destroyed.setBoolean(pool, oldDestroyed);
                    }
                }
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
    private static void placeAdvancedCluster(GameTestHelper helper) {
        appeng.api.parts.PartHelper.setPart(helper.getLevel(), helper.absolutePos(AddonCraftingScene.FIRST_CPU),
                null, null, appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
        // Advanced AE requires the core and storage inside a complete structure shell (except a lone core).
        for (int x = 1; x <= 3; x++) for (int y = 3; y <= 6; y++) for (int z = 4; z <= 6; z++) {
            var shell = x == 1 || x == 3 || y == 3 || y == 6 || z == 4 || z == 6;
            helper.setBlock(new net.minecraft.core.BlockPos(x, y, z), AddonCraftingScene.block("advanced_ae:"
                    + (shell ? "quantum_structure" : y == 4 ? "quantum_core" : "quantum_storage_256")));
        }
    }

    private static boolean terminal(net.minecraft.world.level.block.entity.BlockEntity owner, java.util.UUID id) {
        try {
            var type = Class.forName("space.controlnet.ae2federation.crafting.projection.CraftingCpuLifecycle");
            var method = type.getDeclaredMethod("terminated", net.minecraft.world.level.block.entity.BlockEntity.class,
                    java.util.UUID.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, owner, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
