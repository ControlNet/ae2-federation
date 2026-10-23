package space.controlnet.ae2federation.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;

/**
 * Read-only vanilla chunk lifecycle evidence for factory chunks whose site tickets were released. It reads the chunk
 * map's private state by reflection and never requests, loads or ticks a chunk.
 */
final class PlaythroughChunkDiagnostics {
    /** A forced ticket (level 31) keeps chunks up to 13 away at a loaded level. */
    private static final int RADIUS = 13;
    private final ServerLevel level;
    private final Set<Long> chunks = new TreeSet<>();
    private final Map<String, Integer> states = new TreeMap<>();
    private final Set<String> touched = new TreeSet<>();
    private int samples;
    private int unknownTicketSamples;
    private int touches;

    PlaythroughChunkDiagnostics(ServerLevel level, Collection<BlockPos> positions) {
        this.level = level;
        positions.forEach(pos -> chunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)));
    }

    /** One tick's holder level, unload stage and tickets of every factory chunk, counted by distinct state. */
    void sample() {
        samples++;
        boolean unknown = false;
        for (long chunk : chunks) {
            var state = state(chunk);
            unknown |= state.contains("unknown");
            if (states.size() < 64 || states.containsKey(state)) states.merge(state, 1, Integer::sum);
        }
        if (unknown) unknownTicketSamples++;
    }

    /** Harness code reached a factory position while it should be unloading. */
    void touched(BlockPos pos) {
        touches++;
        if (touched.size() < 16) touched.add(pos.toShortString());
    }

    Map<String, Object> report() {
        var report = new TreeMap<String, Object>();
        report.put("samples", samples);
        report.put("samplesWithUnknownTicket", unknownTicketSamples);
        report.put("states", new TreeMap<>(states));
        report.put("harnessTouches", touches);
        report.put("harnessTouchedPositions", List.copyOf(touched));
        var server = level.getServer();
        report.put("sprinting", server.tickRateManager().isSprinting());
        report.put("averageTickMillis", server.getAverageTickTimeNanos() / 1_000_000.0);
        try {
            var map = level.getChunkSource().chunkMap;
            report.put("pendingUnloads", ((Map<?, ?>) field(map, ChunkMap.class, "pendingUnloads")).size());
            report.put("toDrop", ((Collection<?>) field(map, ChunkMap.class, "toDrop")).size());
            report.put("unloadQueue", ((Collection<?>) field(map, ChunkMap.class, "unloadQueue")).size());
            var chunkStates = new TreeMap<String, Object>();
            for (long chunk : chunks) {
                var pos = new ChunkPos(chunk);
                chunkStates.put(pos.x + "," + pos.z, Map.of("state", state(chunk), "tickets", tickets(chunk).stream().map(Object::toString).toList(),
                        "debug", level.getChunkSource().getChunkDebugData(pos).replaceAll("§.", "").replace('\n', ' ')));
            }
            report.put("chunks", chunkStates);
            var nearby = new ArrayList<String>();
            for (var entry : tickets().entrySet()) {
                long key = entry.getKey();
                if (chunks.contains(key) || nearby.size() >= 24) continue;
                var pos = new ChunkPos(key);
                boolean close = chunks.stream().map(ChunkPos::new).anyMatch(chunk -> Math.max(Math.abs(chunk.x - pos.x), Math.abs(chunk.z - pos.z)) <= RADIUS);
                if (close) nearby.add(pos.x + "," + pos.z + ": " + entry.getValue());
            }
            report.put("nearbyTickets", nearby);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            report.put("unavailable", exception.toString());
        }
        return report;
    }

    private String state(long chunk) {
        try {
            var map = level.getChunkSource().chunkMap;
            var updating = (ChunkHolder) ((Map<?, ?>) field(map, ChunkMap.class, "updatingChunkMap")).get(chunk);
            var pending = (ChunkHolder) ((Map<?, ?>) field(map, ChunkMap.class, "pendingUnloads")).get(chunk);
            boolean drop = ((Collection<?>) field(map, ChunkMap.class, "toDrop")).contains(chunk);
            var holder = updating != null ? updating : pending;
            var types = new TreeSet<String>();
            for (var ticket : tickets(chunk)) types.add(ticket.getType() + ":" + ticket.getTicketLevel());
            return (holder == null ? "absent" : "L" + holder.getTicketLevel() + (updating != null ? " updating" : " pending"))
                    + (drop ? " drop" : "") + " " + types;
        } catch (ReflectiveOperationException exception) {
            return "unavailable: " + exception;
        }
    }

    private List<Ticket<?>> tickets(long chunk) throws ReflectiveOperationException {
        var found = ticketMap().get(chunk);
        return found == null ? List.of() : List.copyOf(found);
    }

    private Map<Long, List<Ticket<?>>> tickets() throws ReflectiveOperationException {
        var copy = new TreeMap<Long, List<Ticket<?>>>();
        ticketMap().forEach((chunk, set) -> copy.put(chunk, List.copyOf(set)));
        return copy;
    }

    /** Every ticket of this level by chunk; NeoForge's forced tickets are among them, marked "force ticks". */
    @SuppressWarnings("unchecked")
    private Map<Long, Collection<Ticket<?>>> ticketMap() throws ReflectiveOperationException {
        return (Map<Long, Collection<Ticket<?>>>) field(level.getChunkSource().chunkMap.getDistanceManager(), DistanceManager.class, "tickets");
    }

    private static Object field(Object owner, Class<?> type, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
