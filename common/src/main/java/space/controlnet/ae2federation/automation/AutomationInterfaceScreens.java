package space.controlnet.ae2federation.automation;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The interface's screen as the server sees it: what it shows (the ID and the alias) and the alias the player sends
 * back. The loader's network code registers how both travel.
 */
public final class AutomationInterfaceScreens {
    /** What the screen shows; {@code refused} when the alias the player sent was not taken. */
    public record View(BlockPos position, String id, String alias, boolean refused) {
    }

    @FunctionalInterface
    public interface ToClient {
        void send(ServerPlayer player, View view);
    }

    @FunctionalInterface
    public interface ToServer {
        void send(BlockPos position, String alias);
    }

    private static @Nullable ToClient toClient;
    private static @Nullable ToServer toServer;

    private AutomationInterfaceScreens() {
    }

    public static void register(ToClient client, ToServer server) {
        toClient = Objects.requireNonNull(client);
        toServer = Objects.requireNonNull(server);
    }

    /** Opens the screen of an interface whose ID is claimed. */
    public static boolean open(ServerPlayer player, AutomationInterfaceBlockEntity automationInterface) {
        var id = automationInterface.id();
        if (id == null || toClient == null || !(automationInterface.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        toClient.send(player, new View(automationInterface.getBlockPos(), id, AutomationInterfaces.get(level).alias(id),
                false));
        return true;
    }

    /** Client side: the player finished editing the alias. */
    public static void sendAlias(BlockPos position, String alias) {
        if (toServer != null) {
            toServer.send(position, alias);
        }
    }

    /** Server side: sets the alias if the player can reach the interface, and answers with what it shows now. */
    public static void setAlias(ServerPlayer player, BlockPos position, String alias) {
        var level = player.serverLevel();
        if (!player.canInteractWithBlock(position, 4.0)
                || !(level.getBlockEntity(position) instanceof AutomationInterfaceBlockEntity automationInterface)
                || automationInterface.id() == null || toClient == null) {
            return;
        }
        var id = automationInterface.id();
        var interfaces = AutomationInterfaces.get(level);
        var result = interfaces.setAlias(level, id, alias);
        var refused = result == AutomationInterfaceBook.AliasResult.TAKEN
                || result == AutomationInterfaceBook.AliasResult.INVALID;
        toClient.send(player, new View(position, id, interfaces.alias(id), refused));
    }
}
