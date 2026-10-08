package space.controlnet.ae2federation.test.p2p;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.BaseActionSource;
import appeng.me.helpers.IGridConnectedBlockEntity;
import java.util.List;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.world.BlockEntityReload;
import space.controlnet.ae2federation.test.world.OtherDimensionSite;
import space.controlnet.ae2federation.test.world.QuantumBridges;

/**
 * TEST-ONLY: a Federation Pattern Provider in the overworld drives an Endpoint in the nether, the two reaching each other
 * only through the {@link QuantumP2PCarrier}'s tunnels.
 * <p>
 * Overworld: the source network is a creative energy cell, an ME chest and a 1k crafting storage in a column at x 1,
 * and the Provider east of the chest, its Federation face east on the overworld tunnel's front cable. Nether: the
 * tunnel's front cable leads south to the Endpoint, whose Federation face looks north at it. South of the Endpoint is
 * its subnet's ME chest, the one machine beside it that takes FE, and south of that a charged energy cell, which powers
 * the subnet. A stand-in machine takes the input the Endpoint delivers and returns a diamond for each through the
 * Endpoint, as a real machine beside it would.
 */
public final class QuantumP2PProviderScene implements AutoCloseable {
    public static final BlockPos SITE_SIZE = new BlockPos(4, 2, 8);
    public static final AEItemKey INPUT = AEItemKey.of(Items.COBBLESTONE);
    public static final AEItemKey OUTPUT = AEItemKey.of(Items.DIAMOND);
    private static final BlockPos PROVIDER = QuantumP2PCarrier.OVERWORLD_FRONT.west();
    private static final BlockPos SOURCE_CHEST = PROVIDER.west();
    private static final BlockPos SOURCE_POWER = SOURCE_CHEST.north();
    private static final BlockPos SOURCE_CPU = SOURCE_CHEST.south();
    private static final BlockPos ENDPOINT = QuantumP2PCarrier.NETHER_FRONT.south();
    private static final BlockPos TARGET_CHEST = ENDPOINT.south();
    private static final BlockPos TARGET_CELL = TARGET_CHEST.south();
    /** The Endpoint's side the stand-in machine returns its products through. */
    private static final Direction RETURN_SIDE = Direction.SOUTH;

    private final GameTestHelper helper;
    private final OtherDimensionSite site;
    private final QuantumP2PCarrier carrier;
    private final NetworkId sourceNetwork = NetworkId.create();
    private final NetworkId targetNetwork = NetworkId.create();
    private Future<ICraftingPlan> plan;
    private long returned;
    private boolean machineRuns = true;
    private int step;

