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
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphLayer;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphLayout;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphLayoutCache;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphSnapshot;
import space.controlnet.ae2federation.client.policy.FederationDomainPolicySession;

final class FederationDomainPolicyMenuHolder implements PlayerUIMenuType.PlayerUIHolder {
    private static final ResourceLocation XML = ResourceLocation.fromNamespaceAndPath(
            "ae2federation", "ui/domain.xml");
    private final @Nullable FederationDomainPolicySession session;
    private final @Nullable UUID menuNonce;
    private long menuSequence;
    private @Nullable ClientAuthority clientAuthority;

    FederationDomainPolicyMenuHolder(@Nullable FederationDomainPolicySession session) {
        this.session = session;
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
        bind(ui, "entrance_value", this::entranceText);
        bind(ui, "members_value", this::membersText);
        bind(ui, "consumer_value", this::consumerText);
        bind(ui, "provider_value", this::providerText);
        bind(ui, "rule_value", this::ruleText);
        bind(ui, "ack_status", this::statusText);
        bind(ui, "mapping_provider_value", this::mappingProviderText);
        bind(ui, "mapping_selection_value", this::mappingSelectionText);
        bind(ui, "mapping_status", this::mappingStatusText);
        bind(ui, "endpoint_detail", this::endpointDetailText);

        var consumer = element(ui, "consumer_next", Button.class);
        var provider = element(ui, "provider_next", Button.class);
        var capability = element(ui, "capability_next", Button.class);
        var toggle = element(ui, "policy_toggle", Button.class);
        consumer.setOnClick(event -> send(FederationDomainPolicyAction.NEXT_CONSUMER));
        provider.setOnClick(event -> send(FederationDomainPolicyAction.NEXT_PROVIDER));
        capability.setOnClick(event -> send(FederationDomainPolicyAction.NEXT_CAPABILITY));
        toggle.setOnClick(event -> send(FederationDomainPolicyAction.TOGGLE_POLICY));
        element(ui, "mapping_provider_next", Button.class)
                .setOnClick(event -> send(FederationDomainPolicyAction.NEXT_MAPPING_PROVIDER));
        element(ui, "mapping_slot_next", Button.class)
                .setOnClick(event -> send(FederationDomainPolicyAction.NEXT_MAPPING_SLOT));
        element(ui, "mapping_lane_next", Button.class)
                .setOnClick(event -> send(FederationDomainPolicyAction.NEXT_MAPPING_LANE));
        element(ui, "mapping_toggle", Button.class)
                .setOnClick(event -> send(FederationDomainPolicyAction.TOGGLE_MAPPING));
        element(ui, "mapping_release", Button.class)
                .setOnClick(event -> send(FederationDomainPolicyAction.RELEASE_ENDPOINT));
        element(ui, "endpoint_next", Button.class)
                .setOnClick(event -> send(FederationDomainPolicyAction.NEXT_ENDPOINT));

        var graph = element(ui, "domain_graph", GraphView.class);
        var graphState = new ClientGraphState(graph, virtualList(ui, "member_list"), virtualList(ui, "pattern_list"));
        element(ui, "graph_zoom_in", Button.class).setOnClick(event -> graph.setScale(graph.getScale() * 1.25f));
        element(ui, "graph_zoom_out", Button.class).setOnClick(event -> graph.setScale(graph.getScale() / 1.25f));
        element(ui, "graph_fit", Button.class).setOnClick(event -> graph.fitToChildren(12, 0.25f));
        element(ui, "physical_layer_toggle", Button.class).setOnClick(event -> graphState.togglePhysical());
        element(ui, "capability_layer_toggle", Button.class).setOnClick(event -> graphState.toggleCapability());
        element(ui, "pattern_search", TextField.class).setTextResponder(graphState::setPatternFilter);

        var state = new BindableValue<String>("");
        state.bind(DataBindingBuilder.stringS2C(this::statusCode)
                .initialValue("")
                .remoteSetter(code -> applyState(code, ui, consumer, provider, capability, toggle))
                .build());
        state.addClass("state-sync");
        ui.rootElement.addChild(state);
        var authority = new BindableValue<String>("");
        authority.bind(DataBindingBuilder.stringS2C(() -> authorityText(player))
                .initialValue("")
                .remoteSetter(this::acceptAuthority)
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
        var authority = clientAuthority;
        if (authority != null) {
            FederationDomainPolicyActionSink.send(new FederationDomainPolicyActionRequest(action, authority.containerId(),
                    authority.menuNonce(), authority.menuSequence(), authority.context(), authority.expectedRevision()));
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
        return session == null ? Component.literal("pending") : session.mappingStatusText();
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

    @SuppressWarnings("unchecked")
    private static VirtualScrollerView<String> virtualList(UI ui, String id) {
        return (VirtualScrollerView<String>) (VirtualScrollerView<?>) element(ui, id, VirtualScrollerView.class);
    }

    private static UIElement row(String value) {
        var row = new Label();
        row.setText(Component.literal(value));
        row.addClass("virtual-row");
        return row;
    }

    private static final class ClientGraphState {
        private final GraphView graph;
        private final VirtualScrollerView<String> memberList;
        private final VirtualScrollerView<String> patternList;
        private final FederationDomainGraphLayoutCache layoutCache = new FederationDomainGraphLayoutCache();
        private FederationDomainGraphSnapshot snapshot = FederationDomainGraphSnapshot.empty();
        private boolean physical = true;
        private boolean capability = true;
        private String patternFilter = "";

        private ClientGraphState(GraphView graph, VirtualScrollerView<String> memberList,
                VirtualScrollerView<String> patternList) {
            this.graph = graph;
            this.memberList = memberList.setItemUIProvider(FederationDomainPolicyMenuHolder::row);
            this.patternList = patternList.setItemUIProvider(FederationDomainPolicyMenuHolder::row);
        }

        private void accept(String encoded) {
            if (encoded.isEmpty()) {
                return;
            }
            snapshot = FederationDomainGraphSnapshot.decode(encoded);
            render();
        }

        private void togglePhysical() {
            physical = !physical;
            render();
        }

        private void toggleCapability() {
            capability = !capability;
            render();
        }

        private void setPatternFilter(String value) {
            patternFilter = value.toLowerCase(java.util.Locale.ROOT);
            refreshLists();
        }

        private void render() {
            graph.clearAllContentChildren();
            FederationDomainGraphLayout layout = layoutCache.layout(snapshot);
            var positions = new HashMap<String, FederationDomainGraphLayout.Node>();
            layout.nodes().forEach(node -> {
                positions.put(node.id(), node);
                var status = snapshot.nodes().stream().filter(value -> value.id().equals(node.id())).findFirst()
                        .map(FederationDomainGraphSnapshot.Node::status).orElse("");
                var label = new Label();
                label.setText(Component.literal(node.kind().name() + "\n" + shortId(node.id()) + "\n" + status));
                label.setId("graph_node_" + safeId(node.id()));
                label.addClass("graph-node");
                label.addClass(node.kind().name().toLowerCase(java.util.Locale.ROOT));
                label.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(node.x()).top(node.y())
                        .width(96).height(31));
                graph.addContentChild(label);
            });
            snapshot.edges().stream().filter(edge -> visible(edge.layer())).forEach(edge -> {
                var from = positions.get(edge.from());
                var to = positions.get(edge.to());
                if (from == null || to == null) {
                    return;
                }
                var label = new Label();
                label.setText(Component.literal(edge.layer() == FederationDomainGraphLayer.PHYSICAL ? "--- PHYS --->" : "--- CAP ---->"));
                label.addClass("graph-edge");
                label.addClass(edge.layer().name().toLowerCase(java.util.Locale.ROOT));
                label.layout(style -> style.positionType(TaffyPosition.ABSOLUTE)
                        .left((from.x() + to.x()) / 2f).top((from.y() + to.y()) / 2f + 11).width(88).height(9));
                graph.addContentChild(label);
            });
            refreshLists();
        }

        private boolean visible(FederationDomainGraphLayer layer) {
            return layer == FederationDomainGraphLayer.PHYSICAL ? physical : capability;
        }

        private void refreshLists() {
            memberList.setItems(snapshot.nodes().stream()
                    .filter(node -> node.kind() == space.controlnet.ae2federation.client.domain.FederationDomainGraphNodeKind.MEMBER)
                    .map(node -> shortId(node.id()) + "  " + node.status()).toList());
            List<String> patterns = snapshot.patterns().stream()
                    .filter(value -> value.toLowerCase(java.util.Locale.ROOT).contains(patternFilter)).toList();
            patternList.setItems(patterns);
        }

        private static String shortId(String value) {
            return value.length() <= 12 ? value : value.substring(0, 12);
        }

        private static String safeId(String value) {
            return value.replaceAll("[^a-zA-Z0-9_-]", "_");
        }
    }
}
