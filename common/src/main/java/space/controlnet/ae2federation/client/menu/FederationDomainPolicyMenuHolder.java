package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphSnapshot;
import space.controlnet.ae2federation.client.policy.FederationDomainPolicySession;

final class FederationDomainPolicyMenuHolder implements PlayerUIMenuType.PlayerUIHolder {
    private static final ResourceLocation XML = ResourceLocation.fromNamespaceAndPath(
            "ae2federation", "ui/domain.xml");
    private final @Nullable FederationDomainPolicySession session;
    private final @Nullable UUID menuNonce;
    private long menuSequence;
    private @Nullable ClientAuthority clientAuthority;
    private final ActionRequestProgress requestProgress = new ActionRequestProgress();
    private UI currentUi;
    private FederationWorkspace currentWorkspace;
    private String serverStatus = "pending";
    private final int preferredWidth;
    private final int preferredHeight;

    FederationDomainPolicyMenuHolder(@Nullable FederationDomainPolicySession session, int preferredWidth, int preferredHeight) {
        this.session = session;
        this.preferredWidth = preferredWidth;
        this.preferredHeight = preferredHeight;
        menuNonce = session == null ? null : UUID.randomUUID();
    }

    @Override
    public ModularUI createUI(Player player) {
        var document = XmlUtils.loadXml(XML);
        if (document == null) {
            try (var input = FederationDomainPolicyMenuHolder.class.getResourceAsStream("/assets/ae2federation/ui/domain.xml")) {
                document = input == null ? null : XmlUtils.loadXml(input);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Cannot load production Federation Domain policy UI " + XML, exception);
            }
        }
        document = Objects.requireNonNull(document, "Missing production Federation Domain policy UI " + XML);
        var ui = UI.of(document);
        currentUi = ui;
        bind(ui, "entrance_value", this::entranceText);
        bind(ui, "members_value", this::membersText);
        bind(ui, "consumer_value", this::consumerText);
        bind(ui, "provider_value", this::providerText);
        bind(ui, "rule_value", this::ruleText);
        bind(ui, "ack_status", this::statusText);
        bind(ui, "mapping_provider_value", this::mappingProviderText);
        bind(ui, "mapping_selection_value", this::mappingSelectionText);
        bind(ui, "mapping_status", this::mappingStatusText);
        var mappingFeedback = new BindableValue<String>("pending");
        mappingFeedback.bind(DataBindingBuilder.stringS2C(this::currentMappingStatus).initialValue("pending")
                .remoteSetter(code -> {
                    var label = element(ui, "mapping_status", Label.class);
                    label.style(style -> style.tooltips(Component.literal(code)));
                    for (var tone : new String[] {"neutral", "waiting", "success", "error"}) label.removeClass("feedback-" + tone);
                    label.addClass("feedback-" + space.controlnet.ae2federation.client.policy.MappingFeedback.fromCode(code).tone());
                }).build());
        mappingFeedback.addClass("state-sync");
        ui.rootElement.addChild(mappingFeedback);
        bind(ui, "endpoint_detail", this::endpointDetailText);
        bind(ui, "endpoint_identity", () -> session == null
                ? Component.translatable("ae2federation.ui.domain.endpoint.none") : session.endpointIdentityText());

        var releaseDialog = new FederationReleaseDialog(ui, this::send);
        var workspace = new FederationWorkspace(ui, target -> send(FederationDomainPolicyAction.SELECT_TARGET, target));
        currentWorkspace = workspace;
        var consumer = element(ui, "consumer_next", UIElement.class);
        var provider = element(ui, "provider_next", UIElement.class);
        var capability = element(ui, "capability_next", UIElement.class);
        var toggle = element(ui, "policy_toggle", Button.class);
        toggle.setOnClick(event -> send(FederationDomainPolicyAction.TOGGLE_POLICY));
        element(ui, "mapping_toggle", Button.class).setOnClick(event -> send(FederationDomainPolicyAction.TOGGLE_MAPPING));
        element(ui, "mapping_release", Button.class).setOnClick(event -> {
            if (clientAuthority != null) {
                releaseDialog.prepare(clientAuthority.menuSequence());
                send(FederationDomainPolicyAction.PREPARE_RELEASE);
            }
        });
        element(ui, "return_provider", Button.class).setOnClick(event ->
                FederationDomainPolicyActionSink.returnToProvider(player.containerMenu.containerId));
        var graph = element(ui, "domain_graph", GraphView.class);
        var graphState = new FederationGraphPresenter(graph, element(ui, "graph_selection", Label.class), virtualList(ui, "member_list"),
                element(ui, "graph_open", Button.class), element(ui, "graph_search", com.lowdragmc.lowdraglib2.gui.ui.elements.TextField.class),
                element(ui, "graph_search_empty", Label.class), workspace::openObject);
        workspace.bindGraph(graphState);
        for (var zoomId : new String[] {"graph_zoom_in", "graph_zoom_out"}) {
            element(ui, zoomId, Button.class).layout(style -> style.flexGrow(0).flexShrink(0).width(22));
        }
        element(ui, "graph_zoom_in", Button.class).setOnClick(event -> graph.setScale(graph.getScale() * 1.25f));
        element(ui, "graph_zoom_out", Button.class).setOnClick(event -> graph.setScale(graph.getScale() / 1.25f));
        element(ui, "graph_fit", Button.class).setOnClick(event -> graph.fitToChildren(12, 0.25f));
        var physical = element(ui, "physical_layer_toggle", Button.class);
        var ownership = element(ui, "capability_layer_toggle", Button.class);
        FederationGraphPresenter.mark(physical, true);
        FederationGraphPresenter.mark(ownership, true);
        physical.setOnClick(event -> graphState.togglePhysical(physical));
        ownership.setOnClick(event -> graphState.toggleCapability(ownership));
        var choices = new BindableValue<String>("");
        choices.bind(DataBindingBuilder.stringS2C(() -> session == null ? "" : session.workspaceChoices())
                .initialValue("").remoteSetter(value -> {
                    workspace.acceptChoices(value);
                    graphState.acceptChoices(value);
                    releaseDialog.acceptChoices(value);
                }).build());
        choices.addClass("state-sync");
        ui.rootElement.addChild(choices);

        var state = new BindableValue<String>("");
        state.bind(DataBindingBuilder.stringS2C(this::statusCode)
                .initialValue("")
                .remoteSetter(code -> {
                    serverStatus = code;
                    renderRequestProgress();
                })
                .build());
        state.addClass("state-sync");
        ui.rootElement.addChild(state);
        var authority = new BindableValue<String>("");
        authority.bind(DataBindingBuilder.stringS2C(() -> authorityText(player))
                .initialValue("")
                .remoteSetter(value -> {
                    acceptAuthority(value);
                    releaseDialog.acceptAuthority(clientAuthority == null ? -1 : clientAuthority.menuSequence());
                    if (clientAuthority != null) requestProgress.observe(clientAuthority.menuSequence());
                    renderRequestProgress();
                })
                .build());
        authority.addClass("authority-sync");
        ui.rootElement.addChild(authority);
        var graphBinding = new BindableValue<String>("");
        graphBinding.bind(DataBindingBuilder.stringS2C(this::graphSnapshotText)
                .initialValue("")
                .remoteSetter(graphState::accept)
                .build());
        graphBinding.addClass("state-sync");
        ui.rootElement.addChild(graphBinding);
        var observation = session == null ? java.util.Optional
                .<space.controlnet.ae2federation.observability.subscription.ObservationSubscription>empty()
                : session.openObservation();
        return new ModularUI(ui, player) {
            @Override
            public void init(int screenWidth, int screenHeight) {
                ui.rootElement.removeClass("compact");
                if (screenHeight < 280) ui.rootElement.addClass("compact");
                ui.rootElement.layout(style -> style.width(Math.min(preferredWidth, screenWidth - 8))
                        .height(Math.min(preferredHeight, screenHeight - 8)));
                super.init(screenWidth, screenHeight);
            }

            @Override
            public void onRemoved() {
                observation.ifPresent(value -> session.closeObservation(value));
                super.onRemoved();
            }
        };
    }

