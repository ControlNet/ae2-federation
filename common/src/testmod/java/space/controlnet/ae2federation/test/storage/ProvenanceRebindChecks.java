package space.controlnet.ae2federation.test.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityRegistry;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomain;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomainRegistry;
import space.controlnet.ae2federation.storage.provenance.ProvenanceDiagnostic;
import space.controlnet.ae2federation.storage.provenance.ProvenanceException;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

public final class ProvenanceRebindChecks {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final IActionSource SOURCE = IActionSource.empty();

    private ProvenanceRebindChecks() {
    }

    public static void run(GameTestHelper helper) {
        var state = new State(helper);
        helper.succeedWhen(state::tick);
    }

    private static final class State {
        private final GameTestHelper helper;
        private final ProvenanceStorageFixture provenanceFixture;
        private final PolicyBridgeFixtures mountFixture;
        private final NativeSourceDomainRegistry registry = new NativeSourceDomainRegistry();
        private final ProvenanceCallbackProviders.ManagedSlotRebound slotProvider =
                new ProvenanceCallbackProviders.ManagedSlotRebound();
        private int phase;
        private CompoundTag chestState;
        private CompoundTag providerState;
        private NativeSourceDomain reboundBefore;
        private NativeSourceDomain reboundAfter;
        private NativeSourceDomain slotBefore;
        private IManagedGridNode providerNode;
        private int reboundGridBefore;
        private int slotGridBefore;
        private IGrid slotRuntimeGrid;
        private ProvenanceEvidence.CallbackSnapshot callbackBefore;
        private ProvenanceEvidence.CallbackSnapshot callbackAfter;
        private MEStorage projectionBefore;
        private long mountSourceGenerationBefore;
        private long mountGenerationBefore;
        private int removalsBefore;

        private State(GameTestHelper helper) {
            this.helper = helper;
            provenanceFixture = new ProvenanceStorageFixture(helper);
            mountFixture = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
            mountFixture.installStorageCells();
        }

        private void tick() {
            switch (phase) {
                case 0 -> removeOriginalGrid();
                case 1 -> restoreOriginalGrid();
                case 2 -> capturePositiveReboundAndPrepareCollision();
                case 3 -> removeCollisionGrid();
                case 4 -> restoreCollisionGrid();
                case 5 -> rejectFalseContinuityAndActivateMount();
                case 6 -> captureMountAndReplaceSource();
                case 7 -> verifyNewerMountIsolation();
                default -> throw new IllegalStateException("Unexpected provenance repair phase");
            }
        }

        private void removeOriginalGrid() {
            helper.assertTrue(provenanceFixture.ready(), "Waiting for native identity before rebound");
            helper.assertTrue(FederationDomainRegistryAccess.confirmedNetworkId(provenanceFixture.grid()).isPresent(),
                    "Waiting for original Grid origin settlement");
            reboundBefore = registry.discover(provenanceFixture.grid());
            reboundGridBefore = System.identityHashCode(provenanceFixture.grid());
            chestState = provenanceFixture.chest().saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.setBlock(ProvenanceStorageFixture.CHEST, Blocks.AIR);
            phase = 1;
            helper.assertTrue(false, "Waiting for original native Grid removal");
        }

        private void restoreOriginalGrid() {
            helper.setBlock(ProvenanceStorageFixture.CHEST,
                    appeng.core.definitions.AEBlocks.ME_CHEST.block());
            provenanceFixture.chest().loadWithComponents(chestState, helper.getLevel().registryAccess());
            provenanceFixture.chest().setChanged();
            phase = 2;
            helper.assertTrue(false, "Waiting for positive native Grid rebound");
        }

        private void capturePositiveReboundAndPrepareCollision() {
            helper.assertTrue(provenanceFixture.ready(), "Waiting for rebound callback");
            reboundAfter = registry.discover(provenanceFixture.grid());
            helper.assertValueEqual(reboundAfter.origin(), reboundBefore.origin(),
                    "NetworkId must survive native rebound");
            helper.assertValueEqual(reboundAfter.sources().getFirst().id(), reboundBefore.sources().getFirst().id(),
                    "Callback-owned ExportSource identity must survive rebound");
            helper.assertTrue(reboundAfter.generation().value() > reboundBefore.generation().value(),
                    "Rebound must advance source generation");
            helper.assertTrue(!registry.isCurrent(reboundBefore) && registry.isCurrent(reboundAfter),
                    "Old generation must be invalid before new operations");
            helper.assertTrue(System.identityHashCode(provenanceFixture.grid()) != reboundGridBefore,
                    "Rebound must use a new runtime IGrid");
            helper.assertValueEqual(reboundAfter.sources().getFirst().storage().insert(
                    IRON, 7, Actionable.MODULATE, SOURCE), 7L, "New native callback must own operations");
            var delegate = provenanceFixture.source();
            provenanceFixture.chest().setCell(ItemStack.EMPTY);
            slotProvider.configureManagedPrefix(delegate);
            providerNode = provenanceFixture.addProvider(slotProvider);
            phase = 3;
            helper.assertTrue(false, "Waiting for managed-prefix callback registration");
        }

