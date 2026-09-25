package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.me.storage.NetworkStorage;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.storage.mount.RelationshipStorageProvider;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomainRegistry;
import space.controlnet.ae2federation.storage.provenance.ProvenanceDiagnostic;
import space.controlnet.ae2federation.storage.provenance.ProvenanceException;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.storage.ProvenanceCallbackProviders;
import space.controlnet.ae2federation.test.storage.ProvenanceEvidence;
import space.controlnet.ae2federation.test.storage.ProvenanceRebindChecks;
import space.controlnet.ae2federation.test.storage.ProvenanceStorageFixture;

@PrefixGameTestTemplate(false)
public final class StorageProvenanceGameTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final IActionSource SOURCE = IActionSource.empty();

    private StorageProvenanceGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void provenanceMultiEntry(GameTestHelper helper) {
        var fixture = new ProvenanceStorageFixture(helper);
        var registry = new NativeSourceDomainRegistry();
        var aliases = new ProvenanceCallbackProviders.MutableAliases();
        var phase = new int[1];
        var providerNode = new appeng.api.networking.IManagedGridNode[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native source callback");
            if (phase[0] == 0) {
                providerNode[0] = fixture.addProvider(aliases);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for duplicate callback registration");
            }
            if (phase[0] == 1) {
                helper.assertTrue(fixture.providerReady(providerNode[0]), "Waiting for alias provider node");
                aliases.configureDuplicates(fixture.source());
                // Native remount: the alias entries only exist once AE2 really mounted them.
                appeng.api.storage.IStorageProvider.requestUpdate(providerNode[0]);
                phase[0] = 2;
            }
            var domain = registry.discover(fixture.grid());
            helper.assertValueEqual(domain.sources().size(), 1, "Aliases must resolve to one ExportSource");
            var export = domain.sources().getFirst();
            helper.assertValueEqual(export.priority(), 40, "Highest alias priority must win");
            helper.assertValueEqual(export.aliases().size(), 3, "Three callback entries must identify one source");
            helper.assertValueEqual(export.storage().insert(IRON, 11, Actionable.MODULATE, SOURCE), 11L,
                    "Native source remains operation authority");
            var aggregate = new NetworkStorage();
            aggregate.mount(export.priority(), export.storage());
            helper.assertValueEqual(aggregate.getAvailableStacks().get(IRON), 11L,
                    "Alias aggregation must not double quantity");
            var sourceCapacity = export.storage().insert(IRON, Long.MAX_VALUE, Actionable.SIMULATE, SOURCE);
            var aggregateCapacity = aggregate.insert(IRON, Long.MAX_VALUE, Actionable.SIMULATE, SOURCE);
            helper.assertValueEqual(aggregateCapacity, sourceCapacity, "Alias aggregation must not double capacity");
            ProvenanceEvidence.domain("provenancemultientry", "current", domain);
            PolicyEvidence.write("provenancemultientry", 10, Map.ofEntries(
                    Map.entry("originNetworkId", domain.origin().value().toString()),
                    Map.entry("exportSourceId", export.id().toString()),
                    Map.entry("generation", Long.toString(domain.generation().value())),
                    Map.entry("aliasIds", export.aliases().stream().map(alias -> alias.id().toString())
                            .collect(java.util.stream.Collectors.joining(","))),
                    Map.entry("nativeStorageIdentity", Integer.toUnsignedString(System.identityHashCode(export.storage()))),
                    Map.entry("aliasCount", Integer.toString(export.aliases().size())),
                    Map.entry("exportSourceCount", Integer.toString(domain.sources().size())),
                    Map.entry("selectedPriority", Integer.toString(export.priority())),
                    Map.entry("providerQuantity", "11"), Map.entry("visibleQuantity", "11"),
                    Map.entry("providerCapacity", Long.toString(sourceCapacity)),
                    Map.entry("visibleCapacity", Long.toString(aggregateCapacity)),
                    Map.entry("nativeAuthority", "true")));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void provenanceNativeRebind(GameTestHelper helper) {
        ProvenanceRebindChecks.run(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void provenanceExcludeImport(GameTestHelper helper) {
        var fixture = new ProvenanceStorageFixture(helper);
        var registry = new NativeSourceDomainRegistry();
        var installed = new boolean[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native source callback");
            if (!installed[0]) {
                fixture.addProvider(new RelationshipStorageProvider(
                        ProvenanceCallbackProviders.wrapper(fixture.source()), 90));
                installed[0] = true;
                helper.assertTrue(false, "Waiting for managed import registration");
            }
            var domain = registry.discover(fixture.grid());
            helper.assertValueEqual(domain.sources().size(), 1, "Managed import must not become an ExportSource");
            var export = domain.sources().getFirst();
            helper.assertValueEqual(export.aliases().size(), 1, "Only the native callback may identify the source");
            helper.assertTrue(export.priority() != 90, "Managed import priority must be excluded");
            helper.assertValueEqual(export.storage().insert(IRON, 5, Actionable.MODULATE, SOURCE), 5L,
                    "Native store remains operation authority");
            ProvenanceEvidence.domain("provenanceexcludeimport", "current", domain);
            PolicyEvidence.write("provenanceexcludeimport", 8, Map.ofEntries(
                    Map.entry("originNetworkId", domain.origin().value().toString()),
                    Map.entry("exportSourceId", export.id().toString()),
                    Map.entry("generation", Long.toString(domain.generation().value())),
                    Map.entry("aliasId", export.aliases().getFirst().id().toString()),
                    Map.entry("nativeStorageIdentity", Integer.toUnsignedString(System.identityHashCode(export.storage()))),
                    Map.entry("exportSourceCount", "1"), Map.entry("nativeAliasCount", "1"),
                    Map.entry("managedPriorityAccepted", "false"), Map.entry("nativeAccepted", "5"),
                    Map.entry("managedImportsExcluded", "true")));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void provenanceOpaqueBoundary(GameTestHelper helper) {
        var fixture = new ProvenanceStorageFixture(helper);
        var registry = new NativeSourceDomainRegistry();
        var aliases = new ProvenanceCallbackProviders.MutableAliases();
        var phase = new int[1];
        var initial = new space.controlnet.ae2federation.storage.provenance.NativeSourceDomain[1];
        var providerNode = new appeng.api.networking.IManagedGridNode[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native source callback");
            if (phase[0] == 0) {
                initial[0] = registry.discover(fixture.grid());
                providerNode[0] = fixture.addProvider(aliases);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for opaque callback registration");
            }
            if (phase[0] == 1) {
                helper.assertTrue(fixture.providerReady(providerNode[0]), "Waiting for opaque provider node");
                aliases.configureOpaque(fixture.source());
                // Native remount: AE2 mounts both the native handle and the third-party opaque wrapper.
                appeng.api.storage.IStorageProvider.requestUpdate(providerNode[0]);
                phase[0] = 2;
            }
            ProvenanceException rejected = null;
            try {
                registry.discover(fixture.grid());
            } catch (ProvenanceException exception) {
                rejected = exception;
            }
            helper.assertTrue(rejected != null, "Opaque external alias must reject the complete domain");
            helper.assertValueEqual(rejected.diagnostic(), ProvenanceDiagnostic.OPAQUE_EXTERNAL_ALIAS,
                    "Opaque rejection must carry a precise diagnostic");
            helper.assertTrue(!registry.isCurrent(initial[0]), "Rejected discovery must invalidate earlier sources");
            helper.assertValueEqual(initial[0].sources().getFirst().storage().getAvailableStacks().get(IRON), 0L,
                    "Rejection must not mutate native authority");
            ProvenanceEvidence.domain("provenanceopaqueboundary", "before", initial[0]);
            ProvenanceEvidence.rejection("provenanceopaqueboundary", initial[0], rejected.diagnostic());
            PolicyEvidence.write("provenanceopaqueboundary", 8, Map.ofEntries(
                    Map.entry("originNetworkId", initial[0].origin().value().toString()),
                    Map.entry("generationBefore", Long.toString(initial[0].generation().value())),
                    Map.entry("diagnostic", rejected.diagnostic().name()),
                    Map.entry("wholeRelationshipRejected", "true"), Map.entry("partialSourcesAccepted", "0"),
                    Map.entry("oldGenerationCurrent", "false"), Map.entry("nativeQuantity", "0"),
                    Map.entry("opaqueCompatibility", "unsupported-fail-closed")));
            fixture.close();
        });
    }
}
