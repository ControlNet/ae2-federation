package space.controlnet.ae2federation.compat;

import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;

/** A shelf of independent processing subnets, powered by the layout's single creative AE cell. */
final class PlaythroughMachines {
    static final String PATH = System.getProperty("ae2federation.playthrough.machinePath", "projection");
    private record Installation(Site site, PlaythroughMachineAdapter.Recipe recipe) { }
    private final GameTestHelper helper;
    private final boolean endpoint;
    private final List<Installation> installations = new ArrayList<>();
    private final Set<String> installed = new java.util.HashSet<>();
    private final List<BlockPos> outputCells = new ArrayList<>();
    private final Set<BlockPos> placed;

    PlaythroughMachines(GameTestHelper helper, Set<BlockPos> placed, boolean federated) {
        this.helper = helper; this.placed = placed; this.endpoint = federated && PATH.equals("endpoint");
        if (!Set.of("projection", "endpoint").contains(PATH)) throw new IllegalArgumentException("Unknown machine path: " + PATH);
    }

    /** The top Storage Bus delivers inputs; the bottom hopper extracts only the machine's exposed outputs. */
    record Site(GameTestHelper helper, Set<BlockPos> placed, BlockPos provider, BlockPos machine, BlockPos returnPort) {
        void block(BlockPos pos, Block block) { placed.add(pos); helper.setBlock(pos, block); }
        void cable(BlockPos pos) { placeCable(helper, placed, pos, false); }
        appeng.parts.storagebus.StorageBusPart storageBus(BlockPos pos, Direction face) {
            cable(pos);
            var host = (appeng.api.parts.IPartHost) helper.getBlockEntity(pos);
            if (host.getPart(face) instanceof appeng.parts.storagebus.StorageBusPart existing) return existing;
            var bus = PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), face, null, AEParts.STORAGE_BUS.asItem());
            if (!(bus instanceof appeng.parts.storagebus.StorageBusPart storage))
                throw new IllegalStateException("Storage Bus refused: " + pos);
            return storage;
        }
    }

    private static void placeCable(GameTestHelper helper, Set<BlockPos> placed, BlockPos pos, boolean dense) {
        if (!placed.add(pos)) return;
        var item = dense ? AEParts.COVERED_DENSE_CABLE.item(AEColor.TRANSPARENT) : AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT);
        if (PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, item) == null)
            throw new IllegalStateException("Cable refused: " + pos);
    }

    private void denseCable(BlockPos pos) { placeCable(helper, placed, pos, true); }

    void initialize(PlaythroughRecipes.Proof proof, BlockPos factoryChest, BlockPos requesterChest) {
        if (proof.machines().isEmpty()) return;
        if (proof.machines().size() > 24) throw new IllegalArgumentException("Machine shelf supports 24 distinct recipes");
        var cells = new java.util.TreeSet<String>();
        proof.machines().values().forEach(recipe -> cells.addAll(PlaythroughMachineAdapters.byId(recipe.adapter()).storageCells(recipe)));
        if (cells.size() > 12) throw new IllegalArgumentException("Requester supports at most 12 additional cell types");
        for (var cell : cells) {
            var pos = requesterChest.above(outputCells.size() + 1);
            placed.add(pos); helper.setBlock(pos, AEBlocks.ME_CHEST.block());
            var chest = helper.<appeng.blockentity.storage.MEChestBlockEntity>getBlockEntity(pos);
            chest.setCell(new net.minecraft.world.item.ItemStack(AddonCraftingScene.item(cell)));
            chest.setPriority(1000);
            if (chest.getOriginalCellInventory(0) == null) throw new IllegalArgumentException("Not an AE2 storage cell: " + cell);
            outputCells.add(pos);
        }
        // The riser is attached beyond the controller spine, clear of all assembler faces.
        for (int x = factoryChest.getX() + 1; x <= 33; x++) denseCable(new BlockPos(x, factoryChest.getY(), factoryChest.getZ()));
        for (int y = factoryChest.getY() + 1; y <= 12; y++) denseCable(new BlockPos(33, y, factoryChest.getZ()));
        int index = 0;
        int requestedCopies = Integer.getInteger("ae2federation.playthrough.machineCopies", 4);
        if (requestedCopies < 1 || requestedCopies > 24) throw new IllegalArgumentException("machineCopies must be 1..24");
        int copies = Math.min(requestedCopies, 24 / proof.machines().size());
        for (int copy = 0; copy < copies; copy++) for (var recipe : proof.machines().values()) {
            int row = index / 6;
            var pos = new BlockPos(2 + index % 6 * 5, 12 + row * 5, 22);
            for (int z = factoryChest.getZ() + 1; z <= 25; z++) denseCable(new BlockPos(33, 12, z));
            for (int y = 13; y <= pos.getY(); y++) denseCable(new BlockPos(33, y, 25));
            for (int x = Math.min(33, pos.getX()); x <= Math.max(33, pos.getX()); x++) denseCable(new BlockPos(x, pos.getY(), 25));
            denseCable(new BlockPos(33, pos.getY(), 25));
            var current = new Site(helper, placed, pos, pos.above(2), endpoint ? pos.east() : pos);
            current.cable(pos.south()); current.cable(pos.south(2));
            if (endpoint) {
                placed.add(pos);
                helper.setBlock(pos, ProcessingRegistration.PROVIDER.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
                placed.add(pos.east());
                helper.setBlock(pos.east(), ProcessingRegistration.ENDPOINT.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST));
            } else {
                current.block(pos, AEBlocks.PATTERN_PROVIDER.block());
                AEBlocks.PATTERN_PROVIDER.block().setSide(helper.getLevel(), helper.absolutePos(pos), Direction.WEST);
                current.block(pos.east(), AEBlocks.INTERFACE.block());
                // Quartz Fiber joins power only; the input subnet must never join the factory's item network.
                current.cable(pos.north()); current.cable(pos.east().north());
                if (PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos.north()), Direction.EAST, null,
                        AEParts.QUARTZ_FIBER.asItem()) == null) throw new IllegalStateException("Quartz Fiber refused");
            }
            var adapter = PlaythroughMachineAdapters.byId(recipe.adapter());
            for (int y = 1; y <= 3; y++) current.cable(pos.east().above(y));
            current.cable(pos.above(3));
            current.storageBus(current.machine().relative(adapter.inputFace()), adapter.inputFace().getOpposite());
            adapter.place(current, recipe);
            if (adapter.itemCollector()) {
                placed.add(pos.above());
                helper.setBlock(pos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING,
                        endpoint ? Direction.EAST : Direction.DOWN));
                if (endpoint) {
                    // A second hopper drops the product into the Endpoint, bypassing its input Storage Buses.
                    placed.add(pos.east().above());
                    helper.setBlock(pos.east().above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
                    // Reroute the subnet riser around the collector.
                    for (int y = 0; y <= 3; y++) current.cable(pos.east(2).above(y));
                }
            }
            installations.add(new Installation(current, recipe)); index++;
        }
    }

    /** Read/drain all physical requester cells together; this wrapper is never mounted on an AE2 grid. */
    appeng.api.storage.MEStorage storage(appeng.api.storage.MEStorage primary) {
        if (outputCells.isEmpty()) return primary;
        var stores = new ArrayList<appeng.api.storage.MEStorage>(); stores.add(primary);
        for (var pos : outputCells) stores.add(java.util.Objects.requireNonNull(
                helper.<appeng.blockentity.storage.MEChestBlockEntity>getBlockEntity(pos).getOriginalCellInventory(0)));
        return new appeng.api.storage.MEStorage() {
            @Override public long extract(appeng.api.stacks.AEKey key, long amount, appeng.api.config.Actionable mode,
                                          appeng.api.networking.security.IActionSource source) {
                long extracted = 0;
                for (var store : stores) extracted += store.extract(key, amount - extracted, mode, source);
                return extracted;
            }
            @Override public void getAvailableStacks(appeng.api.stacks.KeyCounter out) {
                stores.forEach(store -> store.getAvailableStacks(out));
            }
            @Override public net.minecraft.network.chat.Component getDescription() {
                return net.minecraft.network.chat.Component.literal("Playthrough requester cells");
            }
        };
    }

    static long executionTicks(PlaythroughRecipes.Proof proof, long crafts) {
        int slowest = proof.machines().values().stream().mapToInt(PlaythroughMachineAdapter.Recipe::ticks).max().orElse(0);
        return Math.multiplyExact(crafts, Math.max(250L, slowest));
    }

    boolean install(PlaythroughRecipes.Proof proof, Set<NetworkId> networks) {
        for (var installation : installations) {
            var site = installation.site(); var recipe = installation.recipe();
            if (installed.contains(recipe.id() + site.provider())) continue;
            var pattern = proof.patterns().get(recipe.id());
            if (endpoint) {
                var provider = helper.<FederationPatternProviderBlockEntity>getBlockEntity(site.provider());
                var binding = EndpointTargetBinding.findEndpoint(helper.getLevel(), helper.absolutePos(site.provider().east()));
                if (binding == null || provider.runtime().isEmpty() || binding.subnetNode().getGrid() == null) return false;
                FederationDomainRegistryAccess.confirmedNetworkId(binding.subnetNode().getGrid()).ifPresent(networks::add);
                if (provider.getTerminalPatternInventory().getStackInSlot(0).isEmpty())
                    provider.getTerminalPatternInventory().insertItem(0, pattern.copy(), false);
                if (!provider.toggleEndpoint(provider.mappedProvider().mappingHandle(0), binding).startsWith("accepted-")) return false;
            } else {
                var host = (PatternProviderLogicHost) helper.getBlockEntity(site.provider());
                var input = helper.<appeng.blockentity.misc.InterfaceBlockEntity>getBlockEntity(site.provider().east());
                var subnet = input.getMainNode().getGrid();
                if (host.getLogic().getGrid() == null || subnet == null || !subnet.getEnergyService().isNetworkPowered()) return false;
                if (subnet == host.getLogic().getGrid()) throw new IllegalStateException("Machine input subnet merged with factory");
                FederationDomainRegistryAccess.confirmedNetworkId(subnet).ifPresent(networks::add);
                host.getLogic().getPatternInv().setItemDirect(0, pattern.copy()); host.getLogic().updatePatterns();
            }
            installed.add(recipe.id() + site.provider());
        }
        return true;
    }

    String signatures(PlaythroughRecipes.Proof proof, Map<String, PlaythroughAccounting.Signature> signatures) {
        for (var installation : installations) {
            var pos = installation.site().provider();
            var pattern = endpoint ? helper.<FederationPatternProviderBlockEntity>getBlockEntity(pos)
                    .getTerminalPatternInventory().getStackInSlot(0) : ((PatternProviderLogicHost) helper.getBlockEntity(pos)).getLogic().getPatternInv().getStackInSlot(0);
            if (!AEItemKey.of(pattern).equals(AEItemKey.of(proof.patterns().get(installation.recipe().id())))) return "Machine pattern changed";
            var decoded = appeng.api.crafting.PatternDetailsHelper.decodePattern(pattern, helper.getLevel());
            if (decoded == null) return "Machine pattern no longer decodes";
            var signature = PlaythroughAccounting.signature(decoded, helper.getLevel());
            signatures.put(installation.recipe().id(), signature);
            if (!signature.equals(proof.signatures().get(installation.recipe().id()))) return "Machine signature changed";
        }
        return "";
    }

    Map<String, String> checks() {
        var failures = new TreeMap<String, String>();
        for (var installation : installations) {
            var site = installation.site(); var recipe = installation.recipe();
            String state = PlaythroughMachineAdapters.byId(recipe.adapter()).leftovers(helper, site.machine(), recipe);
            if (!state.isEmpty()) failures.put(recipe.id(), state);
            if (PlaythroughMachineAdapters.byId(recipe.adapter()).itemCollector()) for (var pos : endpoint ? List.of(site.provider().above(), site.provider().east().above()) : List.of(site.provider().above())) {
                if (!helper.<HopperBlockEntity>getBlockEntity(pos).isEmpty()) failures.put(recipe.id() + "/collector", "Hopper occupied");
            }
            if (endpoint) {
                var provider = helper.<FederationPatternProviderBlockEntity>getBlockEntity(site.provider());
                for (int lane = 0; lane < provider.laneCount(); lane++) if (provider.lane(lane).hasPendingSend() || !provider.lane(lane).getReturnInv().isEmpty())
                    failures.put(recipe.id() + "/provider", "Pending send or return");
            } else {
                var input = helper.<appeng.blockentity.misc.InterfaceBlockEntity>getBlockEntity(site.provider().east());
                var storage = input.getInterfaceLogic().getStorage();
                for (int slot = 0; slot < storage.size(); slot++) if (storage.getStack(slot) != null)
                    failures.put(recipe.id() + "/interface", "Buffered input: " + storage.getStack(slot));
                var logic = ((PatternProviderLogicHost) helper.getBlockEntity(site.provider())).getLogic();
                if (logic.isBusy() || !logic.getReturnInv().isEmpty()) failures.put(recipe.id() + "/provider", "Pending send or return");
            }
        }
        return Map.of("machine-idle-empty", failures.isEmpty() ? "PASS" : failures.toString());
    }
}
