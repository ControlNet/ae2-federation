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
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyDelete;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.processing.ProviderTargetRuntimeFixtures;
import space.controlnet.ae2federation.test.world.MockServerPlayers;

final class RealObservationScene implements AutoCloseable {
    private final GameTestHelper helper;
    private final PolicyBridgeFixtures first;
    private final boolean withProcessing;
    private PolicyBridgeFixtures second;
    private ProviderTargetRuntimeFixtures processing;
    private ServerPlayer firstPlayer;
    private ServerPlayer secondPlayer;
    private boolean placed;
    private boolean federationdomainsReady;
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
        if (!federationdomainsReady) {
            first.refreshFirstBridge();
            second.refreshFirstBridge();
            if (!first.firstBridgeReady() || !second.firstBridgeReady()) {
                status = "bridge-domains";
                return false;
            }
            federationdomainsReady = true;
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
        if (!processing.connectFederationDomain()) {
            status = "processing-domain";
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
        helper.assertTrue(editObservedPolicy(false), "A Crafting rule addition must be accepted");
        helper.assertTrue(processing.pushOnce(), "Native Processing send must accept");
        var handler = processing.endpointBinding().runtime().itemReturn(Direction.NORTH).orElseThrow();
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.GOLD_INGOT), false).isEmpty(),
                "Native aggregate return must be accepted");
    }

    boolean removePolicy() {
        return editObservedPolicy(true);
    }

    /** The domain the Endpoint's Federation face joins; a Bridge's direct domain has no nodes to hold an Endpoint. */
    FederationDomainReference processingScope() {
        return processing.federationDomain().orElseThrow().reference();
    }

    /**
     * Adds or deletes a disabled Crafting rule between the Bridge's two networks: a policy change that is visible in the
     * scoped snapshot but starts no binding. Processing needs no rule.
     */
    private boolean editObservedPolicy(boolean delete) {
        var service = PolicyService.get(helper.getLevel());
        var key = new PolicyKey(first.mainNetwork(), first.outerNetwork(), PolicyCapability.CRAFTING);
        var result = delete ? service.delete(new PolicyDelete(key, service.revision(key)))
                : service.edit(new PolicyEdit(key, service.revision(key),
                        PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)).withEnabled(false)));
        return result instanceof PolicyMutationResult.Accepted;
    }

    FederationDomainReference firstScope() {
        return scope(first);
    }

    space.controlnet.ae2federation.bridge.BridgeRightClickContext firstBridgeContext() {
        return first.firstBridgeContext();
    }

    boolean wakeNativeTicker() {
        return processing.wakeNativeTicker();
    }

    java.util.List<Long> nativeTickerInvocations() {
        return processing.nativeTickerInvocations();
    }

    int nativeTickerDelegateCount() {
        return processing.nativeTickerDelegateCount();
    }

    int nativeProgressOwnerIdentity() {
        return processing.nativeProgressOwnerIdentity();
    }

    FederationDomainReference secondScope() {
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
        var player = MockServerPlayers.inLevel(helper);
        player.setPos(Vec3.atCenterOf(fixture.firstBridgeContext().position()));
        ObservationGameTestPlayerTransport.install(player);
        helper.assertTrue(FederationDomainPolicyMenu.openBridge(player, fixture.firstBridgeContext()),
                "Production Federation Domain policy menu must open");
        return player;
    }

    private FederationDomainReference scope(PolicyBridgeFixtures fixture) {
        var registry = space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.get(helper.getLevel());
        return registry.federationdomainsFor(fixture.mainNetwork()).stream()
                .filter(id -> registry.federationdomainsFor(fixture.outerNetwork()).contains(id)).findFirst()
                .flatMap(registry::federationDomain).orElseThrow().reference();
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
