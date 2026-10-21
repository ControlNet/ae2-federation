package space.controlnet.ae2federation.automation;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.parts.automation.StackWorldBehaviors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.projection.HeldReturnTransfer;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.script.ListValue;
import space.controlnet.ae2federation.script.RecordValue;
import space.controlnet.ae2federation.script.RunError;
import space.controlnet.ae2federation.script.TypeRef;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityRegistry;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyChanges;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyFilterMode;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyResource;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomainRegistry;
import space.controlnet.ae2federation.storage.provenance.ProvenanceException;

/**
 * The library calls that reach ME networks: the domain's storage, crafting, energy and rules, and inventories at a
 * selected block. A program reaches every network of its domain directly, whatever the rules share (user decision).
 *
 * <p>Assistant choices: {@code network=None} is every network of the domain. Amounts are read from each network's own
 * storage (its drives, cells and storage buses, never what the Federation mounts from other networks), so a sum over
 * the domain counts each item once. Moving into or out of the domain goes through the networks one by one, in the
 * order of their IDs, each paying AE power as an import or export bus does. Crafting runs on the first network that
 * has a pattern and a free CPU. A resource is named by its ID only, so items with components read as their plain item.
 */
final class DomainCalls {
    private static final TypeRef NETWORK = new TypeRef("Network", List.of());

    private final MinecraftServer server;
    private final FederationDomainId domain;
    private final String owner;
    private final GameCatalog catalog;
    /** Whether actions only check what they would do (the editor's dry run). */
    private final boolean simulate;
    private final RegistryResources resources;
    private final NativeSourceDomainRegistry provenance = new NativeSourceDomainRegistry();
    private Map<NetworkId, IGrid> grids = Map.of();
    private int gridsTick = -1;

    DomainCalls(MinecraftServer server, FederationDomainId domain, String owner, GameCatalog catalog,
            boolean simulate) {
        this.server = server;
        this.domain = domain;
        this.owner = owner;
        this.catalog = catalog;
        this.simulate = simulate;
        this.resources = new RegistryResources(server.registryAccess());
    }

    Object call(ProgramHost host, String namespace, String name, List<Object> args) {
        return switch (namespace + "." + name) {
            case "ae2.amount" -> amount(key(args.get(0)), args.get(1));
            case "ae2.storage" -> stacks(stock(args.get(0)));
            case "ae2.craftable" -> grids(args.get(1)).stream()
                    .anyMatch(grid -> grid.getCraftingService().isCraftable(key(args.get(0))));
            case "ae2.crafting" -> grids(args.get(1)).stream()
                    .mapToLong(grid -> grid.getCraftingService().getRequestedAmount(key(args.get(0)))).sum();
            case "ae2.patterns" -> patterns(args.get(0));
            case "ae2.craft" -> craft(key(args.get(0)), (Long) args.get(1), args.get(2));
            // A dry run has no jobs of its own to cancel.
            case "ae2.cancel" -> !simulate && ProgramCrafting.get(server).cancel(owner, key(args.get(0)));
            case "ae2.import_from" -> importFrom(host, args.get(0), key(args.get(1)),
                    host.movable(key(args.get(1)), (Long) args.get(2)), args.get(3));
            case "ae2.export_to" -> exportTo(host, args.get(0), key(args.get(1)),
                    host.movable(key(args.get(1)), (Long) args.get(2)), args.get(3));
            case "ae2.move" -> moveWithin(host, args.get(0), args.get(1), key(args.get(2)), (Long) args.get(3));
            case "ae2.free_cpus" -> grids(args.get(0)).stream().mapToLong(ProgramCrafting::freeCpus).sum();
            case "ae2.energy" -> grids(args.get(0)).stream()
                    .mapToDouble(grid -> grid.getEnergyService().getStoredPower()).sum();
            case "ae2f.online" -> online((String) args.get(0));
            case "ae2f.rule" -> ruleName(PolicyChanges.mode(policies(), policyKey(args)));
            case "ae2f.set_rule" -> setRule(policyKey(args), (String) args.get(3));
            case "ae2f.set_filter" -> setFilter(policyKey(args), (String) args.get(3), (ListValue) args.get(4));
            case "mc.amount" -> host.place(args.get(0)).filter(place -> !ProgramHost.own(place))
                    .map(place -> blockStorage(place, key(args.get(1)).getType())
                            .map(storage -> storage.getAvailableStacks().get(key(args.get(1)))).orElse(0L))
                    .orElse(0L);
            case "mc.storage" -> stacks(host.place(args.get(0)).filter(place -> !ProgramHost.own(place))
                    .map(DomainCalls::blockStock).orElseGet(KeyCounter::new));
            case "mc.move" -> move(host, args.get(0), args.get(1), key(args.get(2)),
                    host.movable(key(args.get(2)), (Long) args.get(3)));
            case "mc.break_block" -> breakBlock(host, args.get(0));
            case "mc.place_block" -> placeBlock(host, args.get(0), key(args.get(1)));
            default -> throw new RunError("NotImplementedError", namespace + "." + name + " is not available yet");
        };
    }

