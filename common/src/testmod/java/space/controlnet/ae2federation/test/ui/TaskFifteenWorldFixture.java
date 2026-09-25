package space.controlnet.ae2federation.test.ui;

import appeng.api.networking.GridHelper;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
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
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyService;

final class TaskFifteenWorldFixture {
    private static final String WORLD = "task15.world";
    private static final String OBSERVATION = "task15.observation";

    private TaskFifteenWorldFixture() {
    }

    static ScenarioBuilder arrange(ScenarioBuilder scenario) {
        return scenario.server("place proven real Bridge topology", TaskFifteenWorldFixture::placeBridgeTopology)
                .serverTicks(3)
                .waitUntilServer("Bridge networks become operational and settled",
                        TaskFifteenWorldFixture::bridgeNetworksSettled)
                .server("extend Bridge networks to the Router", TaskFifteenWorldFixture::extendNetworksToRouter)
                .serverTicks(3)
                .waitUntilServer("extended native identities remain settled", TaskFifteenWorldFixture::extensionsSettled)
                .server("place real Federation Router", TaskFifteenWorldFixture::placeRouter)
                .serverTicks(3)
                .waitUntilServer("Router and Bridge expose the same networks", TaskFifteenWorldFixture::entrancesReady)
                .teardownServer("remove Task 15 topology", TaskFifteenWorldFixture::cleanup);
    }

    static void openRouter(ServerContext context) {
        var world = world(context);
        require(FederationDomainPolicyMenu.openRouter(context.player(), world.router), "Production Router policy menu must open");
    }

    static void openBridge(ServerContext context) {
        var bridge = bridge(context);
        require(FederationDomainPolicyMenu.openBridge(context.player(), bridge.rightClickContext()),
                "Production Bridge policy menu must open");
    }

    static boolean policyConfigured(ServerContext context) {
        return PolicyService.get(context.level()).configured(world(context).routerKey).isPresent();
    }

    static boolean policyEnabled(ServerContext context) {
        return PolicyService.get(context.level()).configured(world(context).routerKey)
                .map(record -> record.rule().enabled()).orElse(false);
    }

    static long policyRevision(ServerContext context) {
        return PolicyService.get(context.level()).revision(world(context).routerKey).value();
    }

    static void observe(ServerContext context, String mutationStatus) {
        var world = world(context);
        var service = PolicyService.get(context.level());
        var configured = service.configured(world.routerKey);
        context.put(OBSERVATION, new Observation(identity(world.routerKey), identity(world.bridgeKey),
                service.revision(world.routerKey).value(), configured.map(record -> record.rule().enabled()).orElse(false),
                mutationStatus, world.previousRevision));
    }

    static Observation observation(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        return context.get(OBSERVATION);
    }

    static void invalidateRouterContext(ServerContext context) {
        context.level().setBlockAndUpdate(world(context).router.north(), Blocks.AIR.defaultBlockState());
    }

    static boolean routerContextInvalidated(ServerContext context) {
        var reference = world(context).routerReference;
        return reference != null && !FederationDomainRegistryAccess.get(context.level()).isCurrent(reference);
    }

    static void disableBridge(ServerContext context) {
        context.level().setBlockAndUpdate(world(context).bridge.north(), Blocks.AIR.defaultBlockState());
    }

    static boolean bridgeDisabled(ServerContext context) {
        return bridge(context).operationalReason() != BridgeOperationalReason.VALID;
    }

    static boolean menuClosed(ServerContext context) {
        return context.menu() == context.player().inventoryMenu;
    }

    static BlockPos routerPosition(ServerContext context) {
        return world(context).router;
    }

    static BlockPos bridgePosition(ServerContext context) {
        return world(context).bridge;
    }

    static BlockPos mainNetworkCablePosition(ServerContext context) {
        return world(context).bridge.east();
    }

    static MultipartBridgePart multipartBridge(ServerContext context) {
        return bridge(context);
    }

    static void mutateAfterClose(ServerContext context) {
        var world = world(context);
        var service = PolicyService.get(context.level());
        var current = service.configured(world.routerKey).orElseThrow();
        world.previousRevision = current.revision().value();
        var result = service.edit(new PolicyEdit(world.routerKey, current.revision(),
                current.rule().withEnabled(!current.rule().enabled())));
        require(result instanceof PolicyMutationResult.Accepted, "Post-close production policy edit must succeed");
    }

    private static void placeBridgeTopology(ServerContext context) {
        var bridge = context.player().blockPosition().above(3).east(2);
        var router = bridge.east(2);
        var world = new WorldState(router, bridge);
        context.put(WORLD, world);
        cleanupBlocks(context, world);
        context.level().setBlockAndUpdate(bridge.north(), AEBlocks.ME_CHEST.block().defaultBlockState());
        context.level().setBlockAndUpdate(bridge.south(2), AEBlocks.ME_CHEST.block().defaultBlockState());
        placeCable(context, bridge.south(), AEColor.TRANSPARENT);
        placeCable(context, bridge, AEColor.TRANSPARENT);
        placeBridge(context);
    }

    private static boolean bridgeNetworksSettled(ServerContext context) {
        var world = world(context);
        var bridge = bridgeOrNull(context);
        if (bridge == null || bridge.operationalReason() != BridgeOperationalReason.VALID) {
            return false;
        }
        var candidate = bridge.membershipCandidate().orElseThrow();
        world.mainNetwork = FederationDomainRegistryAccess.confirmedNetworkId(candidate.mainGrid()).orElse(null);
        world.outerNetwork = FederationDomainRegistryAccess.confirmedNetworkId(candidate.outerGrid()).orElse(null);
        return world.mainNetwork != null && world.outerNetwork != null && !world.mainNetwork.equals(world.outerNetwork);
    }

