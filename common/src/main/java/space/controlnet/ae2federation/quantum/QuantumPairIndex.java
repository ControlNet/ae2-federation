package space.controlnet.ae2federation.quantum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.power.CableCrossSection;

/**
 * The formed, loaded Quantum Cubes of a server by pair id. A pair links when exactly two cubes of one id are loaded and
 * they are the same size; a pair id of 0 is no pair.
 */
public final class QuantumPairIndex {
    /** A formed cube: its dimension, lowest block, the square it takes, and its pair id. */
    public record Cube(String dimension, BlockPos min, CableCrossSection section, long pairId) {
        public Cube {
            Objects.requireNonNull(dimension);
            min = min.immutable();
            Objects.requireNonNull(section);
        }

        Key key() {
            return new Key(dimension, min);
        }
    }

    /** Where a cube is: its dimension and lowest block. */
    public record Key(String dimension, BlockPos min) {
    }

    /** What a cube's link is doing; {@code partner} is set while linked. */
    public record Status(Kind kind, @Nullable Cube partner) {
        public enum Kind { LINKED, NOT_BOUND, PARTNER_NOT_LOADED, WRONG_SIZE, MORE_THAN_TWO }
    }

    private final Map<Key, Cube> cubes = new HashMap<>();
    private final Map<Long, List<Cube>> pairs = new HashMap<>();

    /** Adds or replaces the cube at its place; returns the pair ids whose links may have changed. */
    public Set<Long> put(Cube cube) {
        var previous = cubes.put(cube.key(), cube);
        if (cube.equals(previous)) return Set.of();
        var changed = new LinkedHashSet<Long>();
        if (previous != null) detach(previous, changed);
        if (cube.pairId() != 0) {
            pairs.computeIfAbsent(cube.pairId(), ignored -> new ArrayList<>()).add(cube);
            changed.add(cube.pairId());
        }
        return changed;
    }

    /** Removes the cube at a place; returns the pair ids whose links may have changed. */
    public Set<Long> remove(String dimension, BlockPos min) {
        var previous = cubes.remove(new Key(dimension, min));
        if (previous == null) return Set.of();
        var changed = new LinkedHashSet<Long>();
        detach(previous, changed);
        return changed;
    }

    /** Removes every cube of a dimension that unloads; returns the pair ids whose links may have changed. */
    public Set<Long> removeDimension(String dimension) {
        var changed = new LinkedHashSet<Long>();
        for (var key : List.copyOf(cubes.keySet())) {
            if (key.dimension().equals(dimension)) changed.addAll(remove(key.dimension(), key.min()));
        }
        return changed;
    }

    private void detach(Cube cube, Set<Long> changed) {
        if (cube.pairId() == 0) return;
        var members = pairs.get(cube.pairId());
        members.remove(cube);
        if (members.isEmpty()) pairs.remove(cube.pairId());
        changed.add(cube.pairId());
    }

    /** The cube at a place, if formed and loaded. */
    public @Nullable Cube at(String dimension, BlockPos min) {
        return cubes.get(new Key(dimension, min));
    }

    /** The loaded cubes of one pair id. */
    public List<Cube> members(long pairId) {
        return List.copyOf(pairs.getOrDefault(pairId, List.of()));
    }

    /** What the cube's link is doing. */
    public Status status(Cube cube) {
        if (cube.pairId() == 0) return new Status(Status.Kind.NOT_BOUND, null);
        var members = pairs.getOrDefault(cube.pairId(), List.of());
        if (members.size() > 2) return new Status(Status.Kind.MORE_THAN_TWO, null);
        Cube partner = null;
        for (var member : members) {
            if (!member.key().equals(cube.key())) partner = member;
        }
        if (partner == null) return new Status(Status.Kind.PARTNER_NOT_LOADED, null);
        if (partner.section() != cube.section()) return new Status(Status.Kind.WRONG_SIZE, null);
        return new Status(Status.Kind.LINKED, partner);
    }

    public void clear() {
        cubes.clear();
        pairs.clear();
    }
}
