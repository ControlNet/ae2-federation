package space.controlnet.ae2federation.test.ui;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.phys.Vec3;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.ClaimRequest;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.claim.NativeTargetDomainRegistry;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.provider.MappedPatternProvider;
import space.controlnet.ae2federation.processing.provider.MappedPatternProviderHost;
import space.controlnet.ae2federation.processing.provider.ProviderFace;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderOrientation;
import space.controlnet.ae2federation.processing.provider.ProviderRuntime;
import space.controlnet.ae2federation.processing.provider.ProviderTargetRequest;

final class TaskThirtyThreeWorldFixture {
    private static final String STATE = "task33.world";

    private TaskThirtyThreeWorldFixture() {
    }

    static ScenarioBuilder arrange(ScenarioBuilder scenario) {
        return TaskFifteenWorldFixture.arrange(scenario)
                .server("place production Provider and Endpoint diagnostics", TaskThirtyThreeWorldFixture::place)
                .serverTicks(4)
                .waitUntilServer("Provider and Endpoint join the scoped Fabric", TaskThirtyThreeWorldFixture::ready)
                .teardownServer("remove Task 33 processing fixtures", TaskThirtyThreeWorldFixture::cleanup);
    }

    static void openHub(ServerContext context) {
        TaskFifteenWorldFixture.openHub(context);
    }

    static void openBridge(ServerContext context) {
        TaskFifteenWorldFixture.openBridge(context);
    }

    static boolean mappingAccepted(ServerContext context) {
        var state = state(context);
        return state.provider != null && state.provider.lanesForSlot(0).equals(Set.of(0));
    }

    static String mappingLanes(ServerContext context) {
        return state(context).provider.lanesForSlot(0).toString();
    }

    static void positionHubOverviewCamera(ServerContext context) {
        var hub = TaskFifteenWorldFixture.hubPosition(context);
        positionCamera(context, hub.south(4).west(2).above(2), hub);
    }

    static void positionBridgeCamera(ServerContext context) {
        var bridge = TaskFifteenWorldFixture.bridgePosition(context);
        positionCamera(context, bridge.north(3).above(), bridge);
    }

    static void positionProviderCamera(ServerContext context) {
        var fixture = state(context);
        positionCamera(context, fixture.hostPosition.east(3).above(2), fixture.hostPosition);
    }

    static void positionEndpointCamera(ServerContext context) {
        var fixture = state(context);
        positionCamera(context, fixture.endpointPosition.east(2).south(3).above(2), fixture.endpointPosition);
    }

    static String providerId(ServerContext context) {
        return state(context).identity.id().value().toString();
    }

    static String endpointId(ServerContext context) {
        var endpoint = endpoint(context);
        return endpoint == null ? "missing" : endpoint.endpointIdentity().id().value().toString();
    }

    static String claimConflict(ServerContext context) {
        var endpoint = endpoint(context);
        if (endpoint == null) {
            return "endpoint-missing";
        }
        return endpoint.claim(new ClaimRequest(endpoint.endpointIdentity(), endpoint.claimState().epoch(),
                new EndpointOwnerIdentity(ProviderIdentity.create()))).getClass().getSimpleName();
    }

    static String multipartAttachment(ServerContext context) {
        var bridge = TaskFifteenWorldFixture.multipartBridge(context);
        return bridge.getSide().getSerializedName() + ":bridge;cable-extension:"
                + (int) bridge.getCableConnectionLength(AECableType.GLASS);
    }

    private static void place(ServerContext context) {
        var hostPosition = TaskFifteenWorldFixture.hubPosition(context).south(2);
        var endpointPosition = hostPosition.south();
        context.level().setBlockAndUpdate(hostPosition, Blocks.CHEST.defaultBlockState());
        context.level().setBlockAndUpdate(endpointPosition, ProcessingRegistration.ENDPOINT.get().defaultBlockState());
        var existing = GridHelper.getExposedNode(context.level(),
                TaskFifteenWorldFixture.mainNetworkCablePosition(context), Direction.UP);
        require(existing != null, "Task 33 Provider requires the settled main network cable");
        var networkId = FabricRegistryAccess.confirmedNetworkId(existing.getGrid()).orElseThrow(
                () -> new IllegalStateException("Task 33 Provider requires confirmed main network identity"));
        var endpoint = context.level().getBlockEntity(endpointPosition) instanceof EndpointBlockEntity value
                ? value
                : null;
        require(endpoint != null, "Task 33 Endpoint must be placed before identity seeding");
        endpoint.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
        var host = new ProviderHost(context, hostPosition);
        var node = GridHelper.createManagedNode(host, (IGridNodeListener<ProviderHost>) (owner, gridNode) -> {
        }).setTagName("task33-provider").setInWorldNode(true).setIdlePowerUsage(0)
                .setExposedOnSides(EnumSet.allOf(Direction.class));
        node.loadFromNBT(NetworkIdentityNodeSeed.managedNode("task33-provider", networkId));
        var provider = new MappedPatternProvider(node, host, List.of(host, host), 6);
        host.provider = provider;
        node.create(context.level(), hostPosition);
        GridHelper.createConnection(node.getNode(), existing);
        context.put(STATE, new State(hostPosition, endpointPosition, host, node, provider, ProviderIdentity.create()));
    }

