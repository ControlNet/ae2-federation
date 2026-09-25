package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.ae2.storage.NativeStorageProvenance;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.storage.SourceIndexFixtures;

/**
 * Source-index cost and native storage extension compatibility. Every inventory here is mounted through AE2's public
 * storage API only; Federation observes AE2's real mount table.
 */
@PrefixGameTestTemplate(false)
public final class StorageSourceIndexGameTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final int WIDE_KEYS = 2_500;
    private static final int ENUMERATIONS = 5;
    private static final int OPERATIONS = 20;

    private StorageSourceIndexGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void storageSourceIndexScale(GameTestHelper helper) {
        var fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        fixtures.installStorageCells();
        var wide = new SourceIndexFixtures.CountingStorage("wide", Long.MAX_VALUE / 4)
                .preload(SourceIndexFixtures.distinctKeys(WIDE_KEYS), 3);
        var provider = new SourceIndexFixtures.MultiHandleProvider(5, wide);
        var state = new int[1];
        helper.succeedWhen(() -> {
            if (state[0] == 0) {
                helper.assertTrue(fixtures.networksSettled(), "Waiting for scale fixture identities");
                fixtures.addOuterStorageProvider(provider);
                fixtures.placeFirstBridge();
                state[0] = 1;
                helper.assertTrue(false, "Waiting for scale Federation Domain");
            }
            helper.assertTrue(fixtures.firstBridgeReady(), "Waiting for scale Federation Domain");
            var key = PolicyLifecycleGameTests.storageKey(fixtures);
            var policies = PolicyService.get(helper.getLevel());
            if (policies.revision(key).equals(PolicyRevision.NONE)) {
                policies.edit(new PolicyEdit(key, PolicyRevision.NONE, PolicyRule.storageDefaults()));
            }
            var mounts = StorageMountService.get(helper.getLevel());
            var projection = mounts.projection(key);
            helper.assertTrue(projection != null, "Scale relationship must mount one projection");
            var warm = projection.getAvailableStacks();
            helper.assertTrue(warm.size() >= WIDE_KEYS, "Waiting for the wide native source to be visible");

            var before = Counters.read(mounts);
            var visible = 0;
            for (var index = 0; index < ENUMERATIONS; index++) {
                visible = projection.getAvailableStacks().size();
            }
            var afterList = Counters.read(mounts);
            var consumerAggregate = fixtures.mainGrid().getStorageService().getInventory();
            var consumerVisible = consumerAggregate.getAvailableStacks().size();
            var afterConsumer = Counters.read(mounts);
            var paper = SourceIndexFixtures.distinctKeys(1).getFirst();
            for (var index = 0; index < OPERATIONS; index++) {
                helper.assertValueEqual(projection.insert(paper, 1, Actionable.MODULATE, SOURCE), 1L,
                        "Insertion must reach the native wide source");
                helper.assertValueEqual(projection.extract(paper, 1, Actionable.MODULATE, SOURCE), 1L,
                        "Extraction must reach the native wide source");
            }
            var afterOps = Counters.read(mounts);
            helper.assertValueEqual(wide.amount(paper), 3L, "Native quantity must be conserved by paired operations");

            var list = afterList.minus(before);
            var consumer = afterConsumer.minus(afterList);
            var ops = afterOps.minus(afterConsumer);
            helper.assertTrue(visible >= WIDE_KEYS, "Projection must list every distinct native key");
            helper.assertTrue(consumerVisible >= WIDE_KEYS, "Consumer aggregate must list every distinct native key");
            PolicyEvidence.trace("storagesourceindexscale", "measurement", "keys=" + visible
                    + " enumerations=" + ENUMERATIONS + " list=" + list + " consumerList=" + consumer
                    + " operations=" + (OPERATIONS * 2) + " ops=" + ops);
            helper.assertValueEqual(list.rebuilds(), 0L, "Stable enumeration must not rebuild source domains");
            helper.assertValueEqual(list.scans(), 0L, "Stable enumeration must not scan providers or Grid nodes");
            helper.assertValueEqual(list.replays(), 0L, "Stable enumeration must not replay mountInventories");
            helper.assertValueEqual(list.validations(), (long) ENUMERATIONS,
                    "Source validity must be evaluated exactly once per enumeration, not per key");
            helper.assertValueEqual(consumer.validations(), 1L,
                    "Native consumer aggregate listing must evaluate source validity once");
            helper.assertValueEqual(ops.rebuilds() + ops.scans() + ops.replays(), 0L,
                    "Stable insert/extract must not rebuild, scan or replay");
            PolicyEvidence.write("storagesourceindexscale", 12, facts(visible, list, consumer, ops));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void storageSourceIndexLifecycle(GameTestHelper helper) {
        new Lifecycle(helper).run();
    }

    private static Map<String, String> facts(int visible, Counters list, Counters consumer, Counters ops) {
        var facts = new LinkedHashMap<String, String>();
        facts.put("distinctKeys", Integer.toString(visible));
        facts.put("enumerations", Integer.toString(ENUMERATIONS));
        facts.put("listRebuilds", Long.toString(list.rebuilds()));
        facts.put("listScans", Long.toString(list.scans()));
        facts.put("listMountReplays", Long.toString(list.replays()));
        facts.put("listSourceValidations", Long.toString(list.validations()));
        facts.put("consumerListSourceValidations", Long.toString(consumer.validations()));
        facts.put("operations", Integer.toString(OPERATIONS * 2));
        facts.put("operationRebuilds", Long.toString(ops.rebuilds()));
        facts.put("operationScans", Long.toString(ops.scans()));
        facts.put("operationMountReplays", Long.toString(ops.replays()));
        facts.put("operationSourceValidations", Long.toString(ops.validations()));
        return facts;
    }

    record Counters(long rebuilds, long scans, long replays, long validations) {
        static Counters read(StorageMountService mounts) {
            return new Counters(mounts.sourceDiscoveryRebuilds(), mounts.sourceProviderScans(),
                    NativeStorageProvenance.mountReplayCount(), mounts.sourceValidationCount());
        }

        Counters minus(Counters other) {
            return new Counters(rebuilds - other.rebuilds, scans - other.scans, replays - other.replays,
                    validations - other.validations);
        }
    }

    /** Native mount lifecycle, invalidation and conservation checks on one provider Grid. */
    private static final class Lifecycle {
        private final GameTestHelper helper;
        private final PolicyBridgeFixtures fixtures;
        private final SourceIndexFixtures.CountingStorage first = new SourceIndexFixtures.CountingStorage("first", 64);
        private final SourceIndexFixtures.CountingStorage second = new SourceIndexFixtures.CountingStorage("second", 64);
        private final SourceIndexFixtures.CountingStorage third = new SourceIndexFixtures.CountingStorage("third", 64);
        private final SourceIndexFixtures.CountingStorage global = new SourceIndexFixtures.CountingStorage("global", 64);
        private final SourceIndexFixtures.MultiHandleProvider nodeProvider =
                new SourceIndexFixtures.MultiHandleProvider(5, first, second);
        private final SourceIndexFixtures.MultiHandleProvider globalProvider =
                new SourceIndexFixtures.MultiHandleProvider(7, global);
        private final Map<String, String> facts = new LinkedHashMap<>();
        private IManagedGridNode providerNode;
        private MEStorage held;
        private MEStorage opaque;
        private long rebuildsBefore;
        private int phase;

        private Lifecycle(GameTestHelper helper) {
            this.helper = helper;
            fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
            fixtures.installStorageCells();
            first.insert(IRON, 4, Actionable.MODULATE, SOURCE);
            second.insert(AEItemKey.of(Items.GOLD_INGOT), 6, Actionable.MODULATE, SOURCE);
            global.insert(AEItemKey.of(Items.COPPER_INGOT), 8, Actionable.MODULATE, SOURCE);
        }

        private void run() {
            helper.succeedWhen(this::tick);
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

        private void tick() {
            switch (phase) {
                case 0 -> setup();
                case 1 -> activate();
                case 2 -> verifyMultiHandleGlobalAndConservation();
                case 3 -> verifyPriorityRemount();
                case 4 -> verifyDynamicMountAndUnmount();
                case 5 -> verifyGlobalRemoval();
                case 6 -> verifyOpaqueDiagnosed();
                case 7 -> verifyOpaqueRemovedAndRevoke();
                case 8 -> verifyDisconnect();
                default -> throw new IllegalStateException("Unexpected lifecycle phase");
            }
        }

        private void setup() {
            helper.assertTrue(fixtures.networksSettled(), "Waiting for lifecycle identities");
            providerNode = fixtures.addOuterStorageProvider(nodeProvider);
            fixtures.outerGrid().getStorageService().addGlobalStorageProvider(globalProvider);
            fixtures.placeFirstBridge();
            phase = 1;
            waitFor("Waiting for lifecycle Federation Domain");
        }

        private void activate() {
            helper.assertTrue(fixtures.firstBridgeReady(), "Waiting for lifecycle Federation Domain");
            var policies = PolicyService.get(helper.getLevel());
            if (policies.revision(key()).equals(PolicyRevision.NONE)) {
                policies.edit(new PolicyEdit(key(), PolicyRevision.NONE, PolicyRule.storageDefaults()));
            }
            held = mounts().projection(key());
            helper.assertTrue(held != null, "Lifecycle relationship must mount");
            phase = 2;
            waitFor("Advancing to multi-handle verification");
        }

        private void verifyMultiHandleGlobalAndConservation() {
            var domain = mounts().sourceDomain(key());
            // chest cell + two independent handles of one node provider + one global provider handle
            helper.assertValueEqual(domain.sources().size(), 4,
                    "Independent handles of one provider and global provider handles must each be one source");
            var consumerDomain = new space.controlnet.ae2federation.storage.provenance.NativeSourceDomainRegistry()
                    .discover(fixtures.mainGrid());
            helper.assertTrue(consumerDomain.sources().stream().noneMatch(source -> source.storage() == held
                            || source.storage() instanceof space.controlnet.ae2federation.storage.provenance
                                    .FederationManagedStorage),
                    "Federation's own projection mounted on the consumer must never become a source");
            helper.assertValueEqual(consumerDomain.sources().size(), 1,
                    "Consumer domain must hold only its native chest (projection and crafting hook excluded)");

            var nativeInventory = fixtures.outerGrid().getStorageService().getInventory();
            var nativeList = nativeInventory.getAvailableStacks();
            var federationList = held.getAvailableStacks();
            helper.assertTrue(sameCounter(nativeList, federationList),
                    "Federation listing must equal the provider's native listing (no double count, no omission)");
            var nativeInsert = nativeInventory.insert(IRON, 500, Actionable.SIMULATE, SOURCE);
            var federationInsert = held.insert(IRON, 500, Actionable.SIMULATE, SOURCE);
            helper.assertValueEqual(federationInsert, nativeInsert, "Simulated insert capacity must equal native");
            var nativeExtract = nativeInventory.extract(IRON, 500, Actionable.SIMULATE, SOURCE);
            var federationExtract = held.extract(IRON, 500, Actionable.SIMULATE, SOURCE);
            helper.assertValueEqual(federationExtract, nativeExtract, "Simulated extract must equal native");
            var totalBefore = total(nativeList);
            var inserted = held.insert(IRON, 30, Actionable.MODULATE, SOURCE);
            var extracted = held.extract(IRON, 12, Actionable.MODULATE, SOURCE);
            var totalAfter = total(nativeInventory.getAvailableStacks());
            helper.assertValueEqual(totalAfter, totalBefore + inserted - extracted,
                    "Federation operations must conserve native resource totals exactly");
            helper.assertTrue(sameCounter(nativeInventory.getAvailableStacks(), held.getAvailableStacks()),
                    "Listings must stay identical after real operations");
            facts.put("sourcesMultiHandleAndGlobal", Integer.toString(domain.sources().size()));
            facts.put("consumerSourcesExcludingProjection", Integer.toString(consumerDomain.sources().size()));
            facts.put("nativeSimulateInsert", Long.toString(nativeInsert));
            facts.put("federationSimulateInsert", Long.toString(federationInsert));
            facts.put("nativeSimulateExtract", Long.toString(nativeExtract));
            facts.put("federationSimulateExtract", Long.toString(federationExtract));
            facts.put("conservedTotalBefore", Long.toString(totalBefore));
            facts.put("conservedTotalAfter", Long.toString(totalAfter));

            rebuildsBefore = mounts().sourceDiscoveryRebuilds();
            nodeProvider.setPriority(50);
            IStorageProvider.requestUpdate(providerNode);
            helper.assertValueEqual(held.insert(IRON, 1, Actionable.MODULATE, SOURCE), 0L,
                    "A native priority remount must stop the held projection immediately");
            facts.put("staleAfterPriorityInsert", "0");
            phase = 3;
            waitFor("Waiting for native remount reconcile");
        }

        private void verifyPriorityRemount() {
            var current = mounts().projection(key());
            helper.assertTrue(current != null && current != held, "Priority remount must install a new projection");
            helper.assertValueEqual(mounts().mountedPriority(key()), 50, "New projection must carry native priority");
            helper.assertTrue(mounts().sourceDiscoveryRebuilds() > rebuildsBefore,
                    "Priority remount must rebuild the source domain");
            facts.put("priorityAfterRemount", "50");
            held = current;
            nodeProvider.remove(second);
            nodeProvider.add(third);
            third.insert(AEItemKey.of(Items.DIAMOND), 2, Actionable.MODULATE, SOURCE);
            IStorageProvider.requestUpdate(providerNode);
            helper.assertValueEqual(held.extract(AEItemKey.of(Items.GOLD_INGOT), 1, Actionable.MODULATE, SOURCE), 0L,
                    "Unmounted native handle must not be reachable through the held projection");
            phase = 4;
            waitFor("Waiting for dynamic mount reconcile");
        }

        private void verifyDynamicMountAndUnmount() {
            var current = mounts().projection(key());
            helper.assertTrue(current != null && current != held, "Dynamic remount must install a new projection");
            var listed = current.getAvailableStacks();
            helper.assertValueEqual(listed.get(AEItemKey.of(Items.GOLD_INGOT)), 0L, "Unmounted handle must vanish");
            helper.assertValueEqual(listed.get(AEItemKey.of(Items.DIAMOND)), 2L, "Newly mounted handle must appear");
            helper.assertValueEqual(second.amount(AEItemKey.of(Items.GOLD_INGOT)), 6L,
                    "Unmounting must not mutate the native inventory");
            facts.put("unmountedVisible", "0");
            facts.put("mountedVisible", "2");
            held = current;
            fixtures.outerGrid().getStorageService().removeGlobalStorageProvider(globalProvider);
            helper.assertValueEqual(held.extract(AEItemKey.of(Items.COPPER_INGOT), 1, Actionable.MODULATE, SOURCE), 0L,
                    "Removed global provider must stop real extraction immediately");
            phase = 5;
            waitFor("Waiting for global provider removal reconcile");
        }

        private void verifyGlobalRemoval() {
            var current = mounts().projection(key());
            helper.assertTrue(current != null && current != held, "Global removal must install a new projection");
            helper.assertValueEqual(current.getAvailableStacks().get(AEItemKey.of(Items.COPPER_INGOT)), 0L,
                    "Removed global provider source must vanish");
            helper.assertValueEqual(global.amount(AEItemKey.of(Items.COPPER_INGOT)), 8L,
                    "Global provider removal must not mutate its inventory");
            facts.put("globalRemovedVisible", "0");
            held = current;
            opaque = new SourceIndexFixtures.OpaqueWrapper(first);
            nodeProvider.add(opaque);
            IStorageProvider.requestUpdate(providerNode);
            phase = 6;
            waitFor("Waiting for opaque alias reconcile");
        }

        private void verifyOpaqueDiagnosed() {
            helper.assertTrue(mounts().projection(key()) == null, "Unprovable opaque alias must fail closed");
            helper.assertValueEqual(mounts().lastDiagnostic(key()),
                    space.controlnet.ae2federation.storage.provenance.ProvenanceDiagnostic.OPAQUE_EXTERNAL_ALIAS,
                    "Opaque alias must carry an explicit diagnostic");
            helper.assertValueEqual(held.insert(IRON, 1, Actionable.MODULATE, SOURCE), 0L,
                    "Held projection must not operate while the domain is diagnosed");
            facts.put("opaqueDiagnostic", "OPAQUE_EXTERNAL_ALIAS");
            nodeProvider.remove(opaque);
            IStorageProvider.requestUpdate(providerNode);
            phase = 7;
            waitFor("Waiting for opaque removal reconcile");
        }

        private void verifyOpaqueRemovedAndRevoke() {
            held = mounts().projection(key());
            helper.assertTrue(held != null, "Removing the opaque alias must restore the relationship");
            var ironBefore = first.amount(IRON);
            var policies = PolicyService.get(helper.getLevel());
            policies.edit(new PolicyEdit(key(), policies.revision(key()),
                    PolicyRule.storageDefaults().withEnabled(false)));
            helper.assertValueEqual(held.insert(IRON, 1, Actionable.MODULATE, SOURCE), 0L,
                    "Revocation must stop real insertion immediately");
            helper.assertValueEqual(held.extract(IRON, 1, Actionable.MODULATE, SOURCE), 0L,
                    "Revocation must stop real extraction immediately");
            helper.assertValueEqual(held.getAvailableStacks().size(), 0, "Revocation must hide listings");
            helper.assertValueEqual(first.amount(IRON), ironBefore, "Revocation must not mutate native storage");
            policies.edit(new PolicyEdit(key(), policies.revision(key()), PolicyRule.storageDefaults()));
            held = mounts().projection(key());
            helper.assertTrue(held != null, "Re-enabled Policy must remount");
            facts.put("revokedInsert", "0");
            facts.put("revokedExtract", "0");
            phase = 8;
            waitFor("Advancing to disconnect verification");
        }

        private void verifyDisconnect() {
            fixtures.removeFirstBridge();
            helper.assertValueEqual(held.insert(IRON, 1, Actionable.MODULATE, SOURCE), 0L,
                    "Disconnect must stop real insertion immediately");
            helper.assertValueEqual(held.extract(IRON, 1, Actionable.MODULATE, SOURCE), 0L,
                    "Disconnect must stop real extraction immediately");
            facts.put("disconnectedInsert", "0");
            facts.put("disconnectedExtract", "0");
            PolicyEvidence.write("storagesourceindexlifecycle", 30, facts);
            fixtures.close();
        }

        private static boolean sameCounter(KeyCounter left, KeyCounter right) {
            if (left.size() != right.size()) {
                return false;
            }
            for (var entry : left) {
                if (right.get(entry.getKey()) != entry.getLongValue()) {
                    return false;
                }
            }
            return true;
        }

        private static long total(KeyCounter counter) {
            var total = 0L;
            for (var entry : counter) {
                total += entry.getLongValue();
            }
            return total;
        }
    }
}
