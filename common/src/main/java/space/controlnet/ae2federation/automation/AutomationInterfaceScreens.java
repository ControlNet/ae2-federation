package space.controlnet.ae2federation.automation;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The ID and alias screen of an Automation Interface or a computing device, as the server sees it: what it shows and
 * the alias the player sends back. The loader's network code registers how both travel.
 */
public final class AutomationInterfaceScreens {
    /**
     * What the screen shows: its title (the block's translation key), the ID and the alias; {@code refused} when the
     * alias the player sent was not taken.
     */
    public record View(BlockPos position, String title, String id, String alias, boolean refused) {
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

    /** Opens the screen of a block whose ID is claimed. */
    public static boolean open(ServerPlayer player, DomainDeviceBlockEntity device) {
        var id = device.id();
        if (id == null || toClient == null || !(device.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        toClient.send(player, new View(device.getBlockPos(), title(device), id, device.alias(level), false));
        return true;
    }

    private static String title(DomainDeviceBlockEntity device) {
        return device.getBlockState().getBlock().getDescriptionId();
    }

    /** Client side: the player finished editing the alias. */
    public static void sendAlias(BlockPos position, String alias) {
        if (toServer != null) {
            toServer.send(position, alias);
        }
    }

    /** Server side: sets the alias if the player can reach the block, and answers with what it shows now. */
    public static void setAlias(ServerPlayer player, BlockPos position, String alias) {
        var level = player.serverLevel();
        if (!player.canInteractWithBlock(position, 4.0)
                || !(level.getBlockEntity(position) instanceof DomainDeviceBlockEntity device)
                || device.id() == null || toClient == null) {
            return;
        }
        var result = device.setAlias(level, alias);
        var refused = result == AutomationInterfaceBook.AliasResult.TAKEN
                || result == AutomationInterfaceBook.AliasResult.INVALID;
        if (result == AutomationInterfaceBook.AliasResult.SET) {
            ProgramSync.get(level.getServer()).changed();
        }
        toClient.send(player, new View(position, title(device), device.id(), device.alias(level), refused));
    }
}
