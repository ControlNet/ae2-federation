package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.me.service.CraftingService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.world.OtherDimensionSite;
import space.controlnet.ae2federation.test.world.QuantumBridges;

/**
 * TEST-ONLY: a consumer in the overworld uses a Federation Pattern Provider that sits in the nether. In the overworld,
 * {@link PolicyBridgeFixtures}' two networks share a Bridge and the consumer has a 1k crafting storage beside its chest;
 * the provider (outer) network's cable leads west to a real Quantum Network Bridge. Its twin is in a nether site with
 * its own creative energy cell and the provider network's only pattern provider, a Federation Provider, whose
 * Federation face looks south along a Federation cable at an Endpoint. The Endpoint's subnet is an ME chest and a
 * charged energy cell south of it. A stand-in machine takes the cobblestone the Endpoint delivers into its subnet and
 * returns a diamond for each through the Endpoint, as a real machine beside it would.
 *
 * <p>The provider network spans both dimensions, so the nether Provider's lane is on the same Grid as the overworld
 * part of the provider network: only a server-wide look at Federation Providers finds it.
 */
public final class NetherProviderProjectionScene implements AutoCloseable {
    public static final BlockPos SITE_SIZE = new BlockPos(5, 2, 9);
    public static final AEItemKey INPUT = AEItemKey.of(Items.COBBLESTONE);
    public static final AEItemKey OUTPUT = AEItemKey.of(Items.DIAMOND);
    private static final BlockPos BASE = new BlockPos(5, 3, 5);
    private static final BlockPos CONSUMER_CPU = BASE.south().west();
    /** The overworld Quantum Bridge's link chamber; its ring's east edge touches the provider network's cable. */
    private static final BlockPos OVERWORLD_CHAMBER = BASE.offset(-4, 0, -1);
    private static final BlockPos NETHER_CHAMBER = new BlockPos(1, 0, 1);
    private static final BlockPos NETHER_CELL = NETHER_CHAMBER.east(2);
    public static final BlockPos PROVIDER = NETHER_CHAMBER.south(2);
    private static final BlockPos FEDERATION_CABLE = PROVIDER.south();
    public static final BlockPos ENDPOINT = FEDERATION_CABLE.south();
    private static final BlockPos SUBNET_CHEST = ENDPOINT.south();
    private static final BlockPos SUBNET_CELL = SUBNET_CHEST.south();
    /** The Endpoint's side the stand-in machine returns its products through. */
    private static final Direction RETURN_SIDE = Direction.SOUTH;

    private final GameTestHelper helper;
    private final PolicyBridgeFixtures bridge;
    private final OtherDimensionSite site;
    private final long frequency;
    private final NetworkId subnet = NetworkId.create();
    private @Nullable NetworkId consumerId;
    private @Nullable NetworkId providerId;
    private @Nullable Future<ICraftingPlan> planFuture;
    private long returned;
    private boolean machineRuns = true;
    private int step;

    public NetherProviderProjectionScene(GameTestHelper helper) {
        this.helper = helper;
        frequency = QuantumBridges.randomFrequency(helper);
        bridge = new PolicyBridgeFixtures(helper, BASE);
        bridge.installStorageCells();
        site = OtherDimensionSite.nether(helper, SITE_SIZE);
    }

