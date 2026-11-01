package space.controlnet.ae2federation.automation;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEItemIds;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.script.DictValue;
import space.controlnet.ae2federation.script.ListValue;
import space.controlnet.ae2federation.script.Ops;
import space.controlnet.ae2federation.script.RecordValue;
import space.controlnet.ae2federation.script.RunError;

/**
 * The library calls for patterns: the recipes a pattern can hold ({@code mc.recipes_for}), the domain's pattern
 * providers and their patterns, and writing a recipe on a blank pattern into a provider.
 *
 * <p>Providers are found as AE2's Pattern Access Terminal finds them: every active machine of a network that is a
 * {@link PatternContainer}, so an addon's providers are listed too. A provider is placed by its block entity: a
 * {@link PatternProviderLogicHost}'s own, or the container itself when it is one; on a cable, the part's side is part
 * of its ID. A container placed neither way has no position to give and is left out.
 *
 * <p>Assistant choices: a provider's {@code targets} are the items of the machines on its faces, found per face with
 * AE2's own rule ({@link PatternContainerGroup#fromMachine}), because a named provider's terminal group no longer shows
 * them. A provider is named in later calls by its ID, looked up again among the domain's providers at each call; one
 * no longer there gives an empty list or False. Writing takes one blank pattern from the domain's storage, network by
 * network as other calls take items, paying AE power, only once the encoded pattern decodes and the provider has an
 * empty slot that takes it. A slot whose item is not picked takes the accepted item the domain stores most of, else the
 * first. Fluid substitutes stay off. A processing recipe the index does not hold is written as its record gives it, as
 * AE2's terminal encodes any processing pattern a player sets up.
 */
final class PatternCalls {
    private final MinecraftServer server;
    private final boolean simulate;
    private final DomainCalls domain;

    PatternCalls(MinecraftServer server, boolean simulate, DomainCalls domain) {
        this.server = server;
        this.simulate = simulate;
        this.domain = domain;
    }

    /** A pattern container on a network of the domain, and where it is. */
    private record Found(PatternContainer container, IGridNode node, NetworkId network, ServerLevel level,
            BlockPos position, @Nullable Direction side) {
        /** {@code minecraft:overworld 12 64 -3}, and the part's side on a cable: {@code ... north}. */
        String id() {
            return level.dimension().location() + " " + position.getX() + " " + position.getY() + " "
                    + position.getZ() + (side == null ? "" : " " + side.getName());
        }
    }

    Object call(ProgramHost host, String call, List<Object> args) {
        return switch (call) {
            case "mc.recipes_for" -> recipesFor(args.get(0));
            case "ae2.providers" -> new ListValue(new ArrayList<>(found(args.get(0)).stream()
                    .map(found -> (Object) record(found)).toList()));
            case "ae2.provider_at" -> providerAt(host, args.get(0));
            case "ae2.provider_patterns" -> patterns((RecordValue) args.get(0));
            case "ae2.write_pattern" -> write((RecordValue) args.get(0), (RecordValue) args.get(1),
                    (DictValue) args.get(2), (Boolean) args.get(3));
            default -> throw new RunError("NotImplementedError", call + " is not available yet");
        };
    }

    // ---- recipes ----

    /** The server's recipes for the item, then those a player's recipe viewer showed ({@link ViewerRecipes}). */
    private ListValue recipesFor(Object item) {
        var out = new ArrayList<Object>();
        if (domain.key(item) instanceof AEItemKey key) {
            var viewer = ViewerRecipes.get(server);
            RecipeIndex.of(server).making(key.getItem())
                    .forEach(entry -> out.add(RecipeIndex.record(entry, viewer.machines(entry.type()))));
            out.addAll(viewer.making(key.getItem(), domain::named));
        }
        return new ListValue(out);
    }

    // ---- finding providers ----

    /** The domain's providers, or one network's, network by network, each by position. */
    private List<Found> found(@Nullable Object network) {
        return foundOn(domain.networks(network));
    }

