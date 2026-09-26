package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.prioritylist.IPartitionList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.ae2.storage.NativeMountLedger;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.storage.provenance.ProvenanceDiagnostic;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.storage.SourceIndexFixtures;
import space.controlnet.ae2federation.test.storage.SourceIndexFixtures.CountingStorage;

/**
 * Source identity versus execution semantics of mounted AE2 wrappers. Every comparison runs the same operation against
 * the provider Grid's native {@code NetworkStorage} and against the Federation projection, and compares where the
 * resources actually land. Wrappers are AE2's own {@link MEInventoryHandler} (the Storage Bus and Drive handler) and
 * the plain forwarding {@link DelegatingMEInventory}.
 */
@PrefixGameTestTemplate(false)
public final class StorageAliasSemanticsGameTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey GOLD = AEItemKey.of(Items.GOLD_INGOT);
    private static final AEItemKey COPPER = AEItemKey.of(Items.COPPER_INGOT);
    private static final IActionSource SOURCE = IActionSource.empty();

    private StorageAliasSemanticsGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void storageAliasWrapperSemantics(GameTestHelper helper) {
        new Scenario(helper).run();
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final PolicyBridgeFixtures fixtures;
        private final SourceIndexFixtures.PriorityMountProvider provider = new SourceIndexFixtures.PriorityMountProvider();
        private final CountingStorage bare = new CountingStorage("B", 1_000);
        private final CountingStorage independent = new CountingStorage("C", 1_000);
        private final MEInventoryHandler filtered = new MEInventoryHandler(bare);
        private final CountingStorage forwarded = new CountingStorage("D", 1_000);
        private final DelegatingMEInventory transparent = new DelegatingMEInventory(forwarded);
        private final CountingStorage replacement = new CountingStorage("E", 1_000);
        private final CountingStorage shared = new CountingStorage("F", 1_000);
        private final MEInventoryHandler firstLimited = new MEInventoryHandler(shared);
        private final MEInventoryHandler secondLimited = new MEInventoryHandler(shared);
        private final SourceIndexFixtures.MultiHandleProvider duplicateMount =
                new SourceIndexFixtures.MultiHandleProvider(7, forwarded);
        private final Map<String, String> facts = new LinkedHashMap<>();
        private IManagedGridNode providerNode;
        private long rebuildsBefore;
        private int phase;

        private Scenario(GameTestHelper helper) {
            this.helper = helper;
            fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
            // Powers both Grids; the chests' cells sit at priority 0 below every inventory compared here.
            fixtures.installStorageCells();
            // W: native wrapper of B at priority 100 that refuses iron. B is also mounted directly at priority 0.
            // C: independent inventory at priority 50 that accepts iron.
            filtered.setWhitelist(IncludeExclude.BLACKLIST);
            filtered.setPartitionList(partition(IRON));
            provider.put(filtered, 100).put(bare, 0).put(independent, 50);
            forwarded.insert(COPPER, 5, Actionable.MODULATE, SOURCE);
            replacement.insert(GOLD, 3, Actionable.MODULATE, SOURCE);
        }

        private void run() {
            helper.succeedWhen(this::tick);
        }

        private void tick() {
            switch (phase) {
                case 0 -> setup();
                case 1 -> activate();
                case 2 -> wrapperWithMountedDelegate();
                case 3 -> wrapperAloneKeepsNativeSemantics();
                case 4 -> priorityRemount();
                case 5 -> transparentAliasAndStableQueries();
                case 6 -> dynamicDelegate();
                case 7 -> sharedUnderlyingWrappers();
                case 8 -> dynamicDelegateOntoMountedHandle();
                default -> throw new IllegalStateException("Unexpected alias phase");
            }
        }

        private void setup() {
            helper.assertTrue(fixtures.networksSettled(), "Waiting for identities");
            providerNode = fixtures.addOuterStorageProvider(provider);
            fixtures.placeFirstBridge();
            phase = 1;
            waitFor("Waiting for the Federation Domain");
        }

        private void activate() {
            helper.assertTrue(fixtures.firstBridgeReady(), "Waiting for the Federation Domain");
            var policies = PolicyService.get(helper.getLevel());
            if (policies.revision(key()).equals(PolicyRevision.NONE)) {
                policies.edit(new PolicyEdit(key(), PolicyRevision.NONE, PolicyRule.storageDefaults()));
            }
            mounts().reconcileAll();
            phase = 2;
            waitFor("Advancing to the wrapper comparison");
        }

        /** W wraps B, and B is mounted too: W's filter and priority must not be transferred onto bare B. */
        private void wrapperWithMountedDelegate() {
            var projection = mounts().projection(key());
            helper.assertTrue(projection != null || mounts().lastDiagnostic(key()) != null,
                    "Waiting for the relationship to mount or be diagnosed");
            var nativeLanding = nativeLanding(IRON);
            facts.put("nativeIronLanding", nativeLanding);
            if (projection != null) {
                var federationLanding = landing(projection, IRON);
                facts.put("federationIronLanding", federationLanding);
                helper.assertValueEqual(federationLanding, nativeLanding,
                        "Federation must land iron where native AE2 does, not in the bare delegate");
            }
            helper.assertValueEqual(nativeLanding, "C", "Native AE2 skips the filtering wrapper and uses C");
            helper.assertTrue(projection == null, "A filtering wrapper aliasing a mounted handle must fail closed");
            helper.assertTrue(ProvenanceDiagnostic.NON_TRANSPARENT_ALIAS == mounts().lastDiagnostic(key()),
                    "The unsafe alias carries an explicit diagnostic");
            facts.put("wrapperAndDelegateDiagnostic", "NON_TRANSPARENT_ALIAS");
            provider.remove(bare);
            IStorageProvider.requestUpdate(providerNode);
            phase = 3;
            waitFor("Waiting for the remount without bare B");
        }

        /** W alone: the source executes through W, so filter, access limits and priority stay native. */
        private void wrapperAloneKeepsNativeSemantics() {
            var projection = mounts().projection(key());
            helper.assertTrue(projection != null, "Waiting for the wrapper-only relationship");
            var domain = mounts().sourceDomain(key());
            helper.assertTrue(domain.sources().stream().anyMatch(source -> source.storage() == filtered)
                    && domain.sources().stream().noneMatch(source -> source.storage() == bare),
                    "The mounted wrapper, not its delegate, is the source");
            helper.assertValueEqual(landing(projection, IRON), nativeLanding(IRON), "Filtered iron lands like native");
            helper.assertValueEqual(landing(projection, GOLD), nativeLanding(GOLD), "Accepted gold lands like native");
            helper.assertValueEqual(landing(projection, GOLD), "B", "Gold enters B through the wrapper at priority 100");
            helper.assertValueEqual(projection.insert(IRON, 7, Actionable.SIMULATE, SOURCE),
                    nativeInventory().insert(IRON, 7, Actionable.SIMULATE, SOURCE), "Simulated insert equals native");
            bare.insert(GOLD, 2, Actionable.MODULATE, SOURCE);
            filtered.setAllowExtraction(false);
            helper.assertValueEqual(projection.extract(GOLD, 1, Actionable.MODULATE, SOURCE),
                    nativeInventory().extract(GOLD, 1, Actionable.MODULATE, SOURCE),
                    "A wrapper that forbids extraction forbids it through Federation too");
            helper.assertValueEqual(projection.extract(GOLD, 1, Actionable.SIMULATE, SOURCE), 0L,
                    "No extraction through the read-only wrapper");
            filtered.setAllowExtraction(true);
            helper.assertValueEqual(projection.extract(GOLD, 1, Actionable.SIMULATE, SOURCE),
                    nativeInventory().extract(GOLD, 1, Actionable.SIMULATE, SOURCE),
                    "Extraction through the wrapper equals native once allowed");
            filtered.setAllowInsertion(false);
            helper.assertValueEqual(landing(projection, GOLD), nativeLanding(GOLD),
                    "A wrapper that forbids insertion forbids it through Federation too");
            filtered.setAllowInsertion(true);
            // Dynamic filter change without a remount: now iron is allowed and gold is refused.
            filtered.setPartitionList(partition(GOLD));
            helper.assertTrue(mounts().projection(key()) == projection, "A filter change needs no remount");
            helper.assertValueEqual(landing(projection, IRON), nativeLanding(IRON), "Iron follows the new filter");
            helper.assertValueEqual(landing(projection, IRON), "B", "Iron now enters B through W at priority 100");
            helper.assertValueEqual(landing(projection, GOLD), nativeLanding(GOLD), "Gold follows the new filter");
            helper.assertTrue(sameListing(projection), "Listings equal native without double counting");
            facts.put("wrapperIsSource", "true");
            facts.put("filterAndAccessLimitsNative", "true");
            facts.put("dynamicFilterNative", "true");
            provider.put(filtered, 10);
            IStorageProvider.requestUpdate(providerNode);
            phase = 4;
            waitFor("Waiting for the priority remount");
        }

        /** W drops below C: the landing follows the native priority of the wrapper. */
        private void priorityRemount() {
            var projection = mounts().projection(key());
            helper.assertTrue(projection != null
                    && mounts().sourceDomain(key()).sources().stream()
                            .anyMatch(source -> source.storage() == filtered && source.priority() == 10),
                    "Waiting for the wrapper at its new native priority");
            helper.assertValueEqual(landing(projection, IRON), nativeLanding(IRON), "Iron lands like native");
            helper.assertValueEqual(landing(projection, IRON), "C", "Iron now enters C at priority 50");
            facts.put("priorityRemountLanding", "C");
            provider.put(transparent, 20).put(forwarded, 5);
            // The identical handle D is also mounted by a second, global provider.
            fixtures.outerGrid().getStorageService().addGlobalStorageProvider(duplicateMount);
            IStorageProvider.requestUpdate(providerNode);
            phase = 5;
            waitFor("Waiting for the transparent alias remount");
        }

        /** AE2's plain DelegatingMEInventory forwards everything: it and D are one source, counted once. */
        private void transparentAliasAndStableQueries() {
            var projection = mounts().projection(key());
            helper.assertTrue(projection != null, "Waiting for the transparent alias relationship");
            var domain = mounts().sourceDomain(key());
            var forwardedSources = domain.sources().stream()
                    .filter(source -> source.storage() == forwarded || source.storage() == transparent).toList();
            helper.assertValueEqual(forwardedSources.size(), 1,
                    "A transparent wrapper, its delegate and a duplicate mount of it share one source");
            helper.assertValueEqual(forwardedSources.getFirst().aliases().size(), 3,
                    "The source records all three native mounts as aliases");
            helper.assertTrue(forwardedSources.getFirst().storage() == forwarded
                    && forwardedSources.getFirst().priority() == 20, "Executed through D at the wrapper's priority");
            helper.assertValueEqual(projection.getAvailableStacks().get(COPPER), 5L,
                    "Transparent alias contents are counted once");
            var mountEvents = NativeMountLedger.mountEventCount();
            rebuildsBefore = mounts().sourceDiscoveryRebuilds();
            for (int read = 0; read < 5; read++) {
                projection.getAvailableStacks();
            }
            helper.assertValueEqual(NativeMountLedger.mountEventCount(), mountEvents,
                    "Stable queries do not replay native mount callbacks");
            helper.assertValueEqual(mounts().sourceDiscoveryRebuilds(), rebuildsBefore,
                    "Stable queries do not rebuild the source domain");
            var totalBefore = bare.total() + independent.total() + forwarded.total();
            var inserted = projection.insert(COPPER, 4, Actionable.MODULATE, SOURCE);
            var extracted = projection.extract(COPPER, 2, Actionable.MODULATE, SOURCE);
            helper.assertValueEqual(bare.total() + independent.total() + forwarded.total(),
                    totalBefore + inserted - extracted, "Operations conserve resources exactly");
            facts.put("transparentSources", "1");
            facts.put("duplicateMountAliases", "3");
            facts.put("stableQueryMountReplays", "0");
            SourceIndexFixtures.setDelegate(transparent, replacement);
            mounts().reconcileAll();
            phase = 6;
            waitFor("Changed the transparent wrapper's delegate without a remount");
        }

        /** The delegate link is part of the cached domain's validity; a changed link splits the source again. */
        private void dynamicDelegate() {
            var projection = mounts().projection(key());
            helper.assertTrue(projection != null, "Waiting for the relationship after the delegate change");
            var domain = mounts().sourceDomain(key());
            helper.assertTrue(domain.sources().stream().anyMatch(source -> source.storage() == transparent)
                    && domain.sources().stream().anyMatch(source -> source.storage() == forwarded),
                    "After the change the wrapper and D are independent sources");
            helper.assertTrue(mounts().sourceDiscoveryRebuilds() > rebuildsBefore, "The changed link forced a rebuild");
            helper.assertValueEqual(projection.getAvailableStacks().get(GOLD), 3L, "The new delegate's contents appear");
            facts.put("dynamicDelegateSplit", "true");
            provider.put(firstLimited, 30).put(secondLimited, 40);
            IStorageProvider.requestUpdate(providerNode);
            phase = 7;
            waitFor("Waiting for two wrappers sharing one inner inventory");
        }

        private void sharedUnderlyingWrappers() {
            helper.assertTrue(mounts().projection(key()) == null, "Waiting for the shared-underlying diagnosis");
            helper.assertTrue(ProvenanceDiagnostic.AMBIGUOUS_SHARED_DELEGATE == mounts().lastDiagnostic(key()),
                    "Two differently limited wrappers over one inventory cannot be deduplicated");
            facts.put("sharedUnderlyingDiagnostic", "AMBIGUOUS_SHARED_DELEGATE");
            provider.remove(secondLimited);
            IStorageProvider.requestUpdate(providerNode);
            phase = 8;
            waitFor("Waiting for the remaining wrapper");
        }

        private void dynamicDelegateOntoMountedHandle() {
            if (mounts().projection(key()) != null && facts.get("limitedRestored") == null) {
                facts.put("limitedRestored", "true");
                // Storage Bus retarget onto an inventory that is itself mounted.
                SourceIndexFixtures.setDelegate(firstLimited, independent);
                mounts().reconcileAll();
            }
            helper.assertTrue(facts.get("limitedRestored") != null, "Waiting for the restored relationship");
            helper.assertTrue(mounts().projection(key()) == null, "A retarget onto a mounted handle must fail closed");
            helper.assertTrue(ProvenanceDiagnostic.NON_TRANSPARENT_ALIAS == mounts().lastDiagnostic(key()),
                    "The dynamic unsafe alias carries an explicit diagnostic");
            facts.put("dynamicRetargetDiagnostic", "NON_TRANSPARENT_ALIAS");
            PolicyEvidence.write("storagealiaswrappersemantics", 35, facts);
            fixtures.close();
        }

        /** Inserts one unit through the provider Grid's native storage and names the inventory it reached. */
        private String nativeLanding(AEItemKey key) {
            return landing(nativeInventory(), key);
        }

        /** Inserts one unit through {@code storage}, names the inventory it reached and takes it back out of there. */
        private String landing(MEStorage storage, AEItemKey key) {
            var targets = Map.of("B", bare, "C", independent, "D", forwarded);
            var before = new LinkedHashMap<String, Long>();
            targets.forEach((name, target) -> before.put(name, target.amount(key)));
            var inserted = storage.insert(key, 1, Actionable.MODULATE, SOURCE);
            if (inserted == 0) {
                return "none";
            }
            for (var entry : targets.entrySet()) {
                if (entry.getValue().amount(key) > before.get(entry.getKey())) {
                    entry.getValue().extract(key, inserted, Actionable.MODULATE, SOURCE);
                    return entry.getKey();
                }
            }
            return "other";
        }

        private boolean sameListing(MEStorage projection) {
            var nativeList = nativeInventory().getAvailableStacks();
            var federationList = projection.getAvailableStacks();
            for (var entry : nativeList) {
                if (federationList.get(entry.getKey()) != entry.getLongValue()) {
                    return false;
                }
            }
            for (var entry : federationList) {
                if (nativeList.get(entry.getKey()) != entry.getLongValue()) {
                    return false;
                }
            }
            return true;
        }

        private MEStorage nativeInventory() {
            return fixtures.outerGrid().getStorageService().getInventory();
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

        private static IPartitionList partition(AEItemKey key) {
            var builder = IPartitionList.builder();
            builder.add(key);
            return builder.build();
        }
    }

}
