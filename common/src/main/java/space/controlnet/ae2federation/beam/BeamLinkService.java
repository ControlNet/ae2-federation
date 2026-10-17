package space.controlnet.ae2federation.beam;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.beam.BeamDebounce.Seen;
import space.controlnet.ae2federation.beam.BeamPartnerIndex.Former;

/**
 * The beams of one level. Formed Beam Formers report themselves through their lowest block ({@link #track}); each pair
 * facing each other within range gets a beam, whose volume is checked a fixed number of blocks a tick in rotation, so a
 * long beam costs no more per tick, it only notices a change later. A position in an unloaded chunk is never read and
 * keeps its last result; a block change in a beam, reported by NeoForge's block events, is checked first. A beam links
 * and unlinks through {@link BeamDebounce}, and both formers then publish the link in the same pass.
 */
public final class BeamLinkService {
    /** Blocks one beam checks per tick at most. */
    static final int BLOCKS_PER_BEAM = 1024;
    /** Blocks all beams of a level check per tick at most. */
    static final int BLOCKS_PER_TICK = 16384;
    static final int DEBOUNCE_TICKS = 20;

    private static final Map<ServerLevel, BeamLinkService> SERVICES = new HashMap<>();

    /** What a former's beam is doing, for Jade. */
    public record Status(Kind kind, int value, @Nullable BlockPos blocker) {
        public enum Kind { LINKED, BLOCKED, CHECKING, NO_PARTNER, WRONG_SIZE }
    }

    private enum Check { CLEAR, BLOCKS, UNLOADED }

    private static final class Beam {
        final Former from;
        final Former to;
        final BeamVolume volume;
        final BeamDebounce debounce = new BeamDebounce(DEBOUNCE_TICKS);
        final ArrayDeque<Integer> hints = new ArrayDeque<>();
        int cursor;
        int blocker = -1;
        boolean lastSweepClear;
        boolean linked;

        Beam(Former from, Former to, int gap) {
            this.from = from;
            this.to = to;
            volume = BeamVolume.of(from.min(), from.max(), from.facing(), gap);
            lastSweepClear = gap == 0;
        }

        Seen seen() {
            return blocker >= 0 ? Seen.BLOCKED : lastSweepClear ? Seen.CLEAR : Seen.UNKNOWN;
        }
    }

    private final ServerLevel level;
    private final BeamPartnerIndex index = new BeamPartnerIndex();
    private final Map<BlockPos, Former> formers = new HashMap<>();
    /** Beams by the lowest block of the former at their positive end's opposite, the end the volume runs from. */
    private final Map<BlockPos, Beam> beams = new LinkedHashMap<>();
    private boolean pairsDirty;
    private int pairedRange = -1;
    private int rotation;

    private BeamLinkService(ServerLevel level) {
        this.level = level;
    }

