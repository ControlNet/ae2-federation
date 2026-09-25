package space.controlnet.ae2federation.client.policy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import space.controlnet.ae2federation.bridge.BridgeOperationalReason;
import space.controlnet.ae2federation.bridge.BridgeRightClickContext;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.FederationDomainSnapshot;
import space.controlnet.ae2federation.domain.FederationDomainSourceId;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.provider.PatternSlotHandle;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;

public final class FederationDomainPolicySession {
    private static final double MAX_DISTANCE_SQUARED = 64.0;

    private final ServerPlayer player;
    private final ServerLevel level;
    private final FederationDomainPolicyEntrance entrance;
    private final FederationDomainReference context;
    private final FederationDomainPolicyObservation observation;
    private PolicyEditorSelection selection;
    private PolicyRevision expectedRevision = PolicyRevision.NONE;
    private final PolicyEditorSessionState state;
    private String acknowledgmentId = "";
    private int mappingProviderIndex;
    private int mappingSlotIndex;
    private int mappingLaneIndex;
    private int endpointIndex;
    private String mappingAcknowledgment = "ready";

    private FederationDomainPolicySession(ServerPlayer player, FederationDomainPolicyEntrance entrance, Optional<FederationDomainSnapshot> federationDomain,
            PolicyEditorSessionState.Status emptyState) {
        this.player = player;
        level = player.serverLevel();
        this.entrance = entrance;
        context = federationDomain.map(FederationDomainSnapshot::reference).orElse(null);
        observation = context == null ? null : new FederationDomainPolicyObservation(this, player, context);
        var members = federationDomain.map(snapshot -> List.copyOf(snapshot.memberships().keySet())).orElse(List.of());
        selection = members.size() >= 2 ? PolicyEditorSelection.initial(members) : null;
        var initialState = selection == null ? emptyState : PolicyEditorSessionState.Status.READY;
        state = new PolicyEditorSessionState(initialState, selection != null && entrance.enabled());
        refreshExpectedRevision();
    }

    public static FederationDomainPolicySession forRouter(ServerPlayer player, BlockPos position) {
        var level = player.serverLevel();
        var nodeId = FederationDomainRegistryAccess.nodeId(level, position);
        var federationDomains = FederationDomainRegistryAccess.get(level).snapshot().federationDomains().values().stream()
                .filter(snapshot -> snapshot.nodes().contains(nodeId)).toList();
        var current = federationDomains.size() == 1 ? Optional.of(federationDomains.getFirst()) : Optional.<FederationDomainSnapshot>empty();
        return new FederationDomainPolicySession(player, new RouterPolicyEntrance(position), current,
                PolicyEditorSessionState.Status.PENDING);
    }

