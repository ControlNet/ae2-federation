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
import space.controlnet.ae2federation.client.menu.FabricPolicyMenu;
import space.controlnet.ae2federation.client.policy.PolicyEditorSelection;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.hub.HubBlockEntity;
import space.controlnet.ae2federation.hub.HubRegistration;
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
                .server("extend Bridge networks to the Hub", TaskFifteenWorldFixture::extendNetworksToHub)
                .serverTicks(3)
                .waitUntilServer("extended native identities remain settled", TaskFifteenWorldFixture::extensionsSettled)
                .server("place real Federation Hub", TaskFifteenWorldFixture::placeHub)
                .serverTicks(3)
                .waitUntilServer("Hub and Bridge expose the same networks", TaskFifteenWorldFixture::entrancesReady)
                .teardownServer("remove Task 15 topology", TaskFifteenWorldFixture::cleanup);
    }

    static void openHub(ServerContext context) {
        var world = world(context);
        require(FabricPolicyMenu.openHub(context.player(), world.hub), "Production Hub policy menu must open");
    }

    static void openBridge(ServerContext context) {
        var bridge = bridge(context);
        require(FabricPolicyMenu.openBridge(context.player(), bridge.rightClickContext()),
                "Production Bridge policy menu must open");
    }

    static boolean policyConfigured(ServerContext context) {
        return PolicyService.get(context.level()).configured(world(context).hubKey).isPresent();
    }

    static boolean policyEnabled(ServerContext context) {
        return PolicyService.get(context.level()).configured(world(context).hubKey)
                .map(record -> record.rule().enabled()).orElse(false);
    }

    static long policyRevision(ServerContext context) {
        return PolicyService.get(context.level()).revision(world(context).hubKey).value();
    }

    static void observe(ServerContext context, String mutationStatus) {
        var world = world(context);
        var service = PolicyService.get(context.level());
        var configured = service.configured(world.hubKey);
        context.put(OBSERVATION, new Observation(identity(world.hubKey), identity(world.bridgeKey),
                service.revision(world.hubKey).value(), configured.map(record -> record.rule().enabled()).orElse(false),
                mutationStatus, world.previousRevision));
    }

    static Observation observation(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        return context.get(OBSERVATION);
    }

    static void invalidateHubContext(ServerContext context) {
        context.level().setBlockAndUpdate(world(context).hub.north(), Blocks.AIR.defaultBlockState());
    }

    static boolean hubContextInvalidated(ServerContext context) {
        var reference = world(context).hubReference;
        return reference != null && !FabricRegistryAccess.get(context.level()).isCurrent(reference);
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

    static void mutateAfterClose(ServerContext context) {
        var world = world(context);
        var service = PolicyService.get(context.level());
        var current = service.configured(world.hubKey).orElseThrow();
        world.previousRevision = current.revision().value();
        var result = service.edit(new PolicyEdit(world.hubKey, current.revision(),
                current.rule().withEnabled(!current.rule().enabled())));
        require(result instanceof PolicyMutationResult.Accepted, "Post-close production policy edit must succeed");
    }

    private static void placeBridgeTopology(ServerContext context) {
        var bridge = context.player().blockPosition().above(3).east(2);
        var hub = bridge.east(2);
        var world = new WorldState(hub, bridge);
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
        world.mainNetwork = FabricRegistryAccess.confirmedNetworkId(candidate.mainGrid()).orElse(null);
        world.outerNetwork = FabricRegistryAccess.confirmedNetworkId(candidate.outerGrid()).orElse(null);
        return world.mainNetwork != null && world.outerNetwork != null && !world.mainNetwork.equals(world.outerNetwork);
    }

    private static void extendNetworksToHub(ServerContext context) {
        var world = world(context);
        placeCable(context, world.bridge.east(), AEColor.RED);
        placeCable(context, world.bridge.north().east(), AEColor.BLUE);
        placeCable(context, world.hub.north(), AEColor.BLUE);
    }

    private static boolean extensionsSettled(ServerContext context) {
        var world = world(context);
        return world.mainNetwork.equals(confirmedNetwork(context, world.bridge.east()))
                && world.outerNetwork.equals(confirmedNetwork(context, world.hub.north()));
    }

    private static void placeBridge(ServerContext context) {
        var world = world(context);
        var placed = PartHelper.setPart(context.level(), world.bridge, Direction.NORTH, null,
                BridgeRegistration.MULTIPART_BRIDGE.get());
        require(placed instanceof MultipartBridgePart, "Registered Multipart Bridge must be placed on the cable bus");
    }

    private static void placeHub(ServerContext context) {
        context.level().setBlockAndUpdate(world(context).hub, HubRegistration.HUB.get().defaultBlockState());
    }

    private static boolean entrancesReady(ServerContext context) {
        var world = world(context);
        var hubEntity = context.blockEntityOrNull(world.hub, HubBlockEntity.class);
        var bridge = bridgeOrNull(context);
        if (hubEntity == null || bridge == null || bridge.operationalReason() != BridgeOperationalReason.VALID) {
            return false;
        }
        var nodeId = FabricRegistryAccess.nodeId(context.level(), world.hub);
        var hubFabrics = FabricRegistryAccess.get(context.level()).snapshot().fabrics().values().stream()
                .filter(snapshot -> snapshot.nodes().contains(nodeId))
                .filter(snapshot -> snapshot.memberships().containsKey(world.mainNetwork)
                        && snapshot.memberships().containsKey(world.outerNetwork))
                .toList();
        var bridgeContext = bridge.rightClickContext();
        if (hubFabrics.size() != 1 || bridgeContext.mainGrid() == null || bridgeContext.outerGrid() == null) {
            return false;
        }
        var bridgeMain = FabricRegistryAccess.confirmedNetworkId(bridgeContext.mainGrid()).orElse(null);
        var bridgeOuter = FabricRegistryAccess.confirmedNetworkId(bridgeContext.outerGrid()).orElse(null);
        if (!world.mainNetwork.equals(bridgeMain) || !world.outerNetwork.equals(bridgeOuter)) {
            return false;
        }
        world.hubKey = PolicyEditorSelection.initial(List.of(world.mainNetwork, world.outerNetwork)).key();
        world.bridgeKey = PolicyEditorSelection.initial(List.of(bridgeMain, bridgeOuter)).key();
        world.hubReference = hubFabrics.getFirst().reference();
        return world.hubKey.equals(world.bridgeKey);
    }

    private static NetworkId confirmedNetwork(ServerContext context, BlockPos position) {
        var node = GridHelper.getExposedNode(context.level(), position, Direction.UP);
        return node == null || node.getGrid() == null
                ? null
                : FabricRegistryAccess.confirmedNetworkId(node.getGrid()).orElse(null);
    }

    private static void placeCable(ServerContext context, BlockPos position, AEColor color) {
        require(PartHelper.setPart(context.level(), position, null, null, AEParts.GLASS_CABLE.item(color)) != null,
                "Native glass cable must be placed at " + position);
    }

    private static MultipartBridgePart bridge(ServerContext context) {
        var bridge = bridgeOrNull(context);
        if (bridge == null) {
            throw new IllegalStateException("Real Multipart Bridge is missing");
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

    record Observation(String hubRecordIdentity, String bridgeRecordIdentity, long policyRevision, boolean enabled,
            String mutationStatus, long previousRevision) {
    }

    private static final class WorldState {
        private final BlockPos hub;
        private final BlockPos bridge;
        private NetworkId mainNetwork;
        private NetworkId outerNetwork;
        private PolicyKey hubKey;
        private PolicyKey bridgeKey;
        private FabricReference hubReference;
        private long previousRevision = -1;

        private WorldState(BlockPos hub, BlockPos bridge) {
            this.hub = hub;
            this.bridge = bridge;
        }

        private List<BlockPos> positions() {
            return List.of(hub, bridge, bridge.south(), bridge.south(2), bridge.north(), bridge.east(),
                    bridge.north().east(), hub.north());
        }
    }
}