        private void removeCollisionGrid() {
            helper.assertTrue(provenanceFixture.providerReady(providerNode),
                    "Waiting for managed-prefix callback node");
            slotBefore = registry.discover(provenanceFixture.grid());
            if (slotBefore.sources().size() != 1) {
                helper.assertTrue(false, "Waiting for chest callback removal");
            }
            callbackBefore = ProvenanceEvidence.callback("provenancenativerebind", "slot-before", slotProvider);
            helper.assertValueEqual(slotBefore.sources().getFirst().aliases().getFirst().id().callbackIndex(), 1,
                    "Native alias must retain literal callback slot after managed-prefix exclusion");
            helper.assertValueEqual(callbackBefore.nativeSlot(), 1,
                    "Runtime callback replay must observe native source at slot one");
            slotGridBefore = System.identityHashCode(provenanceFixture.grid());
            slotRuntimeGrid = provenanceFixture.grid();
            chestState = provenanceFixture.chest().saveWithFullMetadata(helper.getLevel().registryAccess());
            providerState = provenanceFixture.saveAndRemoveProvider(providerNode);
            provenanceFixture.removeGrid();
            slotProvider.configureReplacement(slotBefore.sources().getFirst().storage());
            phase = 4;
            helper.assertTrue(false, "Waiting for managed-slot source Grid removal");
        }

        private void restoreCollisionGrid() {
            helper.assertTrue(!slotRuntimeGrid.getNodes().iterator().hasNext(),
                    "Waiting for removed callback Grid to release all native nodes");
            NetworkIdentityRegistry.get(helper.getLevel()).release(slotRuntimeGrid);
            providerNode = provenanceFixture.restoreStandaloneProvider(slotProvider, providerState);
            phase = 5;
            helper.assertTrue(false, "Waiting for managed-slot collision rebound");
        }

        private void rejectFalseContinuityAndActivateMount() {
            helper.assertTrue(provenanceFixture.providerReady(providerNode),
                    "Waiting for collision callback node settlement");
            var providerGrid = providerNode.getNode().getGrid();
            var restoredOrigin = FederationDomainRegistryAccess.confirmedNetworkId(providerGrid);
            helper.assertTrue(restoredOrigin.isPresent() && restoredOrigin.orElseThrow().equals(slotBefore.origin().value()),
                    "Waiting for restored callback Grid origin settlement");
            helper.assertTrue(System.identityHashCode(providerGrid) != slotGridBefore,
                    "Collision probe must use a new runtime IGrid");
            callbackAfter = ProvenanceEvidence.callback("provenancenativerebind", "slot-after", slotProvider);
            ProvenanceException rejected = null;
            try {
                registry.discover(providerGrid);
            } catch (ProvenanceException exception) {
                rejected = exception;
            }
            helper.assertTrue(rejected != null, "Filtered callback-slot collision must reject rebound continuity");
            helper.assertValueEqual(rejected.diagnostic(), ProvenanceDiagnostic.UNPROVEN_GRID_REBOUND,
                    "False callback continuity must carry the rebound diagnostic");
            helper.assertValueEqual(callbackAfter.nativeSlot(), 0,
                    "Replacement callback replay must observe native source at slot zero");
            helper.assertTrue(!registry.isCurrent(slotBefore), "Rejected rebound must invalidate the prior domain");
            ProvenanceEvidence.rejection("provenancenativerebind", slotBefore, rejected.diagnostic());
            helper.assertTrue(mountFixture.networksSettled(), "Waiting for mount fixture identities");
            mountFixture.placeFirstBridge();
            phase = 6;
            helper.assertTrue(false, "Waiting for confirmed mount Federation Domain");
        }

