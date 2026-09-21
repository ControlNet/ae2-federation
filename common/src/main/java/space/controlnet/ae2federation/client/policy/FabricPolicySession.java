package space.controlnet.ae2federation.client.policy;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import space.controlnet.ae2federation.bridge.BridgeOperationalReason;
import space.controlnet.ae2federation.bridge.BridgeRightClickContext;
import space.controlnet.ae2federation.fabric.FabricId;
import space.controlnet.ae2federation.fabric.FabricNodeEvidence;
import space.controlnet.ae2federation.fabric.FabricNodeId;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.fabric.FabricSnapshot;
import space.controlnet.ae2federation.fabric.FabricSourceId;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;

public final class FabricPolicySession {
    private static final double MAX_DISTANCE_SQUARED = 64.0;

    private final ServerPlayer player;
    private final ServerLevel level;
    private final FabricPolicyEntrance entrance;
    private final FabricReference context;
    private final FabricPolicyObservation observation;
    private PolicyEditorSelection selection;
    private PolicyRevision expectedRevision = PolicyRevision.NONE;
    private final PolicyEditorSessionState state;
    private String acknowledgmentId = "";

    private FabricPolicySession(ServerPlayer player, FabricPolicyEntrance entrance, Optional<FabricSnapshot> fabric,
            PolicyEditorSessionState.Status emptyState) {
        this.player = player;
        level = player.serverLevel();
        this.entrance = entrance;
        context = fabric.map(FabricSnapshot::reference).orElse(null);
        observation = context == null ? null : new FabricPolicyObservation(this, player, context);
        var members = fabric.map(snapshot -> List.copyOf(snapshot.memberships().keySet())).orElse(List.of());
        selection = members.size() >= 2 ? PolicyEditorSelection.initial(members) : null;
        var initialState = selection == null ? emptyState : PolicyEditorSessionState.Status.READY;
        state = new PolicyEditorSessionState(initialState, selection != null && entrance.enabled());
        refreshExpectedRevision();
    }

    public static FabricPolicySession forHub(ServerPlayer player, BlockPos position) {
        var level = player.serverLevel();
        var nodeId = FabricRegistryAccess.nodeId(level, position);
        var fabrics = FabricRegistryAccess.get(level).snapshot().fabrics().values().stream()
                .filter(snapshot -> snapshot.nodes().contains(nodeId)).toList();
        var current = fabrics.size() == 1 ? Optional.of(fabrics.getFirst()) : Optional.<FabricSnapshot>empty();
        return new FabricPolicySession(player, new HubPolicyEntrance(position), current,
                PolicyEditorSessionState.Status.PENDING);
    }

    public static FabricPolicySession forBridge(ServerPlayer player, BridgeRightClickContext bridge) {
        var entrance = new BridgePolicyEntrance(bridge.position(), bridge.side(), bridge.reason());
        return new FabricPolicySession(player, entrance, bridgeFabric(player.serverLevel(), bridge),
                PolicyEditorSessionState.Status.DISABLED);
    }

    public boolean isStillValid(Player candidate) {
        return candidate.getUUID().equals(player.getUUID()) && candidate.level() == level
                && level.isLoaded(entrance.position())
                && candidate.distanceToSqr(Vec3.atCenterOf(entrance.position())) <= MAX_DISTANCE_SQUARED
                && entrance.present(level);
    }

    public boolean canSubmit() {
        return state.editingAllowed();
    }

    public java.util.Optional<space.controlnet.ae2federation.observability.subscription.ObservationSubscription>
            openObservation() {
        return observation == null ? java.util.Optional.empty() : observation.open();
    }

    public void closeObservation(
            space.controlnet.ae2federation.observability.subscription.ObservationSubscription subscription) {
        if (observation != null) {
            observation.close(subscription);
        }
    }

    public void nextConsumer() {
        changeSelection(PolicyEditorSelection::nextConsumer);
    }

    public void nextProvider() {
        changeSelection(PolicyEditorSelection::nextProvider);
    }

    public void nextCapability() {
        changeSelection(PolicyEditorSelection::nextCapability);
    }

    public void toggleEnabled() {
        if (!authorizeAction()) {
            return;
        }
        var service = PolicyService.get(level);
        var key = selection.key();
        var current = service.configured(key).map(record -> record.rule()).orElseGet(() -> defaults(key.capability()));
        var enabled = service.configured(key).isEmpty() || !current.enabled();
        var result = service.edit(new PolicyEdit(key, expectedRevision, current.withEnabled(enabled)));
        if (result instanceof PolicyMutationResult.Accepted accepted) {
            expectedRevision = accepted.revision();
            state.accepted();
            acknowledgmentId = "policy-" + accepted.revision().value();
        } else if (result instanceof PolicyMutationResult.Rejected rejected) {
            expectedRevision = rejected.currentRevision();
            reject(PolicyEditorSessionState.Status.STALE_REVISION);
        }
    }

    public Component entranceText() {
        return entrance.label();
    }

