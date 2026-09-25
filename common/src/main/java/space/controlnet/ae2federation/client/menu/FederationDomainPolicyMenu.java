package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import java.util.Optional;
import space.controlnet.ae2federation.bridge.BridgeRightClickContext;
import space.controlnet.ae2federation.client.policy.FederationDomainPolicySession;

public final class FederationDomainPolicyMenu {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("ae2federation", "domain_policy");
    private static final Map<UUID, FederationDomainPolicySession> PENDING = new ConcurrentHashMap<>();

    private FederationDomainPolicyMenu() {
    }

    public static void register() {
        PlayerUIMenuType.register(ID, player -> new FederationDomainPolicyMenuHolder(
                player instanceof ServerPlayer serverPlayer ? PENDING.remove(serverPlayer.getUUID()) : null));
    }

    public static boolean openRouter(ServerPlayer player, BlockPos position) {
        return open(player, FederationDomainPolicySession.forRouter(player, position));
    }

    public static boolean openBridge(ServerPlayer player, BridgeRightClickContext bridge) {
        return open(player, FederationDomainPolicySession.forBridge(player, bridge));
    }

    private static boolean open(ServerPlayer player, FederationDomainPolicySession session) {
        if (PENDING.putIfAbsent(player.getUUID(), session) != null) {
            return false;
        }
        var opened = PlayerUIMenuType.openUI(player, ID);
        if (!opened) {
            PENDING.remove(player.getUUID(), session);
        }
        return opened;
    }

    public static FederationDomainPolicyActionResult dispatch(ServerPlayer player, FederationDomainPolicyActionRequest request) {
        if (!player.getServer().isSameThread()) {
            return FederationDomainPolicyActionResult.WRONG_THREAD;
        }
        if (!(player.containerMenu instanceof ModularUIContainerMenu menu)
                || !(menu.uiHolder instanceof FederationDomainPolicyMenuHolder holder)) {
            return FederationDomainPolicyActionResult.WRONG_MENU;
        }
        return holder.dispatch(player, menu, request);
    }

    public static Optional<FederationDomainPolicyActionRequest> currentRequest(ServerPlayer player, FederationDomainPolicyAction action) {
        if (player.containerMenu instanceof ModularUIContainerMenu menu
                && menu.uiHolder instanceof FederationDomainPolicyMenuHolder holder) {
            return holder.currentRequest(menu, action);
        }
        return Optional.empty();
    }

    public static Receipt currentReceipt(ServerPlayer player) {
        if (player.containerMenu instanceof ModularUIContainerMenu menu
                && menu.uiHolder instanceof FederationDomainPolicyMenuHolder holder) {
            return new Receipt(menu.containerId, holder.currentSequence(), holder.currentMappingStatus());
        }
        throw new IllegalStateException("Federation Domain policy menu is not current");
    }

    public record Receipt(int containerId, long sequence, String mappingStatus) {
    }
}