    public static BeamLinkService get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level, BeamLinkService::new);
    }

    public static void tickAll(MinecraftServer server) {
        for (var service : new ArrayList<>(SERVICES.values())) {
            if (service.level.getServer() == server) service.tick();
        }
    }

    public static void closeLevel(ServerLevel level) {
        SERVICES.remove(level);
    }

    /** A block changed at {@code pos}: a beam through it checks it first. */
    public static void hint(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var service = SERVICES.get(serverLevel);
        if (service == null) return;
        for (var beam : service.beams.values()) {
            int at = beam.volume.indexOf(pos);
            if (at >= 0) beam.hints.add(at);
        }
    }

    /** A formed former, reported by its lowest block whenever its body or facing changes. */
    public void track(Former former) {
        var previous = formers.put(former.min(), former);
        if (former.equals(previous)) return;
        if (previous != null) index.remove(previous);
        index.put(former);
        pairsDirty = true;
    }

    /** The former whose lowest block is {@code min} is gone, broken, unformed or unloaded. */
    public void untrack(BlockPos min) {
        var previous = formers.remove(min);
        if (previous == null) return;
        index.remove(previous);
        pairsDirty = true;
    }

    /** What the former whose lowest block is {@code min} is doing. */
    public Status status(BlockPos min) {
        var former = formers.get(min);
        if (former == null) return new Status(Status.Kind.NO_PARTNER, BeamLinkRule.range(), null);
        for (var beam : beams.values()) {
            if (!beam.from.equals(former) && !beam.to.equals(former)) continue;
            if (beam.linked) return new Status(Status.Kind.LINKED, beam.volume.length(), null);
            if (beam.blocker >= 0) {
                var blocker = beam.volume.at(beam.blocker, new BlockPos.MutableBlockPos()).immutable();
                return new Status(Status.Kind.BLOCKED, 0, blocker);
            }
            return new Status(Status.Kind.CHECKING, 0, null);
        }
        var kind = otherSizeAhead(former) ? Status.Kind.WRONG_SIZE : Status.Kind.NO_PARTNER;
        return new Status(kind, BeamLinkRule.range(), null);
    }

    /** Whether a former of another square faces back at this one from within range, its square across this beam. */
    private boolean otherSizeAhead(Former former) {
        var axis = former.facing().getAxis();
        int step = former.facing().getAxisDirection().getStep();
        for (var other : formers.values()) {
            if (other.facing() != former.facing().getOpposite() || other.equals(former)) continue;
            if (other.max().subtract(other.min()).equals(former.max().subtract(former.min()))) continue;
            int gap = (other.min().get(axis) - former.min().get(axis)) * step - 1;
            if (gap < 0 || gap > BeamLinkRule.range()) continue;
            boolean across = true;
            for (var side : Direction.Axis.values()) {
                if (side == axis) continue;
                across &= other.min().get(side) <= former.max().get(side)
                        && other.max().get(side) >= former.min().get(side);
            }
            if (across) return true;
        }
        return false;
    }

    void tick() {
        var range = BeamLinkRule.range();
        if (pairsDirty || range != pairedRange) pair(range);
        if (beams.isEmpty()) return;
        var order = new ArrayList<>(beams.values());
        int start = Math.floorMod(rotation++, order.size());
        int budget = BLOCKS_PER_TICK;
        long tick = level.getGameTime();
        for (int offset = 0; offset < order.size() && budget > 0; offset++) {
            var beam = order.get((start + offset) % order.size());
            budget -= scan(beam, Math.min(BLOCKS_PER_BEAM, budget));
            var linked = beam.debounce.update(beam.seen(), tick);
            if (linked != beam.linked) {
                beam.linked = linked;
                publish(beam.from, linked ? beam.to.min() : null);
                publish(beam.to, linked ? beam.from.min() : null);
            }
        }
    }

    /** Pairs every former facing the positive way with its partner, keeping beams whose pair is unchanged. */
    private void pair(int range) {
        pairsDirty = false;
        pairedRange = range;
        var wanted = new HashMap<BlockPos, BeamPartnerIndex.Partner>();
        for (var former : formers.values()) {
            if (former.facing().getAxisDirection() != Direction.AxisDirection.POSITIVE) continue;
            index.partner(former, range)
                    .filter(partner -> index.partner(partner.former(), range).map(back -> back.former().equals(former))
                            .orElse(false))
                    .ifPresent(partner -> wanted.put(former.min(), partner));
        }
        for (var iterator = beams.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var beam = entry.getValue();
            var partner = wanted.get(entry.getKey());
            if (partner != null && beam.from.equals(formers.get(entry.getKey())) && partner.former().equals(beam.to)
                    && partner.gap() == beam.volume.length()) {
                wanted.remove(entry.getKey());
                continue;
            }
            iterator.remove();
            if (beam.linked) {
                publish(beam.from, null);
                publish(beam.to, null);
            }
        }
        wanted.forEach((min, partner) -> beams.put(min, new Beam(formers.get(min), partner.former(), partner.gap())));
    }

    /** Checks up to {@code allowance} blocks of the beam and returns how many it read. */
    private int scan(Beam beam, int allowance) {
        int used = 0;
        while (!beam.hints.isEmpty() && used < allowance) {
            int at = beam.hints.poll();
            used++;
            if (beam.blocker < 0 && check(beam, at) == Check.BLOCKS) {
                beam.blocker = at;
                beam.lastSweepClear = false;
            }
        }
        if (beam.blocker >= 0) {
            used++;
            if (check(beam, beam.blocker) != Check.CLEAR) return used;
            // The blocker went: whatever else is in the way is found by a fresh sweep.
            beam.blocker = -1;
            beam.cursor = 0;
            beam.lastSweepClear = beam.volume.size() == 0;
        }
        int size = beam.volume.size();
        while (used < allowance && size > 0) {
            int at = beam.cursor;
            used++;
            beam.cursor = at + 1 >= size ? 0 : at + 1;
            if (check(beam, at) == Check.BLOCKS) {
                beam.blocker = at;
                beam.lastSweepClear = false;
                break;
            }
            if (beam.cursor == 0) beam.lastSweepClear = true;
        }
        return used;
    }

    private Check check(Beam beam, int at) {
        var pos = beam.volume.at(at, new BlockPos.MutableBlockPos());
        if (level.isOutsideBuildHeight(pos.getY())) return Check.CLEAR;
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return Check.UNLOADED;
        var section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        if (section.hasOnlyAir()) return Check.CLEAR;
        var state = section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        return BeamObstruction.blocks(state, chunk, pos) ? Check.BLOCKS : Check.CLEAR;
    }

    /** Tells the former's lowest block its linked partner, or none; it publishes the change at once. */
    private void publish(Former former, @Nullable BlockPos partner) {
        if (level.isLoaded(former.min()) && level.getBlockEntity(former.min()) instanceof BeamFormerBlockEntity block) {
            block.setBeamPartner(partner);
        }
    }
}
