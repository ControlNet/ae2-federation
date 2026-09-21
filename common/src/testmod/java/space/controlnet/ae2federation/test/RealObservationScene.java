package space.controlnet.ae2federation.test;

import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import space.controlnet.ae2federation.client.menu.FabricPolicyMenu;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.processing.ProviderTargetRuntimeFixtures;

final class RealObservationScene implements AutoCloseable {
    private final GameTestHelper helper;
    private final PolicyBridgeFixtures first;
    private final boolean withProcessing;
    private PolicyBridgeFixtures second;
    private ProviderTargetRuntimeFixtures processing;
    private ServerPlayer firstPlayer;
    private ServerPlayer secondPlayer;
    private boolean placed;
    private boolean fabricsReady;
    private boolean processingConnected;
    private String status = "created";

    RealObservationScene(GameTestHelper helper) {
        this(helper, false);
    }

    RealObservationScene(GameTestHelper helper, boolean withProcessing) {
        this.helper = helper;
        first = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        this.withProcessing = withProcessing;
    }

    boolean ready() {
        if (!placed) {
            if (!first.networksSettled()) {
                status = "first-bridge-networks";
                return false;
            }
            if (second == null) {
                second = new PolicyBridgeFixtures(helper, new BlockPos(10, 3, 10));
                status = "second-bridge-placement";
                return false;
            }
            if (!second.networksSettled()) {
                status = "second-bridge-networks";
                return false;
            }
            first.placeFirstBridge();
            second.placeFirstBridge();
            placed = true;
            status = "bridge-placement";
            return false;
        }
        if (!fabricsReady) {
            first.refreshFirstBridge();
            second.refreshFirstBridge();
            if (!first.firstBridgeReady() || !second.firstBridgeReady()) {
                status = "bridge-fabrics";
                return false;
            }
            fabricsReady = true;
        }
        if (!withProcessing) {
            status = "ready";
            return true;
        }
        if (processing == null) {
            processing = new ProviderTargetRuntimeFixtures(helper, true, first.mainNetwork(), first.outerNetwork());
            processing.connectSourceTo(first.mainGrid().getNodes().iterator().next());
            status = "processing-source-connect";
            return false;
        }
        if (!processingConnected) {
            if (!processing.connectTargetTo(first.outerGrid().getNodes().iterator().next())) {
                status = "processing-target-placement";
                return false;
            }
            processingConnected = true;
            status = "processing-target-connect";
            return false;
        }
        if (!processing.initialize()) {
            status = "processing-" + processing.status();
            return false;
        }
        var ready = processing.sourceGrid() == first.mainGrid() && processing.targetGrid() == first.outerGrid();
        status = ready ? "ready" : "processing-grid-merge";
        return ready;
    }

    String status() {
        return status;
    }

    void mutateObservedState() {
        helper.assertTrue(processing.enablePolicy(Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY)),
                "Processing policy must activate");
        helper.assertTrue(processing.pushOnce(), "Native Processing send must accept");
        var handler = processing.endpointBinding().runtime().itemReturn(Direction.NORTH).orElseThrow();
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.GOLD_INGOT), false).isEmpty(),
                "Native aggregate return must be accepted");
    }

    boolean removePolicy() {
        return processing.deletePolicy();
    }

    FabricReference firstScope() {
        return scope(first);
    }

    FabricReference secondScope() {
        return scope(second);
    }

    Set<NetworkId> secondNetworks() {
        return Set.of(second.mainNetwork(), second.outerNetwork());
    }

    boolean openFirstMenu() {
        firstPlayer = openPlayer(firstPlayer, first);
        return firstPlayer.containerMenu != firstPlayer.inventoryMenu;
    }

    boolean openSecondMenu() {
        secondPlayer = openPlayer(secondPlayer, second);
        return secondPlayer.containerMenu != secondPlayer.inventoryMenu;
    }

    ServerPlayer player() {
        return Objects.requireNonNull(firstPlayer);
    }

    ServerPlayer secondPlayer() {
        return Objects.requireNonNull(secondPlayer);
    }

    void closeFirstMenu() {
        player().doCloseContainer();
    }

    void closeSecondMenu() {
        secondPlayer().doCloseContainer();
    }

    private ServerPlayer openPlayer(ServerPlayer existing, PolicyBridgeFixtures fixture) {
        if (existing != null) {
            return existing;
        }
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atCenterOf(fixture.firstBridgeContext().position()));
        ObservationGameTestPlayerTransport.install(player);
        helper.assertTrue(FabricPolicyMenu.openBridge(player, fixture.firstBridgeContext()),
                "Production Fabric policy menu must open");
        return player;
    }

    private FabricReference scope(PolicyBridgeFixtures fixture) {
        var registry = space.controlnet.ae2federation.fabric.FabricRegistryAccess.get(helper.getLevel());
        return registry.fabricsFor(fixture.mainNetwork()).stream()
                .filter(id -> registry.fabricsFor(fixture.outerNetwork()).contains(id)).findFirst()
                .flatMap(registry::fabric).orElseThrow().reference();
    }

    @Override
    public void close() {
        if (firstPlayer != null && firstPlayer.containerMenu != firstPlayer.inventoryMenu) {
            firstPlayer.doCloseContainer();
        }
        if (secondPlayer != null && secondPlayer.containerMenu != secondPlayer.inventoryMenu) {
            secondPlayer.doCloseContainer();
        }
        first.close();
        if (second != null) {
            second.close();
        }
        if (processing != null) {
            processing.close();
        }
    }
}