    // ---- names ----

    private AEKey key(Object resource) {
        var id = (String) resource;
        return resources.key(id).orElseThrow(() -> new RunError("ValueError", "unknown resource '" + id + "'"));
    }

    private static String id(AEKey key) {
        return key.getId().toString();
    }

    private NetworkId network(String name) {
        var ids = catalog.matches(NETWORK, name);
        if (ids.isEmpty()) {
            throw new RunError("ValueError", "no network '" + name + "' in this domain");
        }
        if (ids.size() > 1) {
            throw new RunError("ValueError", ids.size() + " networks are named '" + name + "'; name it by its ID");
        }
        return NetworkShortIds.get(server).network(ids.get(0)).orElseThrow();
    }

    /** The domain's networks in the order of their IDs, or the one named; loaded and settled ones only. */
    /** The networks of the domain, in the order of their IDs. */
    List<IGrid> domainGrids() {
        return grids(null);
    }

    private List<IGrid> grids(@Nullable Object network) {
        var live = live();
        if (network != null) {
            var grid = live.get(network((String) network));
            return grid == null ? List.of() : List.of(grid);
        }
        var shortIds = NetworkShortIds.get(server);
        return members().stream().sorted(Comparator.comparing(shortIds::id)).map(live::get)
                .filter(grid -> grid != null).toList();
    }

    private List<NetworkId> members() {
        return FederationDomainRegistryAccess.get(server.overworld()).federationDomain(domain)
                .map(snapshot -> List.copyOf(snapshot.memberships().keySet())).orElse(List.of());
    }

    /** Settled networks by identity, built once per tick: the registry copies its claims on every read. */
    private Map<NetworkId, IGrid> live() {
        if (gridsTick != server.getTickCount()) {
            var found = new HashMap<NetworkId, IGrid>();
            for (var grid : NetworkIdentityRegistry.get(server.overworld()).liveClaims().keySet()) {
                FederationDomainRegistryAccess.confirmedNetworkId(grid).ifPresent(id -> found.put(id, grid));
            }
            grids = found;
            gridsTick = server.getTickCount();
        }
        return grids;
    }

    // ---- the domain's storage ----

    private KeyCounter nativeStock(IGrid grid) {
        var counter = new KeyCounter();
        try {
            for (var source : provenance.discover(grid).sources()) {
                source.storage().getAvailableStacks(counter);
            }
        } catch (ProvenanceException unreadable) {
            // A network whose sources cannot be told apart now counts nothing rather than counting twice.
        }
        return counter;
    }

    private long amount(AEKey key, @Nullable Object network) {
        return grids(network).stream().mapToLong(grid -> nativeStock(grid).get(key)).sum();
    }

