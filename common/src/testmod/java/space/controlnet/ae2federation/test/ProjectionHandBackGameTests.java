package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.cobblestone;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.stone;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.crafting.projection.HeldReturnTransfer;
import space.controlnet.ae2federation.test.crafting.FaultStorage;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/**
 * Handing held stock back under injected faults: targets whose real insert takes less than the simulated one promised,
 * an extract-only source, stock spread over two sources, and an executing network that cannot take the rest back. The
 * {@link FaultStorage} targets and source are test doubles; the networks, the job and the ledger are real. What was
 * taken out but not delivered must stay real stock somewhere, the debt goes down only by what was delivered, and the
 * job ends with exactly what it asked for.
 */
@PrefixGameTestTemplate(false)
public final class ProjectionHandBackGameTests {
    private ProjectionHandBackGameTests() {
    }

    /**
     * Two stone are held for the consumer while the link is broken: one in the provider's chest, one in an extract-only
     * storage. A refusing target takes nothing; a target that simulates all but takes one gets one, and the other goes
     * back into the chest. With the chest's cell out, a target that takes nothing leaves the stone in transit, which the
     * provider network lists and the chest takes back once its cell returns. Done again, the transit stone survives a
     * service restart, and the reconnected job gets it first, once.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1600)
    public static void projectionHandBackFaults(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        var sweeps = new long[1];
        var extractOnly = FaultStorage.extractOnly();
        var partial = new FaultStorage(Long.MAX_VALUE, 1);
        var cell = new ItemStack[1];
        var unmount = new Runnable[1];
        var problems = new ArrayList<String>();
        helper.succeedWhen(() -> {
            // What went wrong once the test started moving stock is reported, not retried on changed stock.
            if (!problems.isEmpty()) helper.fail(problems.get(0));
            pushedTwoStone(helper, fixture, stage);
            if (stage[0] == 4) {
                fixture.disconnect();
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertValueEqual(fixture.projections(), 0, "Waiting for the patterns to be withdrawn");
                helper.assertValueEqual(fixture.runMachine(2), 2L, "The provider network takes the stone");
                stage[0] = 6;
            }
            if (stage[0] == 6) {
                helper.assertValueEqual(fixture.heldForConsumer(stone()), 2L, "The stone is held for the consumer");
                helper.assertValueEqual(fixture.providerChest().extract(stone(), 1, Actionable.MODULATE,
                        IActionSource.empty()), 1L, "One stone moves out of the chest");
                extractOnly.put(stone(), 1);
                unmount[0] = fixture.mountOnProvider(extractOnly, -1);
                stage[0] = 7;
            }
            if (stage[0] == 7) {
                helper.assertValueEqual(fixture.onProviderNetwork(stone()), 2L,
                        "Waiting for the provider network to list both sources");
                stage[0] = 8;
                var inventory = fixture.providerGrid().getStorageService().getInventory();
                inventory.insert(stone(), 5, Actionable.SIMULATE, IActionSource.empty());
                check(problems, fixture.owed(stone()), 2L, "A simulated insert changes no debt");
                check(problems, fixture.heldForConsumer(stone()), 2L, "A simulated insert holds nothing");
                check(problems, fixture.transit(stone()), 0L, "Nothing is in transit");
                check(problems, fixture.consumerCpu().craftingLogic.getWaitingFor(stone()), 2L,
                        "The job still waits for two stone");

                var refusing = new FaultStorage(0, 0);
                check(problems, handBack(helper, fixture, 2, refusing), 0L, "A refusing target gets nothing");
                check(problems, fixture.held(fixture.providerChest(), stone()) + extractOnly.held(stone()), 2L,
                        "Nothing leaves the sources for a refusing target");
                check(problems, fixture.heldForConsumer(stone()), 2L, "Both stone stay held");

                check(problems, handBack(helper, fixture, 2, partial), 1L,
                        "A target that simulates two but takes one gets one");
                check(problems, partial.held(stone()), 1L, "The target holds the one stone");
                check(problems, extractOnly.held(stone()), 0L, "The extract-only source gave its stone");
                check(problems, fixture.held(fixture.providerChest(), stone()), 1L,
                        "The stone the target refused went back into the chest");
                check(problems, fixture.transit(stone()), 0L, "Nothing had to stay in transit");
                check(problems, fixture.owed(stone()), 1L, "The debt goes down by the one stone delivered");
                check(problems, fixture.heldForConsumer(stone()), 1L, "One stone stays held");
            }
            if (stage[0] == 8) {
                stage[0] = 9;
                intoTransit(helper, fixture, extractOnly, cell, problems);
                check(problems, fixture.onProviderNetwork(stone()), 1L,
                        "The provider network lists the transit stone");
                fixture.restoreProviderCell(cell[0]);
                sweeps[0] = fixture.ledgerSweeps();
            }
            if (stage[0] == 9) {
                helper.assertTrue(fixture.ledgerSweeps() > sweeps[0], "Waiting for a ledger sweep");
                helper.assertValueEqual(fixture.transit(stone()), 0L, "The chest takes the transit stone back");
                helper.assertValueEqual(fixture.held(fixture.providerChest(), stone()), 1L, "The chest holds it");
                stage[0] = 10;
                intoTransit(helper, fixture, extractOnly, cell, problems);
                // A service restart: the ledger goes through its saved form, the service starts anew.
                var level = helper.getLevel();
                var server = level.getServer();
                CraftingProjectionService.closeServer(server);
                var registries = level.registryAccess();
                var saved = CraftingReturnLedger.get(level).save(new CompoundTag(), registries);
                server.overworld().getDataStorage().set(CraftingReturnLedger.DATA_NAME,
                        CraftingReturnLedger.load(saved, registries));
                check(problems, fixture.transit(stone()), 1L, "The transit stone survives the restart");
                check(problems, fixture.owed(stone()), 1L, "The debt survives the restart");
                check(problems, fixture.heldForConsumer(stone()), 1L, "The stone stays held");
                fixture.reconnect();
            }
            if (stage[0] == 10) {
                helper.assertTrue(fixture.connected(), "Waiting for the Federation link to come back");
                helper.assertValueEqual(fixture.owed(stone()), 0L, "Waiting for the transit stone to reach the job");
                helper.assertValueEqual(fixture.heldForConsumer(stone()), 0L, "Nothing stays held");
                helper.assertValueEqual(fixture.transit(stone()), 0L, "The job got the transit stone");
                helper.assertValueEqual(fixture.consumerCpu().craftingLogic.getWaitingFor(stone()), 1L,
                        "The job got exactly one stone, and waits for the one the test target took");
                stage[0] = 11;
                fixture.restoreProviderCell(cell[0]);
                unmount[0].run();
                long moved = partial.extract(stone(), 1, Actionable.MODULATE, IActionSource.empty());
                check(problems, fixture.consumerGrid().getStorageService().getInventory().insert(stone(), moved,
                        Actionable.MODULATE, IActionSource.empty()), 1L, "The consumer takes the target's stone");
            }
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Waiting for the job to finish");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), stone()), 2L,
                    "The job stores exactly its two stone");
            helper.assertValueEqual(fixture.held(fixture.providerChest(), stone()), 0L,
                    "The provider network keeps none of it");
            helper.assertValueEqual(extractOnly.held(stone()) + partial.held(stone()), 0L,
                    "The test storages keep none of it");
            helper.assertValueEqual(fixture.owed(stone()), 0L, "Nothing is owed afterwards");
            helper.assertValueEqual(fixture.transit(stone()), 0L, "Nothing is in transit afterwards");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), cobblestone()), 0L, "The cobblestone was used");
            PolicyEvidence.write("projectionhandbackfaults", 36, Map.of("refusingTarget", "0",
                    "partialTarget", "1 of 2", "residualToOwnStorage", "1", "residualToTransit", "1",
                    "transitAfterRestart", "1", "transitStoredBack", "1", "transitDelivered", "1"));
            fixture.close();
        });
    }

    /**
     * Moves the chest's one held stone to the extract-only storage, takes the chest's cell out, and hands it to a
     * target that simulates taking it but takes nothing: the executing network has nowhere to put it back, so it stays
     * in transit.
     */
    private static void intoTransit(GameTestHelper helper, PatternProjectionFixture fixture,
            FaultStorage extractOnly, ItemStack[] cell, List<String> problems) {
        check(problems, fixture.providerChest().extract(stone(), 1, Actionable.MODULATE, IActionSource.empty()), 1L,
                "The chest's stone moves to the extract-only storage");
        extractOnly.put(stone(), 1);
        cell[0] = fixture.takeProviderCell();
        var taking = new FaultStorage(Long.MAX_VALUE, 0);
        check(problems, handBack(helper, fixture, 1, taking), 0L, "A target that simulates one but takes none gets nothing");
        check(problems, extractOnly.held(stone()), 0L, "The extract-only source gave its stone");
        check(problems, fixture.transit(stone()), 1L, "The stone nobody took back stays in transit; "
                + "the ledger owes " + fixture.owed(stone()));
        check(problems, fixture.owed(stone()), 1L, "The debt is unchanged");
        check(problems, fixture.heldForConsumer(stone()), 1L, "The stone stays held");
    }

