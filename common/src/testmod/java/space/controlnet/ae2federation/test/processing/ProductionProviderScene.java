package space.controlnet.ae2federation.test.processing;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.BaseActionSource;
import appeng.me.service.CraftingService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.router.RouterRegistration;
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

/**
 * World built only from production blocks: a source Grid (creative energy, ME Chest, 1k crafting CPU and the
 * production ME Federation Pattern Provider facing east), Federation Cable to a Router, and three Endpoint Subnets on the
 * Router's north, east and south faces. A test machine consumes each Subnet's delivered input and returns the product
 * through the Endpoint's item return capability, exactly as an external machine would.
 */
public final class ProductionProviderScene {
    public enum Target {
        A, B, C
    }

    public static final BlockPos SOURCE_ENERGY = new BlockPos(1, 1, 3);
    public static final BlockPos SOURCE_CHEST = new BlockPos(2, 1, 3);
    public static final BlockPos SOURCE_CPU = new BlockPos(3, 1, 3);
    public static final BlockPos PROVIDER = new BlockPos(4, 1, 3);
    public static final BlockPos CABLE_NEAR = new BlockPos(5, 1, 3);
    public static final BlockPos CABLE_FAR = new BlockPos(6, 1, 3);
    public static final BlockPos ROUTER = new BlockPos(7, 1, 3);
    private static final Map<Target, BlockPos[]> SUBNETS = new EnumMap<>(Map.of(
            Target.A, new BlockPos[] { new BlockPos(7, 1, 4), new BlockPos(7, 1, 5), new BlockPos(7, 1, 6) },
            Target.B, new BlockPos[] { new BlockPos(8, 1, 3), new BlockPos(8, 2, 3), new BlockPos(8, 3, 3) },
            Target.C, new BlockPos[] { new BlockPos(7, 1, 2), new BlockPos(7, 1, 1), new BlockPos(7, 1, 0) }));
    private static final Map<Target, Direction> RETURN_SIDE = new EnumMap<>(Map.of(
            Target.A, Direction.SOUTH, Target.B, Direction.UP, Target.C, Direction.NORTH));
    public static final AEItemKey INPUT = AEItemKey.of(Items.COBBLESTONE);
    public static final AEItemKey OUTPUT = AEItemKey.of(Items.DIAMOND);

    private final GameTestHelper helper;
    private final NetworkId sourceNetwork = NetworkId.create();
    private final Map<Target, NetworkId> targetNetworks = new EnumMap<>(Target.class);
    private final Map<Target, Long> consumed = new EnumMap<>(Target.class);
    private final Set<Target> stalled = java.util.EnumSet.noneOf(Target.class);
    private Future<ICraftingPlan> planFuture;
    private boolean routerPlaced;

    public ProductionProviderScene(GameTestHelper helper) {
        this.helper = helper;
        for (var target : Target.values()) {
            targetNetworks.put(target, NetworkId.create());
            consumed.put(target, 0L);
        }
        placeNative(SOURCE_ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), sourceNetwork);
        placeNative(SOURCE_CHEST, AEBlocks.ME_CHEST.block().defaultBlockState(), sourceNetwork);
        helper.<MEChestBlockEntity>getBlockEntity(SOURCE_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
        placeNative(SOURCE_CPU, AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState(), sourceNetwork);
        placeProvider(Direction.EAST);
        helper.setBlock(CABLE_NEAR, RouterRegistration.FEDERATION_CABLE.get());
        helper.setBlock(CABLE_FAR, RouterRegistration.FEDERATION_CABLE.get());
        for (var target : Target.values()) {
            var positions = SUBNETS.get(target);
            placeNative(positions[0], ProcessingRegistration.ENDPOINT.get().defaultBlockState(),
                    targetNetworks.get(target));
            placeNative(positions[1], AEBlocks.ME_CHEST.block().defaultBlockState(), targetNetworks.get(target));
            helper.<MEChestBlockEntity>getBlockEntity(positions[1]).setCell(AEItems.ITEM_CELL_1K.stack());
            placeNative(positions[2], AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(),
                    targetNetworks.get(target));
        }
    }

    // ---- topology

