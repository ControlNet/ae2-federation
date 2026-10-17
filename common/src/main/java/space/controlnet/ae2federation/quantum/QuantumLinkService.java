package space.controlnet.ae2federation.quantum;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.quantum.QuantumPairIndex.Cube;
import space.controlnet.ae2federation.router.DeviceStructure;
import space.controlnet.ae2federation.router.DeviceStructureUpdater;

/**
 * The Quantum Cubes of a server, across its dimensions. Formed cubes report themselves through their lowest block
 * ({@link #track}); once a tick every pair whose cubes changed is judged again, and both cubes of a pair publish the
 * link in the same pass, so the domain registry never sees one end only. As AE2's Quantum Network Bridge, a cube that
 * unloads leaves the index and its pair unlinks; it links again when it loads. Nothing here loads a chunk.
 */
public final class QuantumLinkService {
    /** The prefix of the remote port each linked cube names the other by. */
    public static final String PORT = "quantum:";

    private static final Map<MinecraftServer, QuantumLinkService> SERVICES = new HashMap<>();

    private final MinecraftServer server;
    private final QuantumPairIndex index = new QuantumPairIndex();
    private final Set<Long> dirty = new LinkedHashSet<>();

    private QuantumLinkService(MinecraftServer server) {
        this.server = server;
    }

    public static QuantumLinkService get(MinecraftServer server) {
        return SERVICES.computeIfAbsent(server, QuantumLinkService::new);
    }

    public static void tickAll(MinecraftServer server) {
        var service = SERVICES.get(server);
        if (service != null) service.tick();
    }

    /** A dimension unloads: its cubes leave, and their partners elsewhere unlink. */
    public static void closeLevel(ServerLevel level) {
        var service = SERVICES.get(level.getServer());
        if (service == null) return;
        service.dirty.addAll(service.index.removeDimension(FederationDomainRegistryAccess.dimension(level)));
    }

    public static void closeServer(MinecraftServer server) {
        SERVICES.remove(server);
    }

    /** A formed cube, reported by its lowest block whenever its body or pair changes. */
    public void track(Cube cube) {
        dirty.addAll(index.put(cube));
    }

    /** The cube whose lowest block is {@code min} is gone, broken, unformed or unloaded. */
    public void untrack(String dimension, BlockPos min) {
        dirty.addAll(index.remove(dimension, min));
    }

    /** What the cube whose lowest block is {@code min} is doing, or null while it is not formed or not reported yet. */
    public QuantumPairIndex.@Nullable Status status(String dimension, BlockPos min) {
        var cube = index.at(dimension, min);
        return cube == null ? null : index.status(cube);
    }

    void tick() {
        if (dirty.isEmpty()) return;
        var pairs = List.copyOf(dirty);
        dirty.clear();
        for (var pairId : pairs) {
            for (var cube : index.members(pairId)) {
                var status = index.status(cube);
                publish(cube, status.kind() == QuantumPairIndex.Status.Kind.LINKED ? status.partner() : null);
            }
        }
    }

    /** Tells the cube's lowest block its linked partner, or none; it publishes the change at once. */
    private void publish(Cube cube, @Nullable Cube partner) {
        var level = level(cube.dimension());
        if (level != null && level.isLoaded(cube.min())
                && level.getBlockEntity(cube.min()) instanceof QuantumCubeBlockEntity anchor) {
            anchor.setQuantumPartner(partner);
        }
    }

    private @Nullable ServerLevel level(String dimension) {
        for (var level : server.getAllLevels()) {
            if (FederationDomainRegistryAccess.dimension(level).equals(dimension)) return level;
        }
        return null;
    }

    /** What binding two cubes with a Memory Card did. */
    public enum Bind { BOUND, SAME_CUBE, CARD_CUBE_MISSING }

    /**
     * Binds the cube whose lowest block the card names to the cube whose lowest block is {@code to}, under a new pair
     * id. Both leave whatever pair they had: a loaded cube left behind is cleared, so a third cube never joins a pair.
     */
    public Bind bind(GlobalPos from, GlobalPos to) {
        if (from.equals(to)) return Bind.SAME_CUBE;
        var fromLevel = server.getLevel(from.dimension());
        var toLevel = server.getLevel(to.dimension());
        var fromBody = body(fromLevel, from.pos());
        var toBody = body(toLevel, to.pos());
        if (fromBody == null || toBody == null) return Bind.CARD_CUBE_MISSING;
        var dropped = new LinkedHashSet<Long>();
        for (var end : List.of(fromBody, toBody)) {
            if (end.anchor.pairId() != 0) dropped.add(end.anchor.pairId());
        }
        for (var pairId : dropped) {
            for (var cube : index.members(pairId)) {
                var level = level(cube.dimension());
                var body = body(level, cube.min());
                if (body != null && body.anchor != fromBody.anchor && body.anchor != toBody.anchor) {
                    QuantumCubeBlockEntity.bind(level, body.min, body.max, 0, null);
                }
            }
        }
        long id;
        do {
            id = toLevel.getRandom().nextLong();
        } while (id == 0 || !index.members(id).isEmpty());
        QuantumCubeBlockEntity.bind(fromLevel, fromBody.min, fromBody.max, id, to);
        QuantumCubeBlockEntity.bind(toLevel, toBody.min, toBody.max, id, from);
        return Bind.BOUND;
    }

    private record Body(QuantumCubeBlockEntity anchor, BlockPos min, BlockPos max) {
    }

    /** The formed, loaded cube whose lowest block is {@code min}, or null. */
    private static @Nullable Body body(@Nullable ServerLevel level, BlockPos min) {
        if (level == null || !level.isLoaded(min)
                || !(level.getBlockEntity(min) instanceof QuantumCubeBlockEntity anchor)
                || !(DeviceStructureUpdater.body(level, min) instanceof DeviceStructure.Valid valid)
                || !valid.min().equals(min) || QuantumCubeShape.of(valid.min(), valid.max()).isEmpty()) {
            return null;
        }
        return new Body(anchor, valid.min(), valid.max());
    }
}
