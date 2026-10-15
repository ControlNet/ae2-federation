package space.controlnet.ae2federation.client.menu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class EndpointTextTest {
    private static final String SUBNET = "c556a0e2-3c1d-4f7e-9a51-0d5b6c7e8f90";

    private static JsonObject endpoint(int networkIndex, String nativeNetwork) {
        var json = new JsonObject();
        json.addProperty("networkIndex", networkIndex);
        if (nativeNetwork != null) json.addProperty("nativeNetwork", nativeNetwork);
        return json;
    }

    private static TranslatableContents translated(Component component) {
        return (TranslatableContents) component.getContents();
    }

    @Test
    void aSubnetThatIsAlsoADomainMemberShowsTheMembersName() {
        assertEquals("Workshop", EndpointText.network(endpoint(1, SUBNET), List.of("Main", "Workshop")).getString());
    }

    @Test
    void aSubnetOutsideTheMembersIsNamedByItsIdentityAsTheTopologyNamesIt() {
        var name = translated(EndpointText.network(endpoint(-1, SUBNET), List.of("Main")));
        assertEquals("ae2federation.ui.topology.network_name", name.getKey());
        assertArrayEquals(new Object[] {"C556"}, name.getArgs());
    }

    @Test
    void aSubnetWithoutAConfirmedIdentitySaysSo() {
        assertEquals("ae2federation.ui.workspace.network_unconfirmed",
                translated(EndpointText.network(endpoint(-1, null), List.of("Main"))).getKey());
    }

    private static JsonObject waiting(boolean powered, String energyReason) {
        var json = new JsonObject();
        json.addProperty("nodePowered", powered);
        if (energyReason != null) json.addProperty("energyReason", energyReason);
        return json;
    }

    @Test
    void anUnpoweredUnclaimedEndpointWaitsForAProviderToMapIt() {
        assertEquals("ae2federation.ui.topology.endpoint_energy.unclaimed",
                translated(EndpointText.notReady(waiting(false, "unclaimed"))).getKey());
    }

    @Test
    void anyOtherNodeThatIsNotReadyStillWaitsForItsNode() {
        for (var json : List.of(waiting(true, "unclaimed"), waiting(false, "waiting"), waiting(false, null), new JsonObject())) {
            assertEquals("ae2federation.ui.topology.endpoint_node.not_ready", translated(EndpointText.notReady(json)).getKey());
        }
    }
}
