package space.controlnet.ae2federation.test.p2p;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.world.OtherDimensionSite;

/**
 * TEST-ONLY: a consumer network in the overworld and a provider network in the nether, whose Routers reach each other
 * only through the {@link QuantumP2PCarrier}'s tunnels. The consumer is an ME chest with no energy of its own, west of its
 * Router, whose east face meets the overworld tunnel's front cable. In the nether, the tunnel's front cable leads south
 * to the provider's Router; south of that are the provider's ME chest, its creative energy cell, and east of the chest
 * an AE2 pattern provider.
 */
public final class QuantumP2PRouterScene implements AutoCloseable {
    public static final BlockPos SITE_SIZE = new BlockPos(4, 2, 8);
    public static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final BlockPos CONSUMER_ROUTER = QuantumP2PCarrier.OVERWORLD_FRONT.west();
    private static final BlockPos CONSUMER_CHEST = CONSUMER_ROUTER.west();
    private static final BlockPos PROVIDER_ROUTER = QuantumP2PCarrier.NETHER_FRONT.south();
    private static final BlockPos PROVIDER_CHEST = PROVIDER_ROUTER.south();
    private static final BlockPos PROVIDER_POWER = PROVIDER_CHEST.south();
    private static final BlockPos PATTERN_PROVIDER = PROVIDER_CHEST.east();

    private final GameTestHelper helper;
    private final OtherDimensionSite site;
    private final QuantumP2PCarrier carrier;
    private int step;

    public QuantumP2PRouterScene(GameTestHelper helper) {
        this.helper = helper;
        site = OtherDimensionSite.nether(helper, SITE_SIZE);
        carrier = new QuantumP2PCarrier(helper, site);
        helper.setBlock(CONSUMER_CHEST, AEBlocks.ME_CHEST.block());
        helper.<MEChestBlockEntity>getBlockEntity(CONSUMER_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
    }

    public OtherDimensionSite site() {
        return site;
    }

    /**
     * Builds the scene one step per call and fails until the two networks share a domain through the tunnels, every
     * rule is set, the provider holds nine iron and its pattern provider a pattern for stone.
     */
    public void advance() {
        if (step == 0) {
            helper.assertTrue(site.ready(), "Waiting for the nether site to tick: " + site.tickDiagnostics());
            site.setBlock(PROVIDER_CHEST, AEBlocks.ME_CHEST.block().defaultBlockState());
            site.<MEChestBlockEntity>getBlockEntity(PROVIDER_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
            site.setBlock(PROVIDER_POWER, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            site.setBlock(PATTERN_PROVIDER, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
            carrier.build();
            step = 1;
        }
        if (step == 1) {
            var provider = providerNode();
            helper.assertTrue(provider != null && provider.isActive() && consumerNode() != null
                    && consumerNode().hasGridBooted() && network(consumerGrid()) != null && network(providerGrid()) != null,
                    "Waiting for both networks to settle");
            helper.assertTrue(carrier.linked(), "Waiting for the Quantum Bridge to join the carrier's halves");
            carrier.pair();
            helper.setBlock(CONSUMER_ROUTER, RouterRegistration.SWITCH.get());
            helper.setBlock(QuantumP2PCarrier.OVERWORLD_FRONT, RouterRegistration.FEDERATION_CABLE.get());
            site.setBlock(QuantumP2PCarrier.NETHER_FRONT, RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
            site.setBlock(PROVIDER_ROUTER, RouterRegistration.SWITCH.get().defaultBlockState());
            step = 2;
        }
        if (step == 2) {
            helper.assertTrue(connected(), "Waiting for the tunnels to join the two Routers' domain");
            var policies = PolicyService.get(helper.getLevel());
            var result = policies.editAll(List.of(
                    new PolicyEdit(key(PolicyCapability.STORAGE), policies.revision(key(PolicyCapability.STORAGE)),
                            PolicyRule.storageDefaults()),
                    new PolicyEdit(key(PolicyCapability.ME_POWER), policies.revision(key(PolicyCapability.ME_POWER)),
                            PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY))),
                    new PolicyEdit(key(PolicyCapability.CRAFTING), policies.revision(key(PolicyCapability.CRAFTING)),
                            PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)))));
            helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "The rules must be accepted: " + result);
            helper.assertValueEqual(providerGrid().getStorageService().getInventory()
                    .insert(IRON, 9, Actionable.MODULATE, IActionSource.empty()), 9L, "The provider takes the iron");
            PatternProviderBlockEntity patterns = site.getBlockEntity(PATTERN_PROVIDER);
            patterns.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)), List.of(new GenericStack(stone(), 1))));
            patterns.getLogic().updatePatterns();
            step = 3;
        }
    }

    /** Whether the consumer's and the provider's networks share a domain. */
    public boolean connected() {
        var consumer = network(consumerGrid());
        var provider = network(providerGrid());
        if (consumer == null || provider == null) return false;
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var domains = registry.federationdomainsFor(consumer);
        return registry.federationdomainsFor(provider).stream().anyMatch(domains::contains);
    }

    /**
     * Whether the overworld tunnel's domain holds any nether node: the nether tunnel, the cable in front of it or the
     * provider's Router.
     */
    public boolean domainReachesNether() {
        var tunnel = carrier.overworldTunnel();
        if (tunnel == null) return false;
        var level = helper.getLevel();
        var nether = FederationDomainRegistryAccess.dimension(site.level());
        var node = FederationDomainRegistryAccess.nodeId(level, new FederationPort(tunnel.getBlockEntity().getBlockPos(),
                tunnel.getSide(), tunnel.getSide().getSerializedName()));
        return FederationDomainRegistryAccess.get(level).federationDomainOf(node)
                .map(domain -> domain.nodes().stream().anyMatch(other -> other.dimension().equals(nether)))
                .orElse(false);
    }

    public long consumerIron() {
        return consumerGrid().getStorageService().getInventory().getAvailableStacks().get(IRON);
    }

    /** Whether the consumer, which has no energy of its own, runs. */
    public boolean consumerPowered() {
        return consumerNode().isPowered();
    }

    public boolean consumerCraftsStone() {
        return consumerGrid().getCraftingService().isCraftable(stone());
    }

    public QuantumP2PCarrier carrier() {
        return carrier;
    }

    private PolicyKey key(PolicyCapability capability) {
        return new PolicyKey(network(consumerGrid()), network(providerGrid()), capability);
    }

    private static NetworkId network(IGrid grid) {
        return grid == null ? null : FederationDomainRegistryAccess.confirmedNetworkId(grid).orElse(null);
    }

    private static AEItemKey stone() {
        return AEItemKey.of(Items.STONE);
    }

    private IGrid consumerGrid() {
        var node = consumerNode();
        return node == null ? null : node.getGrid();
    }

    private IGrid providerGrid() {
        var node = providerNode();
        return node == null ? null : node.getGrid();
    }

    private IGridNode consumerNode() {
        MEChestBlockEntity chest = helper.getBlockEntity(CONSUMER_CHEST);
        return chest.getMainNode().getNode();
    }

    private IGridNode providerNode() {
        MEChestBlockEntity chest = site.getBlockEntity(PROVIDER_CHEST);
        return chest == null ? null : chest.getMainNode().getNode();
    }

    @Override
    public void close() {
        site.close();
    }
}
