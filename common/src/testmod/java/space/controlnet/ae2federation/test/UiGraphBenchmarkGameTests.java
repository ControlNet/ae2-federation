package space.controlnet.ae2federation.test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.client.fabric.FabricGraphLayer;
import space.controlnet.ae2federation.client.fabric.FabricGraphLayoutCache;
import space.controlnet.ae2federation.client.fabric.FabricGraphNodeKind;
import space.controlnet.ae2federation.client.fabric.FabricGraphSnapshot;
import space.controlnet.ae2federation.client.policy.FabricGraphProjection;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;
import space.controlnet.ae2federation.test.ui.UiGraphBenchmarkEvidence;
import space.controlnet.ae2federation.test.ui.UiGraphBenchmarkProfile;

@PrefixGameTestTemplate(false)
public final class UiGraphBenchmarkGameTests {
    private UiGraphBenchmarkGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void uiGraphBenchmarkSmall(GameTestHelper helper) {
        var profile = UiGraphBenchmarkProfile.load();
        helper.assertTrue(!Boolean.getBoolean("ae2federation.benchmarkEmpty") && profile.samples() > 0,
                "UI graph benchmark must request nonzero work");
        ObservationRuntimeEvidence.useHeadlessGameTestTransport();
        var scene = new RealObservationScene(helper, true);
        var state = new BenchmarkState();
        helper.succeedWhen(() -> {
            helper.assertTrue(scene.ready(), "Waiting for authentic Fabric, Provider, and Endpoint topology: "
                    + scene.status());
            if (state.result == null) {
                helper.assertTrue(scene.openFirstMenu() && scene.openSecondMenu(),
                        "Two production Fabric menus must open before the closed-GUI observation");
                state.result = measure(profile,
                        () -> FabricGraphProjection.snapshot(helper.getLevel(), scene.firstScope(),
                                ProviderObservationRegistry.entries(helper.getLevel()).stream().findFirst()));
                state.before = scene.nativeTickerInvocations();
                state.ownerIdentity = scene.nativeProgressOwnerIdentity();
                state.delegateCount = scene.nativeTickerDelegateCount();
                scene.closeFirstMenu();
                scene.closeSecondMenu();
                helper.assertTrue(scene.player().containerMenu == scene.player().inventoryMenu
                                && scene.secondPlayer().containerMenu == scene.secondPlayer().inventoryMenu,
                        "Both production Fabric menus must be closed before native progress observation");
                helper.assertTrue(scene.wakeNativeTicker(), "AE2 tick manager must own and wake the native Provider ticker");
                state.closedAt = helper.getTick();
                helper.assertTrue(false, "Waiting for native AE2 ticker progress with both menus closed");
            }
            var closedTicks = helper.getTick() - state.closedAt;
            var after = scene.nativeTickerInvocations();
            if (closedTicks < profile.closedGuiTicks() || total(after) <= total(state.before)) {
                helper.assertTrue(false, "Waiting for independently observed native Provider ticker progress");
            }
            state.result.put("closedGuiTicks", Long.toString(closedTicks));
            state.result.put("productionMenusOpened", "2");
            state.result.put("productionMenusClosed", "2");
            state.result.put("nativeProgressOwnerIdentity", Integer.toUnsignedString(state.ownerIdentity));
            state.result.put("nativeProgressDelegateCount", Integer.toString(state.delegateCount));
            state.result.put("nativeProgressBefore", counters(state.before));
            state.result.put("nativeProgressAfter", counters(after));
            state.result.put("nativeProgressDelta", Long.toString(total(after) - total(state.before)));
            UiGraphBenchmarkEvidence.write(profile, state.result);
            scene.close();
        });
    }

