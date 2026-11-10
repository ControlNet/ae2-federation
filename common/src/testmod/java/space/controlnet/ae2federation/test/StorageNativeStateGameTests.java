package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.events.GridPowerStorageStateChanged;
import appeng.api.networking.events.GridPowerStorageStateChanged.PowerEventType;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.ae2.storage.NativeMountLedger;
import space.controlnet.ae2federation.identity.IdentityEpoch;
import space.controlnet.ae2federation.policy.AuthorityEpoch;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.storage.provenance.OriginNetworkId;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.storage.GridEventProbe;
import space.controlnet.ae2federation.test.storage.SourceIndexFixtures;
import space.controlnet.ae2federation.test.storage.SwitchablePowerProvider;

/**
 * A held storage projection follows its source Grid's native AE2 state without re-reading it per operation: a reboot
 * and a power loss reach it through AE2's Grid events, a Storage Bus retarget through the polled delegate links and
 * AE2's remount. Until the Storage Bus is placed, the provider Grid holds nothing that remounts when the Grid loses
 * power or reboots (an ME Chest or a Storage Bus does), which would hide a missing event.
 */
@PrefixGameTestTemplate(false)
public final class StorageNativeStateGameTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey GOLD = AEItemKey.of(Items.GOLD_INGOT);
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final BlockPos FIRST = new BlockPos(5, 3, 5);
    /** East end of the provider Grid's cable, and the external inventory its Storage Bus faces. */
    private static final BlockPos BUS_CABLE = FIRST.east().north();
    private static final BlockPos BUS_TARGET = BUS_CABLE.east();

    private StorageNativeStateGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void storageHeldNativeState(GameTestHelper helper) {
        new Scene(helper).run();
    }

    /**
     * The main network has an ME Storage Bus facing an unconfigured ME Interface of the outer network, so the Bus reads
     * the outer network's whole storage, and the outer network consumes the main one by a storage rule. The main
     * network's own drive must still count once: the Bus reaches the outer network's projection of that same drive.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void storageBusLoopCountsOnce(GameTestHelper helper) {
        var fixtures = new PolicyBridgeFixtures(helper, FIRST);
        fixtures.installStorageCells();
        var phase = new int[] {0};
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                helper.assertTrue(fixtures.networksSettled(), "Waiting for the loop networks");
                helper.assertValueEqual(main(fixtures).insert(IRON, 7, Actionable.MODULATE, SOURCE), 7L,
                        "The main network's drive takes its own iron before the Bus exists");
                helper.assertValueEqual(outer(fixtures).insert(GOLD, 3, Actionable.MODULATE, SOURCE), 3L,
                        "The outer network's drive takes its own gold");
                fixtures.placeNativeStorageBusToInterface();
                fixtures.placeSecondBridge();
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for the Storage Bus and the Bridge");
            }
            helper.assertTrue(fixtures.secondBridgeReady(), "Waiting for the loop Federation Domain");
            helper.assertValueEqual(main(fixtures).getAvailableStacks().get(GOLD), 3L,
                    "Waiting for the Storage Bus to read the outer network");
            var key = new PolicyKey(fixtures.outerNetwork(), fixtures.mainNetwork(), PolicyCapability.STORAGE);
            var policies = PolicyService.get(helper.getLevel());
            if (policies.revision(key).equals(PolicyRevision.NONE)) {
                policies.edit(new PolicyEdit(key, PolicyRevision.NONE, PolicyRule.storageDefaults()));
            }
            helper.assertTrue(StorageMountService.get(helper.getLevel()).projection(key) != null,
                    "Waiting for the outer network's projection of the main one");
            helper.assertValueEqual(outer(fixtures).getAvailableStacks().get(IRON), 7L,
                    "Waiting for the outer network to see the main network's iron");
            GameTestChecks.once(helper, () -> {
                var mainView = main(fixtures).getAvailableStacks();
                var outerView = outer(fixtures).getAvailableStacks();
                helper.assertValueEqual(mainView.get(IRON), 7L,
                        "The main network counts its own drive once, not again through Bus, Interface and rule");
                helper.assertValueEqual(mainView.get(GOLD), 3L, "The main network sees the outer gold once");
                helper.assertValueEqual(outerView.get(GOLD), 3L, "The outer network counts its own drive once");
                helper.assertValueEqual(main(fixtures).extract(IRON, 7, Actionable.SIMULATE, SOURCE), 7L,
                        "The main network can take all of its real iron");
                helper.assertValueEqual(main(fixtures).extract(IRON, 8, Actionable.SIMULATE, SOURCE), 7L,
                        "The main network cannot take more iron than it really holds");
                PolicyEvidence.write("storagebusloopcountsonce", 7, Map.of("mainIron", "7", "mainGold", "3",
                        "outerGold", "3", "extractableIron", "7"));
                fixtures.close();
            });
        });
    }

    /**
     * Dismantling a network takes its entry, with the dead Grid and its storage handles, out of the source index at
     * the next refresh instead of keeping it until the server stops.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void storageSourceIndexForgetsDismantledNetwork(GameTestHelper helper) {
        var fixtures = new PolicyBridgeFixtures(helper, FIRST);
        helper.setBlock(FIRST.south().below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(FIRST.north(2).below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var phase = new int[] {0};
        var outerOrigin = new OriginNetworkId[1];
        helper.succeedWhen(() -> {
            var mounts = StorageMountService.get(helper.getLevel());
            if (phase[0] == 0) {
                helper.assertTrue(fixtures.networksSettled(), "Waiting for the networks");
                fixtures.placeFirstBridge();
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for the Federation Domain");
            }
            if (phase[0] == 1) {
                helper.assertTrue(fixtures.firstBridgeReady(), "Waiting for the Federation Domain");
                mounts.reconcileAll();
                outerOrigin[0] = new OriginNetworkId(fixtures.outerNetwork());
                helper.assertTrue(mounts.sourceOrigins().contains(outerOrigin[0]),
                        "Waiting for the source index to hold the outer network");
                for (var position : java.util.List.of(FIRST.north(), FIRST.east().north(), FIRST.north(2),
                        FIRST.north(2).below())) {
                    helper.setBlock(position, Blocks.AIR);
                }
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for the outer network to be dismantled");
            }
            GameTestChecks.once(helper, () -> {
                mounts.reconcileAll();
                helper.assertTrue(!mounts.sourceOrigins().contains(outerOrigin[0]),
                        "The source index forgets a dismantled network, saw " + mounts.sourceOrigins());
                helper.assertTrue(mounts.sourceOrigins().contains(new OriginNetworkId(fixtures.mainNetwork())),
                        "The source index keeps the network that is still loaded");
                PolicyEvidence.write("storagesourceindexforgetsdismantlednetwork", 2,
                        Map.of("dismantledForgotten", "true", "loadedKept", "true"));
                fixtures.close();
            });
        });
    }

    private static MEStorage main(PolicyBridgeFixtures fixtures) {
        return fixtures.mainGrid().getStorageService().getInventory();
    }

    private static MEStorage outer(PolicyBridgeFixtures fixtures) {
        return fixtures.outerGrid().getStorageService().getInventory();
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final PolicyBridgeFixtures fixtures;
        private final SourceIndexFixtures.CountingStorage storage = new SourceIndexFixtures.CountingStorage("held", 64);
        private final SwitchablePowerProvider power = new SwitchablePowerProvider(storage);
        private final Map<String, String> facts = new LinkedHashMap<>();
        private MEStorage held;
        private long epochBefore;
        private long identityBefore;
        private long mountEventsBefore;
        private boolean repathAdvancedEpoch;
        private boolean powerLossAdvancedEpoch;
        private long acceptedWhileBooting = -1;
        private long acceptedOnceBooted = -1;
        private long acceptedOnPowerLoss = -1;
        private int phase;

        private Scene(GameTestHelper helper) {
            this.helper = helper;
            fixtures = new PolicyBridgeFixtures(helper, FIRST);
            helper.setBlock(FIRST.south().below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
            fixtures.removeProviderChest();
        }

        private void run() {
            helper.succeedWhen(this::tick);
        }

        private void tick() {
            switch (phase) {
                case 0 -> setup();
                case 1 -> primeAndRepath();
                case 2 -> verifyRepathAndCutPower();
                case 3 -> verifyPowerLossAndRestore();
                case 4 -> verifyRestoreAndPlaceStorageBus();
                case 5 -> retargetStorageBus();
                case 6 -> verifyRetarget();
                default -> throw new IllegalStateException("Unexpected native state phase");
            }
        }

        private void setup() {
            helper.assertTrue(fixtures.networksSettled(), "Waiting for native state identities");
            fixtures.addOuterStorageProvider(power);
            fixtures.placeFirstBridge();
            phase = 1;
            waitFor("Waiting for the native state Federation Domain");
        }

        private void primeAndRepath() {
            helper.assertTrue(fixtures.firstBridgeReady(), "Waiting for the native state Federation Domain");
            helper.assertTrue(fixtures.outerGrid().getEnergyService().isNetworkPowered(),
                    "Waiting for the provider Grid's power");
            var policies = PolicyService.get(helper.getLevel());
            if (policies.revision(key()).equals(PolicyRevision.NONE)) {
                policies.edit(new PolicyEdit(key(), PolicyRevision.NONE, PolicyRule.storageDefaults()));
            }
            held = mounts().projection(key());
            helper.assertTrue(held != null, "Waiting for the native state projection");
            helper.assertValueEqual(held.insert(IRON, 3, Actionable.MODULATE, SOURCE), 3L,
                    "The held projection inserts into the provider's storage");
            helper.assertValueEqual(held.extract(IRON, 1, Actionable.SIMULATE, SOURCE), 1L,
                    "The held projection simulates against the provider's storage");
            mark();
            GridEventProbe.onBooting(fixtures.outerGrid(), booting -> {
                var accepted = held.insert(IRON, 1, Actionable.SIMULATE, SOURCE);
                if (booting) {
                    acceptedWhileBooting = accepted;
                } else {
                    acceptedOnceBooted = accepted;
                }
            });
            fixtures.outerGrid().getPathingService().repath();
            phase = 2;
            waitFor("Waiting for the provider Grid's reboot");
        }

        /** AE2 reboots a Grid within one tick, so no operation sees it booting; only its events tell. */
        private void verifyRepathAndCutPower() {
            helper.assertTrue(!fixtures.outerGrid().getPathingService().isNetworkBooting(),
                    "Waiting for the provider Grid's reboot to finish");
            GridEventProbe.disarm();
            repathAdvancedEpoch = AuthorityEpoch.current() > epochBefore;
            recordChanges("repath");
            helper.assertValueEqual(acceptedWhileBooting, 0L,
                    "The held projection refuses operations while its source Grid boots");
            // The refusal saw the source inactive, so the relationship may have been dropped; it must come back.
            held = mounts().projection(key());
            helper.assertTrue(held != null, "Waiting for the relationship once the source Grid has booted");
            helper.assertValueEqual(held.insert(IRON, 1, Actionable.MODULATE, SOURCE), 1L,
                    "Federation serves again once its source Grid has booted");
            helper.assertValueEqual(storage.amount(IRON), 4L, "The insert after the reboot reached the provider");
            GridEventProbe.onPower(fixtures.outerGrid(), () -> {
                if (!fixtures.outerGrid().getEnergyService().isNetworkPowered()) {
                    acceptedOnPowerLoss = held.insert(IRON, 1, Actionable.SIMULATE, SOURCE);
                }
            });
            power.setPowered(false);
            var energy = fixtures.outerGrid().getEnergyService();
            energy.extractAEPower(energy.getStoredPower() + 1, Actionable.MODULATE, PowerMultiplier.ONE);
            mark();
            phase = 3;
            waitFor("Waiting for the provider Grid to lose power");
        }

        private void verifyPowerLossAndRestore() {
            helper.assertTrue(!fixtures.outerGrid().getEnergyService().isNetworkPowered(),
                    "Waiting for the provider Grid to lose power");
            GridEventProbe.disarm();
            powerLossAdvancedEpoch = AuthorityEpoch.current() > epochBefore;
            recordChanges("powerLoss");
            helper.assertValueEqual(acceptedOnPowerLoss, 0L,
                    "The held projection refuses operations from the moment its source Grid loses power");
            helper.assertValueEqual(held.insert(IRON, 1, Actionable.MODULATE, SOURCE), 0L,
                    "The held projection refuses inserts once its source Grid has no power");
            helper.assertValueEqual(held.extract(IRON, 1, Actionable.MODULATE, SOURCE), 0L,
                    "The held projection refuses extracts once its source Grid has no power");
            helper.assertValueEqual(storage.amount(IRON), 4L, "Refused operations leave the provider unchanged");
            power.setPowered(true);
            // As an AE2 energy cell does when it holds power again: AE2 dropped the empty storage from its providers.
            fixtures.outerGrid().postEvent(new GridPowerStorageStateChanged(power, PowerEventType.PROVIDE_POWER));
            phase = 4;
            waitFor("Waiting for the provider Grid's power to return");
        }

        private void verifyRestoreAndPlaceStorageBus() {
            helper.assertTrue(fixtures.outerGrid().getEnergyService().isNetworkPowered(),
                    "Waiting for the provider Grid's power to return");
            var projection = mounts().projection(key());
            helper.assertTrue(projection != null, "Waiting for the relationship after the power returned");
            helper.assertValueEqual(projection.insert(IRON, 1, Actionable.MODULATE, SOURCE), 1L,
                    "Federation inserts again once the source Grid has power");
            helper.assertValueEqual(storage.amount(IRON), 5L, "The insert after the power returned reached the provider");
            helper.setBlock(BUS_TARGET, Blocks.CHEST);
            target().setItem(0, new ItemStack(Items.GOLD_INGOT, 4));
            var bus = PartHelper.setPart(helper.getLevel(), helper.absolutePos(BUS_CABLE), Direction.EAST, null,
                    AEParts.STORAGE_BUS.asItem());
            helper.assertTrue(bus != null, "A Storage Bus is placeable on the provider Grid");
            phase = 5;
            waitFor("Waiting for the Storage Bus to reach its first target");
        }

        private void retargetStorageBus() {
            helper.assertValueEqual(nativeGold(), 4L, "Waiting for the Storage Bus to reach its first target");
            held = mounts().projection(key());
            helper.assertTrue(held != null, "Waiting for the relationship to remount the Storage Bus");
            helper.assertValueEqual(held.extract(GOLD, 1, Actionable.MODULATE, SOURCE), 1L,
                    "Federation extracts through the Storage Bus from its first target");
            helper.assertValueEqual(targetGold(), 3L, "The first target gave the extracted gold");
            helper.setBlock(BUS_TARGET, Blocks.BARREL);
            target().setItem(0, new ItemStack(Items.GOLD_INGOT, 6));
            phase = 6;
            waitFor("Waiting for the Storage Bus to retarget");
        }

        /**
         * Replacing the target invalidates the Bus's capability, which sets AE2's null inventory as its delegate at
         * once (a polled delegate link), and the Bus then remounts its new target (AE2's mount table).
         */
        private void verifyRetarget() {
            helper.assertValueEqual(nativeGold(), 6L, "Waiting for the Storage Bus to reach its second target");
            var projection = mounts().projection(key());
            helper.assertTrue(projection != null, "Waiting for the relationship after the retarget");
            facts.put("retargetKeepsHeldProjection", Boolean.toString(projection == held));
            helper.assertValueEqual(projection.extract(GOLD, 1, Actionable.MODULATE, SOURCE), 1L,
                    "Federation extracts through the retargeted Storage Bus");
            helper.assertValueEqual(targetGold(), 5L, "The second target, not the first, gave the extracted gold");
            // Checked last, so a missing Grid event shows apart from a wrong operation result.
            helper.assertTrue(repathAdvancedEpoch,
                    "A reboot of the source Grid sends held authorizations back to a full check");
            helper.assertTrue(powerLossAdvancedEpoch,
                    "A power loss of the source Grid sends held authorizations back to a full check");
            facts.put("acceptedWhileBooting", Long.toString(acceptedWhileBooting));
            facts.put("acceptedOnceBooted", Long.toString(acceptedOnceBooted));
            facts.put("acceptedOnPowerLoss", Long.toString(acceptedOnPowerLoss));
            facts.put("repathServesAgain", "true");
            facts.put("powerLossInsertAccepted", "0");
            facts.put("powerLossExtractAccepted", "0");
            facts.put("powerRestoreAccepted", "1");
            facts.put("retargetReachesSecondTarget", "true");
            PolicyEvidence.write("storageheldnativestate", 31, facts);
            fixtures.close();
        }

        private void mark() {
            epochBefore = AuthorityEpoch.current();
            identityBefore = IdentityEpoch.current();
            mountEventsBefore = NativeMountLedger.mountEventCount();
        }

        /** Whether anything besides the Grid event itself also changed: diagnostics, not requirements. */
        private void recordChanges(String step) {
            facts.put(step + "IdentityChanged", Boolean.toString(IdentityEpoch.current() != identityBefore));
            facts.put(step + "MountEvents", Long.toString(NativeMountLedger.mountEventCount() - mountEventsBefore));
            facts.put(step + "AdvancedEpoch", Boolean.toString(AuthorityEpoch.current() > epochBefore));
            facts.forEach((fact, value) -> PolicyEvidence.trace("storageheldnativestate", fact, value));
        }

        private long nativeGold() {
            return fixtures.outerGrid().getStorageService().getInventory().getAvailableStacks().get(GOLD);
        }

        private long targetGold() {
            return target().getItem(0).getCount();
        }

        private Container target() {
            return (Container) helper.getBlockEntity(BUS_TARGET);
        }

        private PolicyKey key() {
            return PolicyLifecycleGameTests.storageKey(fixtures);
        }

        private StorageMountService mounts() {
            return StorageMountService.get(helper.getLevel());
        }

        private void waitFor(String reason) {
            helper.assertTrue(false, reason);
        }
    }
}
