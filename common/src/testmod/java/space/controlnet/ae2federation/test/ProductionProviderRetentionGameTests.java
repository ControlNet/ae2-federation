package space.controlnet.ae2federation.test;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.stacks.AEItemKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.client.policy.FederationDomainPolicySession;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
import space.controlnet.ae2federation.test.processing.ProductionProviderScene;
import space.controlnet.ae2federation.test.processing.ProductionProviderScene.Target;

/**
 * Return ownership of work already inside a remote machine. The stand-in machine absorbs delivered input and returns
 * the product later, so the Lane's native send list and return buffer are empty while the work is still outstanding.
 */
@PrefixGameTestTemplate(false)
public final class ProductionProviderRetentionGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionProviderRetentionGameTests.class);
    private static final String STRUCTURE = "ae2federation_test:harness_native_smoke";
    /** Longer than the Provider's maintenance interval, so an eager release or rebind would have happened. */
    private static final int SETTLE_TICKS = 50;

    private ProductionProviderRetentionGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = true, manualOnly = true)
    public static void productionProviderUnmapRetainsLocked(GameTestHelper helper) {
        unmapRetains(helper, LockCraftingMode.LOCK_UNTIL_RESULT, "productionproviderunmapretainslocked");
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = true, manualOnly = true)
    public static void productionProviderUnmapRetainsUnlocked(GameTestHelper helper) {
        unmapRetains(helper, LockCraftingMode.NONE, "productionproviderunmapretainsunlocked");
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = true, manualOnly = true)
    public static void productionProviderStaleCleanup(GameTestHelper helper) {
        unmapRetains(helper, LockCraftingMode.NONE, "productionproviderstalecleanup");
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = true, manualOnly = true)
    public static void productionProviderReplacedCleanup(GameTestHelper helper) {
        unmapRetains(helper, LockCraftingMode.NONE, "productionproviderreplacedcleanup");
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = true, manualOnly = true)
    public static void productionProviderClaimChangedCleanup(GameTestHelper helper) {
        unmapRetains(helper, LockCraftingMode.NONE, "productionproviderclaimchangedcleanup");
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = true, manualOnly = true)
    public static void productionProviderCleanupBuffers(GameTestHelper helper) {
        unmapRetains(helper, LockCraftingMode.NONE, "productionprovidercleanupbuffers");
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = true, manualOnly = true)
    public static void productionProviderReleaseCasFailure(GameTestHelper helper) {
        unmapRetains(helper, LockCraftingMode.NONE, "productionproviderreleasecasfailure");
    }

    private static void unmapRetains(GameTestHelper helper, LockCraftingMode lockMode, String testId) {
        var scene = new ProductionProviderScene(helper);
        var phase = new int[] { 0 };
        var waited = new int[] { 0 };
        var facts = new LinkedHashMap<String, String>();
        var staleIdentity = new space.controlnet.ae2federation.processing.claim.EndpointIdentity[1];
        var protectedRemoteClaim = new ClaimState[1];
        helper.succeedWhen(() -> {
            switch (phase[0]) {
                case 0 -> {
                    requireReady(helper, scene);
                    helper.assertTrue(scene.setPolicy(Target.A, true), "Processing Policy A must be accepted");
                    helper.assertTrue(scene.setPolicy(Target.B, true), "Processing Policy B must be accepted");
                    scene.installPattern(0);
                    scene.installPattern(1, AEItemKey.of(Items.EMERALD));
                    // Idle Endpoint: mapped and unmapped before any work was sent.
                    accepted(helper, scene.map(0, Target.B), "Mapping B");
                    accepted(helper, scene.map(0, Target.B), "Unmapping idle B");
                    // Partial unmap: A keeps serving slot 0 after slot 1 is unmapped.
                    accepted(helper, scene.map(0, Target.A), "Mapping slot 0 to A");
                    accepted(helper, scene.map(1, Target.A), "Mapping slot 1 to A");
                    accepted(helper, scene.map(1, Target.A), "Unmapping slot 1 from A");
                    scene.provider().getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, lockMode);
                    phase[0] = 1;
                    helper.fail("Mapped A and B, unmapped idle B and slot 1 of A");
                }
                case 1 -> {
                    if (++waited[0] < SETTLE_TICKS) {
                        helper.fail("Letting maintenance run");
                    }
                    var provider = scene.provider();
                    helper.assertTrue(scene.endpoint(Target.B).claimState() instanceof ClaimState.Unclaimed,
                            "An Endpoint that never received work is released when its last mapping is removed");
                    helper.assertTrue(provider.laneFor(scene.endpoint(Target.B).endpointIdentity()).isEmpty(),
                            "The idle Lane is retired");
                    requireOwned(helper, scene, provider, "Partial unmap keeps A's Claim");
                    helper.assertValueEqual(scene.publishedMediums(0), 1, "A still serves slot 0");
                    helper.assertValueEqual(scene.publishedMediums(1), 0, "Unmapped slot 1 is unpublished");
                    facts.put("idleEndpointReleased", "true");
                    facts.put("partialUnmapKeepsClaim", "true");
                    scene.insertSourceInput(1);
                    scene.beginCraft(1);
                    phase[0] = 2;
                    helper.fail("Planning one job for A");
                }
                case 2 -> {
                    helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the native plan");
                    phase[0] = 3;
                    helper.fail("Submitted");
                }
                case 3 -> {
                    helper.assertValueEqual(scene.targetAmount(Target.A, ProductionProviderScene.INPUT), 1L,
                            "Waiting for the push to A " + scene.diagnostics());
                    helper.assertValueEqual(scene.absorb(Target.A), 1L, "The machine takes the input");
                    var lane = scene.provider().lane(0);
                    helper.assertTrue(!lane.hasPendingSend() && lane.getReturnInv().isEmpty(),
                            "Native send list and return buffer are empty while the product is outstanding");
                    helper.assertValueEqual(lane.getCraftingLockedReason(), lockMode == LockCraftingMode.NONE
                            ? LockCraftingMode.NONE : LockCraftingMode.LOCK_UNTIL_RESULT, "Native lock state");
                    accepted(helper, scene.map(0, Target.A), "Unmapping the last mapping of A");
                    waited[0] = 0;
                    phase[0] = 4;
                    helper.fail("Unmapped A while its product is still in the machine");
                }
                case 4 -> {
                    if (++waited[0] < SETTLE_TICKS) {
                        helper.fail("Letting maintenance run");
                    }
                    var provider = scene.provider();
                    requireOwned(helper, scene, provider, "Unmapping must not release a Claim with outstanding work");
                    helper.assertTrue(provider.retained(scene.endpoint(Target.A).endpointIdentity()),
                            "The unmapped Endpoint is reported as retained");
                    helper.assertTrue(scene.returnAvailable(Target.A), "The return path stays open");
                    helper.assertValueEqual(scene.publishedMediums(0), 0, "No new work is published to A");
                    facts.put("unmapRetainsClaim", "true");
                    scene.placeSecondProvider();
                    phase[0] = 5;
                    helper.fail("Placed a competing Provider");
                }
                case 5 -> {
                    var second = scene.secondProvider();
                    helper.assertTrue(second.runtime().isPresent(), "Waiting for the second Provider");
                    var remainder = second.getTerminalPatternInventory().insertItem(0,
                            scene.provider().getTerminalPatternInventory().getStackInSlot(0).copy(), false);
                    helper.assertTrue(remainder.isEmpty(), "Second Provider takes a Pattern");
                    var status = scene.map(second, 0, Target.A);
                    helper.assertTrue(status.startsWith("rejected-"), "A retained Endpoint cannot be claimed: " + status);
                    requireOwned(helper, scene, scene.provider(), "The competing claim leaves the Claim unchanged");
                    facts.put("competingClaim", status);
                    phase[0] = 6;
                    helper.fail("Reloading the Provider");
                }
                case 6 -> {
                    scene.reloadProvider(scene.unloadProvider());
                    phase[0] = 7;
                    helper.fail("Reloaded the Provider");
                }
                case 7 -> {
                    var provider = scene.provider();
                    helper.assertTrue(provider.runtime().isPresent(), "Waiting for the reloaded Provider");
                    requireOwned(helper, scene, provider, "Retention survives save and reload");
                    helper.assertTrue(provider.retained(scene.endpoint(Target.A).endpointIdentity()),
                            "Retained state survives save and reload");
                    helper.assertValueEqual(scene.finishHeld(Target.A), 1L,
                            "Waiting for the machine product to enter the original Lane");
                    phase[0] = 8;
                    helper.fail("Machine returned its product");
                }
                case 8 -> {
                    helper.assertTrue(!scene.cpuBusy(), "Waiting for the native CPU to finish");
                    helper.assertValueEqual(scene.sourceAmount(ProductionProviderScene.OUTPUT), 1L,
                            "The product returned to the original Provider's Grid");
                    var provider = scene.provider();
                    helper.assertValueEqual(provider.lane(0).getCraftingLockedReason(), LockCraftingMode.NONE,
                            "The native lock is released by the returned result");
                    helper.assertValueEqual(scene.consumed(Target.A), 1L, "Exactly one machine operation");
                    requireOwned(helper, scene, provider, "Without native proof of an empty machine the Claim stays");
                    var endpoint = scene.endpoint(Target.A).endpointIdentity();
                    if (testId.equals("productionproviderreleasecasfailure")) {
                        var claimBefore = scene.endpoint(Target.A).claimState();
                        var nativeLane = provider.lane(0);
                        space.controlnet.ae2federation.ae2.processing.FederationPatternProviderTargetCache.find(nativeLane);
                        var oldTarget = ((space.controlnet.ae2federation.processing.provider.ProviderTargetResolution.Authorized)
                                provider.runtime().orElseThrow().lastResolution()).target();
                        var confirmation = provider.releaseConfirmation(endpoint);
                        ReleaseClaimFailureProbe.rejectNext();
                        helper.assertValueEqual(provider.releaseEndpoint(endpoint), "rejected-claim-changed",
                                "A failed CAS cannot report successful release");
                        helper.assertValueEqual(scene.endpoint(Target.A).claimState(), claimBefore, "Failed CAS preserves Claim");
                        helper.assertValueEqual(provider.releaseConfirmation(endpoint), confirmation, "Failed CAS preserves binding");
                        helper.assertTrue(provider.retained(endpoint), "Failed CAS remains retained");
                        helper.assertTrue(provider.releaseEndpoint(endpoint).startsWith("released-"),
                                "Subsequent real CAS can release normally");
                        accepted(helper, scene.map(0, Target.B), "Reuse the drained Lane for B");
                        helper.assertTrue(provider.lane(0) == nativeLane
                                && provider.laneFor(scene.endpoint(Target.B).endpointIdentity()).orElseThrow() == 0,
                                "Reuse preserves the native Lane object with a new binding");
                        helper.assertTrue(provider.saveWithFullMetadata(helper.getLevel().registryAccess())
                                .getList("laneBindings", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                                .getLong("revision") > oldTarget.provenance().lane().revision(), "Reuse advances revision");
                        helper.assertTrue(!space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding
                                .captureFederatedReturn(oldTarget), "Old authorization cannot restore a return path");
                        helper.assertTrue(!scene.returnAvailable(Target.A), "Old Endpoint cannot return into reused Lane");
                        writeEvidence(testId, 10, Map.of("forcedCasFailureRejected", "true", "realCasSucceeded", "true",
                                "reusedLaneRejectsOldReturn", "true"));
                        helper.succeed();
                        return;
                    }
                    if (testId.equals("productionprovidercleanupbuffers")) {
                        // Test-only native buffer fixture: seed a persisted native remainder, then exercise rejection
                        // and native dismantling recovery. This is not evidence of an actual machine partial insert.
                        var lane = provider.lane(0);
                        lane.getReturnInv().insert(AEItemKey.of(Items.EMERALD), 2,
                                appeng.api.config.Actionable.MODULATE,
                                appeng.api.networking.security.IActionSource.empty());
                        helper.assertValueEqual(provider.releaseEndpoint(endpoint), "rejected-pending-return",
                                "A native return buffer cannot be discarded by release");
                        var remainder = new appeng.api.stacks.GenericStack(ProductionProviderScene.INPUT, 3);
                        ((space.controlnet.ae2federation.mixin.PatternProviderLogicAccess) (Object) lane)
                                .ae2federation$getSendList().add(remainder);
                        helper.assertValueEqual(provider.releaseEndpoint(endpoint), "rejected-pending-send",
                                "A native send remainder cannot be rebound or discarded");
                        helper.assertTrue(provider.retained(endpoint), "Buffer rejection preserves binding");
                        var drops = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
                        provider.addAdditionalDrops(helper.getLevel(), provider.getBlockPos(), drops);
                        helper.assertValueEqual(drops.stream().filter(stack -> stack.is(Items.EMERALD))
                                .mapToInt(net.minecraft.world.item.ItemStack::getCount).sum(), 2, "Native return drops preserved");
                        helper.assertValueEqual(drops.stream().filter(stack -> stack.is(Items.COBBLESTONE))
                                .mapToInt(net.minecraft.world.item.ItemStack::getCount).sum(), 3, "Native send drops preserved");
                        // Complete the same native drop/clear sequence used by explicit dismantling.
                        provider.clearContent();
                        helper.setBlock(ProductionProviderScene.PROVIDER, net.minecraft.world.level.block.Blocks.AIR);
                        writeEvidence(testId, 5, Map.of("pendingSendRejected", "true", "pendingReturnRejected", "true",
                                "nativeDropRecovery", "true"));
                        helper.succeed();
                        return;
                    }
                    if (testId.endsWith("cleanup")) {
                        var player = helper.makeMockServerPlayerInLevel();
                        var router = helper.absolutePos(ProductionProviderScene.ROUTER);
                        player.moveTo(router.getX() + 0.5, router.getY() + 1, router.getZ() + 0.5);
                        var session = FederationDomainPolicySession.forRouter(player, router);
                        for (int attempt = 0; attempt < 4 && !"confirm-release".equals(session.mappingStatusCode()); attempt++) {
                            session.releaseEndpoint();
                            if (!"confirm-release".equals(session.mappingStatusCode())) session.nextMappingLane();
                        }
                        helper.assertValueEqual(session.mappingStatusCode(), "confirm-release", "Confirm old binding");
                        ClaimState protectedClaim = null;
                        if (testId.equals("productionproviderreplacedcleanup")) {
                            scene.replaceEndpoint(Target.A);
                            protectedClaim = scene.endpoint(Target.A).claimState();
                        } else if (testId.equals("productionproviderclaimchangedcleanup")) {
                            var entity = scene.endpoint(Target.A);
                            var oldState = entity.claimState();
                            helper.assertTrue(entity.releaseClaim(oldState.owner().orElseThrow(), oldState.epoch()),
                                    "Original owner releases its Claim");
                            entity.claim(new space.controlnet.ae2federation.processing.claim.ClaimRequest(
                                    endpoint, entity.claimState().epoch(),
                                    new space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity(
                                            scene.secondProvider().providerIdentity())));
                            protectedClaim = entity.claimState();
                        } else {
                            scene.unloadEndpoint(Target.A); // Test-only removal, not a real chunk-unload proof.
                        }
                        staleIdentity[0] = endpoint;
                        protectedRemoteClaim[0] = protectedClaim;
                        phase[0] = 90;
                        session.releaseEndpoint();
                        helper.assertTrue(!session.mappingStatusCode().startsWith("released-")
                                        && !session.mappingStatusCode().startsWith("cleared-stale-"),
                                "State change cannot reuse the old confirmation");
                        helper.fail("Reopen management after topology change");
                    }
                    helper.assertTrue(provider.releaseEndpoint(scene.endpoint(Target.B).endpointIdentity())
                            .startsWith("rejected-"), "Releasing an Endpoint without a retained Lane is rejected");
                    // Explicit release through the Domain management session opened at the Router.
                    var player = helper.makeMockServerPlayerInLevel();
                    var router = helper.absolutePos(ProductionProviderScene.ROUTER);
                    player.moveTo(router.getX() + 0.5, router.getY() + 1, router.getZ() + 0.5);
                    var session = FederationDomainPolicySession.forRouter(player, router);
                    for (int attempt = 0; attempt < 3 && !"confirm-release".equals(session.mappingStatusCode());
                            attempt++) {
                        session.releaseEndpoint();
                        if (!"confirm-release".equals(session.mappingStatusCode())) {
                            session.nextMappingLane();
                        }
                    }
                    helper.assertValueEqual(session.mappingStatusCode(), "confirm-release",
                            "The first release press only asks for confirmation");
                    requireOwned(helper, scene, provider, "An unconfirmed release changes nothing");
                    session.releaseEndpoint();
                    var status = session.mappingStatusCode();
                    helper.assertTrue(status.startsWith("released"), "Explicit release: " + status);
                    helper.assertTrue(scene.endpoint(Target.A).claimState() instanceof ClaimState.Unclaimed,
                            "Explicit release releases the Claim");
                    helper.assertTrue(provider.laneFor(endpoint).isEmpty(), "Explicit release retires the Lane");
                    helper.assertTrue(!scene.returnAvailable(Target.A), "Explicit release closes the return path");
                    var second = scene.secondProvider();
                    var claimed = scene.map(second, 0, Target.A);
                    helper.assertTrue(claimed.startsWith("accepted-"), "Re-claim after release: " + claimed);
                    helper.assertTrue(scene.endpoint(Target.A).claimState() instanceof ClaimState.Owned owned
                            && owned.ownerIdentity().provider().equals(second.providerIdentity()),
                            "The second Provider owns the released Endpoint");
                    facts.put("returnedToOriginalLane", "true");
                    facts.put("explicitRelease", status);
                    facts.put("reclaimedBySecond", "true");
                    writeEvidence(testId, 26, facts);
                    helper.succeed();
                }
                case 90 -> {
                    var provider = scene.provider();
                    var endpoint = staleIdentity[0];
                    var player = helper.makeMockServerPlayerInLevel();
                    var router = helper.absolutePos(ProductionProviderScene.ROUTER);
                    player.moveTo(router.getX() + 0.5, router.getY() + 1, router.getZ() + 0.5);
                    var session = FederationDomainPolicySession.forRouter(player, router);
                    for (int attempt = 0; attempt < 5 && !"confirm-release".equals(session.mappingStatusCode()); attempt++) {
                        session.releaseEndpoint();
                        if (!"confirm-release".equals(session.mappingStatusCode())) session.nextMappingLane();
                    }
                    helper.assertValueEqual(session.mappingStatusCode(), "confirm-release", "Select offline retained identity");
                    helper.assertTrue(provider.retained(endpoint), "Unconfirmed cleanup retains binding");
                    session.releaseEndpoint();
                    helper.assertTrue(session.mappingStatusCode().startsWith("cleared-stale-"),
                            "Management cleanup: " + session.mappingStatusCode());
                    helper.assertTrue(provider.laneFor(endpoint).isEmpty(), "Stale lane retired");
                    if (protectedRemoteClaim[0] != null) {
                        helper.assertValueEqual(scene.endpoint(Target.A).claimState(), protectedRemoteClaim[0],
                                "Replacement or new owner's Claim unchanged");
                    }
                    helper.assertValueEqual(scene.sourceAmount(ProductionProviderScene.OUTPUT), 1L, "No resource loss");
                    writeEvidence(testId, 6, Map.of("managementCleanup", "true", "confirmationInvalidated", "true"));
                    helper.succeed();
                }
                default -> throw new IllegalStateException("Unknown phase " + phase[0]);
            }
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 2400, required = true, manualOnly = true)
    public static void productionProviderEndpointReloadReturns(GameTestHelper helper) {
        var scene = new ProductionProviderScene(helper);
        var phase = new int[] { 0 };
        var round = new int[] { 0 };
        var waited = new int[] { 0 };
        var tags = new CompoundTag[2];
        var facts = new LinkedHashMap<String, String>();
        var claim = new Object[1];
        helper.succeedWhen(() -> {
            switch (phase[0]) {
                case 0 -> {
                    requireReady(helper, scene);
                    helper.assertTrue(scene.setPolicy(Target.A, true), "Processing Policy must be accepted");
                    scene.installPattern(0);
                    accepted(helper, scene.map(0, Target.A), "Mapping A");
                    scene.provider().getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE,
                            LockCraftingMode.LOCK_UNTIL_RESULT);
                    scene.insertSourceInput(3);
                    scene.beginCraft(3);
                    phase[0] = 1;
                    helper.fail("Planning three locked jobs");
                }
                case 1 -> {
                    helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the native plan");
                    claim[0] = scene.endpoint(Target.A).claimState();
                    phase[0] = 2;
                    helper.fail("Submitted");
                }
                case 2 -> {
                    helper.assertValueEqual(scene.targetAmount(Target.A, ProductionProviderScene.INPUT), 1L,
                            "Waiting for locked push " + round[0] + " " + scene.diagnostics());
                    helper.assertValueEqual(scene.absorb(Target.A), 1L, "The machine takes the input");
                    helper.assertValueEqual(scene.provider().lane(0).getCraftingLockedReason(),
                            LockCraftingMode.LOCK_UNTIL_RESULT, "The native lock blocks the next push");
                    waited[0] = 0;
                    phase[0] = 6;
                    helper.fail("Machine holds the product of round " + round[0]);
                }
                case 6 -> {
                    // Past the Provider's maintenance interval, so its return binding is settled before the reload.
                    if (++waited[0] < SETTLE_TICKS) {
                        helper.fail("Letting maintenance settle the return binding");
                    }
                    helper.assertTrue(scene.returnAvailable(Target.A), "The return path is bound before the reload");
                    // Round 0: Endpoint only. Round 1: Endpoint then Provider out, Endpoint first back.
                    // Round 2: Provider then Endpoint out, Provider first back.
                    switch (round[0]) {
                        case 0 -> scene.reloadEndpoint(Target.A, scene.unloadEndpoint(Target.A));
                        case 1 -> {
                            tags[0] = scene.unloadEndpoint(Target.A);
                            tags[1] = scene.unloadProvider();
                            scene.reloadEndpoint(Target.A, tags[0]);
                            scene.reloadProvider(tags[1]);
                        }
                        default -> {
                            tags[1] = scene.unloadProvider();
                            tags[0] = scene.unloadEndpoint(Target.A);
                            scene.reloadProvider(tags[1]);
                            scene.reloadEndpoint(Target.A, tags[0]);
                        }
                    }
                    phase[0] = 3;
                    helper.fail("Reloaded in order " + round[0]);
                }
                case 3 -> {
                    helper.assertTrue(scene.provider().runtime().isPresent() && scene.binding(Target.A) != null,
                            "Waiting for the reloaded blocks");
                    helper.assertValueEqual(scene.endpoint(Target.A).claimState(), claim[0],
                            "Reload neither releases nor regenerates the Claim");
                    // No GUI and no new job: the native lock prevents any push that could rebind the path.
                    helper.assertValueEqual(scene.finishHeld(Target.A), 1L,
                            "Waiting for the return path of order " + round[0] + " to be restored");
                    phase[0] = 4;
                    helper.fail("Product returned in order " + round[0]);
                }
                case 4 -> {
                    helper.assertValueEqual(scene.sourceAmount(ProductionProviderScene.OUTPUT), round[0] + 1L,
                            "Waiting for the product to reach the source Grid");
                    facts.put("order" + round[0] + "Returned", "true");
                    round[0]++;
                    phase[0] = round[0] < 3 ? 2 : 5;
                    helper.fail("Next round");
                }
                case 5 -> {
                    helper.assertTrue(!scene.cpuBusy(), "Waiting for the native CPU to finish");
                    helper.assertValueEqual(scene.sourceAmount(ProductionProviderScene.OUTPUT), 3L,
                            "All three products returned through the original Lane");
                    helper.assertValueEqual(scene.consumed(Target.A), 3L, "Exactly three machine operations");
                    helper.assertValueEqual(scene.provider().lane(0).getCraftingLockedReason(), LockCraftingMode.NONE,
                            "The last result released the native lock");
                    writeEvidence("productionproviderendpointreloadreturns", 15, facts);
                    helper.succeed();
                }
                default -> throw new IllegalStateException("Unknown phase " + phase[0]);
            }
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = true, manualOnly = true)
    public static void productionProviderReturnRebindAuthorization(GameTestHelper helper) {
        var scene = new ProductionProviderScene(helper);
        var phase = new int[] { 0 };
        var waited = new int[] { 0 };
        var facts = new LinkedHashMap<String, String>();
        helper.succeedWhen(() -> {
            switch (phase[0]) {
                case 0 -> {
                    requireReady(helper, scene);
                    helper.assertTrue(scene.setPolicy(Target.A, true), "Processing Policy must be accepted");
                    scene.installPattern(0);
                    accepted(helper, scene.map(0, Target.A), "Mapping A");
                    scene.provider().getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE,
                            LockCraftingMode.LOCK_UNTIL_RESULT);
                    scene.insertSourceInput(1);
                    scene.beginCraft(1);
                    phase[0] = 1;
                    helper.fail("Planning one locked job");
                }
                case 1 -> {
                    helper.assertTrue(scene.submitWhenPlanned(), "Waiting for the native plan");
                    phase[0] = 2;
                    helper.fail("Submitted");
                }
                case 2 -> {
                    helper.assertValueEqual(scene.targetAmount(Target.A, ProductionProviderScene.INPUT), 1L,
                            "Waiting for the push to A");
                    helper.assertValueEqual(scene.absorb(Target.A), 1L, "The machine takes the input");
                    waited[0] = 0;
                    phase[0] = 7;
                    helper.fail("Machine holds the product");
                }
                case 7 -> {
                    if (++waited[0] < SETTLE_TICKS) {
                        helper.fail("Letting maintenance settle the return binding");
                    }
                    helper.assertTrue(scene.returnAvailable(Target.A), "The return path is bound before the reload");
                    helper.assertTrue(scene.setPolicy(Target.A, false), "Revoking Policy must be accepted");
                    scene.reloadEndpoint(Target.A, scene.unloadEndpoint(Target.A));
                    waited[0] = 0;
                    phase[0] = 3;
                    helper.fail("Revoked Policy and reloaded the Endpoint");
                }
                case 3 -> {
                    helper.assertTrue(scene.binding(Target.A) != null, "Waiting for the reloaded Endpoint");
                    if (++waited[0] < SETTLE_TICKS) {
                        helper.fail("Letting maintenance run");
                    }
                    helper.assertTrue(!scene.returnAvailable(Target.A),
                            "Without Policy the return path is not rebound to the Lane");
                    helper.assertValueEqual(scene.held(Target.A), 1L, "The product stays in the machine");
                    facts.put("revokedReturnRebound", "false");
                    helper.assertTrue(scene.setPolicy(Target.A, true), "Restoring Policy must be accepted");
                    phase[0] = 4;
                    helper.fail("Restored Policy");
                }
                case 4 -> {
                    helper.assertValueEqual(scene.finishHeld(Target.A), 1L,
                            "Waiting for the authorized return path to be restored");
                    phase[0] = 5;
                    helper.fail("Returned after Policy was restored");
                }
                case 5 -> {
                    helper.assertTrue(!scene.cpuBusy(), "Waiting for the native CPU to finish");
                    helper.assertValueEqual(scene.sourceAmount(ProductionProviderScene.OUTPUT), 1L,
                            "The product returned to the source Grid");
                    helper.assertValueEqual(scene.provider().lane(0).getCraftingLockedReason(), LockCraftingMode.NONE,
                            "The result released the native lock");
                    facts.put("restoredReturn", "true");
                    scene.replaceEndpoint(Target.A);
                    waited[0] = 0;
                    phase[0] = 6;
                    helper.fail("Replaced the Endpoint with a new one");
                }
                default -> {
                    var binding = scene.binding(Target.A);
                    helper.assertTrue(binding != null, "Waiting for the replacement Endpoint");
                    if (++waited[0] < SETTLE_TICKS) {
                        helper.fail("Letting maintenance run");
                    }
                    var provider = scene.provider();
                    helper.assertTrue(scene.endpoint(Target.A).claimState() instanceof ClaimState.Unclaimed,
                            "A replacement Endpoint is not claimed by the old Lane");
                    helper.assertTrue(!binding.runtime().returnOwnedBy(provider.lane(0)),
                            "The old Lane's return ownership is not bound to the replacement Endpoint");
                    facts.put("replacementBound", "false");
                    writeEvidence("productionproviderreturnrebindauthorization", 12, facts);
                    helper.succeed();
                }
            }
        });
    }

    private static void accepted(GameTestHelper helper, String status, String action) {
        helper.assertTrue(status.startsWith("accepted-"), action + ": " + status);
    }

    private static void requireOwned(GameTestHelper helper, ProductionProviderScene scene,
            FederationPatternProviderBlockEntity provider, String message) {
        helper.assertTrue(scene.endpoint(Target.A).claimState() instanceof ClaimState.Owned owned
                && owned.ownerIdentity().provider().equals(provider.providerIdentity()), message);
    }

    private static void requireReady(GameTestHelper helper, ProductionProviderScene scene) {
        var readiness = scene.topologyReadiness();
        helper.assertTrue("ready".equals(readiness), "Waiting for production topology: " + readiness);
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