    private static LinkedHashMap<String, String> measure(UiGraphBenchmarkProfile profile,
            Supplier<FabricGraphSnapshot> productionProjection) {
        var authoritative = productionProjection.get();
        requireProductionProjection(authoritative);
        var initial = derive(profile, authoritative, 7, 11, "online");
        var cache = new FabricGraphLayoutCache();
        var projectionNanos = new ArrayList<Long>();
        var layoutNanos = new ArrayList<Long>();
        var layout = cache.layout(initial);
        var reuseCount = 0;
        for (var sample = 0; sample < profile.samples(); sample++) {
            var projectionStart = System.nanoTime();
            var observed = productionProjection.get();
            requireSameAuthority(authoritative, observed);
            var update = FabricGraphSnapshot.decode(derive(profile, observed, 7, 12 + sample, "busy").encode());
            projectionNanos.add(System.nanoTime() - projectionStart);
            var layoutStart = System.nanoTime();
            if (cache.layout(update) == layout) {
                reuseCount++;
            }
            layoutNanos.add(System.nanoTime() - layoutStart);
        }
        var rebuilt = cache.layout(derive(profile, authoritative, 8, 100, "online"));
        var encoded = initial.encode().getBytes(StandardCharsets.UTF_8);
        var facts = new LinkedHashMap<String, String>();
        facts.put("elapsedNanos", Long.toString(projectionNanos.stream().mapToLong(Long::longValue).sum()
                + layoutNanos.stream().mapToLong(Long::longValue).sum()));
        facts.put("topologyRevision", Long.toString(initial.topologyRevision()));
        facts.put("dataRevision", Long.toString(initial.dataRevision()));
        facts.put("nodeCount", Integer.toString(initial.nodes().size()));
        facts.put("physicalEdgeCount", Long.toString(initial.edges().stream()
                .filter(edge -> edge.layer() == FabricGraphLayer.PHYSICAL).count()));
        facts.put("capabilityEdgeCount", Long.toString(initial.edges().stream()
                .filter(edge -> edge.layer() == FabricGraphLayer.CAPABILITY).count()));
        facts.put("patternCount", Integer.toString(initial.patterns().size()));
        facts.put("encodedPayloadBytes", Integer.toString(encoded.length));
        facts.put("structuralSignatureSha256", digest(initial.structuralSignature()));
        facts.put("layoutSignatureSha256", digest(rebuilt.nodes().toString() + rebuilt.edges()));
        facts.put("layoutNodeCount", Integer.toString(layout.nodes().size()));
        facts.put("layoutEdgeCount", Integer.toString(layout.edges().size()));
        facts.put("layoutBuildCount", "2");
        facts.put("layoutReuseCount", Integer.toString(reuseCount));
        facts.put("dataOnlyLayoutReused", Boolean.toString(reuseCount == profile.samples()));
        facts.put("topologyLayoutRebuilt", Boolean.toString(rebuilt != layout));
        facts.put("maxProjectionNanos", Long.toString(projectionNanos.stream().mapToLong(Long::longValue).max().orElseThrow()));
        facts.put("maxLayoutNanos", Long.toString(layoutNanos.stream().mapToLong(Long::longValue).max().orElseThrow()));
        facts.put("maxRetainedGraphEntries", Integer.toString(initial.nodes().size() + initial.edges().size()
                + initial.patterns().size()));
        facts.put("productionProjectionAuthority", "true");
        facts.put("productionProjectionSamples", Integer.toString(profile.samples() + 1));
        facts.put("productionProjectionNodeCount", Integer.toString(authoritative.nodes().size()));
        facts.put("productionProjectionEdgeCount", Integer.toString(authoritative.edges().size()));
        facts.put("productionProjectionPatternCount", Integer.toString(authoritative.patterns().size()));
        facts.put("productionProjectionSha256", digest(authoritative.encode()));
        facts.put("productionProjectionPayloadBase64", java.util.Base64.getEncoder().encodeToString(
                authoritative.encode().getBytes(StandardCharsets.UTF_8)));
        facts.put("workloadDerivation", "bounded-production-replication-v1");
        return facts;
    }

