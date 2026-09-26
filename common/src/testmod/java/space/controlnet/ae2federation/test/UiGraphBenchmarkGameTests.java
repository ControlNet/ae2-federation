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
import space.controlnet.ae2federation.client.domain.FederationDomainGraphLayer;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphLayoutCache;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphNodeKind;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphSnapshot;
import space.controlnet.ae2federation.client.policy.FederationDomainGraphProjection;
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
            helper.assertTrue(scene.ready(), "Waiting for authentic Federation Domain, Provider, and Endpoint topology: "
                    + scene.status());
            if (state.result == null) {
                helper.assertTrue(scene.openFirstMenu() && scene.openSecondMenu(),
                        "Two production Federation Domain menus must open before the closed-GUI observation");
                state.result = measure(profile,
                        () -> FederationDomainGraphProjection.snapshot(helper.getLevel(), scene.firstScope(),
                                ProviderObservationRegistry.entries(helper.getLevel()).stream().findFirst()));
                state.before = scene.nativeTickerInvocations();
                state.ownerIdentity = scene.nativeProgressOwnerIdentity();
                state.delegateCount = scene.nativeTickerDelegateCount();
                scene.closeFirstMenu();
                scene.closeSecondMenu();
                helper.assertTrue(scene.player().containerMenu == scene.player().inventoryMenu
                                && scene.secondPlayer().containerMenu == scene.secondPlayer().inventoryMenu,
                        "Both production Federation Domain menus must be closed before native progress observation");
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
            Supplier<FederationDomainGraphSnapshot> productionProjection) {
        var authoritative = productionProjection.get();
        requireProductionProjection(authoritative);
        var initial = derive(profile, authoritative, 7, 11, "online");
        var cache = new FederationDomainGraphLayoutCache();
        var projectionNanos = new ArrayList<Long>();
        var layoutNanos = new ArrayList<Long>();
        var layout = cache.layout(initial);
        var reuseCount = 0;
        for (var sample = 0; sample < profile.samples(); sample++) {
            var projectionStart = System.nanoTime();
            var observed = productionProjection.get();
            requireSameAuthority(authoritative, observed);
            var update = FederationDomainGraphSnapshot.decode(derive(profile, observed, 7, 12 + sample, "busy").encode());
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
                .filter(edge -> edge.layer() == FederationDomainGraphLayer.PHYSICAL).count()));
        facts.put("capabilityEdgeCount", Long.toString(initial.edges().stream()
                .filter(edge -> edge.layer() == FederationDomainGraphLayer.CAPABILITY).count()));
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

    private static FederationDomainGraphSnapshot derive(UiGraphBenchmarkProfile profile, FederationDomainGraphSnapshot source,
            long topologyRevision, long dataRevision, String providerStatus) {
        var nodes = new ArrayList<FederationDomainGraphSnapshot.Node>();
        var sourceMembers = nodes(source, FederationDomainGraphNodeKind.MEMBER);
        var sourceProviders = nodes(source, FederationDomainGraphNodeKind.PROVIDER);
        var sourceEndpoints = nodes(source, FederationDomainGraphNodeKind.ENDPOINT);
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
        var edges = new ArrayList<FederationDomainGraphSnapshot.Edge>();
        var sourcePhysical = edges(source, FederationDomainGraphLayer.PHYSICAL);
        var sourceCapability = edges(source, FederationDomainGraphLayer.CAPABILITY);
        for (var index = 0; index < profile.physicalEdges(); index++) {
            var target = index < profile.providers() ? "provider-" + index : "endpoint-" + (index - profile.providers());
            var authority = sourcePhysical.get(index % sourcePhysical.size());
            edges.add(new FederationDomainGraphSnapshot.Edge("member-" + (index % profile.members()), target,
                    FederationDomainGraphLayer.PHYSICAL, authority.status()));
        }
        for (var index = 0; index < profile.capabilityEdges(); index++) {
            var authority = sourceCapability.get(index % sourceCapability.size());
            edges.add(new FederationDomainGraphSnapshot.Edge("provider-" + index, "endpoint-" + index,
                    FederationDomainGraphLayer.CAPABILITY, authority.status()));
        }
        var patterns = new ArrayList<String>();
        for (var index = 0; index < profile.patterns(); index++) {
            patterns.add(source.patterns().get(index % source.patterns().size()) + " | replica=" + index);
        }
        return new FederationDomainGraphSnapshot(topologyRevision, dataRevision, nodes, edges, patterns);
    }

    private static FederationDomainGraphSnapshot.Node derivedNode(String prefix, int index, FederationDomainGraphSnapshot.Node authority,
            String status) {
        return new FederationDomainGraphSnapshot.Node(prefix + "-" + index, authority.kind(), status);
    }

    private static List<FederationDomainGraphSnapshot.Node> nodes(FederationDomainGraphSnapshot source, FederationDomainGraphNodeKind kind) {
        return source.nodes().stream().filter(node -> node.kind() == kind).toList();
    }

    private static List<FederationDomainGraphSnapshot.Edge> edges(FederationDomainGraphSnapshot source, FederationDomainGraphLayer layer) {
        return source.edges().stream().filter(edge -> edge.layer() == layer).toList();
    }

    private static void requireProductionProjection(FederationDomainGraphSnapshot snapshot) {
        if (nodes(snapshot, FederationDomainGraphNodeKind.MEMBER).isEmpty()
                || nodes(snapshot, FederationDomainGraphNodeKind.PROVIDER).isEmpty()
                || nodes(snapshot, FederationDomainGraphNodeKind.ENDPOINT).isEmpty()
                || edges(snapshot, FederationDomainGraphLayer.PHYSICAL).isEmpty()
                || edges(snapshot, FederationDomainGraphLayer.CAPABILITY).isEmpty()
                || snapshot.patterns().isEmpty()) {
            throw new IllegalStateException("Production graph projection lacks required runtime topology");
        }
    }

    private static void requireSameAuthority(FederationDomainGraphSnapshot expected, FederationDomainGraphSnapshot observed) {
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