    private KeyCounter stock(@Nullable Object network) {
        var total = new KeyCounter();
        grids(network).forEach(grid -> total.addAll(nativeStock(grid)));
        return total;
    }

    /** Stacks largest first, then by ID. */
    private static ListValue stacks(KeyCounter counter) {
        var entries = new ArrayList<Map.Entry<AEKey, Long>>();
        counter.forEach(entry -> {
            if (entry.getLongValue() > 0) {
                entries.add(Map.entry(entry.getKey(), entry.getLongValue()));
            }
        });
        entries.sort(Comparator.comparing((Map.Entry<AEKey, Long> entry) -> -entry.getValue())
                .thenComparing(entry -> id(entry.getKey())));
        var items = new ArrayList<Object>();
        for (var entry : entries) {
            var fields = new LinkedHashMap<String, Object>();
            fields.put("resource", id(entry.getKey()));
            fields.put("amount", entry.getValue());
            items.add(new RecordValue("Stack", fields, true));
        }
        return new ListValue(items);
    }

    private ListValue patterns(@Nullable Object network) {
        var ids = new TreeSet<String>();
        grids(network).forEach(grid -> grid.getCraftingService().getCraftables(AEKeyFilter.none())
                .forEach(key -> ids.add(id(key))));
        return new ListValue(new ArrayList<>(ids));
    }

    private boolean craft(AEKey key, long amount, @Nullable Object network) {
        var crafting = ProgramCrafting.get(server);
        for (var grid : grids(network)) {
            if (simulate ? crafting.couldRequest(grid, key, amount) : crafting.request(owner, grid, key, amount)) {
                return true;
            }
        }
        return false;
    }

    /** Puts as much as fits into the domain, network by network; what went in. */
    private long insert(List<IGrid> grids, AEKey key, long amount, Actionable mode) {
        var left = amount;
        for (var grid : grids) {
            if (left <= 0) {
                break;
            }
            left -= StorageHelper.poweredInsert(grid.getEnergyService(), HeldReturnTransfer.ownStorage(grid), key,
                    left, IActionSource.empty(), mode);
        }
        return amount - left;
    }

    /** Takes as much as there is from the domain, network by network; what came out. */
    private long extract(List<IGrid> grids, AEKey key, long amount, Actionable mode) {
        var left = amount;
        for (var grid : grids) {
            if (left <= 0) {
                break;
            }
            left -= StorageHelper.poweredExtraction(grid.getEnergyService(), HeldReturnTransfer.ownStorage(grid), key,
                    left, IActionSource.empty(), mode);
        }
        return amount - left;
    }

    // ---- inventories at a block ----

    /** The block's inventory of one resource type through the selected face, as a storage bus sees it. */
    private static Optional<MEStorage> blockStorage(ProgramHost.Place place, AEKeyType type) {
        ExternalStorageStrategy strategy = StackWorldBehaviors
                .createExternalStorageStrategies(place.level(), place.position(), place.side()).get(type);
        return Optional.ofNullable(strategy == null ? null : strategy.createWrapper(false, () -> {
        }));
    }

    private static KeyCounter blockStock(ProgramHost.Place place) {
        var counter = new KeyCounter();
        StackWorldBehaviors.createExternalStorageStrategies(place.level(), place.position(), place.side()).values()
                .forEach(strategy -> {
                    var storage = strategy.createWrapper(false, () -> {
                    });
                    if (storage != null) {
                        storage.getAvailableStacks(counter);
                    }
                });
        return counter;
    }

    /** What the block can give and the place can take, moved for real unless simulating; what moved. */
    private long transfer(MEStorage from, MEStorage to, AEKey key, long amount) {
        var source = IActionSource.empty();
        var offered = from.extract(key, amount, Actionable.SIMULATE, source);
        var accepted = offered <= 0 ? 0 : to.insert(key, offered, Actionable.SIMULATE, source);
        if (accepted <= 0 || simulate) {
            return accepted;
        }
        var taken = from.extract(key, accepted, Actionable.MODULATE, source);
        var stored = to.insert(key, taken, Actionable.MODULATE, source);
        if (stored < taken) {
            // The target took less than it promised: give the rest back rather than lose it.
            from.insert(key, taken - stored, Actionable.MODULATE, source);
        }
        return stored;
    }

