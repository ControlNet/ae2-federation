package space.controlnet.ae2federation.test.energy;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.energy.DirectionalEnergySource;
import space.controlnet.ae2federation.energy.EnergyBindingService;
import space.controlnet.ae2federation.energy.EnergyCapabilityBinding;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;

public final class DirectionalEnergyFixture implements AutoCloseable {
    private static final BlockPos BASE = new BlockPos(5, 3, 5);
    private static final BlockPos PROVIDER_CELL = BASE.north().below();

    private final GameTestHelper helper;
    private final PolicyBridgeFixtures bridge;
    private boolean bridgePlaced;
    private PolicyKey key;

    public DirectionalEnergyFixture(GameTestHelper helper) {
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
        return providerNode != null && providerNode.hasGridBooted() && providerNode.getGrid() == providerGrid();
    }

    public PolicyKey key() {
        if (key == null) {
            key = new PolicyKey(bridge.mainNetwork(), bridge.outerNetwork(), PolicyCapability.ME_POWER);
        }
        return key;
    }

    public PolicyKey reverseKey() {
        return new PolicyKey(key().providerNetworkId(), key().consumerNetworkId(), PolicyCapability.ME_POWER);
    }

    public void enable() {
        var policies = PolicyService.get(helper.getLevel());
        var result = policies.edit(new PolicyEdit(key(), policies.revision(key()),
                PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY))));
        helper.assertTrue(result instanceof space.controlnet.ae2federation.policy.PolicyMutationResult.Accepted,
                "Directional ME power Policy must be accepted");
    }

    public EnergyBindingService bindings() {
        return EnergyBindingService.get(helper.getLevel());
    }

    public EnergyCapabilityBinding binding() {
        return bindings().capability(key()).orElseThrow(() -> new IllegalStateException(
                "No directional binding: key=" + key() + ", commonFabrics=" + commonFabricCount()
                        + ", relationships=" + bindings().relationshipCount()
                        + ", publications=" + bindings().publicationCount()
                        + ", withdrawals=" + bindings().withdrawalCount()
                        + ", configured=" + PolicyService.get(helper.getLevel()).configured(key()).isPresent()
                        + ", reverseConfigured="
                        + PolicyService.get(helper.getLevel()).configured(reverseKey()).isPresent()));
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

    public double reverseRouteExtract(double amount) {
        return directionalSource(providerGrid()).extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.ONE);
    }

    public double providerStored() {
        var stored = 0.0;
        for (var node : providerGrid().getNodes()) {
            var source = node.getService(appeng.api.networking.energy.IAEPowerStorage.class);
            if (source != null && !(source instanceof DirectionalEnergySource)) {
                stored += source.getAECurrentPower();
            }
        }
        return stored;
    }

    public double providerCapacity() {
        return providerCell().getAEMaxPower();
    }

    public boolean consumerPowered() {
        return consumerGrid().getEnergyService().isNetworkPowered();
    }

    public double consumerAvailable() {
        return consumerGrid().getEnergyService().extractAEPower(Double.MAX_VALUE, Actionable.SIMULATE,
                PowerMultiplier.ONE);
    }

    public double consumerStored() {
        var stored = 0.0;
        for (var node : consumerGrid().getNodes()) {
            var source = node.getService(appeng.api.networking.energy.IAEPowerStorage.class);
            if (source != null && !(source instanceof DirectionalEnergySource)) {
                stored += source.getAECurrentPower();
            }
        }
        return stored;
    }

    public appeng.api.networking.IGrid consumerGrid() {
        return bridge.mainGrid();
    }

    public appeng.api.networking.IGrid providerGrid() {
        return bridge.outerGrid();
    }

    public int consumerChannels() {
        return bridge.consumerChest().getMainNode().getNode().getUsedChannels();
    }

    public int providerChannels() {
        return bridge.providerChest().getMainNode().getNode().getUsedChannels();
    }

    public void addDuplicateBridge() {
        bridge.placeSecondBridge();
    }

    public boolean duplicateBridgeReady() {
        if (!bridge.secondBridgeReady()) {
            bridge.refreshSecondBridge();
            return false;
        }
        bindings().observeConnectedGrids(consumerGrid(), providerGrid());
        return true;
    }

    public long commonFabricCount() {
        var registry = space.controlnet.ae2federation.fabric.FabricRegistryAccess.get(helper.getLevel());
        var provider = registry.fabricsFor(key().providerNetworkId());
        return registry.fabricsFor(key().consumerNetworkId()).stream().filter(provider::contains).count();
    }

    public void disconnect() {
        bridge.removeFirstBridge();
        if (bridge.secondBridgeReady()) {
            bridge.removeSecondBridge();
        }
    }

    public void removeProviderSources() {
        helper.setBlock(PROVIDER_CELL, Blocks.AIR);
        bridge.removeProviderChest();
    }

    public void replaceProviderSource() {
        helper.setBlock(PROVIDER_CELL, LargeEnergyCellRegistration.BLOCK.get());
    }

    public boolean replacementSourceReady() {
        var node = providerCell().getMainNode().getNode();
        return node != null && node.hasGridBooted() && node.getGrid() == providerGrid();
    }

    @Override
    public void close() {
        bridge.close();
    }

    private EnergyCellBlockEntity providerCell() {
        return helper.getBlockEntity(PROVIDER_CELL);
    }

    private static DirectionalEnergySource directionalSource(appeng.api.networking.IGrid grid) {
        for (var node : grid.getNodes()) {
            var source = node.getService(appeng.api.networking.energy.IAEPowerStorage.class);
            if (source instanceof DirectionalEnergySource directional) {
                return directional;
            }
        }
        throw new IllegalStateException("Grid has no Federation directional energy source");
    }
}
