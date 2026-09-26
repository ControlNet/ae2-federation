package space.controlnet.ae2federation.test.multiclient;

import appeng.api.networking.GridHelper;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import space.controlnet.ae2federation.bridge.BridgeOperationalReason;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu;
import space.controlnet.ae2federation.client.policy.PolicyEditorSelection;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.router.RouterBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyService;

public final class MultiClientServerHarness {
    private static State state;

    private MultiClientServerHarness() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MultiClientServerHarness::tick);
    }

    private static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var level = server.overworld();
        var players = server.getPlayerList().getPlayers().stream()
                .filter(player -> player.getGameProfile().getName().equals("ClientA")
                        || player.getGameProfile().getName().equals("ClientB"))
                .toList();
        if (state == null && players.size() == 2) {
            state = new State(level.getSharedSpawnPos().above(3).east(2));
            state.placeBridge(level);
        }
        if (state == null) {
            return;
        }
        state.tick(level, players);
    }

    private static final class State {
        private final BlockPos bridge;
        private final BlockPos router;
        private int stage;
        private int stageTicks;
        private NetworkId mainNetwork;
        private NetworkId outerNetwork;
        private PolicyKey policyKey;
        private FederationDomainReference originalReference;
        private FederationDomainReference refreshedReference;
        private int refreshedFederationDomainMembers;
        private boolean refreshedReferenceCurrent;
        private boolean refreshedExpectedIdentities;
        private long authoritativeRevision;
        private int closedGuiTicks;
        private boolean staleRevisionRejected;
        private boolean splitInvalidated;
        private boolean splitStarted;
        private boolean splitSignaled;
        private boolean revisionSignaled;

        private State(BlockPos bridge) {
            this.bridge = bridge;
            router = bridge.east(2);
        }

        private void tick(ServerLevel level, List<ServerPlayer> players) {
            stageTicks++;
            switch (stage) {
                case 0 -> settleBridge(level);
                case 1 -> settleExtensions(level);
                case 2 -> settleRouter(level, players);
                case 3 -> exerciseConflicts(level);
                case 4 -> settleRetiredRouter(level);
                case 5 -> settleRestoredExtension(level);
                case 6 -> settleCurrentFederationDomain(level);
                case 7 -> finish(level, players);
                default -> {
                }
            }
        }

        private void placeBridge(ServerLevel level) {
            positions().forEach(position -> level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState()));
            level.setBlockAndUpdate(bridge.north(), AEBlocks.ME_CHEST.block().defaultBlockState());
            level.setBlockAndUpdate(bridge.south(2), AEBlocks.ME_CHEST.block().defaultBlockState());
            placeCable(level, bridge.south(), AEColor.TRANSPARENT);
            placeCable(level, bridge, AEColor.TRANSPARENT);
            require(PartHelper.setPart(level, bridge, Direction.NORTH, null,
                    BridgeRegistration.BRIDGE.get()) instanceof MultipartBridgePart,
                    "Task 34 ME Federation Bridge could not be placed");
        }

        private void settleBridge(ServerLevel level) {
            var part = bridge(level);
            if (part == null || part.operationalReason() != BridgeOperationalReason.VALID) {
                return;
            }
            var candidate = part.membershipCandidate().orElseThrow();
            mainNetwork = FederationDomainRegistryAccess.confirmedNetworkId(candidate.mainGrid()).orElse(null);
            outerNetwork = FederationDomainRegistryAccess.confirmedNetworkId(candidate.outerGrid()).orElse(null);
            if (mainNetwork == null || outerNetwork == null || mainNetwork.equals(outerNetwork)) {
                return;
            }
            placeCable(level, bridge.east(), AEColor.RED);
            placeCable(level, bridge.north().east(), AEColor.BLUE);
            placeCable(level, router.north(), AEColor.BLUE);
            advance();
        }

        private void settleExtensions(ServerLevel level) {
            var extendedMain = confirmed(level, bridge.east());
            var extendedOuter = confirmed(level, router.north());
            if (extendedMain == null || extendedOuter == null || extendedMain.equals(extendedOuter)) {
                return;
            }
            mainNetwork = extendedMain;
            outerNetwork = extendedOuter;
            level.setBlockAndUpdate(router, RouterRegistration.ROUTER.get().defaultBlockState());
            advance();
        }

        private void settleRouter(ServerLevel level, List<ServerPlayer> players) {
            var part = bridge(level);
            if (!(level.getBlockEntity(router) instanceof RouterBlockEntity) || part == null) {
                return;
            }
            var candidate = part.membershipCandidate().orElse(null);
            if (candidate == null) {
                return;
            }
            var settledMain = FederationDomainRegistryAccess.confirmedNetworkId(candidate.mainGrid()).orElse(null);
            var settledOuter = FederationDomainRegistryAccess.confirmedNetworkId(candidate.outerGrid()).orElse(null);
            if (settledMain == null || settledOuter == null || settledMain.equals(settledOuter)) {
                return;
            }
            var nodeId = FederationDomainRegistryAccess.nodeId(level, router);
            var federationDomains = FederationDomainRegistryAccess.get(level).snapshot().federationDomains().values().stream()
                    .filter(federationDomain -> federationDomain.nodes().contains(nodeId))
                    .filter(federationDomain -> federationDomain.memberships().containsKey(settledMain)
                            && federationDomain.memberships().containsKey(settledOuter)).toList();
            if (federationDomains.size() != 1) {
                return;
            }
            mainNetwork = settledMain;
            outerNetwork = settledOuter;
            policyKey = PolicyEditorSelection.initial(List.of(mainNetwork, outerNetwork)).key();
            originalReference = federationDomains.getFirst().reference();
            level.setBlockAndUpdate(router.south().below(), Blocks.STONE.defaultBlockState());
            players.forEach(player -> {
                player.connection.teleport(router.getX() + 0.5, router.getY(), router.getZ() + 1.5,
                        player.getYRot(), player.getXRot());
                require(FederationDomainPolicyMenu.openRouter(player, router), "Task 34 production menu did not open");
            });
            advance();
        }

        private void exerciseConflicts(ServerLevel level) {
            authoritativeRevision = PolicyService.get(level).revision(policyKey).value();
            if (authoritativeRevision == 1 && !revisionSignaled) {
                signal("revision-one.ready");
                revisionSignaled = true;
            }
            staleRevisionRejected |= authoritativeRevision == 1
                    && Files.isRegularFile(clientMarker("stale-revision.ready"));
            if (staleRevisionRejected && !splitStarted) {
                consumeMarker("stale-revision.ready");
                level.setBlockAndUpdate(router.north(), Blocks.AIR.defaultBlockState());
                var routerEntity = level.getBlockEntity(router) instanceof RouterBlockEntity found ? found : null;
                require(routerEntity != null, "Task 34 Router disappeared before split invalidation");
                routerEntity.neighborChanged(router.north());
                splitStarted = true;
            }
            if (splitStarted) {
                splitInvalidated = !FederationDomainRegistryAccess.get(level).isCurrent(originalReference);
            }
            if (splitInvalidated && !splitSignaled) {
                signal("domain-split.ready");
                splitSignaled = true;
            }
            var subscriptions = LevelObservabilityService.get(level).subscriptions();
            if (staleRevisionRejected && splitInvalidated && clientFirstPhaseComplete("a")
                    && clientFirstPhaseComplete("b") && subscriptions.activeCount() == 0) {
                level.setBlockAndUpdate(router, Blocks.AIR.defaultBlockState());
                advance();
            }
        }

        private void settleRetiredRouter(ServerLevel level) {
            if (!level.getBlockState(router).isAir() || level.getBlockEntity(router) != null
                    || !mainNetwork.equals(confirmed(level, bridge.east()))
                    || !outerNetwork.equals(confirmed(level, bridge.north().east()))) {
                return;
            }
            placeCable(level, router.north(), AEColor.BLUE);
            advance();
        }

        private void settleRestoredExtension(ServerLevel level) {
            if (!mainNetwork.equals(confirmed(level, bridge.east()))
                    || !outerNetwork.equals(confirmed(level, router.north()))) {
                return;
            }
            level.setBlockAndUpdate(router, RouterRegistration.ROUTER.get().defaultBlockState());
            advance();
        }

        private void settleCurrentFederationDomain(ServerLevel level) {
            var part = bridge(level);
            if (!(level.getBlockEntity(router) instanceof RouterBlockEntity) || part == null
                    || part.operationalReason() != BridgeOperationalReason.VALID) {
                return;
            }
            var nodeId = FederationDomainRegistryAccess.nodeId(level, router);
            var registry = FederationDomainRegistryAccess.get(level);
            var routerFederationDomains = registry.snapshot().federationDomains().values().stream()
                    .filter(federationDomain -> federationDomain.nodes().contains(nodeId)).toList();
            if (routerFederationDomains.size() != 1) {
                return;
            }
            var federationDomain = routerFederationDomains.getFirst();
            if (federationDomain.memberships().size() != 2
                    || !federationDomain.memberships().containsKey(mainNetwork)
                    || !federationDomain.memberships().containsKey(outerNetwork)) {
                return;
            }
            var candidateReference = federationDomain.reference();
            if (candidateReference.equals(originalReference) || !registry.isCurrent(candidateReference)) {
                return;
            }
            refreshedReference = candidateReference;
            refreshedFederationDomainMembers = federationDomain.memberships().size();
            refreshedReferenceCurrent = true;
            refreshedExpectedIdentities = true;
            level.players().stream().filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast)
                    .filter(player -> player.getGameProfile().getName().startsWith("Client"))
                    .forEach(player -> require(FederationDomainPolicyMenu.openRouter(player, router),
                            "Task 34 refreshed production menu did not open"));
            advance();
        }

        private void finish(ServerLevel level, List<ServerPlayer> players) {
            var subscriptions = LevelObservabilityService.get(level).subscriptions();
            if (stageTicks % 200 == 0) {
                players.stream().filter(player -> player.getGameProfile().getName().startsWith("Client"))
                        .filter(player -> !clientFinal(player.getGameProfile().getName()))
                        .forEach(player -> {
                            player.closeContainer();
                            require(FederationDomainPolicyMenu.openRouter(player, router),
                                    "Task 34 refreshed production menu retry did not open");
                        });
            }
            if (players.stream().noneMatch(player -> player.getGameProfile().getName().startsWith("Client"))
                    && subscriptions.activeCount() == 0) {
                closedGuiTicks++;
            }
            if (closedGuiTicks >= 20) {
                write(level, subscriptions.removalCount());
                stage = 6;
                level.getServer().halt(false);
            }
        }

        private void write(ServerLevel level, long removals) {
            var properties = new Properties();
            properties.setProperty("schemaVersion", "1");
            properties.setProperty("status", "passed");
            properties.setProperty("dedicatedServer", "true");
            properties.setProperty("connectedClients", "2");
            properties.setProperty("authoritativeRevision", Long.toString(authoritativeRevision));
            properties.setProperty("staleRevisionRejected", Boolean.toString(staleRevisionRejected));
            properties.setProperty("federationDomainSplitInvalidated", Boolean.toString(splitInvalidated));
            properties.setProperty("scopeRefreshed", Boolean.toString(refreshedReference != null));
            properties.setProperty("refreshedFederationDomainMembers", Integer.toString(refreshedFederationDomainMembers));
            properties.setProperty("refreshedReferenceCurrent", Boolean.toString(refreshedReferenceCurrent
                    && FederationDomainRegistryAccess.get(level).isCurrent(refreshedReference)));
            properties.setProperty("refreshedExpectedIdentities", Boolean.toString(refreshedExpectedIdentities));
            properties.setProperty("activeSubscriptions", Integer.toString(
                    LevelObservabilityService.get(level).subscriptions().activeCount()));
            properties.setProperty("subscriptionRemovals", Long.toString(removals));
            properties.setProperty("closedGuiSimulationTicks", Integer.toString(closedGuiTicks));
            writeProperties(properties);
        }

        private void advance() {
            stage++;
            stageTicks = 0;
        }

        private List<BlockPos> positions() {
            return List.of(router, bridge, bridge.south(), bridge.south(2), bridge.north(), bridge.east(),
                    bridge.north().east(), router.north(), router.south().below());
        }
    }

    private static MultipartBridgePart bridge(ServerLevel level) {
        var host = PartHelper.getPartHost(level, state.bridge);
        return host != null && host.getPart(Direction.NORTH) instanceof MultipartBridgePart part ? part : null;
    }

    private static NetworkId confirmed(ServerLevel level, BlockPos position) {
        var node = GridHelper.getExposedNode(level, position, Direction.UP);
        return node == null || node.getGrid() == null ? null
                : FederationDomainRegistryAccess.confirmedNetworkId(node.getGrid()).orElse(null);
    }

    private static void placeCable(ServerLevel level, BlockPos position, AEColor color) {
        require(PartHelper.setPart(level, position, null, null, AEParts.GLASS_CABLE.item(color)) != null,
                "Task 34 native cable could not be placed at " + position);
    }

    private static void writeProperties(Properties properties) {
        var path = Path.of(System.getProperty("ae2federation.multiclient.serverEvidence", ""));
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation multi-client server evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Task 34 server evidence", exception);
        }
    }

    private static Path clientMarker(String name) {
        var evidence = Path.of(System.getProperty("ae2federation.multiclient.serverEvidence", ""));
        return evidence.toAbsolutePath().normalize().getParent().resolve("clients").resolve(name);
    }

    private static boolean clientFinal(String playerName) {
        var role = playerName.substring("Client".length()).toLowerCase(java.util.Locale.ROOT);
        var path = clientMarker("client-" + role + ".properties");
        if (!Files.isRegularFile(path)) {
            return false;
        }
        var properties = new Properties();
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Task 34 client evidence " + path, exception);
        }
        return properties.getProperty("finalState", "false").equals("true");
    }

    private static boolean clientFirstPhaseComplete(String role) {
        var path = clientMarker("client-" + role + ".properties");
        if (!Files.isRegularFile(path)) {
            return false;
        }
        var properties = new Properties();
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Task 34 first-phase client evidence " + path, exception);
        }
        return properties.getProperty("finalState", "true").equals("false");
    }

    private static void signal(String name) {
        var marker = clientMarker(name);
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "ready\n");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Task 34 phase receipt " + name, exception);
        }
    }

    private static void consumeMarker(String name) {
        try {
            Files.deleteIfExists(clientMarker(name));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot consume Task 34 phase receipt " + name, exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
