package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.client.policy.FabricPolicySession;

final class FabricPolicyMenuHolder implements PlayerUIMenuType.PlayerUIHolder {
    private static final ResourceLocation XML = ResourceLocation.fromNamespaceAndPath(
            "ae2federation", "ui/fabric.xml");
    private final @Nullable FabricPolicySession session;

    FabricPolicyMenuHolder(@Nullable FabricPolicySession session) {
        this.session = session;
    }

    @Override
    public ModularUI createUI(Player player) {
        var document = Objects.requireNonNull(XmlUtils.loadXml(XML), "Missing production Fabric policy UI " + XML);
        var ui = UI.of(document);
        bind(ui, "entrance_value", this::entranceText);
        bind(ui, "members_value", this::membersText);
        bind(ui, "consumer_value", this::consumerText);
        bind(ui, "provider_value", this::providerText);
        bind(ui, "rule_value", this::ruleText);
        bind(ui, "ack_status", this::statusText);

        var consumer = element(ui, "consumer_next", Button.class);
        var provider = element(ui, "provider_next", Button.class);
        var capability = element(ui, "capability_next", Button.class);
        var toggle = element(ui, "policy_toggle", Button.class);
        consumer.setOnServerClick(event -> withSession(FabricPolicySession::nextConsumer));
        provider.setOnServerClick(event -> withSession(FabricPolicySession::nextProvider));
        capability.setOnServerClick(event -> withSession(FabricPolicySession::nextCapability));
        toggle.setOnServerClick(event -> withSession(FabricPolicySession::toggleEnabled));

        var state = new BindableValue<String>("");
        state.bind(DataBindingBuilder.stringS2C(this::statusCode)
                .initialValue("")
                .remoteSetter(code -> applyState(code, ui, consumer, provider, capability, toggle))
                .build());
        state.addClass("state-sync");
        ui.rootElement.addChild(state);
        return ModularUI.of(ui, player);
    }

    @Override
    public boolean isStillValid(Player player) {
        return session == null || session.isStillValid(player);
    }

    private void bind(UI ui, String id, java.util.function.Supplier<Component> value) {
        element(ui, id, Label.class).bind(DataBindingBuilder.componentS2C(value).build());
    }

    private void withSession(java.util.function.Consumer<FabricPolicySession> action) {
        if (session != null) {
            action.accept(session);
        }
    }

    private Component entranceText() {
        return session == null ? Component.translatable("ae2federation.ui.fabric.status.pending") : session.entranceText();
    }

    private Component membersText() {
        return session == null ? Component.translatable("ae2federation.ui.fabric.members.pending") : session.membersText();
    }

    private Component consumerText() {
        return session == null ? Component.translatable("ae2federation.ui.fabric.consumer", "-") : session.consumerText();
    }

    private Component providerText() {
        return session == null ? Component.translatable("ae2federation.ui.fabric.provider", "-") : session.providerText();
    }

    private Component ruleText() {
        return session == null ? Component.translatable("ae2federation.ui.fabric.rule.unavailable") : session.ruleText();
    }

    private Component statusText() {
        return session == null ? Component.translatable("ae2federation.ui.fabric.status.pending") : session.statusText();
    }

    private String statusCode() {
        return session == null ? "pending" : session.statusCode();
    }

    private static void applyState(String code, UI ui, Button consumer, Button provider, Button capability,
            Button toggle) {
        var status = element(ui, "ack_status", Label.class);
        for (var state : new String[] { "ready", "pending", "disabled", "accepted", "stale_context",
                "stale_revision" }) {
            status.removeClass(state);
        }
        status.addClass(code);
        var active = !code.equals("pending") && !code.equals("disabled")
                && !code.equals("stale_context") && !code.equals("stale_revision");
        consumer.setActive(active);
        provider.setActive(active);
        capability.setActive(active);
        toggle.setActive(active);
    }

    private static <T> T element(UI ui, String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing UI element #" + id));
    }
}
