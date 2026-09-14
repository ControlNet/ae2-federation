package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.ae2.storage.NativeStorageProvenance;
import space.controlnet.ae2federation.ae2.storage.StorageProvenanceException;
import space.controlnet.ae2federation.test.storage.StorageProofFixtures;

@PrefixGameTestTemplate(false)
public final class StorageProofGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageProofGameTests.class);
    private static final String STRUCTURE = "ae2federation_test:harness_native_smoke";

    private StorageProofGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void storageProofNativeProjection(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            var aggregate = fixture.aggregate();
            var provenance = new NativeStorageProvenance();
            var nativeProvider = fixture.nativeProvider();
            var source = provenance.qualify(fixture.nativeNode()).getFirst().storage();
            var projectionProvider = provenance.createProvider();
            var projection = projectionProvider.mountProjection(aggregate, 11);
            var destination = fixture.newAggregate();
            projectionProvider.mountInventories((storage, priority) -> destination.mount(priority, storage));
            var sources = provenance.sources(List.of(nativeProvider, projectionProvider));
            helper.assertValueEqual(sources.size(), 1, "Federation projection must be excluded from source export");
            source = sources.getFirst().storage();
            helper.assertTrue(source != aggregate && source != projection, "Export must be the actual native store");
            source.getAvailableStacks();
            var key = AEItemKey.of(Items.IRON_INGOT);
            helper.assertValueEqual(source.insert(key, 3, Actionable.MODULATE, IActionSource.empty()), 3L,
                    "Native source handle must remain operation authority");
            helper.assertValueEqual(source.extract(key, 3, Actionable.MODULATE, IActionSource.empty()), 3L,
                    "Native source extraction must reconcile insertion");
            writeEvidence("storageproofnativeprojection", 6, Map.of(
                    "aggregate", identity(aggregate), "nativeSource", identity(source),
                    "projection", identity(projection), "sourceCount", "1", "projectionExcluded", "true",
                    "sourceIsNativeStore", "true", "inserted", "3", "extracted", "3"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void storageProofFourFabricDiamond(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            var provenance = new NativeStorageProvenance();
            var nativeSource = provenance.qualify(fixture.nativeNode()).getFirst().storage();
            var destination = fixture.newAggregate();
            var facts = new LinkedHashMap<String, String>();
            var providers = new java.util.ArrayList<appeng.api.storage.IStorageProvider>();
            var priorities = List.of(40, 30, 20, 10);
            var aliases = new java.util.ArrayList<MEStorage>();
            for (var index = 0; index < StorageProofFixtures.diamondRoutes().size(); index++) {
                var route = StorageProofFixtures.diamondRoutes().get(index);
                var provider = provenance.createProvider();
                var alias = provider.mountRoute(route, nativeSource, priorities.get(index));
                provider.mountInventories((storage, priority) -> destination.mount(priority, storage));
                providers.add(provider);
                aliases.add(alias);
                facts.put("route." + route.substring(0, 8), identity(alias));
                facts.put("provider." + route.substring(0, 8), identity(provider));
            }
            var sources = provenance.sources(providers);
            helper.assertValueEqual(StorageProofFixtures.diamondRoutes().size(), 4, "Diamond must use four Fabrics");
            helper.assertValueEqual(sources.size(), 1, "Diamond paths must list one physical source");
            helper.assertTrue(sources.getFirst().storage() == nativeSource, "Diamond must preserve native source identity");
            helper.assertValueEqual(sources.getFirst().priority(), 40, "Diamond must preserve highest native mount priority");
            nativeSource.getAvailableStacks();
            facts.put("destinationAggregate", identity(destination));
            facts.put("nativeSource", identity(nativeSource));
            facts.put("routeCount", "4");
            facts.put("sourceCount", "1");
            facts.put("stableIdentityDedup", "true");
            facts.put("sourceIsNativeStore", "true");
            facts.put("selectedPriority", Integer.toString(sources.getFirst().priority()));
            facts.put("routePriorities", "40,30,20,10");
            writeEvidence("storageprooffourfabricdiamond", 6, facts);
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void storageProofRejectLoop(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            var left = fixture.newAggregate();
            var right = fixture.newAggregate();
            left.mount(0, right);
            right.mount(0, left);
            var provenance = new NativeStorageProvenance();
            var provider = fixture.unclassifiedProvider(right, 0);
            var diagnostic = reject(provenance, List.of(provider));
            helper.assertTrue(diagnostic == StorageProvenanceException.Diagnostic.COMPLETE_AGGREGATE_MOUNT,
                    "Mutual aggregate loop must fail closed");
            helper.assertTrue(left.getAvailableStacks().isEmpty(), "Native aggregate recursion guard must terminate listing");
            writeEvidence("storageproofrejectloop", 4, Map.of(
                    "leftAggregate", identity(left), "rightAggregate", identity(right), "loopAttempted", "true",
                    "loopAccepted", "false", "diagnostic", diagnostic.name(), "nativeListingTerminated", "true"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void storageProofOpaqueAlias(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            var provenance = new NativeStorageProvenance();
            var nativeSource = provenance.qualify(fixture.nativeNode()).getFirst().storage();
            var opaque = new appeng.api.storage.MEStorage() {
                @Override
                public long insert(appeng.api.stacks.AEKey what, long amount, Actionable mode, IActionSource source) {
                    return nativeSource.insert(what, amount, mode, source);
                }

                @Override
                public long extract(appeng.api.stacks.AEKey what, long amount, Actionable mode, IActionSource source) {
                    return nativeSource.extract(what, amount, mode, source);
                }

                @Override
                public void getAvailableStacks(appeng.api.stacks.KeyCounter output) {
                    nativeSource.getAvailableStacks(output);
                }

                @Override
                public net.minecraft.network.chat.Component getDescription() {
                    return net.minecraft.network.chat.Component.literal("Third-party opaque storage alias");
                }
            };
            var provider = fixture.unclassifiedProvider(opaque, 0);
            var aggregate = fixture.newAggregate();
            provider.mountInventories((storage, priority) -> aggregate.mount(priority, storage));
            var diagnostic = reject(provenance, List.of(provider));
            helper.assertTrue(diagnostic == StorageProvenanceException.Diagnostic.OPAQUE_ALIAS,
                    "Opaque physical alias must fail closed");
            writeEvidence("storageproofopaquealias", 4, Map.of(
                    "aggregate", identity(aggregate), "alias", identity(opaque), "nativeSource", identity(nativeSource),
                    "opaqueAliasAccepted", "false", "diagnostic", diagnostic.name(), "partialSources", "0"));
        });
    }

    private static StorageProvenanceException.Diagnostic reject(NativeStorageProvenance provenance,
            Iterable<? extends appeng.api.storage.IStorageProvider> providers) {
        try {
            provenance.sources(providers);
            throw new AssertionError("Unsupported provenance boundary was accepted");
        } catch (StorageProvenanceException exception) {
            return exception.diagnostic();
        }
    }

    private static void runFixture(GameTestHelper helper, java.util.function.Consumer<StorageProofFixtures> assertions) {
        var fixture = new StorageProofFixtures(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for powered native storage Grid");
            assertions.accept(fixture);
        });
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    private static void writeEvidence(String testId, int assertions, Map<String, String> facts) {
        var ordered = new TreeMap<>(facts);
        ordered.forEach((name, value) -> LOGGER.info("AE2F_STORAGE_TRACE testId={} fact={} value={}", testId, name, value));
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
        properties.setProperty("inserted", facts.getOrDefault("inserted", "0"));
        properties.setProperty("extracted", facts.getOrDefault("extracted", "0"));
        properties.setProperty("elapsedNanos", "0");
        ordered.forEach(properties::setProperty);
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation native Storage provenance evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Storage provenance evidence to " + path, exception);
        }
    }
}
