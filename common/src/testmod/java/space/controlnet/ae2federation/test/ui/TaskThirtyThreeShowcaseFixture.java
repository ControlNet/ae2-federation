package space.controlnet.ae2federation.test.ui;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.persistence.NetworkNames;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyDelete;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;

/**
 * A fuller Federation Domain for design review: eight real ME networks on the faces of two Routers joined by Federation
 * Cable, named and linked by real rules, with three Pattern Providers mapped many-to-many onto five Endpoints. Everything is placed through
 * production blocks and services; the names, rules and patterns are test-world data chosen to look like a base.
 */
final class TaskThirtyThreeShowcaseFixture {
    private static final String STATE = "task33.showcase";

    private TaskThirtyThreeShowcaseFixture() {
    }

    /**
     * Six more networks, each an ME Chest with a storage cell and its own energy cell: two on the Router's free faces
     * and four on a second Router. That Router sits at the end of the Federation Cable run on the first Router's top
     * (which the Mine's Provider joins later), so both Routers are one domain. No two networks touch.
     */
    static void placeNetworks(ServerContext context) {
        // Eight networks, three Providers and five Endpoints share the Routers' cable: more than the default cable
        // capacity, which would stop the whole domain. The design-review world raises it, as a server owner can.
        space.controlnet.ae2federation.domain.CableCapacityLimit.set(true, 32);
        var router = TaskFifteenWorldFixture.routerPosition(context);
        var state = new State(router.east(), router.below());
        context.put(STATE, state);
        placeChestNetwork(context, state.mine, state.mine.east());
        // Two one-block crafting CPUs on the Mine, for the statistics page's CPU list.
        for (var cpu : mineCpus(state)) {
            require(context.level().isEmptyBlock(cpu.getKey()), "Showcase CPU position must be empty: " + cpu.getKey());
            context.level().setBlockAndUpdate(cpu.getKey(), cpu.getValue().defaultBlockState());
        }
        placeChestNetwork(context, state.hall, state.hall.below());
        state.remoteRouter = router.above().east(4);
        var remote = state.remoteRouter;
        state.remoteNetworks = List.of(
                new BlockPos[] {remote.north(), remote.north(2)},
                new BlockPos[] {remote.south(), remote.south().below()},
                new BlockPos[] {remote.east(), remote.east().below()},
                new BlockPos[] {remote.above(), remote.above(2)});
        var cables = new ArrayList<BlockPos>();
        for (int east = 1; east <= 3; east++) cables.add(router.above().east(east));
        for (var position : cables) {
            require(context.level().isEmptyBlock(position), "Showcase cable position must be empty: " + position);
            context.level().setBlockAndUpdate(position, space.controlnet.ae2federation.router.RouterRegistration.FEDERATION_CABLE.get()
                    .defaultBlockState());
        }
        state.cables = List.copyOf(cables);
        require(context.level().isEmptyBlock(remote), "Showcase second Router position must be empty");
        context.level().setBlockAndUpdate(remote, space.controlnet.ae2federation.router.RouterRegistration.SWITCH.get()
                .defaultBlockState());
        for (var network : state.remoteNetworks) placeChestNetwork(context, network[0], network[1]);
    }

    /** Whether the Router's domain holds all eight networks, each with a confirmed identity. */
    static boolean networksReady(ServerContext context) {
        var state = state(context);
        var main = TaskThirtyThreeWorldFixture.mainNode(context);
        var outer = TaskThirtyThreeWorldFixture.outerNode(context);
        var nodes = new ArrayList<>(List.of(main, outer));
        nodes.add(chestNode(context, state.mine));
        nodes.add(chestNode(context, state.hall));
        for (var network : state.remoteNetworks) nodes.add(chestNode(context, network[0]));
        if (nodes.stream().anyMatch(node -> node == null || !node.isActive())) return false;
        var ids = new ArrayList<NetworkId>();
        for (var node : nodes) {
            var id = node.getGrid() == null ? null : FederationDomainRegistryAccess.confirmedNetworkId(node.getGrid()).orElse(null);
            if (id == null || ids.contains(id)) return false;
            ids.add(id);
        }
        var routerNode = FederationDomainRegistryAccess.nodeId(context.level(), TaskFifteenWorldFixture.routerPosition(context));
        var joined = FederationDomainRegistryAccess.get(context.level()).snapshot().federationDomains().values().stream()
                .anyMatch(domain -> domain.nodes().contains(routerNode) && domain.memberships().keySet().containsAll(ids));
        if (!joined) return false;
        state.networks = List.copyOf(ids);
        if (!state.stocked) {
            stock(context, state.mine, mineStock());
            stock(context, state.hall, hallStock());
            var remoteStock = List.of(farmStock(), outpostStock(), smelteryStock(), labStock());
            for (int index = 0; index < remoteStock.size(); index++) {
                stock(context, state.remoteNetworks.get(index)[0], remoteStock.get(index));
            }
            state.stocked = true;
        }
        return true;
    }