    @Override
    public boolean isStillValid(Player player) {
        return session == null || session.isStillValid(player);
    }

    private void bind(UI ui, String id, java.util.function.Supplier<Component> value) {
        element(ui, id, Label.class).bind(DataBindingBuilder.componentS2C(value).build());
    }

    void returnToProvider() {
        if (session != null) session.returnToProvider();
    }

    FederationDomainPolicyActionResult dispatch(ServerPlayer player, ModularUIContainerMenu menu,
            FederationDomainPolicyActionRequest request) {
        if (menu.uiHolder != this || session == null || menuNonce == null) {
            return FederationDomainPolicyActionResult.WRONG_MENU;
        }
        if (request.containerId() != menu.containerId) {
            return FederationDomainPolicyActionResult.STALE_CONTAINER;
        }
        if (!menuNonce.equals(request.menuNonce())) {
            return FederationDomainPolicyActionResult.STALE_SESSION;
        }
        if (request.menuSequence() != menuSequence) {
            return FederationDomainPolicyActionResult.STALE_SEQUENCE;
        }
        var context = session.context().orElse(null);
        if (context == null || !context.equals(request.context())) {
            return FederationDomainPolicyActionResult.STALE_CONTEXT;
        }
        if (!session.expectedRevision().equals(request.expectedRevision())) {
            return FederationDomainPolicyActionResult.STALE_REVISION;
        }
        if (!session.matchesAuthority(player, request.context(), request.expectedRevision())) {
            if (session.rejectStaleContext(player)) {
                return FederationDomainPolicyActionResult.STALE_CONTEXT;
            }
            session.rejectStaleRevision();
            return FederationDomainPolicyActionResult.STALE_REVISION;
        }
        if (request.action() != FederationDomainPolicyAction.RELEASE_ENDPOINT) {
            session.clearPendingRelease();
        }
        switch (request.action()) {
            case PREPARE_RELEASE -> session.releaseEndpoint();
            case CANCEL_RELEASE -> session.cancelRelease();
            case SELECT_TARGET -> {
                if (!session.selectTarget(request.target())) {
                    return FederationDomainPolicyActionResult.INVALID_TARGET;
                }
            }
            case NEXT_CONSUMER -> session.nextConsumer();
            case NEXT_PROVIDER -> session.nextProvider();
            case NEXT_CAPABILITY -> session.nextCapability();
            case TOGGLE_POLICY -> session.toggleEnabled();
            case NEXT_MAPPING_PROVIDER -> session.nextMappingProvider();
            case NEXT_MAPPING_SLOT -> session.nextMappingSlot();
            case NEXT_MAPPING_LANE -> session.nextMappingLane();
            case TOGGLE_MAPPING -> session.toggleMapping();
            case NEXT_ENDPOINT -> session.nextEndpoint();
            case RELEASE_ENDPOINT -> session.releaseEndpoint();
        }
        menuSequence = Math.incrementExact(menuSequence);
        return FederationDomainPolicyActionResult.ACCEPTED;
    }