    private static List<Found> foundOn(List<java.util.Map.Entry<NetworkId, appeng.api.networking.IGrid>> networks) {
        var out = new ArrayList<Found>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<PatternContainer, Boolean>());
        for (var entry : networks) {
            var grid = entry.getValue();
            var here = new ArrayList<Found>();
            for (var machineClass : grid.getMachineClasses()) {
                if (!PatternContainer.class.isAssignableFrom(machineClass)) {
                    continue;
                }
                for (var node : grid.getMachineNodes(machineClass)) {
                    if (node.isActive() && node.getOwner() instanceof PatternContainer container
                            && seen.add(container)) {
                        locate(container, node, entry.getKey()).ifPresent(here::add);
                    }
                }
            }
            here.sort(Comparator.comparing((Found found) -> found.level().dimension().location().toString())
                    .thenComparing(Found::position)
                    .thenComparing(found -> found.side() == null ? -1 : found.side().ordinal()));
            out.addAll(here);
        }
        return out;
    }

    private static Optional<Found> locate(PatternContainer container, IGridNode node, NetworkId network) {
        BlockEntity entity = container instanceof PatternProviderLogicHost host ? host.getBlockEntity()
                : container instanceof BlockEntity own ? own : null;
        if (entity == null || entity.isRemoved() || !(entity.getLevel() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        Direction side = null;
        if (entity != container && entity instanceof IPartHost parts) {
            for (var direction : Direction.values()) {
                if (parts.getPart(direction) == container) {
                    side = direction;
                }
            }
        }
        return Optional.of(new Found(container, node, network, level, entity.getBlockPos(), side));
    }

    /**
     * The provider a {@code Provider} record names, if it is still on a network of the domain: looked for on the
     * record's network first, so a call costs one network's providers, then on the others in case it moved.
     */
    private Optional<Found> resolve(RecordValue provider) {
        var id = provider.field("id");
        var network = NetworkShortIds.get(server).network((String) provider.field("network")).orElse(null);
        var all = domain.networks(null);
        var own = all.stream().filter(entry -> entry.getKey().equals(network)).toList();
        return foundOn(own).stream().filter(found -> found.id().equals(id)).findFirst()
                .or(() -> foundOn(all).stream().filter(found -> found.id().equals(id)).findFirst());
    }

    private @Nullable RecordValue providerAt(ProgramHost host, Object block) {
        var device = host.place(block).filter(place -> !ProgramHost.own(place))
                .flatMap(place -> DeviceCalls.device(place, PatternContainer.class));
        if (device.isEmpty()) {
            return null;
        }
        return found(null).stream().filter(found -> found.container() == device.get()).findFirst()
                .map(this::record).orElse(null);
    }

    // ---- the Provider record ----

    private RecordValue record(Found found) {
        var container = found.container();
        var inventory = container.getTerminalPatternInventory();
        long free = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            free += inventory.getStackInSlot(slot).isEmpty() ? 1 : 0;
        }
        var fields = new LinkedHashMap<String, Object>();
        fields.put("id", found.id());
        fields.put("name", container instanceof Nameable nameable && nameable.hasCustomName()
                && nameable.getCustomName() != null ? nameable.getCustomName().getString() : null);
        fields.put("targets", new ListValue(new ArrayList<>(targets(found))));
        fields.put("group", container.getTerminalGroup().name().getString());
        fields.put("block", block(found));
        fields.put("network", domain.shortId(found.network()));
        fields.put("x", (long) found.position().getX());
        fields.put("y", (long) found.position().getY());
        fields.put("z", (long) found.position().getZ());
        fields.put("dimension", found.level().dimension().location().toString());
        fields.put("slots", (long) inventory.size());
        fields.put("free", free);
        fields.put("makes", new ListValue(new ArrayList<>(makes(found))));
        fields.put("in_terminal", container.isVisibleInTerminal());
        return new RecordValue("Provider", fields, true);
    }

    /**
     * The items of the machines the provider pushes into, as AE2 groups them: a provider's target faces (a part's
     * own side, a block's faces), without those joined to another provider or to an interface of the same network.
     */
    private static List<Object> targets(Found found) {
        var faces = EnumSet.noneOf(Direction.class);
        if (found.container() instanceof PatternProviderLogicHost host) {
            faces.addAll(host.getTargets());
        } else if (found.side() != null) {
            faces.add(found.side());
        } else {
            faces.addAll(EnumSet.allOf(Direction.class));
        }
        for (var connection : found.node().getInWorldConnections().entrySet()) {
            var other = connection.getValue().getOtherSide(found.node());
            if (other.getOwner() instanceof PatternProviderLogicHost
                    || other.getOwner() instanceof InterfaceLogicHost && other.getGrid() == found.node().getGrid()) {
                faces.remove(connection.getKey());
            }
        }
        var items = new LinkedHashSet<Object>();
        for (var face : faces) {
            var at = found.position().relative(face);
            if (!found.level().isLoaded(at)) {
                continue;
            }
            var group = PatternContainerGroup.fromMachine(found.level(), at, face.getOpposite());
            if (group != null && group.icon() != null && !group.icon().getItem().equals(Items.AIR)) {
                items.add(group.icon().getId().toString());
            }
        }
        return List.copyOf(items);
    }

    /** The provider's own item: its node's icon, else its block's. */
    private static String block(Found found) {
        var icon = found.node().getVisualRepresentation();
        if (icon != null) {
            return icon.getId().toString();
        }
        var state = found.level().getBlockState(found.position());
        return BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()).toString();
    }

    /** What the provider's patterns make: their primary outputs, as AE2 counts them craftable. */
    private static List<Object> makes(Found found) {
        var service = found.node().getService(ICraftingProvider.class);
        List<IPatternDetails> patterns = service != null ? service.getAvailablePatterns() : decoded(found);
        var made = new TreeSet<String>();
        for (var pattern : patterns) {
            var primary = pattern.getPrimaryOutput();
            if (primary != null) {
                made.add(primary.what().getId().toString());
            }
        }
        return List.copyOf(made);
    }

    private static List<IPatternDetails> decoded(Found found) {
        var inventory = found.container().getTerminalPatternInventory();
        var out = new ArrayList<IPatternDetails>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            var details = stack.isEmpty() ? null : PatternDetailsHelper.decodePattern(stack, found.level());
            if (details != null) {
                out.add(details);
            }
        }
        return out;
    }

    // ---- reading and writing patterns ----

    /**
     * The provider's patterns slot by slot, each read as its recipe; what AE2 cannot decode is left out, and so is a
     * processing pattern whose resources a program cannot name by ID.
     */
    private ListValue patterns(RecordValue provider) {
        var out = new ArrayList<Object>();
        resolve(provider).ifPresent(found -> {
            var index = RecipeIndex.of(server);
            var inventory = found.container().getTerminalPatternInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                var stack = inventory.getStackInSlot(slot);
                var details = stack.isEmpty() ? null : PatternDetailsHelper.decodePattern(stack, found.level());
                var recipe = details == null ? null : index.read(stack, details, domain::named,
                        ViewerRecipes.get(server)::machines);
                if (recipe != null) {
                    out.add(recipe);
                }
            }
        });
        return new ListValue(out);
    }

    /**
     * Writes a recipe the index holds as the server has it; any other processing recipe as its record gives its
     * inputs and outputs. A recipe viewer may show a server recipe under another machine's category (Create packs the
     * crafting table's iron block in a basin): with another type than the server's, the record is its own recipe.
     */
    private boolean write(RecordValue provider, RecordValue recipe, DictValue items, boolean substitutes) {
        var id = (String) recipe.field("id");
        var entry = id == null ? null : RecipeIndex.of(server).byId(id);
        if (entry != null && !entry.type().equals(recipe.field("type"))) {
            entry = null;
        }
        if (entry == null && !"processing".equals(recipe.field("kind"))) {
            throw new RunError("ValueError", "AE2 cannot write recipe " + Ops.repr(id) + " on a pattern");
        }
        var label = id == null ? "the recipe" : id;
        var chosen = choose(label, entry != null ? slots(entry) : slots(recipe), items);
        var outputs = entry != null ? List.of(new GenericStack(AEItemKey.of(entry.output()), entry.output().getCount()))
                : stacks(recipe.field("outputs"));
        var found = resolve(provider);
        if (found.isEmpty()) {
            return false;
        }
        var level = found.get().level();
        var encoded = entry != null && !entry.kind().equals("processing") ? encode(entry, chosen, substitutes, level)
                : processing(label, chosen, outputs);
        if (encoded.isEmpty() || PatternDetailsHelper.decodePattern(encoded, level) == null) {
            throw new RunError("ValueError", "AE2 cannot use " + (id == null ? "the recipe" : "recipe " + Ops.repr(id))
                    + " with these items");
        }
        var inventory = found.get().container().getTerminalPatternInventory();
        int slot = freeSlot(inventory, encoded);
        var grids = domain.domainGrids();
        var blank = AEItemKey.of(BuiltInRegistries.ITEM.get(AEItemIds.BLANK_PATTERN));
        if (slot < 0 || domain.extract(grids, blank, 1, Actionable.SIMULATE) < 1) {
            return false;
        }
        if (simulate) {
            return true;
        }
        if (domain.extract(grids, blank, 1, Actionable.MODULATE) < 1) {
            return false;
        }
        if (!inventory.insertItem(slot, encoded, false).isEmpty()) {
            // The slot promised to take it; give the blank back rather than lose it.
            domain.insert(grids, blank, 1, Actionable.MODULATE);
            return false;
        }
        return true;
    }

    /** The first empty slot that takes the pattern; -1 if none. */
    private static int freeSlot(InternalInventory inventory, ItemStack pattern) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty() && inventory.insertItem(slot, pattern, true).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** A recipe's slots as the index reads them, each item one of a slot's. */
    private static List<List<GenericStack>> slots(RecipeIndex.Entry entry) {
        var out = new ArrayList<List<GenericStack>>();
        for (var slot : entry.slots()) {
            out.add(slot.stream().map(stack -> new GenericStack(AEItemKey.of(stack), stack.getCount())).toList());
        }
        return out;
    }

    /** A processing recipe's slots as its record gives them; an empty slot holds nothing to put in. */
    private List<List<GenericStack>> slots(RecordValue recipe) {
        var out = new ArrayList<List<GenericStack>>();
        if (recipe.field("inputs") instanceof ListValue inputs) {
            for (var slot : inputs.items()) {
                var accepted = stacks(slot);
                if (!accepted.isEmpty()) {
                    out.add(accepted);
                }
            }
        }
        return out;
    }

    private List<GenericStack> stacks(Object list) {
        var out = new ArrayList<GenericStack>();
        for (var value : list instanceof ListValue stacks ? stacks.items() : List.of()) {
            if (!(value instanceof RecordValue stack) || !stack.type().equals("Stack")) {
                throw new RunError("TypeError", "a recipe's inputs and outputs hold Stacks, not " + Ops.repr(value));
            }
            long amount = ((Number) stack.field("amount")).longValue();
            if (amount <= 0) {
                throw new RunError("ValueError", "a recipe cannot take or make " + amount + " of "
                        + stack.field("resource"));
            }
            out.add(new GenericStack(domain.key(stack.field("resource")), amount));
        }
        return out;
    }

    /**
     * The stack for each slot: the resource picked in {@code items}, else the one accepted that the domain stores most
     * of, else the first accepted; null for an empty slot. A pick the slot does not take stops the run.
     */
    private GenericStack[] choose(String label, List<List<GenericStack>> slots, DictValue items) {
        var picked = new String[slots.size()];
        var keys = items.keys();
        var values = items.values();
        for (int i = 0; i < keys.size(); i++) {
            long slot = keys.get(i) instanceof Boolean bool ? (bool ? 1 : 0) : ((Number) keys.get(i)).longValue();
            if (slot < 0 || slot >= slots.size() || slots.get((int) slot).isEmpty()) {
                var filled = new ArrayList<String>();
                for (int each = 0; each < slots.size(); each++) {
                    if (!slots.get(each).isEmpty()) {
                        filled.add(String.valueOf(each));
                    }
                }
                throw new RunError("ValueError", label + " has nothing to put in slot " + slot + "; its slots are "
                        + String.join(", ", filled));
            }
            picked[(int) slot] = (String) values.get(i);
        }
        var chosen = new GenericStack[slots.size()];
        appeng.api.stacks.KeyCounter stock = null;
        for (int slot = 0; slot < slots.size(); slot++) {
            var accepted = slots.get(slot);
            if (accepted.isEmpty()) {
                continue;
            }
            if (picked[slot] != null) {
                var pick = picked[slot];
                final int at = slot;
                chosen[slot] = accepted.stream().filter(stack -> stack.what().getId().toString().equals(pick))
                        .findFirst().orElseThrow(() -> new RunError("ValueError", "slot " + at + " of " + label
                                + " does not take " + Ops.repr(pick)));
            } else if (accepted.size() == 1) {
                chosen[slot] = accepted.get(0);
            } else {
                if (stock == null) {
                    stock = domain.stock(null);
                }
                var held = stock;
                chosen[slot] = accepted.stream().filter(stack -> held.get(stack.what()) > 0)
                        .max(Comparator.comparingLong(stack -> held.get(stack.what())))
                        .orElse(accepted.get(0));
            }
        }
        return chosen;
    }

    /** A processing pattern putting in the chosen stacks in order and taking out the outputs. */
    private static ItemStack processing(String label, GenericStack[] chosen, List<GenericStack> outputs) {
        var inputs = java.util.Arrays.stream(chosen).filter(java.util.Objects::nonNull).toList();
        if (inputs.isEmpty() || outputs.isEmpty() || inputs.size() > AEProcessingPattern.MAX_INPUT_SLOTS
                || outputs.size() > AEProcessingPattern.MAX_OUTPUT_SLOTS) {
            throw new RunError("ValueError", label + " needs 1 to " + AEProcessingPattern.MAX_INPUT_SLOTS
                    + " inputs and 1 to " + AEProcessingPattern.MAX_OUTPUT_SLOTS + " outputs on a pattern");
        }
        return PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
    }

    /** The encoded pattern of the recipe with the chosen items; empty if the recipe does not match them. */
    @SuppressWarnings("unchecked")
    private static ItemStack encode(RecipeIndex.Entry entry, GenericStack[] stacks, boolean substitutes,
            ServerLevel level) {
        var registries = level.registryAccess();
        var chosen = new ItemStack[stacks.length];
        for (int slot = 0; slot < stacks.length; slot++) {
            chosen[slot] = stacks[slot] == null ? ItemStack.EMPTY : ((AEItemKey) stacks[slot].what()).toStack();
        }
        switch (entry.kind()) {
            case "crafting" -> {
                var holder = (RecipeHolder<CraftingRecipe>) entry.holder();
                var grid = NonNullList.withSize(9, ItemStack.EMPTY);
                for (int slot = 0; slot < chosen.length; slot++) {
                    grid.set(slot, chosen[slot]);
                }
                var input = CraftingInput.of(3, 3, grid);
                if (!holder.value().matches(input, level)) {
                    return ItemStack.EMPTY;
                }
                var output = holder.value().assemble(input, registries);
                return output.isEmpty() ? ItemStack.EMPTY
                        : PatternDetailsHelper.encodeCraftingPattern(holder, grid.toArray(new ItemStack[0]), output,
                                substitutes, false);
            }
            case "stonecutting" -> {
                var holder = (RecipeHolder<StonecutterRecipe>) entry.holder();
                var input = new SingleRecipeInput(chosen[0]);
                if (!holder.value().matches(input, level)) {
                    return ItemStack.EMPTY;
                }
                var output = holder.value().assemble(input, registries);
                return output.isEmpty() ? ItemStack.EMPTY
                        : PatternDetailsHelper.encodeStonecuttingPattern(holder, AEItemKey.of(chosen[0]),
                                AEItemKey.of(output), substitutes);
            }
            default -> {
                var holder = (RecipeHolder<SmithingRecipe>) entry.holder();
                var input = new SmithingRecipeInput(chosen[0], chosen[1], chosen[2]);
                if (!holder.value().matches(input, level)) {
                    return ItemStack.EMPTY;
                }
                var output = holder.value().assemble(input, registries);
                return output.isEmpty() ? ItemStack.EMPTY
                        : PatternDetailsHelper.encodeSmithingTablePattern(holder, AEItemKey.of(chosen[0]),
                                AEItemKey.of(chosen[1]), AEItemKey.of(chosen[2]), AEItemKey.of(output), substitutes);
            }
        }
    }
}
