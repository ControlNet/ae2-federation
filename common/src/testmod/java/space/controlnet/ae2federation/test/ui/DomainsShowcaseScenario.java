package space.controlnet.ae2federation.test.ui;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.persistence.NetworkNames;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyDelete;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * Several Federation Domains around one, for design review: the topology's "with connected domains" scope with few
 * networks per domain, so every card stays readable at Fit. A hub Switch holds two networks of its own and one shared
 * network on each of three faces; each shared network also touches a neighbour's Switch, which holds one or two
 * networks of its own. Every domain is one hop from the hub, so the picture is three columns wide. Names, stock and
 * rules are test-world data chosen to look like a base.
 */
@LDLRegisterClient(name = "ui.showcase-domains", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DomainsShowcaseScenario implements UIScenario {
    private static final String STATE = "showcaseDomains.state";
    /** Main Base, Storage Hall and one shared network per neighbour. */
    private static final int HUB_NETWORKS = 2 + Neighbour.ALL.size();

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("actual-client").guiScale(3).defaultTimeoutMs(30_000).scenarioTimeoutMs(150_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario.timeoutMs(45_000).waitUntil("resources finish loading", context -> context.mc().getOverlay() == null)
                .frames(5)
                .server("place a hub Switch and three neighbour Switches, linked by shared networks",
                        DomainsShowcaseScenario::place)
                .teardownServer("remove them and their rules", DomainsShowcaseScenario::remove)
                .waitUntilServer("four domains form, each with its networks", DomainsShowcaseScenario::ready)
                .server("name the networks, stock them and link them", DomainsShowcaseScenario::nameAndLink)
                .serverGet("record Main Base", "main.id", context -> state(context).ids.get(
                        state(context).names.keySet().iterator().next()).value().toString())
                .step("hide the tutorial toast", context -> {
                    context.mc().getToasts().clear();
                    context.mc().getTutorial().stop();
                })
                .server("open the hub Switch", context -> {
                    if (!FederationDomainPolicyMenu.openRouter(context.player(), state(context).hub)) {
                        throw new IllegalStateException("the hub Switch's screen must open");
                    }
                })
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .waitUntil("the hub's cards", context -> context.all(".graph-node-member").size() == HUB_NETWORKS)
                .click("#graph_scope")
                .waitUntil("the neighbours' own networks join, read-only", context ->
                        context.all(".related-network").size() == Neighbour.OWN_NETWORKS
                                && context.all(".graph-node-member").size() == HUB_NETWORKS + Neighbour.OWN_NETWORKS)
                .serverTicks(60).frames(10)
                .screenshot("ui-showcase-domains-focus")
                .click("#graph_fit")
                .waitUntil("Fit brings every card into view", context -> {
                    var viewport = context.el("#domain_graph").bounds();
                    return context.all(".graph-node-member").stream().map(element -> element.bounds()).allMatch(card ->
                            card.x() >= viewport.x() && card.y() >= viewport.y()
                                    && card.x() + card.width() <= viewport.x() + viewport.width()
                                    && card.y() + card.height() <= viewport.y() + viewport.height());
                })
                .step("select Main Base, whose links reach every shared network", context ->
                        TaskThirtyThreeScenarioSupport.selectNetworkCard(context, context.get("main.id")))
                .frames(5)
                .frames(10)
                .screenshot("ui-showcase-domains")
                .closeScreen();
    }

    /** A neighbour domain: its shared network's name and stock, then its own networks'. */
    private record Neighbour(Direction face, String shared, Map<Item, Long> sharedStock, List<String> own,
            List<Map<Item, Long>> ownStock) {
        static final List<Neighbour> ALL = List.of(
                new Neighbour(Direction.NORTH, "North Gate", Map.of(Items.RAIL, 640L, Items.POWERED_RAIL, 128L,
                        Items.MINECART, 12L), List.of("Mine"), List.of(
                        Map.of(Items.COBBLESTONE, 18_432L, Items.RAW_IRON, 1_216L, Items.RAW_COPPER, 904L,
                                Items.COAL, 2_310L, Items.DIAMOND, 37L))),
                new Neighbour(Direction.SOUTH, "Portal Room", Map.of(Items.OBSIDIAN, 96L, Items.FLINT, 32L),
                        List.of("Nether Outpost"), List.of(Map.of(Items.NETHERRACK, 12_800L, Items.QUARTZ, 2_048L,
                                Items.GLOWSTONE_DUST, 640L, Items.BLAZE_ROD, 48L))));
        static final int OWN_NETWORKS = ALL.stream().mapToInt(neighbour -> neighbour.own.size()).sum();
    }

    private static final class State {
        final BlockPos hub;
        final List<BlockPos> blocks = new ArrayList<>();
        /** Each network's ME Chest, in naming order, with its name and stock. */
        final Map<BlockPos, String> names = new LinkedHashMap<>();
        final Map<BlockPos, Map<Item, Long>> stock = new LinkedHashMap<>();
        final List<BlockPos> neighbours = new ArrayList<>();
        final List<PolicyKey> rules = new ArrayList<>();
        Map<BlockPos, NetworkId> ids = Map.of();

        State(BlockPos hub) {
            this.hub = hub;
        }
    }

    /**
     * The hub Switch has Main Base on its west face and Storage Hall on its top. On each of its north, east and south
     * faces sits a shared network: an ME Chest against the hub and its energy cell against the neighbour's Switch,
     * three blocks out. The neighbour's own networks sit on its outer face and, for a second one, its clockwise side.
     * No two networks touch and no two Switches touch, so every Switch is a domain of its own.
     */
    private static void place(ServerContext context) {
        context.level().getChunk(0, 0);
        var ground = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 6, 8);
        context.player().connection.teleport(6.5, ground, 8.5, 0, 0);
        var state = new State(new BlockPos(8, ground + 3, 8));
        context.put(STATE, state);
        var hub = state.hub;
        block(context, state, hub, RouterRegistration.SWITCH.get().defaultBlockState());
        network(context, state, hub.west(), hub.west(2), "Main Base", Map.of(Items.OAK_LOG, 3_072L, Items.GLASS, 1_408L,
                Items.IRON_INGOT, 2_560L, Items.TORCH, 384L, Items.BREAD, 96L));
        network(context, state, hub.above(), hub.above(2), "Storage Hall", Map.of(Items.SAND, 5_120L,
                Items.GOLD_INGOT, 312L, Items.QUARTZ, 688L, Items.STRING, 96L, Items.REDSTONE, 2_880L));
        for (var neighbour : Neighbour.ALL) {
            var face = neighbour.face;
            network(context, state, hub.relative(face), hub.relative(face, 2), neighbour.shared, neighbour.sharedStock);
            var at = hub.relative(face, 3);
            state.neighbours.add(at);
            block(context, state, at, RouterRegistration.SWITCH.get().defaultBlockState());
            var sides = List.of(face, face.getClockWise());
            for (int index = 0; index < neighbour.own.size(); index++) {
                var side = sides.get(index);
                network(context, state, at.relative(side), at.relative(side, 2), neighbour.own.get(index),
                        neighbour.ownStock.get(index));
            }
        }
    }

    private static void network(ServerContext context, State state, BlockPos chest, BlockPos cell, String name,
            Map<Item, Long> stock) {
        block(context, state, chest, AEBlocks.ME_CHEST.block().defaultBlockState());
        ((MEChestBlockEntity) context.level().getBlockEntity(chest)).setCell(AEItems.ITEM_CELL_4K.stack());
        block(context, state, cell, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        state.names.put(chest, name);
        state.stock.put(chest, stock);
    }

    private static void block(ServerContext context, State state, BlockPos position,
            net.minecraft.world.level.block.state.BlockState block) {
        if (!context.level().isEmptyBlock(position)) {
            throw new IllegalStateException("Showcase position must be empty: " + position);
        }
        context.level().setBlockAndUpdate(position, block);
        state.blocks.add(position);
    }

    /** Whether every network has its identity and each Switch's domain holds exactly the networks on its faces. */
    private static boolean ready(ServerContext context) {
        var state = state(context);
        var ids = new LinkedHashMap<BlockPos, NetworkId>();
        for (var chest : state.names.keySet()) {
            var node = chestNode(context, chest);
            if (node == null || !node.isActive() || node.getGrid() == null) return false;
            var id = FederationDomainRegistryAccess.confirmedNetworkId(node.getGrid()).orElse(null);
            if (id == null || ids.containsValue(id)) return false;
            ids.put(chest, id);
        }
        var chests = List.copyOf(state.names.keySet());
        // Naming order: Main Base, Storage Hall, then per neighbour its shared network and its own ones.
        var hubNetworks = new ArrayList<NetworkId>(List.of(ids.get(chests.get(0)), ids.get(chests.get(1))));
        int next = 2;
        var expected = new LinkedHashMap<BlockPos, Set<NetworkId>>();
        for (int index = 0; index < Neighbour.ALL.size(); index++) {
            var neighbour = Neighbour.ALL.get(index);
            var shared = ids.get(chests.get(next++));
            hubNetworks.add(shared);
            var own = new ArrayList<NetworkId>(List.of(shared));
            for (int count = 0; count < neighbour.own.size(); count++) own.add(ids.get(chests.get(next++)));
            expected.put(state.neighbours.get(index), Set.copyOf(own));
        }
        expected.put(state.hub, Set.copyOf(hubNetworks));
        var domains = FederationDomainRegistryAccess.get(context.level()).snapshot().federationDomains().values();
        for (var entry : expected.entrySet()) {
            var node = FederationDomainRegistryAccess.nodeId(context.level(), entry.getKey());
            var formed = domains.stream().anyMatch(domain -> domain.nodes().contains(node)
                    && domain.memberships().keySet().equals(entry.getValue()));
            if (!formed) return false;
        }
        state.ids = Map.copyOf(ids);
        return true;
    }

    /**
     * Names and stocks every network. In the hub, Main Base uses Storage Hall and every shared network; in each
     * neighbour, its own networks use the shared one and share its energy.
     */
    private static void nameAndLink(ServerContext context) {
        var state = state(context);
        var names = NetworkNames.get(context.level());
        state.names.forEach((chest, name) -> names.rename(state.ids.get(chest), name));
        state.stock.forEach((chest, items) -> {
            var inventory = chestNode(context, chest).getGrid().getStorageService().getInventory();
            items.forEach((item, amount) -> inventory.insert(AEItemKey.of(item), amount, Actionable.MODULATE,
                    IActionSource.empty()));
        });
        var chests = List.copyOf(state.names.keySet());
        var main = state.ids.get(chests.get(0));
        rule(context, state, new PolicyKey(main, state.ids.get(chests.get(1)), PolicyCapability.STORAGE),
                PolicyRule.storageDefaults());
        int next = 2;
        for (var neighbour : Neighbour.ALL) {
            var shared = state.ids.get(chests.get(next++));
            rule(context, state, new PolicyKey(main, shared, PolicyCapability.STORAGE), PolicyRule.storageDefaults());
            for (int count = 0; count < neighbour.own.size(); count++) {
                var own = state.ids.get(chests.get(next++));
                rule(context, state, new PolicyKey(own, shared, PolicyCapability.STORAGE), PolicyRule.storageDefaults());
                rule(context, state, new PolicyKey(own, shared, PolicyCapability.ME_POWER),
                        PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
            }
        }
    }

    private static void rule(ServerContext context, State state, PolicyKey key, PolicyRule rule) {
        var policies = PolicyService.get(context.level());
        var result = policies.edit(new PolicyEdit(key, policies.revision(key), rule));
        if (!(result instanceof PolicyMutationResult.Accepted)) {
            throw new IllegalStateException("Showcase rule must be accepted: " + key + " " + result);
        }
        state.rules.add(key);
    }

    private static void remove(ServerContext context) {
        State state = context.get(STATE);
        if (state == null) return;
        var policies = PolicyService.get(context.level());
        for (var rule : state.rules) {
            if (policies.configured(rule).isPresent()) policies.delete(new PolicyDelete(rule, policies.revision(rule)));
        }
        for (var position : state.blocks.reversed()) {
            context.level().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        }
    }

    private static IGridNode chestNode(ServerContext context, BlockPos position) {
        return context.level().getBlockEntity(position) instanceof MEChestBlockEntity chest ? chest.getMainNode().getNode() : null;
    }

    private static State state(ServerContext context) {
        return context.get(STATE);
    }
}