    java.util.Optional<FederationDomainPolicyActionRequest> currentRequest(ModularUIContainerMenu menu,
            FederationDomainPolicyAction action) {
        if (session == null || menuNonce == null || menu.uiHolder != this) {
            return java.util.Optional.empty();
        }
        return session.context().map(context -> new FederationDomainPolicyActionRequest(action, menu.containerId, menuNonce,
                menuSequence, context, session.expectedRevision()));
    }

    long currentSequence() {
        return menuSequence;
    }

    String currentMappingStatus() {
        return session == null ? "pending" : session.mappingStatusCode();
    }

    private String authorityText(Player player) {
        if (session == null || menuNonce == null || !(player.containerMenu instanceof ModularUIContainerMenu menu)
                || menu.uiHolder != this) {
            return "";
        }
        var context = session.context().orElse(null);
        if (context == null) {
            return "";
        }
        var encodedFederationDomain = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(context.federationDomainId().value().getBytes(StandardCharsets.UTF_8));
        return menu.containerId + ":" + menuNonce + ":" + menuSequence + ":" + context.generation() + ":"
                + session.expectedRevision().value() + ":" + encodedFederationDomain;
    }

    private void acceptAuthority(String encoded) {
        if (encoded.isEmpty()) {
            clientAuthority = null;
            return;
        }
        var fields = encoded.split(":", 6);
        if (fields.length != 6) {
            throw new IllegalArgumentException("Malformed Federation Domain policy authority");
        }
        var federationDomainId = new String(Base64.getUrlDecoder().decode(fields[5]), StandardCharsets.UTF_8);
        clientAuthority = new ClientAuthority(Integer.parseInt(fields[0]), UUID.fromString(fields[1]),
                Long.parseLong(fields[2]),
                new space.controlnet.ae2federation.domain.FederationDomainReference(
                        new space.controlnet.ae2federation.domain.FederationDomainId(federationDomainId), Long.parseLong(fields[3])),
                new space.controlnet.ae2federation.policy.PolicyRevision(Long.parseLong(fields[4])));
    }

    private void send(FederationDomainPolicyAction action) {
        send(action, "");
    }

