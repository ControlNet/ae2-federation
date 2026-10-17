package space.controlnet.ae2federation.compat.jade;

import appeng.api.integrations.igtooltip.PartTooltips;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.CableCapacity;
import space.controlnet.ae2federation.domain.CableCapacityCounter;
import space.controlnet.ae2federation.domain.CableCapacityLimit;
import space.controlnet.ae2federation.p2p.FederationP2PTunnelPart;

/**
 * A Federation P2P tunnel's capacity count against one cable's capacity, the line a Federation Cable shows. A tunnel
 * is a part, so it goes through AE2's part tooltips, which AE2 shows in Jade; without this a domain stopped by a full
 * pair would show no cable over its limit.
 */
public final class P2PCapacityTooltip {
    private static final String UNITS = "ae2federationCapacityUnits";
    private static final String DOMAIN = "ae2federationCapacityDomain";
    private static final String LIMIT = "ae2federationCapacityLimit";

    private P2PCapacityTooltip() {
    }

    public static void register() {
        PartTooltips.addServerData(FederationP2PTunnelPart.class, (player, part, data) -> {
            if (!(part.getLevel() instanceof ServerLevel level) || part.nodeId() == null) return;
            CableCapacity.demand(level, part.nodeId()).ifPresent(demand -> {
                data.putInt(UNITS, demand.units());
                data.putBoolean(DOMAIN, demand.scope() == CableCapacityCounter.Scope.DOMAIN);
                CableCapacityLimit.current(1).ifPresent(limit -> data.putInt(LIMIT, limit));
            });
        });
        PartTooltips.addBody(FederationP2PTunnelPart.class, (part, context, tooltip) -> {
            var data = context.serverData();
            if (!data.contains(UNITS)) return;
            tooltip.addLine(FederationJadeText.capacity(data.getInt(UNITS), data.contains(LIMIT)
                    ? OptionalInt.of(data.getInt(LIMIT)) : OptionalInt.empty(), data.getBoolean(DOMAIN)));
        });
    }
}