    public void placeProvider(Direction federationFace) {
        helper.setBlock(PROVIDER, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, federationFace));
        seed(PROVIDER, sourceNetwork);
    }

    public void rotateProvider(Direction federationFace) {
        var level = helper.getLevel();
        var position = helper.absolutePos(PROVIDER);
        level.setBlockAndUpdate(position, level.getBlockState(position).setValue(BlockStateProperties.FACING,
                federationFace));
    }

    /** Waits for native nodes and identities, then places the Router so its face ports adopt settled identities. */
    public String topologyReadiness() {
        var source = node(SOURCE_CHEST);
        var provider = node(PROVIDER);
        if (source == null || provider == null || !source.isActive() || !provider.isActive()) {
            return "source-inactive";
        }
        if (source.getGrid() != provider.getGrid()) {
            return "source-not-joined";
        }
        if (FederationDomainRegistryAccess.confirmedNetworkId(source.getGrid()).isEmpty()) {
            return "source-identity";
        }
        for (var target : Target.values()) {
            var endpoint = node(SUBNETS.get(target)[0]);
            var chest = node(SUBNETS.get(target)[1]);
            if (endpoint == null || chest == null || !endpoint.isActive() || endpoint.getGrid() != chest.getGrid()) {
                return "target-" + target + "-inactive";
            }
            if (FederationDomainRegistryAccess.confirmedNetworkId(endpoint.getGrid()).isEmpty()) {
                return "target-" + target + "-identity";
            }
            if (binding(target) == null) {
                return "target-" + target + "-binding";
            }
        }
        if (!routerPlaced) {
            helper.setBlock(ROUTER, RouterRegistration.ROUTER.get());
            routerPlaced = true;
            return "router-placed";
        }
        var networks = new ArrayList<NetworkId>();
        networks.add(FederationDomainRegistryAccess.confirmedNetworkId(source.getGrid()).orElseThrow());
        for (var target : Target.values()) {
            networks.add(FederationDomainRegistryAccess.confirmedNetworkId(targetGrid(target)).orElseThrow());
        }
        var domain = FederationDomainRegistryAccess.get(helper.getLevel()).snapshot().federationDomains().values().stream()
                .anyMatch(federationDomain -> federationDomain.memberships().keySet().containsAll(networks));
        if (!domain) {
            return "domain-pending";
        }
        if (sourceGrid().getCraftingService().getCpus().size() != 1) {
            return "cpu-pending";
        }
        return "ready";
    }

    // ---- policy and mapping

    public boolean setPolicy(Target target, boolean enabled) {
        var service = PolicyService.get(helper.getLevel());
        var key = policyKey(target);
        var rule = PolicyRule.enabled(Set.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY)).withEnabled(enabled);
        return service.edit(new PolicyEdit(key, service.revision(key), rule)) instanceof PolicyMutationResult.Accepted;
    }

    private PolicyKey policyKey(Target target) {
        return new PolicyKey(FederationDomainRegistryAccess.confirmedNetworkId(sourceGrid()).orElseThrow(),
                FederationDomainRegistryAccess.confirmedNetworkId(targetGrid(target)).orElseThrow(),
                PolicyCapability.PROCESSING);
    }

    public void installPattern(int slot) {
        var pattern = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(INPUT, 1)),
                List.of(new GenericStack(OUTPUT, 1)));
        // Pattern Access Terminal and the native menu insert into this same native inventory.
        var remainder = provider().getTerminalPatternInventory().insertItem(slot, pattern, false);
        helper.assertTrue(remainder.isEmpty(), "Production Provider must accept an encoded Processing Pattern");
    }

    public String map(int slot, Target target) {
        var provider = provider();
        return provider.toggleEndpoint(provider.mappedProvider().mappingHandle(slot), binding(target));
    }

    public IPatternDetails pattern(int slot) {
        return PatternDetailsHelper.decodePattern(provider().getTerminalPatternInventory().getStackInSlot(slot),
                helper.getLevel());
    }

    public int publishedMediums(int slot) {
        var pattern = pattern(slot);
        var count = new int[1];
        ((CraftingService) sourceGrid().getCraftingService()).getProviders(pattern).forEach(ignored -> count[0]++);
        return count[0];
    }

    // ---- crafting through the native planner and CPU

    public void insertSourceInput(long amount) {
        var inserted = sourceGrid().getStorageService().getInventory().insert(INPUT, amount, Actionable.MODULATE,
                IActionSource.empty());
        helper.assertValueEqual(inserted, amount, "Source ME storage must accept the starting input");
    }

    public void beginCraft(long amount) {
        var sourceNode = node(SOURCE_CHEST);
        var requester = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return new BaseActionSource();
            }

            @Override
            public IGridNode getGridNode() {
                return sourceNode;
            }
        };
        planFuture = sourceGrid().getCraftingService().beginCraftingCalculation(helper.getLevel(), requester, OUTPUT,
                amount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    /** Returns true once the native plan was computed and submitted to the native CPU. */
    public boolean submitWhenPlanned() {
        if (planFuture == null || !planFuture.isDone()) {
            return false;
        }
        try {
            var plan = planFuture.get();
            helper.assertTrue(!plan.simulation(), "Native plan must be complete, not a missing-items simulation");
            var result = sourceGrid().getCraftingService().submitJob(plan, null, null, false, new BaseActionSource());
            helper.assertTrue(result.successful(), "Native CPU must accept the job: " + result.errorCode());
            planFuture = null;
            return true;
        } catch (InterruptedException | java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Native crafting calculation failed", exception);
        }
    }

    public boolean cpuBusy() {
        return sourceGrid().getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy());
    }

    public void cancelJobs() {
        sourceGrid().getCraftingService().getCpus().forEach(cpu -> cpu.cancelJob());
    }

    // ---- test machine

    public void stall(Target target, boolean value) {
        if (value) {
            stalled.add(target);
        } else {
            stalled.remove(target);
        }
    }

    /** Consumes delivered input in every non-stalled Subnet and returns one product per input via the Endpoint. */
    public void runMachines() {
        for (var target : Target.values()) {
            var chest = node(SUBNETS.get(target)[1]);
            if (stalled.contains(target) || chest == null) {
                continue;
            }
            var storage = chest.getGrid().getStorageService().getInventory();
            var available = storage.extract(INPUT, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
            if (available <= 0) {
                continue;
            }
            var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                    helper.absolutePos(SUBNETS.get(target)[0]), RETURN_SIDE.get(target));
            if (handler == null) {
                continue;
            }
            var extracted = storage.extract(INPUT, available, Actionable.MODULATE, IActionSource.empty());
            var remainder = new ItemStack(Items.DIAMOND, (int) extracted);
            for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = handler.insertItem(slot, remainder, false);
            }
            helper.assertTrue(remainder.isEmpty(), "Endpoint return must accept the machine product");
            consumed.merge(target, extracted, Long::sum);
        }
    }

    public long consumed(Target target) {
        return consumed.get(target);
    }

    public long consumedTotal() {
        return consumed.values().stream().mapToLong(Long::longValue).sum();
    }

    // ---- observation

    public String diagnostics() {
        var provider = provider();
        var text = new StringBuilder("cpuBusy=" + cpuBusy() + ",sourceInput=" + sourceAmount(INPUT));
        for (int lane = 0; lane < provider.laneCount(); lane++) {
            var index = lane;
            text.append(",lane").append(index).append("/patterns=").append(provider.lane(lane).getAvailablePatterns().size())
                    .append("/send=").append(provider.lane(lane).hasPendingSend());
        }
        for (var target : Target.values()) {
            text.append(",").append(target).append("=").append(targetAmount(target, INPUT));
        }
        return text.toString();
    }

    public long sourceAmount(AEItemKey key) {
        return sourceGrid().getStorageService().getInventory().getAvailableStacks().get(key);
    }

    public long targetAmount(Target target, AEItemKey key) {
        return targetGrid(target).getStorageService().getInventory().getAvailableStacks().get(key);
    }

    public FederationPatternProviderBlockEntity provider() {
        return helper.getBlockEntity(PROVIDER);
    }

    public EndpointBlockEntity endpoint(Target target) {
        return helper.getBlockEntity(SUBNETS.get(target)[0]);
    }

    public EndpointTargetBinding binding(Target target) {
        return EndpointTargetBinding.findEndpoint(helper.getLevel(), helper.absolutePos(SUBNETS.get(target)[0]));
    }

    public IGrid sourceGrid() {
        return node(SOURCE_CHEST).getGrid();
    }

    public IGrid targetGrid(Target target) {
        return node(SUBNETS.get(target)[1]).getGrid();
    }

    public NetworkId sourceNetwork() {
        return sourceNetwork;
    }

    private IGridNode node(BlockPos position) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(position)) instanceof IGridConnectedBlockEntity entity
                ? entity.getMainNode().getNode() : null;
    }

    private void placeNative(BlockPos position, net.minecraft.world.level.block.state.BlockState state,
            NetworkId network) {
        helper.setBlock(position, state);
        seed(position, network);
    }

    private void seed(BlockPos position, NetworkId network) {
        var entity = (IGridConnectedBlockEntity) helper.getBlockEntity(position);
        entity.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", network));
    }
}
