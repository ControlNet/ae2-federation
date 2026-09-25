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
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.ClaimRequest;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.claim.NativeTargetDomainRegistry;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
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
                .waitUntilServer("Provider and Endpoint join the scoped Federation Domain", TaskThirtyThreeWorldFixture::ready)
                .teardownServer("remove Task 33 processing fixtures", TaskThirtyThreeWorldFixture::cleanup);
    }

    static void openRouter(ServerContext context) {
        TaskFifteenWorldFixture.openRouter(context);
    }

    static void openBridge(ServerContext context) {
        TaskFifteenWorldFixture.openBridge(context);
    }

    static boolean mappingAccepted(ServerContext context) {
        var provider = provider(context);
        return provider != null && provider.mappedProvider().lanesForSlot(0).equals(Set.of(0));
    }

    static String mappingLanes(ServerContext context) {
        return provider(context).mappedProvider().lanesForSlot(0).toString();
    }

    static void positionRouterOverviewCamera(ServerContext context) {
        var router = TaskFifteenWorldFixture.routerPosition(context);
        positionCamera(context, router.south(4).west(2).above(2), router);
    }

    static void positionBridgeCamera(ServerContext context) {
        var bridge = TaskFifteenWorldFixture.bridgePosition(context);
        positionCamera(context, bridge.north(3).above(), bridge);
    }

    static void positionProviderCamera(ServerContext context) {
        var fixture = state(context);
        positionCamera(context, fixture.hostPosition().east(3).above(2), fixture.hostPosition());
    }

    static void positionEndpointCamera(ServerContext context) {
        var fixture = state(context);
        positionCamera(context, fixture.endpointPosition().east(2).south(3).above(2), fixture.endpointPosition());
    }

    static String providerId(ServerContext context) {
        return provider(context).providerIdentity().id().value().toString();
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
        var hostPosition = TaskFifteenWorldFixture.routerPosition(context).south(2);
        var endpointPosition = hostPosition.south();
        var existing = GridHelper.getExposedNode(context.level(),
                TaskFifteenWorldFixture.mainNetworkCablePosition(context), Direction.UP);
        require(existing != null, "Task 33 Provider requires the settled main network cable");
        var networkId = FederationDomainRegistryAccess.confirmedNetworkId(existing.getGrid()).orElseThrow(
                () -> new IllegalStateException("Task 33 Provider requires confirmed main network identity"));
        // The production ME Federation Pattern Provider block; its Federation face points away from the Endpoint.
        context.level().setBlockAndUpdate(hostPosition, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP));
        context.level().setBlockAndUpdate(endpointPosition, ProcessingRegistration.ENDPOINT.get().defaultBlockState());
        var endpoint = context.level().getBlockEntity(endpointPosition) instanceof EndpointBlockEntity value
                ? value
                : null;
        require(endpoint != null, "Task 33 Endpoint must be placed before identity seeding");
        endpoint.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
        var provider = (FederationPatternProviderBlockEntity) context.level().getBlockEntity(hostPosition);
        provider.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
        context.put(STATE, new State(hostPosition, endpointPosition, existing));
    }

    private static boolean ready(ServerContext context) {
        var state = state(context);
        var endpoint = endpoint(context);
        var provider = provider(context);
        if (endpoint == null || provider == null || endpoint.getMainNode().getNode() == null
                || provider.getMainNode().getNode() == null || provider.runtime().isEmpty()) {
            return false;
        }
        if (provider.getMainNode().getGrid() != state.existing.getGrid()) {
            GridHelper.createConnection(provider.getMainNode().getNode(), state.existing);
            return false;
        }
        var binding = endpoint.binding();
        if (binding == null) {
            return false;
        }
        if (provider.getTerminalPatternInventory().getStackInSlot(0).isEmpty()) {
            // Inserted through the Provider's native Pattern inventory, as AE2's menu or Pattern Access Terminal do.
            provider.getTerminalPatternInventory().insertItem(0, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 4_000_000_000L)),
                    List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1))), false);
            provider.getTerminalPatternInventory().insertItem(1, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                    List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1))), false);
        }
        var providerNetwork = FederationDomainRegistryAccess.confirmedNetworkId(provider.getMainNode().getGrid())
                .orElse(null);
        var endpointNetwork = FederationDomainRegistryAccess.confirmedNetworkId(binding.subnetNode().getGrid())
                .orElse(null);
        var routerNode = FederationDomainRegistryAccess.nodeId(context.level(),
                TaskFifteenWorldFixture.routerPosition(context));
        var federationDomains = FederationDomainRegistryAccess.get(context.level()).snapshot().federationDomains()
                .values();
        if (providerNetwork == null || !providerNetwork.equals(endpointNetwork) || !federationDomains.stream()
                .anyMatch(domain -> domain.nodes().contains(routerNode)
                        && domain.memberships().containsKey(providerNetwork))) {
            return false;
        }
        if (!(endpoint.claimState() instanceof space.controlnet.ae2federation.processing.claim.ClaimState.Owned)) {
            // Pattern slot 1 maps the Endpoint through the production controller, which claims it (epoch 1).
            var status = provider.toggleEndpoint(provider.mappedProvider().mappingHandle(1), binding);
            require(status.startsWith("accepted-"), "Task 33 Endpoint mapping must be accepted: " + status);
        }
        return true;
    }

    private static EndpointBlockEntity endpoint(ServerContext context) {
        var state = state(context);
        return context.level().getBlockEntity(state.endpointPosition()) instanceof EndpointBlockEntity endpoint
                ? endpoint : null;
    }

    private static FederationPatternProviderBlockEntity provider(ServerContext context) {
        var state = state(context);
        return context.level().getBlockEntity(state.hostPosition()) instanceof FederationPatternProviderBlockEntity provider
                ? provider : null;
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
        context.level().setBlockAndUpdate(state.endpointPosition(), Blocks.AIR.defaultBlockState());
        context.level().setBlockAndUpdate(state.hostPosition(), Blocks.AIR.defaultBlockState());
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

    private record State(BlockPos hostPosition, BlockPos endpointPosition,
            appeng.api.networking.IGridNode existing) {
    }
}