    public QuantumP2PProviderScene(GameTestHelper helper) {
        this.helper = helper;
        site = OtherDimensionSite.nether(helper, SITE_SIZE);
        carrier = new QuantumP2PCarrier(helper, site);
        placeSource(SOURCE_POWER, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        placeSource(SOURCE_CHEST, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.<MEChestBlockEntity>getBlockEntity(SOURCE_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
        placeSource(SOURCE_CPU, AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
        placeSource(PROVIDER, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
    }

    /**
     * Builds the scene one step per call and fails until the Provider's domain holds the nether Endpoint through the
     * tunnels, with one pattern of the Provider mapped to it.
     */
    public void advance() {
        if (step == 0) {
            helper.assertTrue(site.ready(), "Waiting for the nether site to tick: " + site.tickDiagnostics());
            placeTarget(ENDPOINT, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.NORTH));
            placeTarget(TARGET_CHEST, AEBlocks.ME_CHEST.block().defaultBlockState());
            site.<MEChestBlockEntity>getBlockEntity(TARGET_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
            placeTarget(TARGET_CELL, AEBlocks.ENERGY_CELL.block().defaultBlockState());
            EnergyCellBlockEntity cell = site.getBlockEntity(TARGET_CELL);
            helper.assertValueEqual(cell.injectAEPower(100_000, Actionable.MODULATE), 0.0,
                    "The subnet's energy cell takes its charge");
            carrier.build();
            step = 1;
        }
        if (step == 1) {
            helper.assertTrue(active(node(helper.getLevel().getBlockEntity(helper.absolutePos(SOURCE_CHEST))))
                    && active(node(helper.getLevel().getBlockEntity(helper.absolutePos(PROVIDER))))
                    && active(node(site.getBlockEntity(ENDPOINT))) && active(node(site.getBlockEntity(TARGET_CHEST)))
                    && binding() != null, "Waiting for the source network and the Endpoint's subnet to settle");
            helper.assertTrue(carrier.linked(), "Waiting for the Quantum Bridge to join the carrier's halves");
            carrier.pair();
            helper.setBlock(QuantumP2PCarrier.OVERWORLD_FRONT, RouterRegistration.FEDERATION_CABLE.get());
            site.setBlock(QuantumP2PCarrier.NETHER_FRONT, RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
            step = 2;
        }
        if (step == 2) {
            var endpointNode = FederationDomainRegistryAccess.nodeId(site.level(), site.absolute(ENDPOINT));
            var registry = FederationDomainRegistryAccess.get(helper.getLevel());
            helper.assertTrue(registry.federationdomainsFor(sourceNetwork).stream().map(registry::federationDomain)
                    .anyMatch(domain -> domain.isPresent() && domain.get().nodes().contains(endpointNode)),
                    "Waiting for the nether Endpoint to join the Provider's domain through the tunnels");
            var remainder = provider().getTerminalPatternInventory().insertItem(0,
                    PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(INPUT, 1)),
                            List.of(new GenericStack(OUTPUT, 1))), false);
            helper.assertTrue(remainder.isEmpty(), "The Provider takes the pattern");
            var status = provider().toggleEndpoint(provider().mappedProvider().mappingHandle(0), binding());
            helper.assertTrue(status.startsWith("accepted-"), "The nether Endpoint must be mapped: " + status);
            step = 3;
        }
    }

    /** Puts {@code amount} inputs in the source network and asks its crafting service for as many outputs. */
    public void beginCraft(long amount) {
        helper.assertValueEqual(sourceGrid().getStorageService().getInventory().insert(INPUT, amount,
                Actionable.MODULATE, IActionSource.empty()), amount, "The source takes the inputs");
        var sourceNode = node(helper.getLevel().getBlockEntity(helper.absolutePos(SOURCE_CHEST)));
        plan = sourceGrid().getCraftingService().beginCraftingCalculation(helper.getLevel(),
                new ICraftingSimulationRequester() {
                    @Override
                    public IActionSource getActionSource() {
                        return new BaseActionSource();
                    }

                    @Override
                    public IGridNode getGridNode() {
                        return sourceNode;
                    }
                }, OUTPUT, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    /** True once the plan is computed and the source's crafting CPU has taken the job. */
    public boolean submitWhenPlanned() {
        if (plan == null || !plan.isDone()) return false;
        try {
            var computed = plan.get();
            helper.assertTrue(!computed.simulation(), "The plan must be complete: " + computed.missingItems());
            var result = sourceGrid().getCraftingService().submitJob(computed, null, null, false,
                    new BaseActionSource());
            helper.assertTrue(result.successful(), "The crafting CPU must take the job: " + result.errorCode());
            plan = null;
            return true;
        } catch (InterruptedException | java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("The crafting calculation failed", exception);
        }
    }

    /** The stand-in machine: takes the input delivered to the subnet and returns a diamond for each. */
    public void runMachine() {
        // Looking at an unloaded chunk's block entity would load it.
        if (!machineRuns || !site.ticking()) return;
        var subnet = node(site.getBlockEntity(TARGET_CHEST));
        if (subnet == null || subnet.getGrid() == null) return;
        var storage = subnet.getGrid().getStorageService().getInventory();
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

    /** Stops or restarts the stand-in machine. */
    public void machineRuns(boolean runs) {
        machineRuns = runs;
    }

    /** What the Endpoint's subnet holds of {@code key}: inputs delivered and not yet taken by the machine. */
    public long subnetAmount(AEItemKey key) {
        if (!site.ticking()) return 0;
        var subnet = node(site.getBlockEntity(TARGET_CHEST));
        if (subnet == null || subnet.getGrid() == null) return 0;
        return subnet.getGrid().getStorageService().getInventory().extract(key, Long.MAX_VALUE, Actionable.SIMULATE,
                IActionSource.empty());
    }

    /** Whether the nether Endpoint is in a Federation Domain with the source network. */
    public boolean endpointLinked() {
        var endpointNode = FederationDomainRegistryAccess.nodeId(site.level(), site.absolute(ENDPOINT));
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        return registry.federationdomainsFor(sourceNetwork).stream().map(registry::federationDomain)
                .anyMatch(domain -> domain.isPresent() && domain.get().nodes().contains(endpointNode));
    }

    /** Breaks the nether Endpoint and places a fresh one where it stood, as a player replacing it does. */
    public void replaceEndpoint() {
        site.setBlock(ENDPOINT, Blocks.AIR.defaultBlockState());
        placeTarget(ENDPOINT, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
    }

    /** Offers {@code amount} diamonds to the nether Endpoint from the machine's side; how many it took. */
    public long offerEndpoint(int amount) {
        var handler = site.level().getCapability(Capabilities.ItemHandler.BLOCK, site.absolute(ENDPOINT), RETURN_SIDE);
        if (handler == null) return 0;
        var remainder = new ItemStack(Items.DIAMOND, amount);
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);
        }
        return amount - remainder.getCount();
    }

    /** Cancels the source network's crafting jobs. */
    public void cancelJobs() {
        sourceGrid().getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy())
                .forEach(cpu -> ((appeng.me.cluster.implementations.CraftingCPUCluster) cpu).cancelJob());
    }

    public OtherDimensionSite site() {
        return site;
    }

    public QuantumP2PCarrier carrier() {
        return carrier;
    }

    public long returned() {
        return returned;
    }

    public long sourceAmount(AEItemKey key) {
        return sourceGrid().getStorageService().getInventory().getAvailableStacks().get(key);
    }

    public boolean cpuBusy() {
        return sourceGrid().getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy());
    }

    /**
     * The FE relay the Provider sends its FE into: the cable in front of it, asked from the Provider's side, as Applied
     * Flux's Induction Card asks.
     */
    public IEnergyStorage relay() {
        return helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                helper.absolutePos(QuantumP2PCarrier.OVERWORLD_FRONT), Direction.WEST);
    }

    /** The AE in the internal buffer of the ME chest beside the Endpoint, which takes FE like any powered machine. */
    public double machinePower() {
        MEChestBlockEntity chest = site.getBlockEntity(TARGET_CHEST);
        return chest.getAECurrentPower();
    }

    public FederationPatternProviderBlockEntity provider() {
        return helper.getBlockEntity(PROVIDER);
    }

    public EndpointBlockEntity endpoint() {
        return site.getBlockEntity(ENDPOINT);
    }

    private EndpointTargetBinding binding() {
        return EndpointTargetBinding.findEndpoint(site.level(), site.absolute(ENDPOINT));
    }

    /**
     * Places a twin: an Endpoint in the overworld at the nether Endpoint's very coordinates, with nothing in front of
     * it and no subnet, so no Provider may take it for the nether one.
     */
    public void placeTwin() {
        helper.getLevel().setBlock(site.absolute(ENDPOINT), ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH), Block.UPDATE_ALL);
    }

    public EndpointBlockEntity twin() {
        return (EndpointBlockEntity) helper.getLevel().getBlockEntity(site.absolute(ENDPOINT));
    }

    /** Offers {@code amount} diamonds to the twin from the side a machine returns through; how many it took. */
    public long offerTwin(int amount) {
        var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, site.absolute(ENDPOINT),
                RETURN_SIDE);
        if (handler == null) return 0;
        var remainder = new ItemStack(Items.DIAMOND, amount);
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);
        }
        return amount - remainder.getCount();
    }

    /**
     * A simulated restart of both sides: every block entity of the overworld half and of the nether site is saved and
     * loaded again in one tick, as a chunk reload does. The server and its services keep running.
     */
    public void reloadBoth() {
        BlockEntityReload.reload(helper, new BlockPos(0, 1, 1), new BlockPos(9, 3, 5));
        site.reloadBlockEntities();
    }

    /** Whether the source network stands, with its chest, Provider and crafting CPU online, after a reload too. */
    public boolean sourceReady() {
        var chest = node(helper.getLevel().getBlockEntity(helper.absolutePos(SOURCE_CHEST)));
        return active(chest) && active(node(helper.getLevel().getBlockEntity(helper.absolutePos(PROVIDER))))
                && !chest.getGrid().getCraftingService().getCpus().isEmpty();
    }

    private IGrid sourceGrid() {
        return node(helper.getLevel().getBlockEntity(helper.absolutePos(SOURCE_CHEST))).getGrid();
    }

    private void placeSource(BlockPos position, net.minecraft.world.level.block.state.BlockState state) {
        helper.setBlock(position, state);
        QuantumBridges.seed(helper.getLevel().getBlockEntity(helper.absolutePos(position)), sourceNetwork);
    }

    private void placeTarget(BlockPos position, net.minecraft.world.level.block.state.BlockState state) {
        site.setBlock(position, state);
        QuantumBridges.seed(site.getBlockEntity(position), targetNetwork);
    }

    private static IGridNode node(net.minecraft.world.level.block.entity.BlockEntity entity) {
        return entity instanceof IGridConnectedBlockEntity connected ? connected.getMainNode().getNode() : null;
    }

    private static boolean active(IGridNode node) {
        return node != null && node.isActive() && node.hasGridBooted();
    }

    @Override
    public void close() {
        if (twin() != null) {
            helper.getLevel().setBlock(site.absolute(ENDPOINT), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        site.close();
    }
}