    public Component membersText() {
        if (selection == null) {
            return Component.translatable("ae2federation.ui.fabric.members.pending");
        }
        var text = Component.translatable("ae2federation.ui.fabric.members", selection.members().size());
        for (var member : selection.members()) {
            text.append("\n").append(Component.literal(member.value().toString()));
        }
        return text;
    }

    public Component consumerText() {
        return selectionText("ae2federation.ui.fabric.consumer", true);
    }

    public Component providerText() {
        return selectionText("ae2federation.ui.fabric.provider", false);
    }

    public Component ruleText() {
        if (selection == null) {
            return Component.translatable("ae2federation.ui.fabric.rule.unavailable");
        }
        var key = selection.key();
        var configured = PolicyService.get(level).configured(key);
        var stateText = configured.map(record -> record.rule().enabled()
                ? Component.translatable("ae2federation.ui.fabric.rule.on")
                : Component.translatable("ae2federation.ui.fabric.rule.off"))
                .orElseGet(() -> Component.translatable("ae2federation.ui.fabric.rule.unconfigured"));
        return Component.translatable("ae2federation.ui.fabric.rule", key.capability().name(), stateText,
                expectedRevision.value());
    }

    public Component statusText() {
        return switch (state.status()) {
            case READY -> Component.translatable("ae2federation.ui.fabric.status.ready");
            case PENDING -> Component.translatable("ae2federation.ui.fabric.status.pending");
            case DISABLED -> Component.translatable("ae2federation.ui.fabric.status.disabled", entrance.diagnostic());
            case ACCEPTED -> Component.translatable("ae2federation.ui.fabric.status.accepted",
                    expectedRevision.value(), acknowledgmentId);
            case STALE_CONTEXT -> Component.translatable("ae2federation.ui.fabric.status.stale_context");
            case STALE_REVISION -> Component.translatable("ae2federation.ui.fabric.status.stale_revision",
                    expectedRevision.value());
        };
    }

    public String statusCode() {
        return state.status().name().toLowerCase(java.util.Locale.ROOT);
    }

    private Component selectionText(String key, boolean consumer) {
        if (selection == null) {
            return Component.translatable(key, "-");
        }
        var selected = consumer ? selection.key().consumerNetworkId() : selection.key().providerNetworkId();
        return Component.translatable(key, selected.value().toString());
    }

    private void changeSelection(UnaryOperator<PolicyEditorSelection> change) {
        if (!authorizeAction()) {
            return;
        }
        selection = change.apply(selection);
        if (!state.selectionChanged()) {
            return;
        }
        acknowledgmentId = "";
        refreshExpectedRevision();
    }

    private boolean authorizeAction() {
        if (!state.editingAllowed() || selection == null) {
            return false;
        }
        if (!isStillValid(player) || context == null || !FabricRegistryAccess.get(level).isCurrent(context)) {
            reject(PolicyEditorSessionState.Status.STALE_CONTEXT);
            return false;
        }
        var currentRevision = PolicyService.get(level).revision(selection.key());
        if (!currentRevision.equals(expectedRevision)) {
            expectedRevision = currentRevision;
            reject(PolicyEditorSessionState.Status.STALE_REVISION);
            return false;
        }
        return true;
    }

    private void refreshExpectedRevision() {
        if (selection != null) {
            expectedRevision = PolicyService.get(level).revision(selection.key());
        }
    }

    private void reject(PolicyEditorSessionState.Status rejectedState) {
        state.reject(rejectedState);
        acknowledgmentId = "";
    }

    private static Optional<FabricSnapshot> bridgeFabric(ServerLevel level, BridgeRightClickContext bridge) {
        if (bridge.reason() != BridgeOperationalReason.VALID || bridge.mainGrid() == null || bridge.outerGrid() == null) {
            return Optional.empty();
        }
        var main = FabricRegistryAccess.confirmedNetworkId(bridge.mainGrid());
        var outer = FabricRegistryAccess.confirmedNetworkId(bridge.outerGrid());
        if (main.isEmpty() || outer.isEmpty()) {
            return Optional.empty();
        }
        var source = new FabricSourceId("bridge:" + FabricRegistryAccess.nodeId(level, bridge.position()) + ":"
                + bridge.side().getSerializedName());
        return FabricRegistryAccess.get(level).fabric(FabricId.direct(source))
                .filter(snapshot -> snapshot.memberships().containsKey(main.orElseThrow())
                        && snapshot.memberships().containsKey(outer.orElseThrow()));
    }

    private static PolicyRule defaults(PolicyCapability capability) {
        return switch (capability) {
            case STORAGE -> PolicyRule.storageDefaults();
            case CRAFTING -> PolicyRule.enabled(java.util.Set.of(space.controlnet.ae2federation.policy.PolicyOperation.REQUEST));
            case PROCESSING -> PolicyRule.enabled(java.util.Set.of(
                    space.controlnet.ae2federation.policy.PolicyOperation.EXECUTE,
                    space.controlnet.ae2federation.policy.PolicyOperation.SUPPLY));
            case ME_POWER -> PolicyRule.enabled(java.util.Set.of(space.controlnet.ae2federation.policy.PolicyOperation.SUPPLY));
        };
    }

}