    private boolean move(ProgramHost host, Object sourceBlock, Object targetBlock, AEKey key, long amount) {
        var from = host.place(sourceBlock).filter(place -> !ProgramHost.own(place))
                .flatMap(place -> blockStorage(place, key.getType()));
        var to = host.place(targetBlock).filter(place -> !ProgramHost.own(place))
                .flatMap(place -> blockStorage(place, key.getType()));
        return from.isPresent() && to.isPresent() && amount > 0 && transfer(from.get(), to.get(), key, amount) > 0;
    }

    /** The domain as one storage for {@link #transfer}: networks in order, paying power. */
    private MEStorage domainStorage(@Nullable Object network) {
        var grids = grids(network);
        return new MEStorage() {
            @Override
            public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
                return DomainCalls.this.insert(grids, what, amount, mode);
            }

            @Override
            public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
                return DomainCalls.this.extract(grids, what, amount, mode);
            }

            @Override
            public net.minecraft.network.chat.Component getDescription() {
                return net.minecraft.network.chat.Component.literal("Federation domain");
            }
        };
    }

    private boolean moveWithin(ProgramHost host, @Nullable Object source, @Nullable Object target, AEKey key,
            long amount) {
        var movable = host.movable(key, amount);
        return movable > 0 && transfer(domainStorage(source), domainStorage(target), key, movable) > 0;
    }

    private boolean importFrom(ProgramHost host, Object block, AEKey key, long amount, @Nullable Object network) {
        var from = host.place(block).filter(place -> !ProgramHost.own(place))
                .flatMap(place -> blockStorage(place, key.getType()));
        return from.isPresent() && amount > 0 && transfer(from.get(), domainStorage(network), key, amount) > 0;
    }

    private boolean exportTo(ProgramHost host, Object block, AEKey key, long amount, @Nullable Object network) {
        var to = host.place(block).filter(place -> !ProgramHost.own(place))
                .flatMap(place -> blockStorage(place, key.getType()));
        return to.isPresent() && amount > 0 && transfer(domainStorage(network), to.get(), key, amount) > 0;
    }

    /** Breaks the block only if all its drops fit into the domain, so nothing is lost or left lying around. */
    private boolean breakBlock(ProgramHost host, Object block) {
        var found = host.place(block).filter(place -> !ProgramHost.own(place));
        if (found.isEmpty()) {
            return false;
        }
        var place = found.get();
        var level = place.level();
        var state = level.getBlockState(place.position());
        if (state.isAir() || state.getDestroySpeed(level, place.position()) < 0) {
            return false;
        }
        var drops = Block.getDrops(state, level, place.position(), level.getBlockEntity(place.position()));
        var grids = grids(null);
        var keys = new KeyCounter();
        drops.forEach(drop -> {
            var key = appeng.api.stacks.AEItemKey.of(drop);
            if (key != null) {
                keys.add(key, drop.getCount());
            }
        });
        for (var entry : keys) {
            if (insert(grids, entry.getKey(), entry.getLongValue(), Actionable.SIMULATE) < entry.getLongValue()) {
                return false;
            }
        }
        if (simulate) {
            return true;
        }
        if (!level.destroyBlock(place.position(), false)) {
            return false;
        }
        keys.forEach(entry -> insert(grids, entry.getKey(), entry.getLongValue(), Actionable.MODULATE));
        return true;
    }

    /** Places the item as a block where the block can be replaced and the new one can stand. */
    private boolean placeBlock(ProgramHost host, Object block, AEKey item) {
        var found = host.place(block).filter(place -> !ProgramHost.own(place));
        if (found.isEmpty() || !(item instanceof appeng.api.stacks.AEItemKey itemKey)
                || !(itemKey.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        var place = found.get();
        var level = place.level();
        var placed = blockItem.getBlock().defaultBlockState();
        if (!level.getBlockState(place.position()).canBeReplaced() || !placed.canSurvive(level, place.position())) {
            return false;
        }
        var grids = grids(null);
        if (extract(grids, item, 1, Actionable.SIMULATE) < 1) {
            return false;
        }
        if (simulate) {
            return true;
        }
        extract(grids, item, 1, Actionable.MODULATE);
        if (!level.setBlock(place.position(), placed, Block.UPDATE_ALL)) {
            insert(grids, item, 1, Actionable.MODULATE);
            return false;
        }
        var sound = placed.getSoundType(level, place.position(), null);
        level.playSound(null, place.position(), sound.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
        return true;
    }

    // ---- networks and rules ----

    private boolean online(String name) {
        var network = network(name);
        var grid = live().get(network);
        var registry = FederationDomainRegistryAccess.get(server.overworld());
        return grid != null && members().contains(network) && grid.getEnergyService().isNetworkPowered()
                && !registry.stopped(domain);
    }

    private PolicyService policies() {
        return PolicyService.get(server.overworld());
    }

    /** {@code (provider, consumer, capability, ...)}, both networks of this domain. */
    private PolicyKey policyKey(List<Object> args) {
        var provider = network((String) args.get(0));
        var consumer = network((String) args.get(1));
        if (provider.equals(consumer)) {
            throw new RunError("ValueError", "a network has no rule with itself");
        }
        var capability = switch ((String) args.get(2)) {
            case "storage" -> PolicyCapability.STORAGE;
            case "crafting" -> PolicyCapability.CRAFTING;
            default -> PolicyCapability.ME_POWER;
        };
        return new PolicyKey(consumer, provider, capability);
    }

    private static String ruleName(RuleMode mode) {
        return switch (mode) {
            case DISABLED -> "off";
            case ENABLED -> "on";
            case REEXPORT -> "reexport";
        };
    }

    private static RuleMode ruleMode(String name) {
        return switch (name) {
            case "on" -> RuleMode.ENABLED;
            case "reexport" -> RuleMode.REEXPORT;
            default -> RuleMode.DISABLED;
        };
    }

    private boolean setRule(PolicyKey key, String modeName) {
        var mode = ruleMode(modeName);
        if (!mode.allowedFor(key.capability())) {
            throw new RunError("ValueError", "shared power is one pool; it cannot be re-exported");
        }
        if (simulate) {
            return true;
        }
        return applied(PolicyChanges.apply(policies(), key, mode, rule -> rule));
    }

    private boolean setFilter(PolicyKey key, String modeName, ListValue list) {
        if (key.capability() != PolicyCapability.STORAGE) {
            throw new RunError("ValueError", "only storage rules have a filter");
        }
        var mode = switch (modeName) {
            case "allow" -> PolicyFilterMode.ALLOW_LIST;
            case "deny" -> PolicyFilterMode.DENY_LIST;
            default -> PolicyFilterMode.ALL;
        };
        var entries = new java.util.HashSet<PolicyResource>();
        if (mode != PolicyFilterMode.ALL) {
            for (var item : list.items()) {
                var resource = key(item);
                entries.add(new PolicyResource(resource.getType().getId(), resource.getId()));
            }
        }
        if (simulate) {
            return true;
        }
        var filter = new PolicyFilter(mode, entries);
        var service = policies();
        return applied(PolicyChanges.apply(service, key, PolicyChanges.mode(service, key),
                rule -> new PolicyRule(rule.enabled(), rule.operations(), filter, rule.allowReexport())));
    }

    /** Nothing to change counts as done: the rule is as asked. */
    private static boolean applied(Optional<PolicyMutationResult> result) {
        return result.map(answer -> answer instanceof PolicyMutationResult.Accepted).orElse(true);
    }
}