        private void captureMountAndReplaceSource() {
            mountFixture.refreshFirstBridge();
            helper.assertTrue(mountFixture.firstBridgeReady(), "Waiting for confirmed mount Federation Domain");
            var mounts = StorageMountService.get(helper.getLevel());
            var key = mountKey();
            var policies = PolicyService.get(helper.getLevel());
            if (policies.revision(key).equals(PolicyRevision.NONE)) {
                policies.edit(new PolicyEdit(key, PolicyRevision.NONE, PolicyRule.storageDefaults()));
            }
            projectionBefore = mounts.projection(key);
            helper.assertTrue(projectionBefore != null, "Active Storage Policy must install projection A");
            mountSourceGenerationBefore = mounts.sourceDomain(key).generation().value();
            mountGenerationBefore = mounts.mountGeneration(key).value();
            removalsBefore = mounts.removedProviderCount();
            helper.assertValueEqual(projectionBefore.insert(IRON, 5, Actionable.MODULATE, SOURCE), 5L,
                    "Projection A must be operational before remount");
            helper.assertValueEqual(projectionBefore.getAvailableStacks().get(IRON), 5L,
                    "Projection A must expose its native source before remount");
            mountFixture.providerChest().setCell(appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
            phase = 7;
            helper.assertTrue(false, "Waiting for projection B with newer generations");
        }

        private void verifyNewerMountIsolation() {
            var mounts = StorageMountService.get(helper.getLevel());
            mounts.reconcileAll();
            var key = mountKey();
            var projectionAfter = mounts.projection(key);
            if (projectionAfter == null || projectionAfter == projectionBefore) {
                helper.assertTrue(false, "Waiting for genuine relationship remount");
            }
            var sourceGenerationAfter = mounts.sourceDomain(key).generation().value();
            var mountGenerationAfter = mounts.mountGeneration(key).value();
            var removalsAfterRemount = mounts.removedProviderCount();
            helper.assertTrue(sourceGenerationAfter > mountSourceGenerationBefore,
                    "Projection B must use a newer source generation");
            helper.assertTrue(mountGenerationAfter > mountGenerationBefore,
                    "Projection B must use a newer mount generation");
            helper.assertValueEqual(removalsAfterRemount, removalsBefore + 1,
                    "Genuine remount must remove exactly projection A's provider");
            var oldVisible = projectionBefore.getAvailableStacks().get(IRON);
            var oldInserted = projectionBefore.insert(IRON, 2, Actionable.MODULATE, SOURCE);
            var oldExtracted = projectionBefore.extract(IRON, 2, Actionable.MODULATE, SOURCE);
            var removalsAfterStale = mounts.removedProviderCount();
            helper.assertValueEqual(oldVisible, 0L, "Projection A listing must fail closed after projection B mounts");
            helper.assertValueEqual(oldInserted, 0L, "Projection A insertion must fail closed after projection B mounts");
            helper.assertValueEqual(oldExtracted, 0L, "Projection A extraction must fail closed after projection B mounts");
            helper.assertTrue(mounts.projection(key) == projectionAfter && mounts.mountedRelationshipCount() == 1,
                    "Projection A must not remove or replace current projection B");
            helper.assertValueEqual(removalsAfterStale, removalsAfterRemount,
                    "Stale projection reentrancy must not remove projection B's provider");
            var newInserted = projectionAfter.insert(IRON, 6, Actionable.MODULATE, SOURCE);
            var newVisible = projectionAfter.getAvailableStacks().get(IRON);
            var newExtracted = projectionAfter.extract(IRON, 2, Actionable.MODULATE, SOURCE);
            var newRemaining = projectionAfter.getAvailableStacks().get(IRON);
            helper.assertValueEqual(newInserted, 6L, "Projection B insertion must remain operational");
            helper.assertValueEqual(newVisible, 6L, "Projection B listing must remain operational");
            helper.assertValueEqual(newExtracted, 2L, "Projection B extraction must remain operational");
            helper.assertValueEqual(newRemaining, 4L, "Projection B must retain the native operation result");
            writeEvidence(projectionAfter, sourceGenerationAfter, mountGenerationAfter, removalsAfterRemount,
                    removalsAfterStale, oldVisible, oldInserted, oldExtracted, newInserted, newVisible,
                    newExtracted, newRemaining);
            provenanceFixture.close();
            mountFixture.close();
        }

        private PolicyKey mountKey() {
            return new PolicyKey(mountFixture.mainNetwork(), mountFixture.outerNetwork(), PolicyCapability.STORAGE);
        }

        private void writeEvidence(MEStorage projectionAfter, long sourceGenerationAfter, long mountGenerationAfter,
                int removalsAfterRemount, int removalsAfterStale, long oldVisible, long oldInserted,
                long oldExtracted, long newInserted, long newVisible, long newExtracted, long newRemaining) {
            ProvenanceEvidence.domain("provenancenativerebind", "before", reboundBefore);
            ProvenanceEvidence.domain("provenancenativerebind", "after", reboundAfter);
            ProvenanceEvidence.domain("provenancenativerebind", "slot-before", slotBefore);
            var projectionBeforeId = Integer.toUnsignedString(System.identityHashCode(projectionBefore));
            var projectionAfterId = Integer.toUnsignedString(System.identityHashCode(projectionAfter));
            ProvenanceEvidence.mountIsolation("provenancenativerebind", projectionBeforeId, projectionAfterId,
                    mountSourceGenerationBefore, sourceGenerationAfter, mountGenerationBefore, mountGenerationAfter,
                    removalsBefore, removalsAfterRemount, removalsAfterStale, oldVisible, oldInserted, oldExtracted,
                    newInserted, newVisible, newExtracted, newRemaining);
            PolicyEvidence.write("provenancenativerebind", 32, Map.ofEntries(
                    Map.entry("originBefore", reboundBefore.origin().value().toString()),
                    Map.entry("originAfter", reboundAfter.origin().value().toString()),
                    Map.entry("sourceBefore", reboundBefore.sources().getFirst().id().toString()),
                    Map.entry("sourceAfter", reboundAfter.sources().getFirst().id().toString()),
                    Map.entry("aliasBefore", reboundBefore.sources().getFirst().aliases().getFirst().id().toString()),
                    Map.entry("aliasAfter", reboundAfter.sources().getFirst().aliases().getFirst().id().toString()),
                    Map.entry("storageBefore", identity(reboundBefore.sources().getFirst().storage())),
                    Map.entry("storageAfter", identity(reboundAfter.sources().getFirst().storage())),
                    Map.entry("generationBefore", Long.toString(reboundBefore.generation().value())),
                    Map.entry("generationAfter", Long.toString(reboundAfter.generation().value())),
                    Map.entry("gridBefore", Integer.toUnsignedString(reboundGridBefore)),
                    Map.entry("gridAfter", identity(reboundAfter.runtimeGrid())),
                    Map.entry("oldGenerationCurrent", "false"), Map.entry("newGenerationCurrent", "true"),
                    Map.entry("acceptedAfterRebind", "7"), Map.entry("sourceCount", "1"),
                    Map.entry("slotOrigin", slotBefore.origin().value().toString()),
                    Map.entry("slotSourceBefore", slotBefore.sources().getFirst().id().toString()),
                    Map.entry("slotAliasBefore", slotBefore.sources().getFirst().aliases().getFirst().id().toString()),
                    Map.entry("slotBeforeNativeIndex", Integer.toString(callbackBefore.nativeSlot())),
                    Map.entry("slotAfterNativeIndex", Integer.toString(callbackAfter.nativeSlot())),
                    Map.entry("slotManagedBefore", Integer.toString(callbackBefore.managedEntries())),
                    Map.entry("slotManagedAfter", Integer.toString(callbackAfter.managedEntries())),
                    Map.entry("slotReboundDiagnostic", ProvenanceDiagnostic.UNPROVEN_GRID_REBOUND.name()),
                    Map.entry("slotOldGenerationCurrent", "false"),
                    Map.entry("mountProjectionBefore", projectionBeforeId),
                    Map.entry("mountProjectionAfter", projectionAfterId),
                    Map.entry("mountSourceGenerationBefore", Long.toString(mountSourceGenerationBefore)),
                    Map.entry("mountSourceGenerationAfter", Long.toString(sourceGenerationAfter)),
                    Map.entry("mountGenerationBefore", Long.toString(mountGenerationBefore)),
                    Map.entry("mountGenerationAfter", Long.toString(mountGenerationAfter)),
                    Map.entry("providerRemovalsBefore", Integer.toString(removalsBefore)),
                    Map.entry("providerRemovalsAfterRemount", Integer.toString(removalsAfterRemount)),
                    Map.entry("providerRemovalsAfterStale", Integer.toString(removalsAfterStale)),
                    Map.entry("mountedAfterStale", "1"), Map.entry("oldVisibleAfterRemount", Long.toString(oldVisible)),
                    Map.entry("oldInsertAfterRemount", Long.toString(oldInserted)),
                    Map.entry("oldExtractAfterRemount", Long.toString(oldExtracted)),
                    Map.entry("newProjectionCurrent", "true"), Map.entry("newInsertAccepted", Long.toString(newInserted)),
                    Map.entry("newVisible", Long.toString(newVisible)),
                    Map.entry("newExtractAccepted", Long.toString(newExtracted)),
                    Map.entry("newRemaining", Long.toString(newRemaining))));
        }

        private static String identity(Object value) {
            return Integer.toUnsignedString(System.identityHashCode(value));
        }
    }
}