    /** Names the four networks and links them with rules of every capability, one of them switched off. */
    static void installNamesAndRules(ServerContext context) {
        var state = state(context);
        var main = state.networks.get(0);
        var outer = state.networks.get(1);
        var mine = state.networks.get(2);
        var hall = state.networks.get(3);
        var names = NetworkNames.get(context.level());
        names.rename(main, "Main Base");
        names.rename(outer, "Automation Tower");
        names.rename(mine, "Mine");
        names.rename(hall, "Storage Hall");
        var farm = state.networks.get(4);
        var outpost = state.networks.get(5);
        var smeltery = state.networks.get(6);
        var lab = state.networks.get(7);
        names.rename(farm, "Farm");
        names.rename(outpost, "Nether Outpost");
        names.rename(smeltery, "Smeltery");
        names.rename(lab, "Sky Lab");
        rule(context, new PolicyKey(main, farm, PolicyCapability.STORAGE), PolicyRule.storageDefaults());
        rule(context, new PolicyKey(smeltery, mine, PolicyCapability.STORAGE), PolicyRule.storageDefaults());
        // The storage rule lets crafting also take the Smeltery's materials.
        rule(context, new PolicyKey(main, smeltery, PolicyCapability.STORAGE), PolicyRule.storageDefaults());
        rule(context, new PolicyKey(main, smeltery, PolicyCapability.CRAFTING), PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)));
        rule(context, new PolicyKey(lab, main, PolicyCapability.ME_POWER), PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
        rule(context, new PolicyKey(outpost, hall, PolicyCapability.STORAGE), PolicyRule.storageDefaults());
        rule(context, new PolicyKey(lab, outpost, PolicyCapability.STORAGE), PolicyRule.storageDefaults().withEnabled(false));
        rule(context, new PolicyKey(main, mine, PolicyCapability.STORAGE), PolicyRule.storageDefaults());
        rule(context, new PolicyKey(main, mine, PolicyCapability.CRAFTING),
                PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)).withEnabled(false));
        rule(context, new PolicyKey(mine, main, PolicyCapability.ME_POWER), PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
        rule(context, new PolicyKey(hall, main, PolicyCapability.STORAGE), PolicyRule.storageDefaults());
    }

    /**
     * Two more Providers (one beside the host on the main network, one on the Mine's energy cell) and four more
     * Endpoints: three on the Automation Tower and one on the Storage Hall. None touches another AE2 block, so each
     * joins its network through a grid connection, as a wireless link would. Every Endpoint faces up, its Federation
     * face on a Federation Cable that runs from the first Endpoint's, so all are nodes of the Router's domain.
     */
    static void placeDevices(ServerContext context) {
        var state = state(context);
        var host = TaskThirtyThreeWorldFixture.hostPosition(context);
        var first = TaskThirtyThreeWorldFixture.endpointPosition(context);
        state.providers = List.of(host.east(), state.mine.east(2));
        state.towerEndpoints = List.of(first.east(2), first.east(4), first.east(4).north(2));
        state.hallEndpoint = first.east(2).north(2);
        for (var position : state.providers) {
            require(context.level().isEmptyBlock(position), "Showcase Provider position must be empty: " + position);
            context.level().setBlockAndUpdate(position, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.UP));
        }
        var cables = new ArrayList<BlockPos>();
        // Each Provider's Federation face joins the Router's domain: the smelter's through the host's cable column,
        // the Mine's through the cable run to the second Router, placed with the networks.
        cables.add(host.east().above());
        for (int east = 1; east <= 4; east++) cables.add(first.above().east(east));
        for (int north = 1; north <= 2; north++) {
            cables.add(first.east(2).above().north(north));
            cables.add(first.east(4).above().north(north));
        }
        for (var cable : cables) {
            require(context.level().isEmptyBlock(cable), "Showcase cable position must be empty: " + cable);
            context.level().setBlockAndUpdate(cable, space.controlnet.ae2federation.router.RouterRegistration.FEDERATION_CABLE.get()
                    .defaultBlockState());
        }
        cables.addAll(state.cables);
        state.cables = List.copyOf(cables);
        for (var position : state.towerEndpoints) placeEndpoint(context, position, state.networks.get(1));
        placeEndpoint(context, state.hallEndpoint, state.networks.get(3));
    }

    static boolean devicesReady(ServerContext context) {
        var state = state(context);
        var providerGrids = List.of(TaskThirtyThreeWorldFixture.mainNode(context), chestNode(context, state.mine));
        for (int index = 0; index < state.providers.size(); index++) {
            var provider = provider(context, state.providers.get(index));
            if (provider == null || provider.getMainNode().getNode() == null || provider.runtime().isEmpty()) return false;
            if (provider.getMainNode().getGrid() != providerGrids.get(index).getGrid()) {
                GridHelper.createConnection(provider.getMainNode().getNode(), providerGrids.get(index));
                return false;
            }
            var routerNode = FederationDomainRegistryAccess.nodeId(context.level(), TaskFifteenWorldFixture.routerPosition(context));
            if (provider.federationDomain().filter(domain -> domain.nodes().contains(routerNode)).isEmpty()) return false;
        }
        var targets = new ArrayList<IGridNode>();
        for (int index = 0; index < state.towerEndpoints.size(); index++) targets.add(TaskThirtyThreeWorldFixture.outerNode(context));
        targets.add(chestNode(context, state.hall));
        var positions = new ArrayList<>(state.towerEndpoints);
        positions.add(state.hallEndpoint);
        for (int index = 0; index < positions.size(); index++) {
            var endpoint = endpoint(context, positions.get(index));
            if (endpoint == null || endpoint.getMainNode().getNode() == null || endpoint.binding() == null) return false;
            var node = endpoint.getMainNode().getNode();
            if (endpoint.getMainNode().getGrid() != targets.get(index).getGrid()) {
                GridHelper.createConnection(node, targets.get(index));
                return false;
            }
            if (!node.isActive() || !node.hasGridBooted()) return false;
        }
        return true;
    }

    /**
     * Patterns on the new Providers, then the many-to-many mapping: the host's two patterns share one Tower Endpoint
     * and one also reaches a second; the smelter feeds a third; the Mine's two patterns both go to the Storage Hall.
     * The last Tower Endpoint stays free.
     */
    static void mapDevices(ServerContext context) {
        var state = state(context);
        var smelter = provider(context, state.providers.get(0));
        var mine = provider(context, state.providers.get(1));
        pattern(smelter, 0, Items.SAND, 4, Items.GLASS, 4);
        pattern(smelter, 1, Items.RAW_IRON, 8, Items.IRON_INGOT, 8);
        pattern(smelter, 2, Items.COBBLESTONE, 16, Items.STONE, 16);
        pattern(mine, 0, Items.RAW_COPPER, 9, Items.COPPER_BLOCK, 1);
        pattern(mine, 1, Items.COAL, 9, Items.COAL_BLOCK, 1);
        var host = TaskThirtyThreeWorldFixture.hostProvider(context);
        var first = TaskThirtyThreeWorldFixture.firstEndpoint(context);
        var second = endpoint(context, state.towerEndpoints.get(0));
        var third = endpoint(context, state.towerEndpoints.get(1));
        var hall = endpoint(context, state.hallEndpoint);
        map(host, 0, first);
        map(host, 1, first);
        map(host, 1, second);
        map(smelter, 0, third);
        map(smelter, 1, third);
        map(mine, 0, hall);
        map(mine, 1, hall);
    }

    static boolean mapped(ServerContext context) {
        var state = state(context);
        var host = TaskThirtyThreeWorldFixture.hostProvider(context);
        return host.endpointsForSlot(1).size() == 2
                && provider(context, state.providers.get(0)).endpointsForSlot(1).size() == 1
                && provider(context, state.providers.get(1)).endpointsForSlot(1).size() == 1;
    }

    static void cleanup(ServerContext context) {
        space.controlnet.ae2federation.domain.CableCapacityLimit.set(true,
                space.controlnet.ae2federation.domain.CableCapacityLimit.DEFAULT_CAPACITY);
        stopConverterFeed(context);
        var state = context.<State>get(STATE);
        if (state == null) return;
        var policies = PolicyService.get(context.level());
        for (var key : state.rules) {
            if (policies.configured(key).isPresent()) {
                require(policies.delete(new PolicyDelete(key, policies.revision(key))) instanceof PolicyMutationResult.Accepted,
                        "Showcase rule must be removed: " + key);
            }
        }
        var names = NetworkNames.get(context.level());
        for (var network : state.networks) names.rename(network, "");
        var blocks = new ArrayList<BlockPos>();
        blocks.addAll(state.providers);
        blocks.addAll(state.cables);
        blocks.addAll(state.towerEndpoints);
        if (state.hallEndpoint != null) blocks.add(state.hallEndpoint);
        blocks.addAll(List.of(state.mine, state.mine.east(), state.hall, state.hall.below()));
        mineCpus(state).forEach(cpu -> blocks.add(cpu.getKey()));
        for (var network : state.remoteNetworks) blocks.addAll(List.of(network));
        if (state.remoteRouter != null) blocks.add(state.remoteRouter);
        blocks.addAll(state.converter);
        for (var position : blocks) context.level().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
    }

    /** The Mine's crafting CPUs: a 1k one north of its energy cell and a 4k one under it, not touching each other. */
    private static List<Map.Entry<BlockPos, net.minecraft.world.level.block.Block>> mineCpus(State state) {
        var cell = state.mine.east();
        return List.of(Map.entry(cell.north(), AEBlocks.CRAFTING_STORAGE_1K.block()),
                Map.entry(cell.below(), AEBlocks.CRAFTING_STORAGE_4K.block()));
    }

    private static void placeChestNetwork(ServerContext context, BlockPos chest, BlockPos cell) {
        require(context.level().isEmptyBlock(chest) && context.level().isEmptyBlock(cell),
                "Showcase network positions must be empty: " + chest);
        context.level().setBlockAndUpdate(chest, AEBlocks.ME_CHEST.block().defaultBlockState());
        ((MEChestBlockEntity) context.level().getBlockEntity(chest)).setCell(AEItems.ITEM_CELL_4K.stack());
        context.level().setBlockAndUpdate(cell, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
    }

    private static Map<Item, Long> farmStock() {
        return Map.of(Items.WHEAT, 4_096L, Items.CARROT, 1_830L, Items.POTATO, 2_204L, Items.SUGAR_CANE, 960L,
                Items.PUMPKIN, 144L);
    }

    private static Map<Item, Long> outpostStock() {
        return Map.of(Items.NETHERRACK, 12_800L, Items.QUARTZ, 2_048L, Items.GLOWSTONE_DUST, 640L,
                Items.BLAZE_ROD, 48L);
    }

    private static Map<Item, Long> smelteryStock() {
        return Map.of(Items.IRON_INGOT, 3_200L, Items.COPPER_INGOT, 1_152L, Items.GOLD_INGOT, 256L, Items.COAL, 4_600L);
    }

    private static Map<Item, Long> labStock() {
        return Map.of(Items.REDSTONE, 2_880L, Items.LAPIS_LAZULI, 512L, Items.ENDER_PEARL, 32L, Items.AMETHYST_SHARD, 210L);
    }

    private static Map<Item, Long> mineStock() {
        return Map.of(Items.COBBLESTONE, 18_432L, Items.RAW_IRON, 1_216L, Items.RAW_COPPER, 904L, Items.COAL, 2_310L,
                Items.REDSTONE, 740L, Items.DIAMOND, 37L);
    }

    private static Map<Item, Long> hallStock() {
        return Map.of(Items.OAK_LOG, 3_072L, Items.GLASS, 1_408L, Items.IRON_INGOT, 2_560L, Items.GOLD_INGOT, 312L,
                Items.SAND, 5_120L, Items.QUARTZ, 688L, Items.STRING, 96L);
    }

    private static void stock(ServerContext context, BlockPos chest, Map<Item, Long> items) {
        var inventory = chestNode(context, chest).getGrid().getStorageService().getInventory();
        items.forEach((item, amount) -> inventory.insert(AEItemKey.of(item), amount, Actionable.MODULATE, IActionSource.empty()));
    }

    private static void rule(ServerContext context, PolicyKey key, PolicyRule rule) {
        var policies = PolicyService.get(context.level());
        var result = policies.edit(new PolicyEdit(key, policies.revision(key), rule));
        require(result instanceof PolicyMutationResult.Accepted, "Showcase rule must be accepted: " + key + " " + result);
        state(context).rules.add(key);
    }

    private static void placeEndpoint(ServerContext context, BlockPos position, NetworkId network) {
        require(context.level().isEmptyBlock(position), "Showcase Endpoint position must be empty: " + position);
        context.level().setBlockAndUpdate(position, ProcessingRegistration.ENDPOINT.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        endpoint(context, position).getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", network));
    }

    private static void pattern(FederationPatternProviderBlockEntity provider, int slot, Item input, long in, Item output, long out) {
        var inventory = provider.getTerminalPatternInventory();
        if (!inventory.getStackInSlot(slot).isEmpty()) return;
        inventory.insertItem(slot, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(input), in)), List.of(new GenericStack(AEItemKey.of(output), out))), false);
    }

    private static void map(FederationPatternProviderBlockEntity provider, int slot, EndpointBlockEntity endpoint) {
        var binding = endpoint.binding();
        if (provider.endpointsForSlot(slot).contains(binding.endpointIdentity())) return;
        var status = provider.toggleEndpoint(provider.mappedProvider().mappingHandle(slot), binding);
        require(status.startsWith("accepted-"), "Showcase mapping must be accepted: " + status);
    }

    private static IGridNode chestNode(ServerContext context, BlockPos position) {
        return context.level().getBlockEntity(position) instanceof MEChestBlockEntity chest ? chest.getMainNode().getNode() : null;
    }

    private static FederationPatternProviderBlockEntity provider(ServerContext context, BlockPos position) {
        return context.level().getBlockEntity(position) instanceof FederationPatternProviderBlockEntity provider ? provider : null;
    }

    private static EndpointBlockEntity endpoint(ServerContext context, BlockPos position) {
        return context.level().getBlockEntity(position) instanceof EndpointBlockEntity endpoint ? endpoint : null;
    }

    /**
     * Whether the Router's domain holds all eight networks again, unchanged for a second. Each Endpoint carries its
     * network's identity but starts in a Grid of its own until {@link #devicesReady} connects it, so for a moment two
     * Grids claim one network. A domain recompute that lands in that moment drops the network until the Router's
     * once-a-second evidence check puts it back; reopening the workspace before then shows six members.
     */
    static boolean domainComplete(ServerContext context) {
        var state = state(context);
        var routerNode = FederationDomainRegistryAccess.nodeId(context.level(), TaskFifteenWorldFixture.routerPosition(context));
        var domain = FederationDomainRegistryAccess.get(context.level()).snapshot().federationDomains().values().stream()
                .filter(candidate -> candidate.nodes().contains(routerNode)).findFirst().orElse(null);
        if (domain == null || !domain.memberships().keySet().containsAll(state.networks)) {
            context.put("task33.showcaseDomainSince", -1L);
            return false;
        }
        var tick = context.level().getGameTime();
        Long generation = context.get("task33.showcaseDomainGeneration");
        Long since = context.get("task33.showcaseDomainSince");
        if (since == null || since < 0 || generation == null || generation != domain.generation()) {
            context.put("task33.showcaseDomainGeneration", domain.generation());
            context.put("task33.showcaseDomainSince", tick);
            return false;
        }
        return tick - since >= 20;
    }

    /** The share of its buffer the showcase converter holds, as the design board's gauge shows it. */
    private static final double CONVERTER_FILL = 0.825;
    /** TEST-ONLY: the converter the server tick tops up with the domain's use, or null when nothing is fed. */
    private static volatile BlockPos feeding;
    private static boolean feederRegistered;

    /**
     * Runs the domain on a 1x1x2 Beam Converter standing on the middle cable, its front down, with power required. The
     * buffer starts at {@link #CONVERTER_FILL} and the server tick feeds it the domain's use, standing in for a
     * generator, so the topology's gauge shows a domain that runs.
     */
    static void placeConverter(ServerContext context) {
        var state = state(context);
        var cable = state.cables.get(1);
        var converter = List.of(cable.above(), cable.above(2));
        for (var position : converter) {
            require(context.level().isEmptyBlock(position), "Showcase converter position must be empty: " + position);
            context.level().setBlockAndUpdate(position, space.controlnet.ae2federation.power.PowerRegistration.BEAM_CONVERTER
                    .get().defaultBlockState().setValue(space.controlnet.ae2federation.power.BeamConverterBlock.FACING,
                            Direction.DOWN));
        }
        state.converter = converter;
    }

    /** Whether the converter has formed and joined the Router's domain; it then gets its stock and power is required. */
    static boolean converterJoined(ServerContext context) {
        var state = state(context);
        var level = context.level();
        if (!state.converter.stream().allMatch(position -> space.controlnet.ae2federation.router.RouterBlock.formed(
                level.getBlockState(position)))) {
            return false;
        }
        var routerNode = FederationDomainRegistryAccess.nodeId(level, TaskFifteenWorldFixture.routerPosition(context));
        var converterNode = FederationDomainRegistryAccess.nodeId(level, state.converter.get(0));
        var joined = FederationDomainRegistryAccess.get(level).snapshot().federationDomains().values().stream()
                .anyMatch(domain -> domain.nodes().contains(routerNode) && domain.nodes().contains(converterNode));
        if (!joined) return false;
        var capacity = space.controlnet.ae2federation.power.BeamConverterBlockEntity.CAPACITY;
        for (var position : state.converter) {
            ((space.controlnet.ae2federation.power.BeamConverterBlockEntity) level.getBlockEntity(position))
                    .setStored(capacity * CONVERTER_FILL);
        }
        space.controlnet.ae2federation.power.OperatingPowerRule.set(true,
                space.controlnet.ae2federation.power.OperatingPowerRule.DEFAULT_MULTIPLIER);
        feeding = state.converter.get(0);
        if (!feederRegistered) {
            feederRegistered = true;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(TaskThirtyThreeShowcaseFixture::feedConverter);
        }
        return true;
    }

    /** Whether the Router's domain runs on its converter. */
    static boolean converterRuns(ServerContext context) {
        return routerDomainReading(context).filter(reading -> reading.running() && reading.capacity() > 0).isPresent();
    }

    /** Removes the converter: the domain can no longer pay and stops. */
    static void removeConverter(ServerContext context) {
        feeding = null;
        for (var position : state(context).converter) {
            context.level().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        }
    }

    static boolean domainUnpowered(ServerContext context) {
        return routerDomainReading(context).filter(reading -> !reading.running()).isPresent();
    }

    private static java.util.Optional<space.controlnet.ae2federation.power.OperatingPowerService.Reading> routerDomainReading(
            ServerContext context) {
        var level = context.level();
        var routerNode = FederationDomainRegistryAccess.nodeId(level, TaskFifteenWorldFixture.routerPosition(context));
        return FederationDomainRegistryAccess.get(level).snapshot().federationDomains().values().stream()
                .filter(domain -> domain.nodes().contains(routerNode)).findFirst()
                .flatMap(domain -> space.controlnet.ae2federation.power.OperatingPowerService.reading(level.getServer(),
                        domain.federationDomainId()));
    }

    /** TEST-ONLY generator: each tick, the converter takes in what the domain used on the last one, rounded up. */
    private static void feedConverter(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) {
        var position = feeding;
        if (position == null) return;
        var level = event.getServer().overworld();
        if (!level.isLoaded(position)
                || !(level.getBlockEntity(position) instanceof space.controlnet.ae2federation.power.BeamConverterBlockEntity converter)) {
            return;
        }
        var node = FederationDomainRegistryAccess.nodeId(level, position);
        FederationDomainRegistryAccess.get(level).snapshot().federationDomains().values().stream()
                .filter(domain -> domain.nodes().contains(node)).findFirst()
                .flatMap(domain -> space.controlnet.ae2federation.power.OperatingPowerService.reading(event.getServer(),
                        domain.federationDomainId()))
                .ifPresent(reading -> converter.energy(Direction.UP)
                        .receiveEnergy((int) Math.ceil(reading.use().total()), false));
    }

    private static void stopConverterFeed(ServerContext context) {
        feeding = null;
        space.controlnet.ae2federation.power.OperatingPowerRule.set(false,
                space.controlnet.ae2federation.power.OperatingPowerRule.DEFAULT_MULTIPLIER);
    }

    /** Ticks of storage traffic before the statistics page is opened: most of its two-minute range. */
    private static final int TRAFFIC_TICKS = 1_500;
    /**
     * Test-world activity for the statistics page: [consumer, provider] network indexes, the item, and a base amount.
     * Each consumer takes the item out of the provider's chest through its Storage rule, as its machines would.
     */
    private static final Object[][] TRAFFIC = {
            {0, 4, Items.WHEAT, 3}, {0, 4, Items.CARROT, 2}, {6, 2, Items.RAW_IRON, 2}, {6, 2, Items.COAL, 4},
            {0, 6, Items.IRON_INGOT, 5}, {0, 6, Items.COPPER_INGOT, 2}, {5, 3, Items.OAK_LOG, 4}, {5, 3, Items.GLASS, 2},
            {0, 2, Items.COBBLESTONE, 12}, {0, 2, Items.REDSTONE, 1}};

    /** Starts the statistics showcase's storage traffic. */
    static void startTraffic(ServerContext context) {
        var state = state(context);
        state.trafficStart = context.level().getGameTime();
        state.trafficSlot = -1;
    }

    /**
     * One tick of storage traffic: every half second each route moves a varying amount, with an occasional burst, as
     * automation pulling in batches does. True once {@link #TRAFFIC_TICKS} ticks have passed.
     */
    static boolean traffic(ServerContext context) {
        var state = state(context);
        long tick = context.level().getGameTime() - state.trafficStart;
        // The wait may read the server more or less than once a tick: each half-second slot runs once.
        for (long slot = state.trafficSlot + 1; slot <= tick / 10; slot++) {
            state.trafficSlot = slot;
            long at = slot * 10;
            for (int route = 0; route < TRAFFIC.length; route++) {
                var entry = TRAFFIC[route];
                var consumer = grid(context, state, (Integer) entry[0]);
                if (consumer == null) continue;
                int base = (Integer) entry[3];
                double wave = 0.4 + 0.6 * Math.abs(Math.sin(at / (37.0 + route * 11)));
                long amount = Math.round(base * wave) + (Math.floorMod(at / 100 + route, 5) == 0 ? base * 3L : 0);
                if (amount > 0) consumer.getStorageService().getInventory().extract(AEItemKey.of((Item) entry[2]), amount,
                        Actionable.MODULATE, IActionSource.empty());
            }
        }
        return tick >= TRAFFIC_TICKS;
    }

    /** The grid of the showcase network at {@code index} in {@link State#networks}. */
    private static appeng.api.networking.IGrid grid(ServerContext context, State state, int index) {
        IGridNode node = switch (index) {
            case 0 -> TaskThirtyThreeWorldFixture.mainNode(context);
            case 1 -> TaskThirtyThreeWorldFixture.outerNode(context);
            case 2 -> chestNode(context, state.mine);
            case 3 -> chestNode(context, state.hall);
            default -> chestNode(context, state.remoteNetworks.get(index - 4)[0]);
        };
        return node == null ? null : node.getGrid();
    }

    private static State state(ServerContext context) {
        var state = context.<State>get(STATE);
        require(state != null, "Showcase world was not arranged");
        return state;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static final class State {
        private final BlockPos mine;
        private final BlockPos hall;
        private final List<PolicyKey> rules = new ArrayList<>();
        private List<NetworkId> networks = List.of();
        private List<BlockPos> providers = List.of();
        private List<BlockPos> towerEndpoints = List.of();
        private List<BlockPos> cables = List.of();
        private BlockPos remoteRouter;
        private List<BlockPos[]> remoteNetworks = List.of();
        private BlockPos hallEndpoint;
        private List<BlockPos> converter = List.of();
        private boolean stocked;
        private long trafficStart;
        private long trafficSlot;

        private State(BlockPos mine, BlockPos hall) {
            this.mine = mine;
            this.hall = hall;
        }
    }
}
