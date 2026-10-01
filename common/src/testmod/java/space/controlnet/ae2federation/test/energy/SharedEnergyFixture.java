package space.controlnet.ae2federation.test.energy;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.energy.EnergySharingService;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;

/**
 * Two native Grids joined by one Federation bridge: the main Grid holds no energy storage, the outer Grid holds one
 * large energy cell. An ME power rule between them, in either direction, puts both in one shared energy pool.
 */
public final class SharedEnergyFixture implements AutoCloseable {
    private static final BlockPos BASE = new BlockPos(5, 3, 5);
    private static final BlockPos PROVIDER_CELL = BASE.north().below();

    private final GameTestHelper helper;
    private final PolicyBridgeFixtures bridge;
    private boolean bridgePlaced;
    private PolicyKey key;

    public SharedEnergyFixture(GameTestHelper helper) {
        this.helper = helper;
        bridge = new PolicyBridgeFixtures(helper, BASE);
        helper.setBlock(PROVIDER_CELL, LargeEnergyCellRegistration.BLOCK.get());
    }

    public boolean ready() {
        if (!bridge.networksSettled()) {
            return false;
        }
        if (!bridgePlaced) {
            bridge.placeFirstBridge();
            bridgePlaced = true;
            return false;
        }
        if (!bridge.firstBridgeReady()) {
            bridge.refreshFirstBridge();
            return false;
        }
        var providerNode = providerCell().getMainNode().getNode();
        if (providerNode == null || !providerNode.hasGridBooted() || providerNode.getGrid() != providerGrid()) {
            return false;
        }
        EnergySharingService.get(helper.getLevel()).observeConnectedGrids(consumerGrid(), providerGrid());
        return true;
    }

    /** The ME power rule in which the storage-less main Grid draws on the outer Grid. */
    public PolicyKey key() {
        if (key == null) {
            key = new PolicyKey(bridge.mainNetwork(), bridge.outerNetwork(), PolicyCapability.ME_POWER);
        }
        return key;
    }

    /** The same pair the other way round: the outer Grid draws on the main Grid. */
    public PolicyKey reverseKey() {
        return new PolicyKey(key().providerNetworkId(), key().consumerNetworkId(), PolicyCapability.ME_POWER);
    }

    public void enable(PolicyKey rule) {
        edit(rule, PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
    }

    public void disable(PolicyKey rule) {
        edit(rule, PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)).withEnabled(false));
    }

    public boolean configured(PolicyKey rule) {
        return PolicyService.get(helper.getLevel()).configured(rule).isPresent();
    }

    public boolean enabled(PolicyKey rule) {
        return PolicyService.get(helper.getLevel()).configured(rule).map(record -> record.rule().enabled()).orElse(false);
    }

    /** A policy editor session opened on the bridge by a player standing beside it. */
    public space.controlnet.ae2federation.client.policy.FederationDomainPolicySession session() {
        var context = bridge.firstBridgeContext();
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(context.position()).add(0, 1, 0));
        return space.controlnet.ae2federation.client.policy.FederationDomainPolicySession.forBridge(player, context);
    }

    /** Flips the pair editor's switch on {@code rule}, as a click with the rule's current revision would. */
    public boolean switchRule(space.controlnet.ae2federation.client.policy.FederationDomainPolicySession session,
            PolicyKey rule, boolean enabled) {
        return session.setPolicy(new space.controlnet.ae2federation.client.policy.PolicySwitchTarget(rule, enabled,
                PolicyService.get(helper.getLevel()).revision(rule)).encode());
    }

    public boolean shares(PolicyKey rule) {
        return EnergySharingService.shares(helper.getLevel(), rule);
    }

    public void charge(double amount) {
        if (providerStored() < amount) {
            var overflow = providerCell().injectAEPower(amount - providerStored(), Actionable.MODULATE);
            helper.assertValueEqual(overflow, 0.0, "Provider native energy cell must accept required charge");
        }
    }

    public double simulate(double amount) {
        return consumerGrid().getEnergyService().extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.ONE);
    }

    public double extract(double amount) {
        return consumerGrid().getEnergyService().extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.ONE);
    }

    /** Energy the main Grid pushes into its own energy service, which a shared pool stores wherever it has room. */
    public double injectThroughConsumer(double amount) {
        return consumerGrid().getEnergyService().injectPower(amount, Actionable.MODULATE);
    }

    public double providerStored() {
        return stored(providerGrid());
    }

    public double providerCapacity() {
        return providerCell().getAEMaxPower();
    }

    public boolean consumerPowered() {
        return consumerGrid().getEnergyService().isNetworkPowered();
    }

    public double consumerAvailable() {
        return available(consumerGrid());
    }

    public double providerAvailable() {
        return available(providerGrid());
    }

    public double consumerStored() {
        return stored(consumerGrid());
    }

    public IGrid consumerGrid() {
        return bridge.mainGrid();
    }

    public IGrid providerGrid() {
        return bridge.outerGrid();
    }

    public int consumerChannels() {
        return bridge.consumerChest().getMainNode().getNode().getUsedChannels();
    }

    public int providerChannels() {
        return bridge.providerChest().getMainNode().getNode().getUsedChannels();
    }

    public void disconnect() {
        bridge.removeFirstBridge();
    }

    @Override
    public void close() {
        bridge.close();
    }

    private void edit(PolicyKey rule, PolicyRule next) {
        var policies = PolicyService.get(helper.getLevel());
        var result = policies.edit(new PolicyEdit(rule, policies.revision(rule), next));
        helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "ME power rule edit must be accepted");
    }

    private EnergyCellBlockEntity providerCell() {
        return helper.getBlockEntity(PROVIDER_CELL);
    }

    private static double available(IGrid grid) {
        return grid.getEnergyService().extractAEPower(Double.MAX_VALUE, Actionable.SIMULATE, PowerMultiplier.ONE);
    }

    private static double stored(IGrid grid) {
        var stored = 0.0;
        for (var node : grid.getNodes()) {
            var source = node.getService(IAEPowerStorage.class);
            if (source != null) {
                stored += source.getAECurrentPower();
            }
        }
        return stored;
    }
}
