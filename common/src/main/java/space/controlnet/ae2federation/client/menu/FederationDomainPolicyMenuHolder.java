package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphSnapshot;
import space.controlnet.ae2federation.client.policy.FederationDomainPolicySession;

final class FederationDomainPolicyMenuHolder implements PlayerUIMenuType.PlayerUIHolder, FederationMenuHolder {
    private static final ResourceLocation XML = ResourceLocation.fromNamespaceAndPath(
            "ae2federation", "ui/domain.xml");
    private final @Nullable FederationDomainPolicySession session;
    private final FederationMenuAuthority authority;
    private UI currentUi;
    private FederationWorkspace currentWorkspace;
    private FederationTopologyView currentTopology;
    private String serverStatus = "pending";
    /** The largest size this screen grows to; it fills the screen up to it. */
    private final int maxWidth;
    private final int maxHeight;

    FederationDomainPolicyMenuHolder(@Nullable FederationDomainPolicySession session, int maxWidth, int maxHeight) {
        this.session = session;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        authority = new FederationMenuAuthority(session);
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
        bind(ui, "ack_status", this::statusText);
        bind(ui, "revision_status", () -> session == null ? Component.empty() : session.revisionsText());
        bind(ui, "processing_status", this::mappingStatusText);
        var mappingFeedback = new BindableValue<String>("pending");
        mappingFeedback.bind(DataBindingBuilder.stringS2C(this::currentMappingStatus).initialValue("pending")
                .remoteSetter(code -> {
                    var label = element(ui, "processing_status", Label.class);
                    label.style(style -> style.tooltips(Component.literal(code)));
                    for (var tone : new String[] {"neutral", "waiting", "success", "error"}) label.removeClass("feedback-" + tone);
                    label.addClass("feedback-" + space.controlnet.ae2federation.client.policy.MappingFeedback.fromCode(code).tone());
                    if (currentWorkspace != null) currentWorkspace.updateFeedback();
                }).build());
        mappingFeedback.addClass("state-sync");
        ui.rootElement.addChild(mappingFeedback);
        bind(ui, "endpoint_detail", this::endpointDetailText);
        bind(ui, "endpoint_identity", () -> session == null
                ? Component.translatable("ae2federation.ui.domain.endpoint.none") : session.endpointIdentityText());

        var releaseDialog = new FederationReleaseDialog(ui, this::send);
        var workspace = new FederationWorkspace(ui, target -> send(FederationDomainPolicyAction.SELECT_TARGET, target));
        currentWorkspace = workspace;
        Runnable prepareRelease = () -> {
            if (authority.authorized()) {
                releaseDialog.prepare(authority.clientSequence());
                send(FederationDomainPolicyAction.PREPARE_RELEASE);
            }
        };
        workspace.bindProcessing(target -> send(FederationDomainPolicyAction.SET_MAPPING, target), prepareRelease);
        element(ui, "return_provider", Button.class).setOnClick(event ->
                FederationDomainPolicyActionSink.returnToProvider(player.containerMenu.containerId));
        var graphState = new FederationTopologyView(ui, target -> send(FederationDomainPolicyAction.SET_POLICY, target),
                target -> send(FederationDomainPolicyAction.RENAME_NETWORK, target), workspace::openObject);
        currentTopology = graphState;
        workspace.bindGraph(graphState);
        var choices = new BindableValue<String>("");
        choices.bind(DataBindingBuilder.stringS2C(() -> session == null ? "" : session.workspaceChoices())
                .initialValue("").remoteSetter(value -> {
                    workspace.acceptChoices(value);
                    if (!value.isEmpty()) graphState.acceptChoices(com.google.gson.JsonParser.parseString(value).getAsJsonObject());
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
        var authoritySync = new BindableValue<String>("");
        authoritySync.bind(DataBindingBuilder.stringS2C(() -> authority.encode(this, player))
                .initialValue("")
                .remoteSetter(value -> {
                    authority.accept(value);
                    releaseDialog.acceptAuthority(authority.clientSequence());
                    renderRequestProgress();
                })
                .build());
        authoritySync.addClass("authority-sync");
        ui.rootElement.addChild(authoritySync);
        var graphBinding = new BindableValue<String>("");
        graphBinding.bind(DataBindingBuilder.stringS2C(this::graphSnapshotText)
                .initialValue("")
                .remoteSetter(graphState::accept)
                .build());
        graphBinding.addClass("state-sync");
        ui.rootElement.addChild(graphBinding);
        var overview = new BindableValue<String>("");
        overview.bind(DataBindingBuilder.stringS2C(() -> session == null ? "" : session.networkOverviewText())
                .initialValue("")
                .remoteSetter(value -> {
                    if (!value.isEmpty()) graphState.acceptOverview(com.google.gson.JsonParser.parseString(value).getAsJsonArray());
                })
                .build());
        overview.addClass("state-sync");
        ui.rootElement.addChild(overview);
        var flows = new BindableValue<String>("");
        flows.bind(DataBindingBuilder.stringS2C(() -> session == null ? "" : session.pairFlowText())
                .initialValue("")
                .remoteSetter(value -> {
                    if (!value.isEmpty()) graphState.acceptFlows(com.google.gson.JsonParser.parseString(value).getAsJsonArray());
                })
                .build());
        flows.addClass("state-sync");
        ui.rootElement.addChild(flows);
        var observation = session == null ? java.util.Optional
                .<space.controlnet.ae2federation.observability.subscription.ObservationSubscription>empty()
                : session.openObservation();
        final class FederationModularUI extends ModularUI implements space.controlnet.ae2federation.client.FederationGuiScale.Fixed {
            FederationModularUI(UI document, Player viewer) {
                super(document, viewer);
            }

            @Override
            public void init(int screenWidth, int screenHeight) {
                ui.rootElement.removeClass("compact");
                if (screenHeight < 280) ui.rootElement.addClass("compact");
                var size = space.controlnet.ae2federation.client.policy.WorkspaceSize.fit(screenWidth, screenHeight, maxWidth, maxHeight);
                ui.rootElement.layout(style -> style.width(size.width()).height(size.height()));
                super.init(screenWidth, screenHeight);
            }

            @Override
            public void onRemoved() {
                observation.ifPresent(value -> session.closeObservation(value));
                super.onRemoved();
            }
        }
        return new FederationModularUI(ui, player);
    }

    @Override
    public boolean isStillValid(Player player) {
        return session == null || session.isStillValid(player);
    }

    private void bind(UI ui, String id, java.util.function.Supplier<Component> value) {
        element(ui, id, Label.class).bind(DataBindingBuilder.componentS2C(value).build());
    }

    @Override
    public java.util.Optional<net.minecraft.core.BlockPos> returnProvider() {
        return session == null ? java.util.Optional.empty()
                : session.providerEntity().map(net.minecraft.world.level.block.entity.BlockEntity::getBlockPos);
    }

    @Override
    public FederationDomainPolicyActionResult dispatch(ServerPlayer player, ModularUIContainerMenu menu,
            FederationDomainPolicyActionRequest request) {
        var refused = authority.reject(this, menu, request);
        if (refused != null) {
            return refused;
        }
        if (request.action() == FederationDomainPolicyAction.SET_POLICY
                || request.action() == FederationDomainPolicyAction.RENAME_NETWORK) {
            // Switches carry the revision of their own rule and names are not rules: the selected rule does not gate them.
            if (!session.matchesContext(player, request.context())) {
                return session.rejectStaleContext(player) ? FederationDomainPolicyActionResult.STALE_CONTEXT
                        : FederationDomainPolicyActionResult.WRONG_MENU;
            }
            session.clearPendingRelease();
            var applied = request.action() == FederationDomainPolicyAction.SET_POLICY
                    ? session.setPolicy(request.target()) : session.renameNetwork(request.target());
            if (!applied) {
                return FederationDomainPolicyActionResult.INVALID_TARGET;
            }
            authority.advance();
            return FederationDomainPolicyActionResult.ACCEPTED;
        }
        if (mappingAction(request.action())) {
            // Provider mapping is not a rule edit: a domain with one ME network still maps its Endpoints.
            if (!session.matchesMappingContext(player, request.context())) {
                return session.rejectStaleContext(player) ? FederationDomainPolicyActionResult.STALE_CONTEXT
                        : FederationDomainPolicyActionResult.WRONG_MENU;
            }
        } else if (!session.expectedRevision().equals(request.expectedRevision())) {
            return FederationDomainPolicyActionResult.STALE_REVISION;
        } else if (!session.matchesAuthority(player, request.context(), request.expectedRevision())) {
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
            case SET_POLICY, RENAME_NETWORK -> throw new IllegalStateException("Dispatched before selection authority");
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
            case SET_MAPPING -> {
                if (!session.setMapping(request.target())) {
                    return FederationDomainPolicyActionResult.INVALID_TARGET;
                }
            }
            case NEXT_ENDPOINT -> session.nextEndpoint();
            case RELEASE_ENDPOINT -> session.releaseEndpoint();
        }
        authority.advance();
        return FederationDomainPolicyActionResult.ACCEPTED;
    }

    private static boolean mappingAction(FederationDomainPolicyAction action) {
        return switch (action) {
            case PREPARE_RELEASE, CANCEL_RELEASE, SELECT_TARGET, NEXT_MAPPING_PROVIDER, NEXT_MAPPING_SLOT,
                    NEXT_MAPPING_LANE, TOGGLE_MAPPING, SET_MAPPING, NEXT_ENDPOINT, RELEASE_ENDPOINT -> true;
            case SET_POLICY, RENAME_NETWORK, NEXT_CONSUMER, NEXT_PROVIDER, NEXT_CAPABILITY, TOGGLE_POLICY -> false;
        };
    }

    @Override
    public java.util.Optional<FederationDomainPolicyActionRequest> currentRequest(ModularUIContainerMenu menu,
            FederationDomainPolicyAction action) {
        return authority.currentRequest(this, menu, action);
    }

    @Override
    public long currentSequence() {
        return authority.sequence();
    }

    @Override
    public String currentMappingStatus() {
        return session == null ? "pending" : session.mappingStatusCode();
    }

    private void send(FederationDomainPolicyAction action) {
        send(action, "");
    }

    private void send(FederationDomainPolicyAction action, String target) {
        if (authority.send(action, target)) renderRequestProgress();
    }

    @Override
    public void acceptReply(UUID nonce, UUID requestId, long sequence, FederationDomainPolicyActionResult result) {
        if (authority.acceptReply(nonce, requestId, sequence, result)) renderRequestProgress();
    }

    private void renderRequestProgress() {
        if (currentUi == null) return;
        var pending = authority.pending();
        var active = applyState(pending ? "pending" : serverStatus, currentUi);
        if (currentTopology != null) currentTopology.setEditable(active && authority.authorized());
        if (currentWorkspace != null) currentWorkspace.setProcessingEditable(active && authority.authorized());
        var message = element(currentUi, "request_status", Label.class);
        var rejection = authority.rejection();
        if (currentWorkspace != null) currentWorkspace.updateNavigationAuthority(!pending && authority.authorized()
                && (serverStatus.equals("ready") || serverStatus.equals("accepted") || serverStatus.equals("conflict")),
                rejection != null);
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

    private Component entranceText() {
        return session == null ? Component.translatable("ae2federation.ui.domain.status.pending") : session.entranceText();
    }

    private Component membersText() {
        return session == null ? Component.translatable("ae2federation.ui.domain.members.pending") : session.membersText();
    }

    private Component statusText() {
        return session == null ? Component.translatable("ae2federation.ui.domain.status.pending") : session.statusText();
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

    private static boolean applyState(String code, UI ui) {
        var status = element(ui, "ack_status", Label.class);
        for (var state : new String[] { "ready", "pending", "disabled", "accepted", "conflict", "stale_context",
                "stale_revision" }) {
            status.removeClass(state);
        }
        status.addClass(code);
        var active = !code.equals("pending") && !code.equals("disabled")
                && !code.equals("stale_context") && !code.equals("stale_revision");
        var lamp = element(ui, "sync_lamp", UIElement.class);
        int tone = active ? FederationTheme.OK : code.equals("pending") ? FederationTheme.WARN : FederationTheme.ERROR;
        lamp.style(style -> style.backgroundTexture(FederationTheme.solid(tone)));
        // The lamp's word, in a darker shade of its colour so it reads on the light frame.
        var sync = element(ui, "sync_text", Label.class);
        sync.setText(Component.translatable("ae2federation.ui.domain.sync." + (active ? "active" : code.equals("pending") ? "pending" : "stale")));
        sync.textStyle(style -> style.textColor(active ? 0xff20a94b : code.equals("pending") ? 0xff79541b : 0xff922e42));
        element(ui, "endpoint_next", UIElement.class).setActive(active);
        return active;
    }

    private static <T> T element(UI ui, String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing UI element #" + id));
    }


}
