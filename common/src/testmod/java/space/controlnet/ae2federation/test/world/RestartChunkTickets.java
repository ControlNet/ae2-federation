package space.controlnet.ae2federation.test.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import space.controlnet.ae2federation.test.FederationTestMod;

/**
 * Keeps a restart test's chunks loaded and ticking into the next server process. NeoForge saves these tickets with the
 * world and reinstates them when the server prepares its levels. Vanilla forced chunks do not survive: the GameTest
 * runner unforces every forced chunk when a batch ends.
 */
public final class RestartChunkTickets {
    private static final TicketController CONTROLLER =
            new TicketController(ResourceLocation.fromNamespaceAndPath(FederationTestMod.MOD_ID, "restart_fixture"));

    private RestartChunkTickets() {
    }

    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    public static void force(ServerLevel level, BlockPos owner, Iterable<ChunkPos> chunks, boolean add) {
        for (var chunk : chunks) {
            CONTROLLER.forceChunk(level, owner, chunk.x, chunk.z, add, true);
        }
    }
}
