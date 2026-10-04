package space.controlnet.ae2federation.test;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.GridHelper;
import appeng.core.definitions.AEItems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;
import space.controlnet.ae2federation.test.processing.AddonPatternHookEmulation;
import space.controlnet.ae2federation.test.processing.ProductionProviderScene;
import space.controlnet.ae2federation.test.processing.ProductionProviderScene.Target;

/**
 * Processing through the production ME Federation Pattern Provider block: native planner and CPU, real Processing
 * Pattern, remote Endpoints, a machine and the Endpoint return path. The test machine only stands in for the external
 * machine; the Provider, Endpoints, Router and cables are the registered production blocks.
 */
@PrefixGameTestTemplate(false)
public final class ProductionProviderGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionProviderGameTests.class);
    private static final String STRUCTURE = "ae2federation_test:harness_native_smoke";

    private ProductionProviderGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void productionProviderThreeWay(GameTestHelper helper) {
        var scene = new ProductionProviderScene(helper);
        var phase = new int[] { 0 };
        var waited = new int[] { 0 };
        var facts = new LinkedHashMap<String, String>();
        helper.succeedWhen(() -> {
            scene.runMachines();
            switch (phase[0]) {
                case 0 -> {
                    requireReady(helper, scene);
                    for (var target : Target.values()) {
                        helper.assertTrue(scene.setAccess(target, true), "Endpoint must connect to the domain");
                    }
                    scene.installPattern(0);
                    for (var target : Target.values()) {
                        var status = scene.map(0, target);
                        helper.assertTrue(status.startsWith("accepted-"), "Mapping " + target + ": " + status);
                    }
                    scene.provider().getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
                    for (var target : Target.values()) {
                        scene.stall(target, true);
                    }
                    phase[0] = 1;
                    helper.fail("Mapped one Pattern to three Endpoints");
                }
                case 1 -> {
                    var provider = scene.provider();
                    helper.assertValueEqual(provider.laneCount(), 3, "One native Lane per mapped Endpoint");
                    helper.assertValueEqual(scene.publishedMediums(0), 3,
                            "AE2 must publish three native provider mediums for the one physical Pattern");
                    for (int lane = 0; lane < 3; lane++) {
                        helper.assertTrue(provider.lane(lane).isBlocking(),
                                "Native Blocking set in the Provider menu must reach every Lane");
                    }
                    for (var target : Target.values()) {
                        var claim = scene.endpoint(target).claimState();
                        helper.assertTrue(claim instanceof ClaimState.Owned owned
                                && owned.ownerIdentity().provider().equals(provider.providerIdentity()),
                                "Endpoint " + target + " must be claimed by the production Provider");
                    }
                    helper.assertValueEqual(provider.getTerminalPatternInventory().size(),
                            FederationPatternProviderBlockEntity.PATTERN_SLOTS, "One native Pattern inventory");
                    scene.insertSourceInput(4);
                    scene.beginCraft(4);
                    phase[0] = 2;
                    helper.fail("Planning four Processing jobs");
                }
                case 2 -> {
                    helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the native plan");
                    phase[0] = 3;
                    helper.fail("Submitted to the native CPU");
                }
                case 3 -> {
                    for (var target : Target.values()) {
                        helper.assertValueEqual(scene.targetAmount(target, ProductionProviderScene.INPUT), 1L,
                                "Waiting for each blocked Lane to deliver one batch to " + target + " "
                                        + scene.diagnostics());
                    }
                    if (++waited[0] < 40) {
                        helper.fail("Observing that every blocked Lane holds only its own batch");
                    }
                    for (var target : Target.values()) {
                        helper.assertValueEqual(scene.targetAmount(target, ProductionProviderScene.INPUT), 1L,
                                "A Lane blocked by native Blocking must not receive a second batch");
                    }
                    helper.assertTrue(scene.cpuBusy(), "The fourth job must still be pending in the native CPU");
                    facts.put("threeWayDelivered", "A=1,B=1,C=1");
                    scene.stall(Target.B, false);
                    scene.stall(Target.C, false);
                    phase[0] = 4;
                    helper.fail("Released B and C; A stays blocked");
                }
                case 4 -> {
                    helper.assertValueEqual(scene.consumed(Target.B) + scene.consumed(Target.C), 3L,
                            "Waiting for B and C to finish their own and the fourth batch");
                    helper.assertValueEqual(scene.sourceAmount(ProductionProviderScene.OUTPUT), 3L,
                            "Waiting for three native returns into the source Grid");
                    helper.assertValueEqual(scene.targetAmount(Target.A, ProductionProviderScene.INPUT), 1L,
                            "Blocked Lane A keeps its undelivered result pending");
                    helper.assertTrue(scene.cpuBusy(), "The native CPU still waits for A's product");
                    facts.put("blockedLaneDidNotBlockOthers", "true");
                    facts.put("consumedWhileABlocked", "B=" + scene.consumed(Target.B) + ",C="
                            + scene.consumed(Target.C));
                    scene.stall(Target.A, false);
                    phase[0] = 5;
                    helper.fail("Released A");
                }
                default -> {
                    helper.assertTrue(!scene.cpuBusy(), "Waiting for the native CPU to finish");
                    helper.assertValueEqual(scene.sourceAmount(ProductionProviderScene.OUTPUT), 4L,
                            "All four products must return to the source Grid");
                    helper.assertValueEqual(scene.sourceAmount(ProductionProviderScene.INPUT), 0L,
                            "All four inputs must have left the source Grid");
                    for (var target : Target.values()) {
                        helper.assertValueEqual(scene.targetAmount(target, ProductionProviderScene.INPUT), 0L,
                                "No input may remain in Subnet " + target);
                    }
                    helper.assertValueEqual(scene.consumedTotal(), 4L, "Machines consumed exactly four inputs");
                    var provider = scene.provider();
                    for (int lane = 0; lane < provider.laneCount(); lane++) {
                        helper.assertTrue(!provider.lane(lane).hasPendingSend()
                                && provider.lane(lane).getReturnInv().isEmpty(),
                                "Every native Lane must be drained after completion");
                    }
                    facts.put("producedOutputs", "4");
                    facts.put("consumedInputs", "4");
                    writeEvidence("productionproviderthreeway", 12, facts);
                    helper.succeed();
                }
            }
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void productionProviderRevocation(GameTestHelper helper) {
        var scene = new ProductionProviderScene(helper);
        var phase = new int[] { 0 };
        var waited = new int[] { 0 };
        var facts = new LinkedHashMap<String, String>();
        helper.succeedWhen(() -> {
            scene.runMachines();
            switch (phase[0]) {
                case 0 -> {
                    requireReady(helper, scene);
                    helper.assertTrue(scene.setAccess(Target.A, true), "Endpoint must connect to the domain");
                    scene.installPattern(0);
                    var status = scene.map(0, Target.A);
                    helper.assertTrue(status.startsWith("accepted-"), "Mapping A: " + status);
                    helper.assertTrue(scene.setAccess(Target.A, false), "Endpoint must disconnect from the domain");
                    scene.insertSourceInput(1);
                    scene.beginCraft(1);
                    phase[0] = 1;
                    helper.fail("Planning with the Endpoint disconnected");
                }
                case 1 -> {
                    helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the native plan");
                    phase[0] = 2;
                    helper.fail("Submitted while the Endpoint is disconnected");
                }
                case 2 -> {
                    if (++waited[0] < 40) {
                        helper.assertValueEqual(scene.targetAmount(Target.A, ProductionProviderScene.INPUT), 0L,
                                "A disconnected Endpoint must stop native delivery");
                        helper.fail("Holding with the Endpoint disconnected");
                    }
                    helper.assertTrue(scene.cpuBusy(), "The native job must stay pending, not fall back");
                    helper.assertValueEqual(laneState(scene), ProviderTargetState.FEDERATION_DOMAIN_DISCONNECTED,
                            "The Lane must report the disconnected Endpoint");
                    facts.put("endpointDisconnectedState", laneState(scene).name());
                    // Replace the Federation Cable with a real local inventory on the Provider's Federation face.
                    helper.setBlock(ProductionProviderScene.CABLE_NEAR, Blocks.CHEST);
                    helper.assertTrue(scene.setAccess(Target.A, true), "Endpoint must reconnect to the domain");
                    waited[0] = 0;
                    phase[0] = 3;
                    helper.fail("Disconnected the Domain and placed a local chest");
                }
                case 3 -> {
                    var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getBlockEntity(
                            ProductionProviderScene.CABLE_NEAR);
                    helper.assertTrue(chest.isEmpty(), "No local fallback into an adjacent inventory");
                    helper.assertValueEqual(scene.targetAmount(Target.A, ProductionProviderScene.INPUT), 0L,
                            "No delivery without a connected Domain");
                    if (++waited[0] < 40) {
                        helper.fail("Holding with the Domain disconnected");
                    }
                    var state = laneState(scene);
                    helper.assertTrue(state != ProviderTargetState.ACTIVE, "Disconnected Lane must not be active");
                    facts.put("disconnectedState", state.name());
                    facts.put("localChestReceived", "0");
                    helper.setBlock(ProductionProviderScene.CABLE_NEAR, RouterRegistration.FEDERATION_CABLE.get());
                    phase[0] = 4;
                    helper.fail("Restored the Federation Cable");
                }
                default -> {
                    helper.assertTrue(!scene.cpuBusy(), "Waiting for the restored route to finish the job");
                    helper.assertValueEqual(scene.sourceAmount(ProductionProviderScene.OUTPUT), 1L,
                            "Restored access must deliver and return exactly one product");
                    helper.assertValueEqual(scene.sourceAmount(ProductionProviderScene.INPUT), 0L,
                            "The single input was consumed exactly once");
                    helper.assertValueEqual(scene.consumed(Target.A), 1L, "Exactly one machine operation");
                    facts.put("restoredOutput", "1");
                    writeEvidence("productionproviderrevocation", 9, facts);
                    helper.succeed();
                }
            }
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void productionProviderLifecycle(GameTestHelper helper) {
        var scene = new ProductionProviderScene(helper);
        var phase = new int[] { 0 };
        var saved = new Object[3];
        var facts = new LinkedHashMap<String, String>();
        helper.succeedWhen(() -> {
            scene.runMachines();
            switch (phase[0]) {
                case 0 -> {
                    requireReady(helper, scene);
                    helper.assertTrue(scene.setAccess(Target.A, true), "Endpoint must connect to the domain");
                    scene.installPattern(0);
                    var status = scene.map(0, Target.A);
                    helper.assertTrue(status.startsWith("accepted-"), "Mapping A: " + status);
                    scene.provider().getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE,
                            LockCraftingMode.LOCK_UNTIL_RESULT);
                    scene.stall(Target.A, true);
                    scene.insertSourceInput(2);
                    scene.beginCraft(2);
                    phase[0] = 1;
                    helper.fail("Planning two jobs under Lock Crafting");
                }
                case 1 -> {
                    helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the native plan");
                    phase[0] = 2;
                    helper.fail("Submitted");
                }
                case 2 -> {
                    helper.assertValueEqual(scene.targetAmount(Target.A, ProductionProviderScene.INPUT), 1L,
                            "Waiting for the first locked push");
                    var provider = scene.provider();
                    helper.assertValueEqual(provider.lane(0).getCraftingLockedReason(),
                            LockCraftingMode.LOCK_UNTIL_RESULT, "Native Lock Crafting holds the Lane");
                    saved[0] = provider.providerIdentity();
                    saved[1] = scene.endpoint(Target.A).claimState();
                    saved[2] = scene.pattern(0);
                    // Emulated chunk unload and reload: real NBT round trip into a new block entity instance.
                    provider.onChunkUnloaded();
                    var tag = provider.saveWithFullMetadata(helper.getLevel().registryAccess());
                    provider.clearContent();
                    helper.setBlock(ProductionProviderScene.PROVIDER, Blocks.AIR);
                    helper.setBlock(ProductionProviderScene.PROVIDER, ProcessingRegistration.PROVIDER.get()
                            .defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
                    scene.provider().loadWithComponents(tag, helper.getLevel().registryAccess());
                    phase[0] = 3;
                    helper.fail("Reloaded the Provider block entity");
                }
                case 3 -> {
                    var provider = scene.provider();
                    helper.assertTrue(provider.runtime().isPresent(), "Waiting for the reloaded Provider to be ready");
                    helper.assertValueEqual(provider.providerIdentity(), saved[0], "Provider identity survives");
                    helper.assertValueEqual(provider.laneCount(), 1, "Lane binding survives");
                    helper.assertValueEqual(scene.endpoint(Target.A).claimState(), saved[1],
                            "Endpoint Claim is neither released nor regenerated by reload");
                    helper.assertValueEqual(provider.lane(0).getCraftingLockedReason(),
                            LockCraftingMode.LOCK_UNTIL_RESULT, "Native lock state survives reload");
                    helper.assertValueEqual(scene.publishedMediums(0), 1, "Reloaded Lane is republished once");
                    helper.assertValueEqual(provider.getTerminalPatternInventory().getStackInSlot(0).getCount(), 1,
                            "The physical Pattern survives exactly once");
                    facts.put("reloadIdentityStable", "true");
                    facts.put("reloadLockRestored", "true");
                    scene.rotateProvider(Direction.UP);
                    phase[0] = 4;
                    helper.fail("Rotated the Federation face up");
                }
                case 4 -> {
                    var provider = scene.provider();
                    var level = helper.getLevel();
                    var position = helper.absolutePos(ProductionProviderScene.PROVIDER);
                    helper.assertTrue(provider.federationPort(Direction.UP) != null
                            && provider.federationPort(Direction.EAST) == null, "Federation port follows rotation");
                    helper.assertTrue(GridHelper.getExposedNode(level, position, Direction.EAST) != null
                            && GridHelper.getExposedNode(level, position, Direction.UP) == null,
                            "Native faces follow rotation");
                    facts.put("rotationUpdatesFaces", "true");
                    scene.rotateProvider(Direction.EAST);
                    scene.stall(Target.A, false);
                    phase[0] = 5;
                    helper.fail("Rotated back and released the machine");
                }
                case 5 -> {
                    helper.assertTrue(!scene.cpuBusy(), "Waiting for both locked jobs to finish");
                    helper.assertValueEqual(scene.sourceAmount(ProductionProviderScene.OUTPUT), 2L,
                            "Both products returned through the reloaded Lane");
                    helper.assertValueEqual(scene.consumed(Target.A), 2L, "Exactly two machine operations");
                    helper.getLevel().destroyBlock(helper.absolutePos(ProductionProviderScene.PROVIDER), true);
                    phase[0] = 6;
                    helper.fail("Broke the Provider");
                }
                default -> {
                    var box = new AABB(helper.absolutePos(ProductionProviderScene.PROVIDER)).inflate(2);
                    var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box);
                    var patterns = items.stream().filter(item -> AEItems.PROCESSING_PATTERN.is(item.getItem()))
                            .mapToInt(item -> item.getItem().getCount()).sum();
                    var blocks = items.stream().filter(item -> item.getItem()
                            .is(ProcessingRegistration.PROVIDER_ITEM.get())).mapToInt(item -> item.getItem().getCount())
                            .sum();
                    helper.assertValueEqual(patterns, 1, "The physical Pattern drops exactly once");
                    helper.assertValueEqual(blocks, 1, "The Provider drops itself");
                    helper.assertTrue(scene.endpoint(Target.A).claimState() instanceof ClaimState.Unclaimed,
                            "Removing the Provider releases its Endpoint Claim");
                    var pattern = (appeng.api.crafting.IPatternDetails) saved[2];
                    var mediums = new int[1];
                    ((appeng.me.service.CraftingService) scene.sourceGrid().getCraftingService())
                            .getProviders(pattern).forEach(ignored -> mediums[0]++);
                    helper.assertValueEqual(mediums[0], 0, "Removed Lanes are unpublished");
                    // Only this Provider's entry: other tests' Providers may share the level.
                    helper.assertTrue(ProviderObservationRegistry.entries(helper.getLevel()).stream()
                                    .noneMatch(entry -> entry.identity().equals(saved[0])),
                            "Removed Provider leaves no observation entry");
                    facts.put("patternDrops", Integer.toString(patterns));
                    facts.put("claimReleased", "true");
                    facts.put("mediumsAfterRemoval", "0");
                    writeEvidence("productionproviderlifecycle", 16, facts);
                    helper.succeed();
                }
            }
        });
    }

    /**
     * Addon hooks on AE2's pattern refresh reach the Provider's Lanes as they reach a native Pattern Provider. A
     * test-only mixin stands in for an addon such as AE2 Lightning Tech, which drops patterns of its own kind inside
     * {@code PatternProviderLogic.updatePatterns}. An addon's own re-run of that refresh reaches the network's crafting
     * index. The Lane's view of its patterns adds none to saves or drops.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void providerNativePatternRefresh(GameTestHelper helper) {
        var scene = new ProductionProviderScene(helper);
        var rejected = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.EMERALD);
        var phase = new int[] { 0 };
        var indexedAtOnce = new boolean[1];
        helper.succeedWhen(() -> {
            switch (phase[0]) {
                case 0 -> {
                    requireReady(helper, scene);
                    helper.assertTrue(scene.setAccess(Target.A, true), "Endpoint must connect to the domain");
                    AddonPatternHookEmulation.reject(rejected);
                    scene.installPattern(0);
                    scene.installPattern(1, rejected);
                    for (int slot = 0; slot < 2; slot++) {
                        var status = scene.map(slot, Target.A);
                        helper.assertTrue(status.startsWith("accepted-"), "Mapping slot " + slot + ": " + status);
                    }
                    phase[0] = 1;
                    helper.fail("Mapped a kept and a dropped Pattern to one Endpoint");
                }
                case 1 -> {
                    var provider = scene.provider();
                    helper.assertValueEqual(provider.laneCount(), 1, "Both Patterns go to one Lane");
                    var lane = provider.lane(0);
                    helper.assertTrue(AddonPatternHookEmulation.refreshed(lane),
                            "AE2's own pattern refresh must run for the Lane");
                    var outputs = lane.getAvailablePatterns().stream()
                            .map(pattern -> pattern.getPrimaryOutput().what()).toList();
                    helper.assertValueEqual(outputs, java.util.List.<appeng.api.stacks.AEKey>of(
                            ProductionProviderScene.OUTPUT), "The Lane drops the Pattern the addon drops");
                    helper.assertFalse(provider.getTerminalPatternInventory().getStackInSlot(1).isEmpty(),
                            "The dropped Pattern stays in its slot, as in a native Pattern Provider");
                    var registries = helper.getLevel().registryAccess();
                    var saved = new net.minecraft.nbt.CompoundTag();
                    lane.writeToNBT(saved, registries);
                    helper.assertTrue(saved.getList("patterns", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(),
                            "A Lane saves no copy of the Provider's Patterns");
                    var drops = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
                    provider.addAdditionalDrops(helper.getLevel(), provider.getBlockPos(), drops);
                    helper.assertValueEqual(drops.stream().filter(stack -> stack.is(AEItems.PROCESSING_PATTERN.asItem()))
                            .mapToInt(net.minecraft.world.item.ItemStack::getCount).sum(), 2,
                            "Breaking the Provider drops each Pattern once");
                    phase[0] = 2;
                    helper.fail("Checked the dropped Pattern");
                }
                case 2 -> {
                    // Addons re-run the refresh on the logic itself, as AE All Pattern does when its async expansion
                    // finishes and ExtendedAE-Plus does when Smart Doubling changes: the network must see the result
                    // at once, not at Federation's next refresh, so it is checked in the same tick.
                    var provider = scene.provider();
                    var crafting = provider.getMainNode().getGrid().getCraftingService();
                    helper.assertTrue(crafting.getCraftingFor(rejected).isEmpty(),
                            "The network must not see the Pattern the addon drops");
                    AddonPatternHookEmulation.reset();
                    provider.lane(0).updatePatterns();
                    indexedAtOnce[0] = !crafting.getCraftingFor(rejected).isEmpty();
                    var status = scene.map(0, Target.A);
                    helper.assertTrue(status.startsWith("accepted-"), "Unmapping slot 0: " + status);
                    phase[0] = 3;
                    helper.fail("Re-ran the Lane's refresh as an addon does and unmapped the kept Pattern");
                }
                case 3 -> {
                    helper.assertTrue(indexedAtOnce[0],
                            "An addon's own refresh of the Lane must reach the network's crafting index");
                    var outputs = scene.provider().lane(0).getAvailablePatterns().stream()
                            .map(pattern -> pattern.getPrimaryOutput().what()).toList();
                    helper.assertValueEqual(outputs, java.util.List.<appeng.api.stacks.AEKey>of(rejected),
                            "An unmapped Pattern leaves the Lane");
                    writeEvidence("providernativepatternrefresh", 9, Map.of("laneNativeRefresh", "true",
                            "addonDropApplied", "true", "addonRefreshIndexed", "true", "noDuplicatePatterns", "true"));
                }
                default -> throw new IllegalStateException("Unexpected phase " + phase[0]);
            }
        });
    }

    /**
     * Loads saved data into a Provider that is already running, as {@code /data merge block} and tools that write data
     * into an existing block do. A save with fewer Lanes must not drop the Provider's Lanes or Claims, and another
     * Provider's save must neither move its identity nor its Claims to the running Provider.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void providerReloadInPlace(GameTestHelper helper) {
        var scene = new ProductionProviderScene(helper);
        var phase = new int[] { 0 };
        helper.succeedWhen(() -> {
            switch (phase[0]) {
                case 0 -> {
                    requireReady(helper, scene);
                    for (var target : Target.values()) {
                        helper.assertTrue(scene.setAccess(target, true), "Endpoint must connect to the domain");
                    }
                    scene.installPattern(0);
                    for (var target : Target.values()) {
                        var status = scene.map(0, target);
                        helper.assertTrue(status.startsWith("accepted-"), "Mapping " + target + ": " + status);
                    }
                    scene.placeSecondProvider();
                    phase[0] = 1;
                    helper.fail("Mapped one Pattern to three Endpoints and placed a second Provider");
                }
                case 1 -> {
                    var first = scene.provider();
                    var second = scene.secondProvider();
                    helper.assertTrue(second.runtime().isPresent(), "Waiting for the second Provider to start");
                    var registries = helper.getLevel().registryAccess();
                    var firstIdentity = first.providerIdentity();
                    var secondIdentity = second.providerIdentity();
                    var bound = new java.util.ArrayList<java.util.Optional<?>>();
                    for (int lane = 0; lane < first.laneCount(); lane++) {
                        bound.add(first.laneEndpoint(lane));
                    }
                    helper.assertValueEqual(bound.size(), 3, "One Lane per mapped Endpoint");
                    var firstSave = first.saveWithoutMetadata(registries);
                    var emptySave = second.saveWithoutMetadata(registries);

                    first.loadWithComponents(emptySave, registries);
                    helper.assertValueEqual(first.laneCount(), 3, "A save with no Lanes keeps the running Lanes");
                    for (int lane = 0; lane < 3; lane++) {
                        helper.assertValueEqual(first.laneEndpoint(lane), bound.get(lane),
                                "Lane " + lane + " keeps its Endpoint");
                    }
                    helper.assertValueEqual(first.providerIdentity(), firstIdentity,
                            "A running Provider keeps its identity");
                    for (var target : Target.values()) {
                        helper.assertTrue(first.retained(scene.binding(target).endpointIdentity()),
                                "Endpoint " + target + " left with no Pattern waits for release, as after an unmap");
                    }

                    second.loadWithComponents(firstSave, registries);
                    helper.assertValueEqual(second.laneCount(), 3, "Another Provider's save adds its Lane count");
                    for (int lane = 0; lane < 3; lane++) {
                        helper.assertTrue(second.laneEndpoint(lane).isEmpty(),
                                "Lane " + lane + " added from another Provider's save is not bound");
                        helper.assertTrue(second.mappedProvider().slotsForLane(lane).isEmpty(),
                                "Lane " + lane + " added from another Provider's save has no Pattern");
                    }
                    helper.assertValueEqual(second.providerIdentity(), secondIdentity,
                            "Another Provider's save does not move its identity");
                    for (var target : Target.values()) {
                        helper.assertTrue(scene.endpoint(target).claimState() instanceof ClaimState.Owned owned
                                && owned.ownerIdentity().provider().equals(firstIdentity),
                                "Endpoint " + target + " is not claimed through another Provider's save");
                    }

                    first.loadWithComponents(firstSave, registries);
                    helper.assertValueEqual(first.mappedProvider().lanesForSlot(0), java.util.Set.of(0, 1, 2),
                            "The Provider's own save restores its mapping in place");
                    helper.assertTrue(first.retainedEndpoints().isEmpty(),
                            "Lanes mapped again by the Provider's own save no longer wait for release");
                    writeEvidence("providerreloadinplace", 16, Map.of("smallerSaveKeepsLanes", "true",
                            "foreignSaveKeepsIdentity", "true", "ownSaveRestoresMapping", "true"));
                }
                default -> throw new IllegalStateException("Unexpected phase " + phase[0]);
            }
        });
    }

    private static void requireReady(GameTestHelper helper, ProductionProviderScene scene) {
        var readiness = scene.topologyReadiness();
        helper.assertTrue("ready".equals(readiness), "Waiting for production topology: " + readiness);
    }

    /** Resolves Lane 0 through the same authorization lookup native push uses, then reads its outcome. */
    private static ProviderTargetState laneState(ProductionProviderScene scene) {
        var provider = scene.provider();
        space.controlnet.ae2federation.ae2.processing.FederationPatternProviderTargetCache.find(provider.lane(0));
        return provider.runtime().map(runtime -> runtime.lastResolution().state())
                .orElse(ProviderTargetState.NATIVE_TARGET_UNAVAILABLE);
    }

    private static void writeEvidence(String testId, int assertions, Map<String, String> facts) {
        facts.forEach((name, value) -> LOGGER.info("AE2F_PRODUCTION_PROVIDER testId={} fact={} value={}", testId,
                name, value));
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) {
            return;
        }
        var path = Path.of(configured).toAbsolutePath().normalize();
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        var properties = new Properties();
        properties.setProperty("schemaVersion", "1");
        properties.setProperty("status", "passed");
        properties.setProperty("kind", "verify");
        properties.setProperty("testId", testId);
        properties.setProperty("structure", STRUCTURE);
        properties.setProperty("assertions", Integer.toString(assertions));
        properties.setProperty("operations", "1");
        properties.setProperty("inserted", "0");
        properties.setProperty("extracted", "0");
        properties.setProperty("elapsedNanos", "0");
        facts.forEach(properties::setProperty);
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation production Pattern Provider evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write production Provider evidence to " + path, exception);
        }
    }
}