    private static FabricGraphSnapshot derive(UiGraphBenchmarkProfile profile, FabricGraphSnapshot source,
            long topologyRevision, long dataRevision, String providerStatus) {
        var nodes = new ArrayList<FabricGraphSnapshot.Node>();
        var sourceMembers = nodes(source, FabricGraphNodeKind.MEMBER);
        var sourceProviders = nodes(source, FabricGraphNodeKind.PROVIDER);
        var sourceEndpoints = nodes(source, FabricGraphNodeKind.ENDPOINT);
        for (var index = 0; index < profile.members(); index++) {
            nodes.add(derivedNode("member", index, sourceMembers.get(index % sourceMembers.size()), "online"));
        }
        for (var index = 0; index < profile.providers(); index++) {
            nodes.add(derivedNode("provider", index, sourceProviders.get(index % sourceProviders.size()), providerStatus));
        }
        for (var index = 0; index < profile.endpoints(); index++) {
            nodes.add(derivedNode("endpoint", index, sourceEndpoints.get(index % sourceEndpoints.size()),
                    sourceEndpoints.get(index % sourceEndpoints.size()).status()));
        }
        var edges = new ArrayList<FabricGraphSnapshot.Edge>();
        var sourcePhysical = edges(source, FabricGraphLayer.PHYSICAL);
        var sourceCapability = edges(source, FabricGraphLayer.CAPABILITY);
        for (var index = 0; index < profile.physicalEdges(); index++) {
            var target = index < profile.providers() ? "provider-" + index : "endpoint-" + (index - profile.providers());
            var authority = sourcePhysical.get(index % sourcePhysical.size());
            edges.add(new FabricGraphSnapshot.Edge("member-" + (index % profile.members()), target,
                    FabricGraphLayer.PHYSICAL, authority.status()));
        }
        for (var index = 0; index < profile.capabilityEdges(); index++) {
            var authority = sourceCapability.get(index % sourceCapability.size());
            edges.add(new FabricGraphSnapshot.Edge("provider-" + index, "endpoint-" + index,
                    FabricGraphLayer.CAPABILITY, authority.status()));
        }
        var patterns = new ArrayList<String>();
        for (var index = 0; index < profile.patterns(); index++) {
            patterns.add(source.patterns().get(index % source.patterns().size()) + " | replica=" + index);
        }
        return new FabricGraphSnapshot(topologyRevision, dataRevision, nodes, edges, patterns);
    }

    private static FabricGraphSnapshot.Node derivedNode(String prefix, int index, FabricGraphSnapshot.Node authority,
            String status) {
        return new FabricGraphSnapshot.Node(prefix + "-" + index, authority.kind(), status);
    }

    private static List<FabricGraphSnapshot.Node> nodes(FabricGraphSnapshot source, FabricGraphNodeKind kind) {
        return source.nodes().stream().filter(node -> node.kind() == kind).toList();
    }

    private static List<FabricGraphSnapshot.Edge> edges(FabricGraphSnapshot source, FabricGraphLayer layer) {
        return source.edges().stream().filter(edge -> edge.layer() == layer).toList();
    }

    private static void requireProductionProjection(FabricGraphSnapshot snapshot) {
        if (nodes(snapshot, FabricGraphNodeKind.MEMBER).isEmpty()
                || nodes(snapshot, FabricGraphNodeKind.PROVIDER).isEmpty()
                || nodes(snapshot, FabricGraphNodeKind.ENDPOINT).isEmpty()
                || edges(snapshot, FabricGraphLayer.PHYSICAL).isEmpty()
                || edges(snapshot, FabricGraphLayer.CAPABILITY).isEmpty()
                || snapshot.patterns().isEmpty()) {
            throw new IllegalStateException("Production graph projection lacks required runtime topology");
        }
    }

    private static void requireSameAuthority(FabricGraphSnapshot expected, FabricGraphSnapshot observed) {
        if (!expected.structuralSignature().equals(observed.structuralSignature())
                || !expected.patterns().equals(observed.patterns())) {
            throw new IllegalStateException("Production graph projection changed during one benchmark capture");
        }
    }

    private static long total(List<Long> counters) {
        return counters.stream().mapToLong(Long::longValue).sum();
    }

    private static String counters(List<Long> counters) {
        return counters.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private static String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class BenchmarkState {
        private LinkedHashMap<String, String> result;
        private List<Long> before;
        private long closedAt;
        private int ownerIdentity;
        private int delegateCount;
    }
}