    /**
     * Advances the build one step per call; true once the nether Provider has a lane to its claimed Endpoint on the
     * provider network.
     */
    public boolean ready() {
        if (!bridge.networksSettled() || !site.ready()) return false;
        switch (step) {
            case 0 -> {
                var outer = providerNetwork();
                for (var cable : new BlockPos[] { BASE.north().west(), BASE.north().west(2) }) {
                    PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable), null, null,
                            AEParts.GLASS_CABLE.item(AEColor.BLUE));
                }
                QuantumBridges.build(OVERWORLD_CHAMBER, frequency, outer, (position, state) -> {
                    helper.setBlock(position, state);
                    return helper.getLevel().getBlockEntity(helper.absolutePos(position));
                });
                QuantumBridges.build(NETHER_CHAMBER, frequency, outer, (position, state) -> {
                    site.setBlock(position, state);
                    return site.getBlockEntity(position);
                });
                place(NETHER_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), outer);
                place(PROVIDER, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.SOUTH), outer);
                site.setBlock(FEDERATION_CABLE, RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
                place(ENDPOINT, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.NORTH), subnet);
                place(SUBNET_CHEST, AEBlocks.ME_CHEST.block().defaultBlockState(), subnet);
                site.<MEChestBlockEntity>getBlockEntity(SUBNET_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
                place(SUBNET_CELL, AEBlocks.ENERGY_CELL.block().defaultBlockState(), subnet);
                EnergyCellBlockEntity cell = site.getBlockEntity(SUBNET_CELL);
                cell.injectAEPower(100_000, Actionable.MODULATE);
                helper.setBlock(CONSUMER_CPU, AEBlocks.CRAFTING_STORAGE_1K.block());
                helper.<CraftingBlockEntity>getBlockEntity(CONSUMER_CPU).getMainNode()
                        .loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", consumerNetwork()));
                step = 1;
                return false;
            }
            case 1 -> {
                bridge.placeFirstBridge();
                step = 2;
                return false;
            }
            case 2 -> {
                if (!bridge.firstBridgeReady()) {
                    bridge.refreshFirstBridge();
                    return false;
                }
                if (provider().getMainNode().getGrid() != providerGrid() || binding() == null
                        || !active(node(site.getBlockEntity(SUBNET_CHEST)))) return false;
                var endpointNode = FederationDomainRegistryAccess.nodeId(site.level(), site.absolute(ENDPOINT));
                var registry = FederationDomainRegistryAccess.get(helper.getLevel());
                if (registry.federationdomainsFor(providerNetwork()).stream().map(registry::federationDomain)
                        .noneMatch(domain -> domain.isPresent() && domain.get().nodes().contains(endpointNode))) {
                    return false;
                }
                var remainder = provider().getTerminalPatternInventory().insertItem(0,
                        PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(INPUT, 1)),
                                List.of(new GenericStack(OUTPUT, 1))), false);
                helper.assertTrue(remainder.isEmpty(), "The Provider takes the pattern");
                var status = provider().toggleEndpoint(provider().mappedProvider().mappingHandle(0), binding());
                helper.assertTrue(status.startsWith("accepted-"), "The nether Endpoint must be mapped: " + status);
                step = 3;
                return false;
            }
            default -> {
                return provider().laneCount() == 1 && providerGrid().getCraftingService().isCraftable(OUTPUT)
                        && consumerGrid().getCraftingService().getCpus().size() == 1;
            }
        }
    }

    /** The consumer uses the provider's crafting and its storage, in one edit. */
    public void enableRules() {
        var policies = PolicyService.get(helper.getLevel());
        var crafting = new PolicyKey(consumerNetwork(), providerNetwork(), PolicyCapability.CRAFTING);
        var storage = new PolicyKey(consumerNetwork(), providerNetwork(), PolicyCapability.STORAGE);
        var result = policies.editAll(List.of(
                new PolicyEdit(storage, policies.revision(storage), PolicyRule.storageDefaults()),
                new PolicyEdit(crafting, policies.revision(crafting),
                        PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)))));
        helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "The rules must be accepted: " + result);
    }

    public int projections() {
        return CraftingProjectionService.get(helper.getLevel()).projectionCount(consumerNetwork(), providerNetwork());
    }

    /** How many crafting providers the consumer's crafting service lists for the Provider's pattern. */
    public int consumerProviders() {
        var service = (CraftingService) consumerGrid().getCraftingService();
        int count = 0;
        for (var details : service.getCraftingFor(OUTPUT)) {
            for (var ignored : service.getProviders(details)) count++;
        }
        return count;
    }

    public boolean consumerCrafts() {
        return consumerGrid().getCraftingService().isCraftable(OUTPUT);
    }

    public void putInConsumer(AEKey what, long amount) {
        helper.assertValueEqual(bridge.consumerChest().getOriginalCellInventory(0).insert(what, amount,
                Actionable.MODULATE, IActionSource.empty()), amount, "The consumer's chest takes the inputs");
    }

    /** Plans {@code amount} diamonds on the consumer's own crafting service, as its ME Terminal does. */
    public void begin(long amount) {
        var node = bridge.consumerChest().getMainNode().getNode();
        planFuture = consumerGrid().getCraftingService().beginCraftingCalculation(helper.getLevel(),
                new ICraftingSimulationRequester() {
                    @Override
                    public IActionSource getActionSource() {
                        return IActionSource.empty();
                    }

                    @Override
                    public IGridNode getGridNode() {
                        return node;
                    }
                }, OUTPUT, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    /** True once the plan is computed and the consumer's CPU has taken the job. */
    public boolean submitWhenPlanned() {
        if (planFuture == null || !planFuture.isDone()) return false;
        try {
            var plan = planFuture.get();
            helper.assertTrue(!plan.simulation(), "The plan must be complete: " + plan.missingItems());
            var result = consumerGrid().getCraftingService().submitJob(plan, null, null, true,
                    IActionSource.empty());
            helper.assertTrue(result.successful(), "The consumer's CPU must take the job: " + result.errorCode());
            planFuture = null;
            return true;
        } catch (InterruptedException | ExecutionException exception) {
            throw new IllegalStateException("The crafting calculation failed", exception);
        }
    }

    /** The stand-in machine: takes the cobblestone delivered to the subnet and returns a diamond for each. */
    public void runMachine() {
        // Looking at an unloaded chunk's block entity would load it.
        if (!machineRuns || !site.ticking()) return;
        var subnetNode = node(site.getBlockEntity(SUBNET_CHEST));
        if (subnetNode == null || subnetNode.getGrid() == null) return;
        var storage = subnetNode.getGrid().getStorageService().getInventory();
        var available = storage.extract(INPUT, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        var handler = site.level().getCapability(Capabilities.ItemHandler.BLOCK, site.absolute(ENDPOINT), RETURN_SIDE);
        if (available <= 0 || handler == null) return;
        var taken = storage.extract(INPUT, available, Actionable.MODULATE, IActionSource.empty());
        var remainder = new ItemStack(Items.DIAMOND, (int) taken);
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);
        }
        helper.assertTrue(remainder.isEmpty(), "The Endpoint must take the machine's products");
        returned += taken;
    }

    public long returned() {
        return returned;
    }

    public boolean consumerBusy() {
        return consumerGrid().getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy());
    }

    public long inConsumerChest(AEKey what) {
        return bridge.consumerChest().getOriginalCellInventory(0).extract(what, Long.MAX_VALUE, Actionable.SIMULATE,
                IActionSource.empty());
    }

    public long onProviderNetwork(AEKey what) {
        return bridge.providerChest().getOriginalCellInventory(0).extract(what, Long.MAX_VALUE, Actionable.SIMULATE,
                IActionSource.empty());
    }

    public long owed() {
        return CraftingReturnLedger.get(helper.getLevel()).owed(providerNetwork(), consumerNetwork(), OUTPUT);
    }

    /** What the Endpoint's subnet holds of {@code key}: inputs delivered and not yet taken by the machine. */
    public long subnetAmount(AEKey key) {
        if (!site.ticking()) return 0;
        var subnetNode = node(site.getBlockEntity(SUBNET_CHEST));
        if (subnetNode == null || subnetNode.getGrid() == null) return 0;
        return subnetNode.getGrid().getStorageService().getInventory().extract(key, Long.MAX_VALUE,
                Actionable.SIMULATE, IActionSource.empty());
    }

    public void machineRuns(boolean runs) {
        machineRuns = runs;
    }

    public long ledgerSweeps() {
        return CraftingProjectionService.get(helper.getLevel()).ledgerSweeps();
    }

    public FederationPatternProviderBlockEntity provider() {
        return site.getBlockEntity(PROVIDER);
    }

    public EndpointBlockEntity endpoint() {
        return site.getBlockEntity(ENDPOINT);
    }

    public OtherDimensionSite site() {
        return site;
    }

    public IGrid consumerGrid() {
        return bridge.mainGrid();
    }

    public IGrid providerGrid() {
        return bridge.outerGrid();
    }

    public NetworkId consumerNetwork() {
        if (consumerId == null) consumerId = bridge.mainNetwork();
        return consumerId;
    }

    public NetworkId providerNetwork() {
        if (providerId == null) providerId = bridge.outerNetwork();
        return providerId;
    }

    private @Nullable EndpointTargetBinding binding() {
        return EndpointTargetBinding.findEndpoint(site.level(), site.absolute(ENDPOINT));
    }

    private void place(BlockPos position, BlockState state, NetworkId network) {
        site.setBlock(position, state);
        QuantumBridges.seed(site.getBlockEntity(position), network);
    }

    private static @Nullable IGridNode node(@Nullable BlockEntity entity) {
        return entity instanceof IGridConnectedBlockEntity connected ? connected.getMainNode().getNode() : null;
    }

    private static boolean active(@Nullable IGridNode node) {
        return node != null && node.isActive() && node.hasGridBooted();
    }

    @Override
    public void close() {
        bridge.close();
        site.close();
    }
}