    public static FederationDomainPolicySession forBridge(ServerPlayer player, BridgeRightClickContext bridge) {
        var entrance = new BridgePolicyEntrance(bridge.position(), bridge.side(), bridge.reason());
        return new FederationDomainPolicySession(player, entrance, bridgeFederationDomain(player.serverLevel(), bridge),
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

    public Optional<FederationDomainReference> context() {
        return Optional.ofNullable(context);
    }

    public PolicyRevision expectedRevision() {
        return expectedRevision;
    }

    public boolean matchesAuthority(ServerPlayer candidate, FederationDomainReference requestedContext,
            PolicyRevision requestedRevision) {
        if (!isStillValid(candidate) || context == null || !context.equals(requestedContext)
                || !FederationDomainRegistryAccess.get(level).isCurrent(context) || !state.editingAllowed() || selection == null
                || !expectedRevision.equals(requestedRevision)) {
            return false;
        }
        return PolicyService.get(level).revision(selection.key()).equals(requestedRevision);
    }

    public boolean rejectStaleContext(ServerPlayer candidate) {
        if (isStillValid(candidate) && context != null && FederationDomainRegistryAccess.get(level).isCurrent(context)) {
            return false;
        }
        reject(PolicyEditorSessionState.Status.STALE_CONTEXT);
        return true;
    }

    public void rejectStaleRevision() {
        if (selection != null) {
            expectedRevision = PolicyService.get(level).revision(selection.key());
        }
        reject(PolicyEditorSessionState.Status.STALE_REVISION);
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

    public void nextMappingProvider() {
        if (!authorizeAction()) {
            mappingAcknowledgment = "rejected-session";
            return;
        }
        var providers = currentProviders();
        mappingProviderIndex = nextIndex(mappingProviderIndex, providers.size());
        mappingSlotIndex = 0;
        mappingLaneIndex = 0;
        mappingAcknowledgment = "ready";
    }

    public void nextMappingSlot() {
        if (!authorizeAction()) {
            mappingAcknowledgment = "rejected-session";
            return;
        }
        selectedProvider().ifPresentOrElse(entry -> {
            mappingSlotIndex = nextIndex(mappingSlotIndex, entry.provider().patternInventory().size());
            mappingAcknowledgment = "ready";
        }, () -> mappingAcknowledgment = "rejected-no-provider");
    }

    public void nextMappingLane() {
        if (!authorizeAction()) {
            mappingAcknowledgment = "rejected-session";
            return;
        }
        selectedProvider().ifPresentOrElse(entry -> {
            // A production Provider is mapped by Endpoint; its Lanes are allocated per mapped Endpoint.
            var choices = entry.controller().isPresent() ? currentEndpoints().size()
                    : entry.provider().nativeLanes().size();
            mappingLaneIndex = nextIndex(mappingLaneIndex, choices);
            mappingAcknowledgment = "ready";
        }, () -> mappingAcknowledgment = "rejected-no-provider");
    }

    public void toggleMapping() {
        if (!authorizeAction()) {
            mappingAcknowledgment = "rejected-session";
            return;
        }
        var entry = selectedProvider().orElse(null);
        if (entry == null) {
            mappingAcknowledgment = "rejected-no-provider";
            return;
        }
        if (entry.controller().isPresent()) {
            var endpoints = currentEndpoints();
            if (endpoints.isEmpty()) {
                mappingAcknowledgment = "rejected-no-endpoint";
                return;
            }
            try {
                var endpoint = endpoints.get(Math.floorMod(mappingLaneIndex, endpoints.size()));
                mappingAcknowledgment = entry.controller().orElseThrow()
                        .toggleEndpoint(entry.provider().mappingHandle(mappingSlotIndex), endpoint);
            } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                mappingAcknowledgment = "rejected-invalid-selection";
            }
            return;
        }
        ProviderTargetState targetState = entry.runtime().lastResolution().state();
        if (targetState == ProviderTargetState.POLICY_DENIED || targetState == ProviderTargetState.CLAIM_MISMATCH) {
            mappingAcknowledgment = "rejected-" + targetState.name().toLowerCase(java.util.Locale.ROOT);
            return;
        }
        try {
            PatternSlotHandle handle = entry.provider().mappingHandle(mappingSlotIndex);
            Set<Integer> replacement = new TreeSet<>(entry.provider().lanesForSlot(mappingSlotIndex));
            if (!replacement.add(mappingLaneIndex)) {
                replacement.remove(mappingLaneIndex);
            }
            mappingAcknowledgment = entry.provider().replaceMapping(handle, replacement)
                    ? "accepted-" + handle.slot() + "-" + handle.generation()
                    : "rejected-stale-slot";
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            mappingAcknowledgment = "rejected-invalid-selection";
        }
    }

    public void nextEndpoint() {
        if (!authorizeAction()) {
            return;
        }
        endpointIndex = nextIndex(endpointIndex, currentEndpoints().size());
    }

    public String graphSnapshotText() {
        return context == null ? space.controlnet.ae2federation.client.domain.FederationDomainGraphSnapshot.empty().encode()
                : FederationDomainGraphProjection.snapshot(level, context, selectedProvider()).encode();
    }

    public Component mappingProviderText() {
        return selectedProvider().map(entry -> Component.literal(shortId(FederationDomainGraphProjection.providerId(context, entry))))
                .orElseGet(() -> Component.literal("-"));
    }

    public Component mappingSelectionText() {
        var controller = selectedProvider().flatMap(ProviderObservationRegistry.Entry::controller);
        if (controller.isPresent()) {
            var endpoints = currentEndpoints();
            if (endpoints.isEmpty()) {
                return Component.translatable("ae2federation.ui.domain.mapping.selection_endpoint", mappingSlotIndex,
                        "-", "-");
            }
            var endpoint = endpoints.get(Math.floorMod(mappingLaneIndex, endpoints.size()));
            var mapped = controller.orElseThrow().endpointsForSlot(mappingSlotIndex)
                    .contains(endpoint.endpointIdentity());
            return Component.translatable("ae2federation.ui.domain.mapping.selection_endpoint", mappingSlotIndex,
                    shortId(FederationDomainGraphProjection.endpointId(context, endpoint)),
                    Component.translatable(mapped ? "ae2federation.ui.domain.mapping.mapped"
                            : "ae2federation.ui.domain.mapping.unmapped"));
        }
        return Component.translatable("ae2federation.ui.domain.mapping.selection", mappingSlotIndex, mappingLaneIndex);
    }

    public Component mappingStatusText() {
        return Component.translatable("ae2federation.ui.domain.mapping.status", mappingAcknowledgment);
    }

    public String mappingStatusCode() {
        return mappingAcknowledgment;
    }

    public Component endpointDetailText() {
        var endpoints = currentEndpoints();
        if (endpoints.isEmpty()) {
            return Component.translatable("ae2federation.ui.domain.endpoint.none");
        }
        var binding = endpoints.get(Math.floorMod(endpointIndex, endpoints.size()));
        var claim = binding.claimState();
        var owner = claim instanceof ClaimState.Owned owned
                ? shortId(owned.ownerIdentity().provider().id().value().toString())
                : "unclaimed";
        return Component.translatable("ae2federation.ui.domain.endpoint.detail",
                shortId(FederationDomainGraphProjection.endpointId(context, binding)), binding.runtime().configuredMode().name(),
                binding.runtime().generation(), claim.epoch().value(), owner, binding.lastClaimResultCode());
    }

    public Component entranceText() {
        return entrance.label(level);
    }

    public Component membersText() {
        if (selection == null) {
            return Component.translatable("ae2federation.ui.domain.members.pending");
        }
        var text = Component.translatable("ae2federation.ui.domain.members", selection.members().size());
        for (var member : selection.members()) {
            text.append("\n").append(Component.literal(shortId(member.value().toString())));
        }
        return text;
    }

    public Component consumerText() {
        return selectionText("ae2federation.ui.domain.consumer", true);
    }

    public Component providerText() {
        return selectionText("ae2federation.ui.domain.provider", false);
    }

    public Component ruleText() {
        if (selection == null) {
            return Component.translatable("ae2federation.ui.domain.rule.unavailable");
        }
        var key = selection.key();
        var configured = PolicyService.get(level).configured(key);
        var stateText = configured.map(record -> record.rule().enabled()
                ? Component.translatable("ae2federation.ui.domain.rule.on")
                : Component.translatable("ae2federation.ui.domain.rule.off"))
                .orElseGet(() -> Component.translatable("ae2federation.ui.domain.rule.unconfigured"));
        return Component.translatable("ae2federation.ui.domain.rule", key.capability().name(), stateText,
                expectedRevision.value());
    }

    public Component statusText() {
        return switch (state.status()) {
            case READY -> Component.translatable("ae2federation.ui.domain.status.ready");
            case PENDING -> Component.translatable("ae2federation.ui.domain.status.pending");
            case DISABLED -> Component.translatable("ae2federation.ui.domain.status.disabled", entrance.diagnostic());
            case ACCEPTED -> Component.translatable("ae2federation.ui.domain.status.accepted",
                    expectedRevision.value(), acknowledgmentId);
            case STALE_CONTEXT -> Component.translatable("ae2federation.ui.domain.status.stale_context");
            case STALE_REVISION -> Component.translatable("ae2federation.ui.domain.status.stale_revision",
                    expectedRevision.value());
        };
    }

    public String statusCode() {
        return state.status().name().toLowerCase(java.util.Locale.ROOT);
    }

    private List<ProviderObservationRegistry.Entry> currentProviders() {
        return currentFederationDomain().map(federationDomain -> FederationDomainGraphProjection.providerEntries(level, federationDomain)).orElse(List.of());
    }

    private Optional<ProviderObservationRegistry.Entry> selectedProvider() {
        var providers = currentProviders();
        return providers.isEmpty() ? Optional.empty()
                : Optional.of(providers.get(Math.floorMod(mappingProviderIndex, providers.size())));
    }

    private List<EndpointTargetBinding> currentEndpoints() {
        return currentFederationDomain().map(federationDomain -> FederationDomainGraphProjection.endpointEntries(level, federationDomain)).orElse(List.of());
    }

    private Optional<FederationDomainSnapshot> currentFederationDomain() {
        if (context == null || !FederationDomainRegistryAccess.get(level).isCurrent(context)) {
            return Optional.empty();
        }
        return FederationDomainRegistryAccess.get(level).federationDomain(context.federationDomainId());
    }

    private static int nextIndex(int current, int size) {
        return size == 0 ? 0 : Math.floorMod(current + 1, size);
    }

    private Component selectionText(String key, boolean consumer) {
        if (selection == null) {
            return Component.translatable(key, "-");
        }
        var selected = consumer ? selection.key().consumerNetworkId() : selection.key().providerNetworkId();
        return Component.translatable(key, shortId(selected.value().toString()));
    }

    private static String shortId(String value) {
        return value.length() <= 16 ? value : value.substring(0, 16);
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
        if (!isStillValid(player) || context == null || !FederationDomainRegistryAccess.get(level).isCurrent(context)) {
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

    private static Optional<FederationDomainSnapshot> bridgeFederationDomain(ServerLevel level, BridgeRightClickContext bridge) {
        if (bridge.reason() != BridgeOperationalReason.VALID || bridge.mainGrid() == null || bridge.outerGrid() == null) {
            return Optional.empty();
        }
        var main = FederationDomainRegistryAccess.confirmedNetworkId(bridge.mainGrid());
        var outer = FederationDomainRegistryAccess.confirmedNetworkId(bridge.outerGrid());
        if (main.isEmpty() || outer.isEmpty()) {
            return Optional.empty();
        }
        var source = new FederationDomainSourceId("bridge:" + FederationDomainRegistryAccess.nodeId(level, bridge.position()) + ":"
                + bridge.side().getSerializedName());
        return FederationDomainRegistryAccess.get(level).federationDomain(FederationDomainId.direct(source))
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
