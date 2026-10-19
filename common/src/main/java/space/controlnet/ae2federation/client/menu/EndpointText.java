package space.controlnet.ae2federation.client.menu;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * How the Provider screen and the topology speak of a Processing Endpoint's ME network, the subnet behind its other
 * faces. The subnet is seldom one of the domain's member networks, yet the Endpoint is in the domain all the same.
 */
public final class EndpointText {
    private EndpointText() {
    }

    /**
     * The subnet's name: the member's name when the subnet is a member network ({@code networkIndex} into
     * {@code memberNames}), otherwise as {@link #unnamedNetwork} names it.
     */
    public static Component network(JsonObject endpoint, List<String> memberNames) {
        int index = endpoint.has("networkIndex") ? endpoint.get("networkIndex").getAsInt() : -1;
        return index >= 0 && index < memberNames.size() ? Component.literal(memberNames.get(index)) : unnamedNetwork(endpoint);
    }

    /** A network that is not a member, as the topology names it: "Network C556" from its identity, or unconfirmed. */
    public static Component unnamedNetwork(JsonObject endpoint) {
        return endpoint.has("nativeNetwork")
                ? Component.translatable("ae2federation.ui.topology.network_name",
                        endpoint.get("nativeNetwork").getAsString().substring(0, 4).toUpperCase(Locale.ROOT))
                : Component.translatable("ae2federation.ui.workspace.network_unconfirmed");
    }

    /**
     * Why a Federated Endpoint's node is not ready. An unpowered subnet that no Provider claims gets its power only
     * once a Provider maps the Endpoint, so it says that; any other node is still waiting for its ME node.
     */
    public static Component notReady(JsonObject endpoint) {
        boolean unpowered = endpoint.has("nodePowered") && !endpoint.get("nodePowered").getAsBoolean();
        boolean unclaimed = endpoint.has("energyReason") && endpoint.get("energyReason").getAsString().equals("unclaimed");
        return Component.translatable(unpowered && unclaimed
                ? "ae2federation.ui.topology.endpoint_energy.unclaimed" : "ae2federation.ui.topology.endpoint_node.not_ready");
    }
}