    private static boolean ready(ServerContext context) {
        var state = state(context);
        var endpoint = endpoint(context);
        if (endpoint == null || endpoint.getMainNode().getNode() == null || state.node.getGrid() == null) {
            return false;
        }
        if (state.runtime == null) {
            var claim = endpoint.claim(new ClaimRequest(endpoint.endpointIdentity(), ClaimEpoch.NONE,
                    new EndpointOwnerIdentity(state.identity)));
            require(!(claim instanceof space.controlnet.ae2federation.processing.claim.ClaimResult.Rejected),
                    "Task 33 Endpoint Claim must be acquired");
            require(endpoint.activateFederated(), "Task 33 Endpoint must enter Federation mode");
            var request = new AtomicReference<>(new ProviderTargetRequest(state.identity, endpoint.endpointIdentity(),
                    endpoint.claimState().epoch(), state.endpointPosition, Direction.WEST, true));
            state.runtime = new ProviderRuntime(context.level(), state.node, state.provider, state.identity,
                    new ProviderOrientation(ProviderFace.EAST), request::get, new NativeTargetDomainRegistry());
            state.runtime.settle();
            state.provider.register();
            state.provider.patternInventory().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 4_000_000_000L)),
                    List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1))));
            state.provider.refreshPatterns();
        }
        var binding = endpoint.binding();
        if (binding == null) {
            return false;
        }
        var providerNetwork = FabricRegistryAccess.confirmedNetworkId(state.node.getGrid()).orElse(null);
        var endpointNetwork = FabricRegistryAccess.confirmedNetworkId(binding.subnetNode().getGrid()).orElse(null);
        var hubNode = FabricRegistryAccess.nodeId(context.level(), TaskFifteenWorldFixture.hubPosition(context));
        var fabrics = FabricRegistryAccess.get(context.level()).snapshot().fabrics().values();
        if (providerNetwork == null || !providerNetwork.equals(endpointNetwork)) {
            return false;
        }
        return !state.provider.patternInventory().getStackInSlot(0).isEmpty() && fabrics.stream()
                .anyMatch(fabric -> fabric.nodes().contains(hubNode)
                        && fabric.memberships().containsKey(providerNetwork));
    }

    private static EndpointBlockEntity endpoint(ServerContext context) {
        var state = state(context);
        return context.level().getBlockEntity(state.endpointPosition) instanceof EndpointBlockEntity endpoint
                ? endpoint : null;
    }

    private static State state(ServerContext context) {
        var state = context.<State>get(STATE);
        if (state == null) {
            throw new IllegalStateException("Task 33 world was not arranged");
        }
        return state;
    }

    private static void cleanup(ServerContext context) {
        var state = context.<State>get(STATE);
        if (state == null) {
            return;
        }
        state.provider.close();
        state.node.destroy();
        context.level().setBlockAndUpdate(state.endpointPosition, Blocks.AIR.defaultBlockState());
        context.level().setBlockAndUpdate(state.hostPosition, Blocks.AIR.defaultBlockState());
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new IllegalStateException(message);
        }
    }

    private static void positionCamera(ServerContext context, BlockPos camera, BlockPos target) {
        var player = context.player();
        player.connection.teleport(camera.getX() + 0.5, camera.getY() + 0.5, camera.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
    }

    private static final class State {
        private final BlockPos hostPosition;
        private final BlockPos endpointPosition;
        private final ProviderHost host;
        private final IManagedGridNode node;
        private final MappedPatternProvider provider;
        private final ProviderIdentity identity;
        private ProviderRuntime runtime;

        private State(BlockPos hostPosition, BlockPos endpointPosition, ProviderHost host, IManagedGridNode node,
                MappedPatternProvider provider, ProviderIdentity identity) {
            this.hostPosition = hostPosition;
            this.endpointPosition = endpointPosition;
            this.host = host;
            this.node = node;
            this.provider = provider;
            this.identity = identity;
        }
    }

    private static final class ProviderHost implements MappedPatternProviderHost, PatternProviderLogicHost {
        private final ServerContext context;
        private final BlockPos position;
        private MappedPatternProvider provider;

        private ProviderHost(ServerContext context, BlockPos position) {
            this.context = context;
            this.position = position;
        }

        @Override
        public MappedPatternProvider mappedPatternProvider() {
            return provider;
        }

        @Override
        public BlockEntity getBlockEntity() {
            return context.level().getBlockEntity(position);
        }

        @Override
        public EnumSet<Direction> getTargets() {
            return EnumSet.of(Direction.EAST);
        }

        @Override
        public void saveChanges() {
            getBlockEntity().setChanged();
        }

        @Override
        public AEItemKey getTerminalIcon() {
            return AEItemKey.of(AEItems.PROCESSING_PATTERN.asItem());
        }

        @Override
        public ItemStack getMainMenuIcon() {
            return AEItems.PROCESSING_PATTERN.stack();
        }
    }
}
