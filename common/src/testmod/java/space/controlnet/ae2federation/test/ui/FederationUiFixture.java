package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.test.ui.IMenuTest;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

@LDLRegister(name = FederationUiFixture.NAME, registry = "ldlib2:menu_test",
        environment = RegistrationEnvironment.DEV_ONLY)
public final class FederationUiFixture implements IMenuTest {
    public static final String NAME = "ae2federation_ui_harness";
    public static final ResourceLocation MENU_ID = ResourceLocation.fromNamespaceAndPath("ldlib2", NAME);
    public static final ResourceLocation XML = ResourceLocation.fromNamespaceAndPath(
            "ae2federation_test", "ui/federation_harness.xml");

    private static final AtomicReference<String> SERVER_ACK = new AtomicReference<>("");

    public static boolean open(Player player) {
        return PlayerUIMenuType.openUI(player, MENU_ID);
    }

    public static String serverAck() {
        return SERVER_ACK.get();
    }

    public static void resetServerAck() {
        SERVER_ACK.set("");
    }

    @Override
    public ModularUI createUI(Player player) {
        var document = Objects.requireNonNull(XmlUtils.loadXml(XML), "Missing actual LDLib2 XML fixture " + XML);
        var ui = UI.of(document);
        var status = ui.selectId("ack_status").findFirst().orElseThrow();
        var button = (Button) ui.selectId("ack_control").findFirst().orElseThrow();
        button.setOnServerClick(event -> {
            var correlationId = System.getProperty("ae2federation.ui.runId", "");
            SERVER_ACK.set(correlationId);
            var message = new CompoundTag();
            message.putString("correlationId", correlationId);
            event.currentElement.sendMessage("server_ack", message);
        }).onMessage("server_ack", (element, message) -> {
            status.addClass("acknowledged");
            ((Label) status).setText(Component.literal(
                    "Server acknowledged " + message.getString("correlationId")));
        });
        return ModularUI.of(ui, player);
    }
}
