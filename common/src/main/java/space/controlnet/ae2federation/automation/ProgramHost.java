package space.controlnet.ae2federation.automation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.function.Evaluator;
import space.controlnet.ae2federation.function.ListValue;
import space.controlnet.ae2federation.function.RunError;
import space.controlnet.ae2federation.function.TypeRef;

/**
 * Performs a program's library calls in the world, for a program running in one Federation domain.
 *
 * <p>Assistant choices: a {@link Block} names the interface it was selected through, not a fixed position, so it
 * follows the interface when the interface is turned, and is resolved again at every use. What the world decides is
 * a result, not an error: a block in an unloaded area, or behind an interface that left the domain or was broken,
 * reads as empty (0, an empty list, {@code None}, {@code ""}) and an action on it gives False. A name that matches no
 * interface is a mistake in the program and stops the run.
 */
public final class ProgramHost implements Evaluator.Host {
    /**
     * A block a program selected: {@code reach} blocks in front of an interface, seen from {@code side}; reach 0 is
     * the interface's own block. Opaque to the program, which can hold it and pass it on but not build one.
     */
    public record Block(String interfaceId, int reach, @Nullable Direction side) {
        @Override
        public String toString() {
            return "Block(" + interfaceId + ", reach=" + reach + (side == null ? "" : ", side=" + side.getName()) + ")";
        }
    }

    /** Where a block is now: its level and position, and the face to reach it through. */
    record Place(ServerLevel level, BlockPos position, Direction side, AutomationInterfaceBlockEntity through) {
    }

    private static final TypeRef INTERFACE = new TypeRef("Interface", List.of());

    private final MinecraftServer server;
    private final FederationDomainId domain;
    private final GameCatalog catalog;
    private final DomainCalls domainCalls;

    /** A host for one run; {@code owner} names the run's crafting jobs, so it cancels only its own. */
    public ProgramHost(MinecraftServer server, FederationDomainId domain, String owner) {
        this.server = server;
        this.domain = domain;
        this.catalog = ProgramStore.catalog(server, domain);
        this.domainCalls = new DomainCalls(server, domain, owner, catalog);
    }

    public GameCatalog catalog() {
        return catalog;
    }

    /** The domain's networks now, for the scheduler's network events. */
    List<appeng.api.networking.IGrid> grids() {
        return domainCalls.domainGrids();
    }

    @Override
    public Object call(String namespace, String name, List<Object> args) {
        return switch (namespace + "." + name) {
            case "ae2f.select" -> select((String) args.get(0), (Long) args.get(1), (String) args.get(2));
            case "ae2f.networks" -> new ListValue(List.copyOf(catalog.values(new TypeRef("Network", List.of()), "")
                    .stream().filter(candidate -> ShortId.valid(NetworkShortIdBook.PREFIX, candidate.label()))
                    .map(candidate -> (Object) candidate.label()).toList()));
            case "mc.block_id" -> place(args.get(0)).map(place -> BuiltInRegistries.BLOCK
                    .getKey(place.level().getBlockState(place.position()).getBlock()).toString()).orElse("");
            case "mc.state" -> place(args.get(0)).map(place -> property(place, (String) args.get(1))).orElse(null);
            case "mc.redstone" -> place(args.get(0))
                    .map(place -> (long) place.level().getBestNeighborSignal(place.position())).orElse(0L);
            case "mc.set_redstone" -> setRedstone((String) args.get(0), (Long) args.get(1));
            case "mc.time" -> server.overworld().getGameTime();
            case "mc.fe" -> energy(args.get(0)).map(energy -> (long) energy.getEnergyStored()).orElse(0L);
            case "mc.move_fe" -> moveEnergy(args.get(0), args.get(1), (Long) args.get(2));
            default -> domainCalls.call(this, namespace, name, args);
        };
    }

    private Block select(String name, long reach, @Nullable String side) {
        return new Block(interfaceId(name), (int) reach,
                side == null ? null : Direction.byName(side.toLowerCase(Locale.ROOT)));
    }

    /** The interface a program names, by ID or alias; a name that matches none in the domain stops the run. */
    String interfaceId(String name) {
        var ids = catalog.matches(INTERFACE, name);
        if (ids.isEmpty()) {
            throw new RunError("ValueError", "no interface '" + name + "' in this domain");
        }
        return ids.get(0);
    }

    /** A {@code Block} argument: a selected block, or an interface's name meaning its own block. */
    Block block(Object argument) {
        return argument instanceof Block block ? block : new Block(interfaceId((String) argument), 0, null);
    }

    /** Where the block is now, if its interface is still in the domain and the area is loaded. */
    Optional<Place> place(Object argument) {
        var block = block(argument);
        var entry = AutomationInterfaces.get(server.overworld()).entry(block.interfaceId());
        if (entry.isEmpty() || !catalog.exists(INTERFACE, block.interfaceId())) {
            return Optional.empty();
        }
        var location = ResourceLocation.tryParse(entry.get().dimension());
        var level = location == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
        var at = entry.get().position();
        if (level == null || !level.isLoaded(at)
                || !(level.getBlockEntity(at) instanceof AutomationInterfaceBlockEntity automationInterface)) {
            return Optional.empty();
        }
        var front = automationInterface.front();
        var position = at.relative(front, block.reach());
        if (!level.isLoaded(position)) {
            return Optional.empty();
        }
        // Seen from the interface by default: the face of the target that looks back at it.
        var side = block.side() != null ? block.side() : front.getOpposite();
        return Optional.of(new Place(level, position, side, automationInterface));
    }

    /** Whether the place is the interface's own block, which holds nothing and is never broken or replaced. */
    static boolean own(Place place) {
        return place.position().equals(place.through().getBlockPos());
    }

    private static @Nullable String property(Place place, String name) {
        var state = place.level().getBlockState(place.position());
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return value(state, property);
            }
        }
        return null;
    }

    private static <T extends Comparable<T>> String value(net.minecraft.world.level.block.state.BlockState state,
            Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private boolean setRedstone(String name, long level) {
        return place(new Block(interfaceId(name), 0, null)).map(place -> {
            place.through().setRedstone((int) level);
            return true;
        }).orElse(false);
    }

    private Optional<IEnergyStorage> energy(Object argument) {
        return place(argument).filter(place -> !own(place)).map(place -> place.level()
                .getCapability(Capabilities.EnergyStorage.BLOCK, place.position(), place.side()));
    }

    private boolean moveEnergy(Object source, Object target, long amount) {
        var from = energy(source);
        var to = energy(target);
        if (from.isEmpty() || to.isEmpty() || amount <= 0) {
            return false;
        }
        var wanted = (int) Math.min(amount, Integer.MAX_VALUE);
        var offered = from.get().extractEnergy(wanted, true);
        var accepted = to.get().receiveEnergy(offered, true);
        if (accepted <= 0) {
            return false;
        }
        var taken = from.get().extractEnergy(accepted, false);
        var leftover = taken - to.get().receiveEnergy(taken, false);
        if (leftover > 0) {
            // The target took less than it promised: give the rest back rather than lose it.
            from.get().receiveEnergy(leftover, false);
        }
        return taken > 0;
    }
}