    private void send(FederationDomainPolicyAction action, String target) {
        var authority = clientAuthority;
        if (authority != null && !requestProgress.pending()) {
            var requestId = UUID.randomUUID();
            requestProgress.begin(requestId, authority.menuSequence());
            renderRequestProgress();
            if (!FederationDomainPolicyActionSink.send(new FederationDomainPolicyActionRequest(action, authority.containerId(),
                    authority.menuNonce(), authority.menuSequence(), authority.context(), authority.expectedRevision(), target), requestId)) {
                requestProgress.reply(requestId, authority.menuSequence(), FederationDomainPolicyActionResult.WRONG_MENU);
                renderRequestProgress();
            }
        }
    }

    void acceptReply(UUID nonce, UUID requestId, long sequence, FederationDomainPolicyActionResult result) {
        if (clientAuthority == null || !clientAuthority.menuNonce().equals(nonce)) return;
        requestProgress.reply(requestId, sequence, result);
        renderRequestProgress();
    }

    private void renderRequestProgress() {
        if (currentUi == null) return;
        var pending = requestProgress.pending();
        applyState(pending ? "pending" : serverStatus, currentUi,
                element(currentUi, "consumer_next", UIElement.class), element(currentUi, "provider_next", UIElement.class),
                element(currentUi, "capability_next", UIElement.class), element(currentUi, "policy_toggle", Button.class));
        var message = element(currentUi, "request_status", Label.class);
        var rejection = requestProgress.rejection();
        if (currentWorkspace != null) currentWorkspace.updateNavigationAuthority(!pending && clientAuthority != null
                && (serverStatus.equals("ready") || serverStatus.equals("accepted")), rejection != null);
        boolean visible = pending || rejection != null;
        message.setDisplay(visible);
        element(currentUi, "ack_status", Label.class).setDisplay(!visible);
        message.removeClass("request-error");
        if (pending) message.setText(Component.translatable("ae2federation.ui.request.pending"));
        else if (rejection != null) {
            message.addClass("request-error");
            message.setText(Component.translatable("ae2federation.ui.request." + rejection.name().toLowerCase(java.util.Locale.ROOT)));
        }
        for (var id : new String[] {"release_confirm", "release_cancel"}) {
            currentUi.selectId(id, Button.class).forEach(button -> button.setActive(!pending));
        }
    }

    private record ClientAuthority(int containerId, UUID menuNonce, long menuSequence,
            space.controlnet.ae2federation.domain.FederationDomainReference context,
            space.controlnet.ae2federation.policy.PolicyRevision expectedRevision) {
    }

    private Component entranceText() {
        return session == null ? Component.translatable("ae2federation.ui.domain.status.pending") : session.entranceText();
    }

    private Component membersText() {
        return session == null ? Component.translatable("ae2federation.ui.domain.members.pending") : session.membersText();
    }

    private Component consumerText() {
        return session == null ? Component.translatable("ae2federation.ui.domain.consumer", "-") : session.consumerText();
    }

    private Component providerText() {
        return session == null ? Component.translatable("ae2federation.ui.domain.provider", "-") : session.providerText();
    }

    private Component ruleText() {
        return session == null ? Component.translatable("ae2federation.ui.domain.rule.unavailable") : session.ruleText();
    }

    private Component statusText() {
        return session == null ? Component.translatable("ae2federation.ui.domain.status.pending") : session.statusText();
    }

    private Component mappingProviderText() {
        return session == null ? Component.literal("-") : session.mappingProviderText();
    }

    private Component mappingSelectionText() {
        return session == null ? Component.literal("-") : session.mappingSelectionText();
    }

    private Component mappingStatusText() {
        return session == null ? Component.translatable("ae2federation.ui.mapping_feedback.pending") : session.mappingStatusText();
    }

    private Component endpointDetailText() {
        return session == null ? Component.translatable("ae2federation.ui.domain.endpoint.none")
                : session.endpointDetailText();
    }

    private String graphSnapshotText() {
        return session == null ? FederationDomainGraphSnapshot.empty().encode() : session.graphSnapshotText();
    }

    private String statusCode() {
        return session == null ? "pending" : session.statusCode();
    }

    private static void applyState(String code, UI ui, UIElement consumer, UIElement provider, UIElement capability,
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
        for (var id : new String[] {"mapping_provider_next", "mapping_slot_next", "mapping_lane_next", "mapping_toggle", "mapping_release", "endpoint_next", "pattern_list"}) {
            element(ui, id, UIElement.class).setActive(active);
        }
    }

    private static <T> T element(UI ui, String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing UI element #" + id));
    }

    @SuppressWarnings("unchecked")
    private static VirtualScrollerView<String> virtualList(UI ui, String id) {
        return (VirtualScrollerView<String>) (VirtualScrollerView<?>) element(ui, id, VirtualScrollerView.class);
    }

}
