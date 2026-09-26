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
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
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
    private NetworkId initialGraphNetwork;
    private String navigationReceipt = "";
    private DeviceDomainAvailability deviceDomainAvailability;
    private space.controlnet.ae2federation.processing.claim.EndpointIdentity mappingEndpointSelection;
    private String mappingAcknowledgment = "ready";
    private @org.jetbrains.annotations.Nullable space.controlnet.ae2federation.processing.provider.ProviderMappingController.ReleaseConfirmation pendingRelease;

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
        var session = new FederationDomainPolicySession(player, entrance, bridgeFederationDomain(player.serverLevel(), bridge),
                PolicyEditorSessionState.Status.DISABLED);
        session.initialGraphNetwork = bridge.mainGrid() == null ? null
                : FederationDomainRegistryAccess.confirmedNetworkId(bridge.mainGrid()).orElse(null);
        return session;
    }

    public static FederationDomainPolicySession forDevice(ServerPlayer player, BlockPos position) {
        var level = player.serverLevel();
        var entity = level.getBlockEntity(position);
        boolean provider = entity instanceof space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
        var network = entity instanceof appeng.blockentity.grid.AENetworkedBlockEntity networked
                && networked.getMainNode().getNode() != null
                ? FederationDomainRegistryAccess.confirmedNetworkId(networked.getMainNode().getNode().getGrid())
                : Optional.<space.controlnet.ae2federation.identity.NetworkId>empty();
        var domains = FederationDomainRegistryAccess.get(level).snapshot().federationDomains().values().stream()
                // A device may also have a singleton topology record; it cannot host this domain editor.
                .filter(domain -> domain.memberships().size() >= 2)
                .filter(domain -> network.filter(domain.memberships()::containsKey).isPresent()).toList();
        var nodeId = FederationDomainRegistryAccess.nodeId(level, position);
        var attached = domains.stream().filter(domain -> domain.nodes().contains(nodeId)).toList();
        var candidates = attached.isEmpty() ? domains : attached;
        var session = new FederationDomainPolicySession(player, new DevicePolicyEntrance(position, provider),
                candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty(), PolicyEditorSessionState.Status.DISABLED);
        session.deviceDomainAvailability = DeviceDomainAvailability.classify(network.isPresent(), candidates.size());
        if (provider) {
            var entries = session.currentProviders();
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).controller().orElse(null) == entity) session.mappingProviderIndex = i;
            }
        } else if (entity instanceof space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity endpoint) {
            var binding = endpoint.binding();
            session.endpointIndex = binding == null ? 0 : Math.max(0, session.currentEndpoints().indexOf(binding));
        }
        return session;
    }

    public void returnToProvider() {
        if (entrance instanceof DevicePolicyEntrance device && device.provider() && isStillValid(player)
                && level.getBlockEntity(device.position()) instanceof space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity provider) {
            provider.openMenu(player, appeng.menu.locator.MenuLocators.forBlockEntity(provider));
        }
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

    /** Resolves a direct selection against the live, authorized domain rather than a client list index. */
    public boolean selectTarget(String target) {
        if (!authorizeAction()) return false;
        var split = target.indexOf(':');
        if (split < 1) return false;
        var group = target.substring(0, split);
        var id = target.substring(split + 1);
        if (group.equals("endpoint_mapping") || group.equals("endpoint_policy")) {
            return navigateEndpoint(group, id, target);
        } else if (group.equals("policy")) {
            var fields = id.split("/", -1);
            if (fields.length != 3) return false;
            try {
                var consumer = new space.controlnet.ae2federation.identity.NetworkId(java.util.UUID.fromString(fields[0]));
                var provider = new space.controlnet.ae2federation.identity.NetworkId(java.util.UUID.fromString(fields[1]));
                var capability = PolicyCapability.valueOf(fields[2]);
                var replacement = new PolicyEditorSelection(selection.members(), selection.members().indexOf(consumer),
                        selection.members().indexOf(provider), capability.ordinal());
                if (PolicyService.get(level).configured(replacement.key()).isEmpty()) return false;
                changeSelection(old -> replacement);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        } else if (group.equals("consumer") || group.equals("provider")) {
            int index = -1;
            for (int i = 0; i < selection.members().size(); i++) {
                if (selection.members().get(i).value().toString().equals(id)) index = i;
            }
            if (index < 0 || index == (group.equals("consumer") ? selection.providerIndex() : selection.consumerIndex())) return false;
            final int selected = index;
            changeSelection(old -> new PolicyEditorSelection(old.members(),
                    group.equals("consumer") ? selected : old.consumerIndex(),
                    group.equals("provider") ? selected : old.providerIndex(), old.capabilityIndex()));
        } else if (group.equals("capability")) {
            PolicyCapability capability;
            try { capability = PolicyCapability.valueOf(id); } catch (IllegalArgumentException exception) { return false; }
            changeSelection(old -> new PolicyEditorSelection(old.members(), old.consumerIndex(), old.providerIndex(), capability.ordinal()));
        } else if (group.equals("mapping_provider")) {
            var providers = currentProviders();
            int index = -1;
            for (int i = 0; i < providers.size(); i++) {
                if (FederationDomainGraphProjection.providerId(context, providers.get(i)).equals(id)) index = i;
            }
            if (index < 0) return false;
            mappingProviderIndex = index;
            mappingSlotIndex = 0;
            mappingLaneIndex = 0;
            mappingEndpointSelection = null;
        } else if (group.equals("slot")) {
            int slot;
            try { slot = Integer.parseInt(id); } catch (NumberFormatException exception) { return false; }
            var provider = selectedProvider();
            if (provider.isEmpty() || slot < 0 || slot >= provider.get().provider().patternInventory().size()) return false;
            mappingSlotIndex = slot;
        } else if (group.equals("target")) {
            var endpoints = mappingEndpoints();
            int index = -1;
            for (int i = 0; i < endpoints.size(); i++) {
                if (endpointChoiceId(endpoints.get(i)).equals(id)) index = i;
            }
            if (index < 0) return false;
            mappingLaneIndex = index;
            mappingEndpointSelection = endpoints.get(index);
        } else if (group.equals("endpoint")) {
            var endpoints = currentEndpoints();
            int index = -1;
            for (int i = 0; i < endpoints.size(); i++) {
                if (FederationDomainGraphProjection.endpointId(context, endpoints.get(i)).equals(id)) index = i;
            }
            if (index < 0) return false;
            endpointIndex = index;
        } else {
            return false;
        }
        mappingAcknowledgment = "ready";
        return true;
    }

    private static String endpointChoiceId(space.controlnet.ae2federation.processing.claim.EndpointIdentity endpoint) {
        return endpoint.id().value() + ":" + endpoint.instanceEpoch().value();
    }

    /** Presentation choices are independent of the graph wire format and retain stable server identities. */
    public String workspaceChoices() {
        refreshPreparedRelease();
        var root = new com.google.gson.JsonObject();
        root.addProperty("navigationReceipt", navigationReceipt);
        root.addProperty("scope", context == null && deviceDomainAvailability != null ? deviceDomainAvailability.key() : "domain");
        root.addProperty("initialPage", entrance instanceof DevicePolicyEntrance device ? (device.provider() ? "mapping" : "diagnostics") : "overview");
        root.addProperty("returnProvider", entrance instanceof DevicePolicyEntrance device && device.provider());
        if (context == null && entrance instanceof DevicePolicyEntrance device
                && level.getBlockEntity(device.position()) instanceof space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity) {
            root.addProperty("localEndpointPosition", device.position().toShortString());
        }
        if (context != null && initialGraphNetwork != null
                && currentFederationDomain().filter(domain -> domain.memberships().containsKey(initialGraphNetwork)).isPresent()) {
            root.addProperty("initialGraphFocus", space.controlnet.ae2federation.observability.id.MemberId
                    .forNetwork(context.federationDomainId(), initialGraphNetwork).value());
        }
        var selected = new com.google.gson.JsonObject();
        root.add("selected", selected);
        var rules = new com.google.gson.JsonArray();
        root.add("rules", rules);
        for (var group : new String[] {"consumer", "provider", "capability", "mapping_provider", "slot", "target", "endpoint"}) {
            root.add(group, new com.google.gson.JsonArray());
        }
        if (selection == null || currentFederationDomain().isEmpty()) return root.toString();
        space.controlnet.ae2federation.persistence.PolicySavedData.get(level).snapshot().entries().values().stream()
                .filter(space.controlnet.ae2federation.policy.PolicyRecord.Configured.class::isInstance)
                .map(space.controlnet.ae2federation.policy.PolicyRecord.Configured.class::cast)
                .filter(record -> selection.members().contains(record.key().consumerNetworkId())
                        && selection.members().contains(record.key().providerNetworkId())
                        && !record.key().consumerNetworkId().equals(record.key().providerNetworkId()))
                .sorted(java.util.Comparator.comparing(record -> record.key().toString()))
                .forEach(record -> {
                    var row = new com.google.gson.JsonObject();
                    row.addProperty("consumer", record.key().consumerNetworkId().value().toString());
                    row.addProperty("provider", record.key().providerNetworkId().value().toString());
                    row.addProperty("capability", record.key().capability().name());
                    row.addProperty("enabled", record.rule().enabled());
                    row.addProperty("revision", record.revision().value());
                    rules.add(row);
                });
        for (var member : selection.members()) {
            var id = member.value().toString();
            if (!member.equals(selection.key().providerNetworkId())) addChoice(root, "consumer", id, shortId(id));
            if (!member.equals(selection.key().consumerNetworkId())) addChoice(root, "provider", id, shortId(id));
        }
        selected.addProperty("consumer", selection.key().consumerNetworkId().value().toString());
        selected.addProperty("provider", selection.key().providerNetworkId().value().toString());
        for (var capability : PolicyCapability.values()) addChoice(root, "capability", capability.name(), capability.name());
        selected.addProperty("capability", selection.key().capability().name());
        for (var provider : currentProviders()) {
            var id = FederationDomainGraphProjection.providerId(context, provider);
            var choice = addChoice(root, "mapping_provider", id, shortId(id));
            if (provider.controller().orElse(null) instanceof net.minecraft.world.level.block.entity.BlockEntity entity) {
                choice.addProperty("position", entity.getBlockPos().toShortString());
            }
        }
        if (pendingRelease != null && state.editingAllowed()) {
            var release = new com.google.gson.JsonObject();
            release.addProperty("endpoint", pendingRelease.endpoint().id().value().toString());
            release.addProperty("epoch", pendingRelease.epoch().value());
            release.addProperty("lane", pendingRelease.lane());
            release.addProperty("position", currentEndpoints().stream()
                    .filter(binding -> binding.endpointIdentity().equals(pendingRelease.endpoint()))
                    .map(binding -> binding.runtime().position().toShortString()).findFirst().orElse(""));
            root.add("release", release);
        }
        selectedProvider().ifPresent(entry -> {
            selected.addProperty("mapping_provider", FederationDomainGraphProjection.providerId(context, entry));
            var inventory = entry.provider().patternInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                var stack = inventory.getStackInSlot(slot);
                var choice = addChoice(root, "slot", Integer.toString(slot), stack.isEmpty() ? "" : stack.getHoverName().getString());
                var details = stack.isEmpty() ? null : appeng.api.crafting.PatternDetailsHelper.decodePattern(stack, level);
                var ops = level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
                var outputs = details == null ? java.util.List.<appeng.api.stacks.GenericStack>of() : details.getOutputs();
                var inputs = details == null ? java.util.List.<appeng.api.stacks.GenericStack>of()
                        : java.util.Arrays.stream(details.getInputs()).map(input -> new appeng.api.stacks.GenericStack(
                                input.getPossibleInputs()[0].what(),
                                Math.multiplyExact(input.getPossibleInputs()[0].amount(), input.getMultiplier()))).toList();
                choice.add("outputs", appeng.api.stacks.GenericStack.CODEC.listOf().encodeStart(ops, outputs).getOrThrow());
                choice.add("inputs", appeng.api.stacks.GenericStack.CODEC.listOf().encodeStart(ops, inputs).getOrThrow());
                choice.addProperty("empty", stack.isEmpty());
                choice.addProperty("mapped", entry.provider().lanesForSlot(slot).size());
            }
            selected.addProperty("slot", Integer.toString(mappingSlotIndex));
            var endpoints = mappingEndpoints();
            for (var endpoint : endpoints) {
                var id = endpointChoiceId(endpoint);
                var choice = addChoice(root, "target", id, shortId(endpoint.id().value().toString()));
                var binding = currentEndpoints().stream().filter(candidate -> candidate.endpointIdentity().equals(endpoint))
                        .findFirst().orElse(null);
                choice.addProperty("state", mappingTargetState(entry, endpoint, binding));
                if (binding != null) {
                    choice.addProperty("position", binding.runtime().position().toShortString());
                    binding.claimState().owner().ifPresent(owner -> {
                        choice.addProperty("owner", owner.provider().id().value().toString());
                        choice.addProperty("ownerInstance", owner.provider().instanceEpoch().value());
                    });
                }
            }
            if (!endpoints.isEmpty()) selected.addProperty("target", endpointChoiceId(selectedMappingEndpoint(endpoints)));
        });
        var endpoints = currentEndpoints();
        for (var endpoint : endpoints) {
            var id = FederationDomainGraphProjection.endpointId(context, endpoint);
            var choice = addChoice(root, "endpoint", id, shortId(id));
            choice.addProperty("position", endpoint.runtime().position().toShortString());
            var navigationOwner = navigationOwner(endpoint);
            choice.addProperty("mappingNavigation", navigationOwner.isPresent());
            choice.addProperty("policyNavigation", navigationOwner.flatMap(entry -> endpointPolicySelection(entry, endpoint)).isPresent());
            choice.addProperty("nodeReady", endpoint.subnetNode().isActive() && endpoint.subnetNode().hasGridBooted());
            choice.addProperty("configuredMode", endpoint.runtime().configuredMode().name());
            choice.addProperty("runtimeMode", endpoint.runtime().mode().map(mode ->
                    mode instanceof space.controlnet.ae2federation.processing.endpoint.EndpointModeGeneration.Local
                            ? "LOCAL" : "FEDERATED").orElse("UNBOUND"));
            choice.addProperty("claimResult", endpoint.lastClaimResultCode());
            choice.addProperty("endpointIdentity", endpoint.endpointIdentity().id().value().toString());
            endpoint.claimState().owner().ifPresent(owner -> {
                choice.addProperty("owner", owner.provider().id().value().toString());
                choice.addProperty("ownerInstance", owner.provider().instanceEpoch().value());
            });
        }
        if (!endpoints.isEmpty()) selected.addProperty("endpoint", FederationDomainGraphProjection.endpointId(context,
                endpoints.get(Math.floorMod(endpointIndex, endpoints.size()))));
        return root.toString();
    }

    private Optional<ProviderObservationRegistry.Entry> navigationOwner(EndpointTargetBinding endpoint) {
        var owner = endpoint.claimState().owner();
        return owner.flatMap(identity -> currentProviders().stream()
                .filter(entry -> entry.controller().isPresent() && entry.identity().equals(identity.provider())).findFirst());
    }

    private Optional<PolicyEditorSelection> endpointPolicySelection(ProviderObservationRegistry.Entry owner,
            EndpointTargetBinding endpoint) {
        if (selection == null) return Optional.empty();
        var consumer = FederationDomainRegistryAccess.confirmedNetworkId(owner.provider().getGrid()).orElse(null);
        var provider = FederationDomainRegistryAccess.confirmedNetworkId(endpoint.subnetNode().getGrid()).orElse(null);
        if (consumer == null || provider == null) return Optional.empty();
        int consumerIndex = selection.members().indexOf(consumer);
        int providerIndex = selection.members().indexOf(provider);
        if (consumerIndex < 0 || providerIndex < 0 || consumerIndex == providerIndex) return Optional.empty();
        return Optional.of(new PolicyEditorSelection(selection.members(), consumerIndex, providerIndex,
                PolicyCapability.PROCESSING.ordinal()));
    }

    private boolean navigateEndpoint(String group, String id, String receipt) {
        int separator = id.lastIndexOf('/');
        if (separator < 1) return false;
        try { java.util.UUID.fromString(id.substring(separator + 1)); }
        catch (IllegalArgumentException exception) { return false; }
        var endpointId = id.substring(0, separator);
        var endpoint = currentEndpoints().stream()
                .filter(binding -> FederationDomainGraphProjection.endpointId(context, binding).equals(endpointId))
                .findFirst().orElse(null);
        if (endpoint == null) return false;
        var owner = navigationOwner(endpoint).orElse(null);
        if (owner == null) return false;
        if (group.equals("endpoint_policy")) {
            var replacement = endpointPolicySelection(owner, endpoint).orElse(null);
            if (replacement == null) return false;
            changeSelection(old -> replacement);
        } else {
            var providers = currentProviders();
            int index = providers.indexOf(owner);
            if (index < 0) return false;
            int slot = java.util.stream.IntStream.range(0, owner.provider().patternInventory().size())
                    .filter(candidate -> owner.controller().orElseThrow().endpointsForSlot(candidate).contains(endpoint.endpointIdentity()))
                    .findFirst().orElse(0);
            mappingProviderIndex = index;
            mappingSlotIndex = slot;
            mappingEndpointSelection = endpoint.endpointIdentity();
            mappingLaneIndex = 0;
        }
        navigationReceipt = receipt;
        mappingAcknowledgment = "ready";
        return true;
    }

    private String mappingTargetState(ProviderObservationRegistry.Entry provider,
            space.controlnet.ae2federation.processing.claim.EndpointIdentity endpoint, EndpointTargetBinding binding) {
        if (binding == null) return "unobserved";
        var owner = binding.claimState().owner();
        if (owner.isPresent() && !owner.orElseThrow().provider().equals(provider.identity())) return "occupied";
        var controller = provider.controller().orElse(null);
        boolean mapped = controller != null && controller.endpointsForSlot(mappingSlotIndex).contains(endpoint);
        boolean retained = controller != null && controller.retained(endpoint);
        if (owner.isEmpty() && (mapped || retained)) return "changed";
        if (mapped) return "mapped";
        if (retained) return "retained";
        if (owner.isPresent()) return "owned";
        if (binding.runtime().configuredMode() != space.controlnet.ae2federation.ae2.processing.endpoint.EndpointMode.FEDERATED)
            return "local";
        return "unclaimed";
    }

    private static com.google.gson.JsonObject addChoice(com.google.gson.JsonObject root, String group, String id, String label) {
        var choice = new com.google.gson.JsonObject();
        choice.addProperty("id", id);
        choice.addProperty("label", label);
        root.getAsJsonArray(group).add(choice);
        return choice;
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
            var choices = entry.controller().isPresent() ? mappingEndpoints().size()
                    : entry.provider().nativeLanes().size();
            if (entry.controller().isPresent()) {
                var endpoints = mappingEndpoints();
                mappingLaneIndex = nextIndex(mappingEndpointSelection == null ? mappingLaneIndex : endpoints.indexOf(mappingEndpointSelection), choices);
                mappingEndpointSelection = endpoints.isEmpty() ? null : endpoints.get(mappingLaneIndex);
            } else {
                mappingLaneIndex = nextIndex(mappingLaneIndex, choices);
            }
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
            var endpoints = mappingEndpoints();
            if (endpoints.isEmpty()) {
                mappingAcknowledgment = "rejected-no-endpoint";
                return;
            }
            try {
                var endpoint = selectedMappingEndpoint(endpoints);
                mappingAcknowledgment = entry.controller().orElseThrow()
                        .toggleEndpoint(entry.provider().mappingHandle(mappingSlotIndex), currentEndpoints().stream()
                                .filter(binding -> binding.endpointIdentity().equals(endpoint)).findFirst().orElseThrow());
            } catch (IllegalArgumentException | IndexOutOfBoundsException | java.util.NoSuchElementException exception) {
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
        } catch (IllegalArgumentException | IndexOutOfBoundsException | java.util.NoSuchElementException exception) {
            mappingAcknowledgment = "rejected-invalid-selection";
        }
    }

    /** Prepares a target-specific confirmation or executes the matching, still-current prepared release. */
    public void releaseEndpoint() {
        if (!authorizeAction()) {
            mappingAcknowledgment = "rejected-session";
            return;
        }
        var controller = selectedProvider().flatMap(ProviderObservationRegistry.Entry::controller).orElse(null);
        var endpoints = mappingEndpoints();
        if (controller == null || endpoints.isEmpty()) {
            pendingRelease = null;
            mappingAcknowledgment = controller == null ? "rejected-no-provider" : "rejected-no-endpoint";
            return;
        }
        var endpoint = selectedMappingEndpoint(endpoints);
        if (!controller.retained(endpoint)) {
            pendingRelease = null;
            mappingAcknowledgment = "rejected-not-retained";
        } else if (!controller.releaseConfirmation(endpoint).filter(value -> value.equals(pendingRelease)).isPresent()) {
            pendingRelease = controller.releaseConfirmation(endpoint).orElse(null);
            mappingAcknowledgment = "confirm-release";
        } else {
            pendingRelease = null;
            mappingAcknowledgment = controller.releaseEndpoint(endpoint);
        }
    }

    public void cancelRelease() {
        pendingRelease = null;
        mappingAcknowledgment = "ready";
    }

    public void clearPendingRelease() {
        pendingRelease = null;
    }

    /** A prepared confirmation must disappear when another action changes its binding or authority. */
    private void refreshPreparedRelease() {
        if (pendingRelease == null) return;
        if (!authorizeAction()) {
            pendingRelease = null;
            mappingAcknowledgment = "rejected-session";
            return;
        }
        var controller = selectedProvider().flatMap(ProviderObservationRegistry.Entry::controller).orElse(null);
        if (controller == null || !controller.retained(pendingRelease.endpoint())
                || controller.releaseConfirmation(pendingRelease.endpoint()).filter(pendingRelease::equals).isEmpty()) {
            pendingRelease = null;
            mappingAcknowledgment = "rejected-claim-changed";
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
            var endpoints = mappingEndpoints();
            if (endpoints.isEmpty()) {
                return Component.translatable("ae2federation.ui.domain.mapping.selection_endpoint", mappingSlotIndex,
                        "-", "-");
            }
            var endpoint = selectedMappingEndpoint(endpoints);
            var mapped = controller.orElseThrow().endpointsForSlot(mappingSlotIndex)
                    .contains(endpoint);
            var state = mapped ? "ae2federation.ui.domain.mapping.mapped"
                    : controller.orElseThrow().retained(endpoint)
                            ? "ae2federation.ui.domain.mapping.retained" : "ae2federation.ui.domain.mapping.unmapped";
            return Component.translatable("ae2federation.ui.domain.mapping.selection_endpoint", mappingSlotIndex,
                    shortId(endpoint.id().value().toString()),
                    Component.translatable(state));
        }
        return Component.translatable("ae2federation.ui.domain.mapping.selection", mappingSlotIndex, mappingLaneIndex);
    }

    public Component mappingStatusText() {
        if (context == null && entrance instanceof DevicePolicyEntrance) {
            return statusText();
        }
        var feedback = MappingFeedback.fromCode(mappingAcknowledgment);
        return Component.translatable(feedback.translationKey(), feedback.arguments().toArray());
    }

    public String mappingStatusCode() {
        return mappingAcknowledgment;
    }

    public Component endpointDetailText() {
        var binding = inspectedEndpoint();
        if (binding == null) return Component.translatable("ae2federation.ui.domain.endpoint.none");
        var runtime = binding.runtime();
        var actual = runtime.mode().map(mode -> mode instanceof space.controlnet.ae2federation.processing.endpoint.EndpointModeGeneration.Local
                ? "LOCAL" : "FEDERATED").orElse("UNBOUND");
        Component nativeNetwork = FederationDomainRegistryAccess.confirmedNetworkId(binding.subnetNode().getGrid())
                .<Component>map(id -> Component.literal(id.value().toString()))
                .orElseGet(() -> Component.translatable("ae2federation.ui.workspace.network_unconfirmed"));
        return Component.translatable("ae2federation.ui.workspace.endpoint_runtime", runtime.position().toShortString(),
                endpointMode(runtime.configuredMode().name()), endpointMode(actual),
                Component.translatable("ae2federation.ui.workspace.face." + runtime.federationFace().getSerializedName()),
                Component.translatable("ae2federation.ui.workspace.return_binding." +
                        (runtime.itemReturnContext().isPresent() || runtime.fluidReturnContext().isPresent() ? "present" : "absent")),
                Component.translatableWithFallback("ae2federation.ui.workspace.claim_result." + binding.lastClaimResultCode(),
                        binding.lastClaimResultCode()))
                .append("\n\n").append(Component.translatable("ae2federation.ui.workspace.native_network", nativeNetwork));
    }

    public Component endpointIdentityText() {
        var binding = inspectedEndpoint();
        if (binding == null) return Component.translatable("ae2federation.ui.domain.endpoint.none");
        var claim = binding.claimState();
        Component owner = claim instanceof ClaimState.Owned owned
                ? Component.literal(owned.ownerIdentity().provider().id().value().toString())
                : Component.translatable("ae2federation.ui.workspace.unclaimed");
        var details = Component.translatable("ae2federation.ui.workspace.endpoint_identity",
                binding.endpointIdentity().id().value().toString(), binding.endpointIdentity().instanceEpoch().value(),
                owner, claim.epoch().value(), binding.runtime().generation(), binding.lastClaimResultCode());
        if (claim instanceof ClaimState.Owned owned) {
            details.append("\n").append(Component.translatable("ae2federation.ui.workspace.owner_instance_epoch",
                    owned.ownerIdentity().provider().instanceEpoch().value()));
        }
        return details;
    }

    private static Component endpointMode(String mode) {
        return Component.translatable("ae2federation.ui.workspace.endpoint_mode." + mode);
    }

    private @org.jetbrains.annotations.Nullable EndpointTargetBinding inspectedEndpoint() {
        var endpoints = currentEndpoints();
        var local = entrance instanceof DevicePolicyEntrance device && !device.provider()
                && level.getBlockEntity(device.position()) instanceof space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity endpoint
                ? endpoint.binding() : null;
        return endpoints.isEmpty() ? local : endpoints.get(Math.floorMod(endpointIndex, endpoints.size()));
    }

    public Component entranceText() {
        return entrance.label(level);
    }

    public Component membersText() {
        if (context == null && deviceDomainAvailability != null) return Component.translatable("ae2federation.ui.workspace.local_scope");
        if (selection == null) {
            return Component.translatable("ae2federation.ui.domain.members.pending");
        }
        return Component.translatable("ae2federation.ui.domain.members", selection.members().size());
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
        return Component.translatable("ae2federation.ui.domain.rule",
                Component.translatable("ae2federation.ui.workspace.capability." + key.capability().name().toLowerCase(java.util.Locale.ROOT)), stateText,
                configured.map(record -> record.revision().value()).orElseGet(() -> PolicyService.get(level).revision(key).value()))
                .append("\n\n").append(runtimeObservationText(key, configured.map(record -> record.rule()).orElse(null)));
    }

    private Component runtimeObservationText(PolicyKey key,
            PolicyRule rule) {
        String prefix = "ae2federation.ui.domain.runtime.";
        if (rule == null) return Component.translatable(prefix + "unconfigured");
        if (!rule.enabled()) return Component.translatable(prefix + "off");
        var required = switch (key.capability()) {
            case CRAFTING -> List.of(PolicyOperation.REQUEST);
            case PROCESSING -> List.of(PolicyOperation.EXECUTE, PolicyOperation.SUPPLY);
            case ME_POWER -> List.of(PolicyOperation.SUPPLY);
            case STORAGE -> List.<PolicyOperation>of();
        };
        for (var operation : required) {
            if (!rule.operations().contains(operation)) {
                return Component.translatable(prefix + "operation_missing",
                        Component.translatable(prefix + "operation." + operation.name().toLowerCase(java.util.Locale.ROOT)));
            }
        }
        if (key.capability() == PolicyCapability.PROCESSING) {
            return Component.translatable(prefix + "on_dispatch");
        }
        boolean published = switch (key.capability()) {
            case STORAGE -> space.controlnet.ae2federation.storage.mount.StorageMountService.hasPublishedBinding(level, key);
            case CRAFTING -> space.controlnet.ae2federation.crafting.binding.CraftingBindingService.hasPublishedBinding(level, key);
            case ME_POWER -> space.controlnet.ae2federation.energy.EnergyBindingService.hasPublishedBinding(level, key);
            case PROCESSING -> throw new IllegalStateException("Processing has no persistent capability binding");
        };
        var text = Component.translatable(prefix + (published ? "published" : "unobserved"));
        var backendDiagnostic = switch (key.capability()) {
            case CRAFTING -> space.controlnet.ae2federation.crafting.binding.CraftingBindingService.lastDiagnostic(level, key);
            case ME_POWER -> space.controlnet.ae2federation.energy.EnergyBindingService.lastDiagnostic(level, key);
            default -> Optional.<space.controlnet.ae2federation.policy.BindingDiagnostic>empty();
        };
        if (!published) backendDiagnostic.ifPresent(diagnostic -> text.append("\n").append(
                Component.translatable(prefix + "backend_reason", Component.translatable(prefix + "backend."
                        + diagnostic.reason().name().toLowerCase(java.util.Locale.ROOT)))));
        if (key.capability() == PolicyCapability.STORAGE && !published) {
            var diagnostic = space.controlnet.ae2federation.storage.mount.StorageMountService.lastDiagnosticIfPresent(level, key);
            if (diagnostic != null) text.append("\n").append(Component.translatable(prefix + "storage_reason",
                    Component.translatable(prefix + "provenance." + diagnostic.name().toLowerCase(java.util.Locale.ROOT))));
        }
        return text;
    }

    public Component statusText() {
        if (context == null && deviceDomainAvailability != null) {
            return Component.translatable("ae2federation.ui.workspace.device_scope." + deviceDomainAvailability.key());
        }
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

    private space.controlnet.ae2federation.processing.claim.EndpointIdentity selectedMappingEndpoint(
            List<space.controlnet.ae2federation.processing.claim.EndpointIdentity> endpoints) {
        if (mappingEndpointSelection == null || !endpoints.contains(mappingEndpointSelection)) {
            mappingEndpointSelection = endpoints.get(Math.floorMod(mappingLaneIndex, endpoints.size()));
        }
        return mappingEndpointSelection;
    }

    private List<space.controlnet.ae2federation.processing.claim.EndpointIdentity> mappingEndpoints() {
        var choices = new java.util.LinkedHashSet<space.controlnet.ae2federation.processing.claim.EndpointIdentity>();
        currentEndpoints().forEach(binding -> choices.add(binding.endpointIdentity()));
        selectedProvider().flatMap(ProviderObservationRegistry.Entry::controller)
                .ifPresent(controller -> choices.addAll(controller.retainedEndpoints()));
        return List.copyOf(choices);
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
