package space.controlnet.ae2federation.test.p2p;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.parts.p2p.MEP2PTunnelPart;
import appeng.parts.p2p.P2PTunnelPart;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.p2p.FederationP2PTunnelPart;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * TEST-ONLY: Federation P2P tunnels made the way a player makes them, in any level: an ME P2P tunnel attuned with a
 * Federation cable, then paired with a memory card held by {@code player}.
 */
public final class P2PTunnels {
    private final Player player;
    private final ItemStack memoryCard = AEItems.MEMORY_CARD.stack();

    public P2PTunnels(Player player) {
        this.player = player;
    }

    /** An AE2 glass cable at {@code position}: a cable bus with a cable and nothing else yet. */
    public static boolean placeCable(ServerLevel level, BlockPos position) {
        return PartHelper.setPart(level, position, null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) != null;
    }

    /** Puts an ME P2P tunnel on {@code side} of the bus at {@code position} and uses a Federation cable on it. */
    public @Nullable FederationP2PTunnelPart attune(ServerLevel level, BlockPos position, Direction side) {
        var tunnel = PartHelper.setPart(level, position, side, null, AEParts.ME_P2P_TUNNEL.get());
        if (!(tunnel instanceof MEP2PTunnelPart)) return null;
        tunnel.onUseItemOn(RouterRegistration.FEDERATION_CABLE_ITEM.get().getDefaultInstance(), player,
                InteractionHand.MAIN_HAND, Vec3.atCenterOf(position));
        return tunnel(level, position, side);
    }

    public static @Nullable FederationP2PTunnelPart tunnel(ServerLevel level, BlockPos position, Direction side) {
        return PartHelper.getPart(level, position, side) instanceof FederationP2PTunnelPart tunnel ? tunnel : null;
    }

    /**
     * Makes {@code input} an input with the memory card (sneaking), and each of {@code outputs} its output; true when
     * every tunnel ends up on the input's frequency in its role.
     */
    public boolean pair(P2PTunnelPart<?> input, List<? extends P2PTunnelPart<?>> outputs) {
        player.setShiftKeyDown(true);
        input.onUseItemOn(memoryCard, player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        player.setShiftKeyDown(false);
        for (var output : outputs) {
            output.onUseItemOn(memoryCard, player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        }
        return input.getFrequency() != 0 && !input.isOutput() && outputs.stream()
                .allMatch(output -> output.isOutput() && output.getFrequency() == input.getFrequency());
    }

    /** Whether every tunnel is on a booted carrier with power and a channel. */
    public static boolean online(FederationP2PTunnelPart... tunnels) {
        for (var tunnel : tunnels) {
            var node = tunnel == null ? null : tunnel.getGridNode();
            if (node == null || !node.hasGridBooted() || !tunnel.isActive()) return false;
        }
        return true;
    }
}