    private static void check(List<String> problems, long actual, long expected, String what) {
        if (actual != expected) problems.add(what + ": expected " + expected + ", was " + actual);
    }

    private static long handBack(GameTestHelper helper, PatternProjectionFixture fixture, long wanted, FaultStorage target) {
        return HeldReturnTransfer.handBack(CraftingReturnLedger.get(helper.getLevel()), fixture.providerNetwork(),
                fixture.providerGrid(), fixture.consumerNetwork(), stone(), wanted, target);
    }

    private static void pushedTwoStone(GameTestHelper helper, PatternProjectionFixture fixture, int[] stage) {
        if (stage[0] >= 4) return;
        helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
        if (stage[0] == 0) {
            fixture.enableRules();
            fixture.putInConsumer(cobblestone(), 2);
            stage[0] = 1;
        }
        if (stage[0] == 1) {
            helper.assertTrue(fixture.consumerService().isCraftable(stone()), "Waiting for the projection");
            fixture.begin(stone(), 2);
            stage[0] = 2;
        }
        if (stage[0] == 2) {
            helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
            helper.assertTrue(fixture.submit(), "The consumer's own CPU must take the job");
            stage[0] = 3;
        }
        helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 2L, "Waiting for both pushes");
        helper.assertValueEqual(fixture.owed(stone()), 2L, "The provider network owes the consumer two stone");
        stage[0] = 4;
    }
}