    private static void extendNetworksToRouter(ServerContext context) {
        var world = world(context);
        placeCable(context, world.bridge.east(), AEColor.RED);
        placeCable(context, world.bridge.north().east(), AEColor.BLUE);
        placeCable(context, world.router.north(), AEColor.BLUE);
    }

    private static boolean extensionsSettled(ServerContext context) {
        var world = world(context);
        return world.mainNetwork.equals(confirmedNetwork(context, world.bridge.east()))
                && world.outerNetwork.equals(confirmedNetwork(context, world.router.north()));
    }

    private static void placeBridge(ServerContext context) {
        var world = world(context);
        var placed = PartHelper.setPart(context.level(), world.bridge, Direction.NORTH, null,
                BridgeRegistration.BRIDGE.get());
        require(placed instanceof MultipartBridgePart, "Registered ME Federation Bridge must be placed on the cable bus");
    }

    private static void placeRouter(ServerContext context) {
        context.level().setBlockAndUpdate(world(context).router, RouterRegistration.ROUTER.get().defaultBlockState());
    }

    private static boolean entrancesReady(ServerContext context) {
        var world = world(context);
        var routerEntity = context.blockEntityOrNull(world.router, RouterBlockEntity.class);
        var bridge = bridgeOrNull(context);
        if (routerEntity == null || bridge == null || bridge.operationalReason() != BridgeOperationalReason.VALID) {
            return false;
        }
        var nodeId = FederationDomainRegistryAccess.nodeId(context.level(), world.router);
        var routerFederationDomains = FederationDomainRegistryAccess.get(context.level()).snapshot().federationDomains().values().stream()
                .filter(snapshot -> snapshot.nodes().contains(nodeId))
                .filter(snapshot -> snapshot.memberships().containsKey(world.mainNetwork)
                        && snapshot.memberships().containsKey(world.outerNetwork))
                .toList();
        var bridgeContext = bridge.rightClickContext();
        if (routerFederationDomains.size() != 1 || bridgeContext.mainGrid() == null || bridgeContext.outerGrid() == null) {
            return false;
        }
        var bridgeMain = FederationDomainRegistryAccess.confirmedNetworkId(bridgeContext.mainGrid()).orElse(null);
        var bridgeOuter = FederationDomainRegistryAccess.confirmedNetworkId(bridgeContext.outerGrid()).orElse(null);
        if (!world.mainNetwork.equals(bridgeMain) || !world.outerNetwork.equals(bridgeOuter)) {
            return false;
        }
        world.routerKey = PolicyEditorSelection.initial(List.of(world.mainNetwork, world.outerNetwork)).key();
        world.bridgeKey = PolicyEditorSelection.initial(List.of(bridgeMain, bridgeOuter)).key();
        world.routerReference = routerFederationDomains.getFirst().reference();
        return world.routerKey.equals(world.bridgeKey);
    }

    private static NetworkId confirmedNetwork(ServerContext context, BlockPos position) {
        var node = GridHelper.getExposedNode(context.level(), position, Direction.UP);
        return node == null || node.getGrid() == null
                ? null
                : FederationDomainRegistryAccess.confirmedNetworkId(node.getGrid()).orElse(null);
    }

    private static void placeCable(ServerContext context, BlockPos position, AEColor color) {
        require(PartHelper.setPart(context.level(), position, null, null, AEParts.GLASS_CABLE.item(color)) != null,
                "Native glass cable must be placed at " + position);
    }

    private static MultipartBridgePart bridge(ServerContext context) {
        var bridge = bridgeOrNull(context);
        if (bridge == null) {
            throw new IllegalStateException("Real ME Federation Bridge is missing");
        }
        return bridge;
    }

    private static MultipartBridgePart bridgeOrNull(ServerContext context) {
        var host = PartHelper.getPartHost(context.level(), world(context).bridge);
        return host != null && host.getPart(Direction.NORTH) instanceof MultipartBridgePart bridge ? bridge : null;
    }

    private static WorldState world(ServerContext context) {
        var world = context.<WorldState>get(WORLD);
        if (world == null) {
            throw new IllegalStateException("Task 15 production world was not arranged");
        }
        return world;
    }

    private static void cleanup(ServerContext context) {
        var world = context.<WorldState>get(WORLD);
        if (world != null) {
            cleanupBlocks(context, world);
        }
    }

    private static void cleanupBlocks(ServerContext context, WorldState world) {
        for (var position : world.positions()) {
            context.level().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        }
    }

    private static String identity(PolicyKey key) {
        return key.consumerNetworkId().value() + "->" + key.providerNetworkId().value() + ":" + key.capability();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    record Observation(String routerRecordIdentity, String bridgeRecordIdentity, long policyRevision, boolean enabled,
            String mutationStatus, long previousRevision) {
    }

    private static final class WorldState {
        private final BlockPos router;
        private final BlockPos bridge;
        private NetworkId mainNetwork;
        private NetworkId outerNetwork;
        private PolicyKey routerKey;
        private PolicyKey bridgeKey;
        private FederationDomainReference routerReference;
        private long previousRevision = -1;

        private WorldState(BlockPos router, BlockPos bridge) {
            this.router = router;
            this.bridge = bridge;
        }

        private List<BlockPos> positions() {
            return List.of(router, bridge, bridge.south(), bridge.south(2), bridge.north(), bridge.east(),
                    bridge.north().east(), router.north());
        }
    }
}
