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
import space.controlnet.ae2federation.policy.RuleLinks;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.provider.PatternSlotHandle;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;

public final class FederationDomainPolicySession {
    private static final double MAX_DISTANCE_SQUARED = 64.0;
    private static final long OVERVIEW_INTERVAL_TICKS = 20;

    private final ServerPlayer player;
    private final ServerLevel level;
    private final FederationDomainPolicyEntrance entrance;
    private final FederationDomainReference context;
    /** The entrance's own domain node when the opened domain contained it, else null (a Bridge or a member device). */
    private final @org.jetbrains.annotations.Nullable space.controlnet.ae2federation.domain.FederationDomainNodeId contextNode;
    private final FederationDomainPolicyObservation observation;
    private PolicyEditorSelection selection;
    private PolicyRevision expectedRevision = PolicyRevision.NONE;
    private final PolicyEditorSessionState state;
    private String acknowledgmentId = "";
    /** Rules the last accepted switch changed along with the selected one, named in the status line. */
    private List<RuleLinks.Change> linkedChanges = List.of();
    private int mappingProviderIndex;
    /** The Provider being edited, so a Provider added or removed elsewhere in the domain does not change it. */
    private space.controlnet.ae2federation.processing.provider.ProviderIdentity mappingProviderIdentity;
    private int mappingSlotIndex;
    private int mappingLaneIndex;
    private int endpointIndex;
    private NetworkId initialGraphNetwork;
    /** The id of an Endpoint opened from its own block with no domain to show it in. */
    private static final String LOCAL_ENDPOINT = "local";
    private String navigationReceipt = "";
    private DeviceDomainAvailability deviceDomainAvailability;
    private space.controlnet.ae2federation.processing.claim.EndpointIdentity mappingEndpointSelection;
    private String mappingAcknowledgment = "ready";
    private boolean mappingAllowed;
    private String overviewText = "";
    private static final long FLOW_INTERVAL_TICKS = 10;
    private static final int MAX_VIA_NODES = 3;
    /** Providers stacked on the processing canvas; more would push the selected one out of reach. */
    private static final int MAX_PROCESSING_PROVIDERS = 6;
    private static final int MAX_RELATED_NETWORKS = 64;
    private String flowText = "";
    private long flowTick;
    /** Networks of other domains that share a member with this one, from the last choices build; read-only here. */
    private java.util.Set<space.controlnet.ae2federation.identity.NetworkId> relatedNetworks = java.util.Set.of();
    private long overviewTick;
    private List<NetworkId> overviewMembers = List.of();
    private @org.jetbrains.annotations.Nullable space.controlnet.ae2federation.processing.provider.ProviderMappingController.ReleaseConfirmation pendingRelease;

    private FederationDomainPolicySession(ServerPlayer player, FederationDomainPolicyEntrance entrance, Optional<FederationDomainSnapshot> federationDomain,
            PolicyEditorSessionState.Status emptyState) {
        this.player = player;
        level = player.serverLevel();
        this.entrance = entrance;
        context = federationDomain.map(FederationDomainSnapshot::reference).orElse(null);
        var entranceNode = FederationDomainRegistryAccess.nodeId(level, entrance.position());
        contextNode = federationDomain.filter(snapshot -> snapshot.nodes().contains(entranceNode)).map(ignored -> entranceNode)
                .orElse(null);
        observation = context == null ? null : new FederationDomainPolicyObservation(this, player, context);
        var members = federationDomain.map(snapshot -> List.copyOf(snapshot.memberships().keySet())).orElse(List.of());
        selection = members.size() >= 2 ? PolicyEditorSelection.initial(members) : null;
        var initialState = selection == null ? emptyState : PolicyEditorSessionState.Status.READY;
        state = new PolicyEditorSessionState(initialState, selection != null && entrance.enabled());
        // Mapping needs no rule pair: a domain may hold one ME network and its Endpoints.
        mappingAllowed = context != null && entrance.enabled();
        refreshExpectedRevision();
    }

    public static FederationDomainPolicySession forRouter(ServerPlayer player, BlockPos position) {
        var level = player.serverLevel();
        var nodeId = FederationDomainRegistryAccess.nodeId(level, position);
        var federationDomains = FederationDomainRegistryAccess.get(level).federationDomains().stream()
                .filter(snapshot -> snapshot.nodes().contains(nodeId)).toList();
        var current = federationDomains.size() == 1 ? Optional.of(federationDomains.getFirst()) : Optional.<FederationDomainSnapshot>empty();
        return new FederationDomainPolicySession(player, new RouterPolicyEntrance(position), current,
                PolicyEditorSessionState.Status.PENDING);
    }

    public static FederationDomainPolicySession forBridge(ServerPlayer player, BridgeRightClickContext bridge) {
        var identityConfirmed = bridge.mainGrid() != null && bridge.outerGrid() != null
                && FederationDomainRegistryAccess.confirmedNetworkId(bridge.mainGrid()).isPresent()
                && FederationDomainRegistryAccess.confirmedNetworkId(bridge.outerGrid()).isPresent();
        var entrance = new BridgePolicyEntrance(bridge.position(), bridge.side(), bridge.reason(), identityConfirmed);
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
        var nodeId = FederationDomainRegistryAccess.nodeId(level, position);
        List<FederationDomainSnapshot> candidates;
        if (entity instanceof space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity || provider) {
            // An Endpoint or a Provider belongs to the domain its Federation face joins; an Endpoint's own subnet is not
            // a member of it, and a Provider's network may be a member of other domains too.
            candidates = FederationDomainRegistryAccess.get(level).federationDomains().stream()
                    .filter(domain -> domain.nodes().contains(nodeId) && !domain.memberships().isEmpty()).toList();
        } else {
            var domains = FederationDomainRegistryAccess.get(level).federationDomains().stream()
                    // A device may also have a singleton topology record; it cannot host this domain editor.
                    .filter(domain -> domain.memberships().size() >= 2)
                    .filter(domain -> network.filter(domain.memberships()::containsKey).isPresent()).toList();
            var attached = domains.stream().filter(domain -> domain.nodes().contains(nodeId)).toList();
            candidates = attached.isEmpty() ? domains : attached;
        }
        var session = new FederationDomainPolicySession(player, new DevicePolicyEntrance(position, provider, entity),
                candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty(), PolicyEditorSessionState.Status.DISABLED);
        session.deviceDomainAvailability = DeviceDomainAvailability.classify(network.isPresent(), candidates.size());
        if (provider) {
            var entries = session.currentProviders();
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).controller().orElse(null) == entity) session.mappingProviderIndex = i;
            }
            // Pinned, so a Provider added or removed elsewhere in the domain before the first sync cannot shift it.
            session.mappingProviderIdentity = ((space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity) entity)
                    .providerIdentity();
        } else if (entity instanceof space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity endpoint) {
            var binding = endpoint.binding();
            session.endpointIndex = binding == null ? 0 : Math.max(0, session.currentEndpoints().indexOf(binding));
        }
        return session;
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
                || !contextCurrent() || !state.editingAllowed() || selection == null
                || !expectedRevision.equals(requestedRevision)) {
            return false;
        }
        return PolicyService.get(level).revision(selection.key()).equals(requestedRevision);
    }

    public boolean rejectStaleContext(ServerPlayer candidate) {
        if (isStillValid(candidate) && context != null && contextCurrent()) {
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
        var split = target.indexOf(':');
        if (split < 1) return false;
        var group = target.substring(0, split);
        var id = target.substring(split + 1);
        boolean ruleSelection = group.equals("policy") || group.equals("consumer") || group.equals("provider")
                || group.equals("capability");
        if (!(ruleSelection ? authorizeAction() : authorizeMappingAction())) return false;
        if (group.equals("endpoint_mapping")) {
            return navigateEndpoint(id, target);
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
            if (index < 0) return false;
            final int selected = index;
            // Picking the opposite side's network swaps the direction; a two-member domain has no other way to reach it.
            changeSelection(old -> group.equals("consumer") ? old.withConsumer(selected) : old.withProvider(selected));
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
            mappingProviderIdentity = null;
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
        root.addProperty("bridgeUnavailable", entrance instanceof BridgePolicyEntrance && context == null);
        root.addProperty("scope", context == null && deviceDomainAvailability != null ? deviceDomainAvailability.key() : "domain");
        // An Endpoint's own entrance opens the topology with that Endpoint selected; a Provider's opens its mappings.
        root.addProperty("initialPage", entrance instanceof DevicePolicyEntrance device && device.provider() ? "mapping" : "overview");
        root.addProperty("returnProvider", entrance instanceof DevicePolicyEntrance device && device.provider());
        var deviceEndpoint = entrance instanceof DevicePolicyEntrance device && !device.provider()
                && level.getBlockEntity(device.position()) instanceof space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity endpoint
                ? endpoint : null;
        if (context == null && deviceEndpoint != null) {
            root.addProperty("localEndpointPosition", deviceEndpoint.getBlockPos().toShortString());
            // Without a domain the topology shows this Endpoint alone, read-only.
            var local = new com.google.gson.JsonArray();
            if (deviceEndpoint.binding() != null) {
                var choice = new com.google.gson.JsonObject();
                choice.addProperty("id", LOCAL_ENDPOINT);
                choice.addProperty("label", "");
                endpointFacts(choice, deviceEndpoint.binding());
                local.add(choice);
                root.addProperty("initialEndpoint", LOCAL_ENDPOINT);
            }
            root.add("localEndpoint", local);
        }
        if (context != null && deviceEndpoint != null) {
            currentEndpoints().stream().filter(binding -> binding.runtime().position().equals(deviceEndpoint.getBlockPos()))
                    .findFirst().ifPresent(binding -> root.addProperty("initialEndpoint",
                            FederationDomainGraphProjection.endpointId(context, binding)));
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
                    row.addProperty("reexport", RuleMode.of(record.rule()) == RuleMode.REEXPORT);
                    row.addProperty("revision", record.revision().value());
                    row.add("terms", termsJson(record.rule()));
                    row.add("runtime", runtimeObservation(record.key(), record.rule()).toJson());
                    rules.add(row);
                });
        // Deleted rules keep a revision; a switch that recreates one must present it for compare-and-set.
        var revisions = new com.google.gson.JsonArray();
        root.add("revisions", revisions);
        space.controlnet.ae2federation.persistence.PolicySavedData.get(level).snapshot().entries().values().stream()
                .filter(space.controlnet.ae2federation.policy.PolicyRecord.Deleted.class::isInstance)
                .filter(record -> selection.members().contains(record.key().consumerNetworkId())
                        && selection.members().contains(record.key().providerNetworkId()))
                .sorted(java.util.Comparator.comparing(record -> record.key().toString()))
                .forEach(record -> {
                    var row = new com.google.gson.JsonObject();
                    row.addProperty("consumer", record.key().consumerNetworkId().value().toString());
                    row.addProperty("provider", record.key().providerNetworkId().value().toString());
                    row.addProperty("capability", record.key().capability().name());
                    row.addProperty("revision", record.revision().value());
                    revisions.add(row);
                });
        var networks = new com.google.gson.JsonArray();
        root.add("networks", networks);
        var providerNetworks = currentProviders().stream().map(entry -> FederationDomainRegistryAccess
                .confirmedNetworkId(entry.provider().getGrid()).orElse(null)).toList();
        var endpointNetworks = currentEndpoints().stream().map(binding -> FederationDomainRegistryAccess
                .confirmedNetworkId(binding.subnetNode().getGrid()).orElse(null)).toList();
        for (var member : selection.members()) {
            var network = new com.google.gson.JsonObject();
            network.addProperty("id", member.value().toString());
            network.addProperty("member", space.controlnet.ae2federation.observability.id.MemberId
                    .forNetwork(context.federationDomainId(), member).value());
            network.addProperty("providers", providerNetworks.stream().filter(member::equals).count());
            network.addProperty("endpoints", endpointNetworks.stream().filter(member::equals).count());
            space.controlnet.ae2federation.persistence.NetworkNames.get(level).name(member)
                    .ifPresent(name -> network.addProperty("name", name));
            networks.add(network);
        }
        addRelated(root);
        currentFederationDomain().ifPresent(domain -> root.add("via", viaJson(domain)));
        for (var member : selection.members()) {
            var id = member.value().toString();
            addChoice(root, "consumer", id, shortId(id));
            addChoice(root, "provider", id, shortId(id));
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
        root.add("processingProviders", processingProvidersJson());
        if (pendingRelease != null && mappingAllowed) {
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
            root.addProperty("mappingGraph", entry.controller().isPresent());
            var inventory = entry.provider().patternInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                var choice = addChoice(root, "slot", Integer.toString(slot), "");
                patternSlot(choice, entry, slot);
            }
            selected.addProperty("slot", Integer.toString(mappingSlotIndex));
            var endpoints = mappingEndpoints();
            for (var endpoint : endpoints) targetChoice(root, entry, endpoint, selection.members());
            if (!endpoints.isEmpty()) selected.addProperty("target", endpointChoiceId(selectedMappingEndpoint(endpoints)));
        });
        var endpoints = currentEndpoints();
        for (var endpoint : endpoints) {
            var id = FederationDomainGraphProjection.endpointId(context, endpoint);
            var choice = addChoice(root, "endpoint", id, shortId(id));
            choice.addProperty("mappingNavigation", navigationOwner(endpoint).isPresent());
            endpointFacts(choice, endpoint);
            endpoint.claimState().owner().ifPresent(owner -> {
                // The network of the owning Provider: the topology draws the Endpoint beside it.
                currentProviders().stream().filter(entry -> entry.identity().equals(owner.provider())).findFirst()
                        .ifPresent(entry -> {
                            FederationDomainRegistryAccess.confirmedNetworkId(entry.provider().getGrid())
                                    .ifPresent(network -> choice.addProperty("ownerNetwork", network.value().toString()));
                            if (entry.controller().orElse(null) instanceof net.minecraft.world.level.block.entity.BlockEntity entity) {
                                choice.addProperty("ownerPosition", entity.getBlockPos().toShortString());
                            }
                        });
            });
            choice.add("patterns", mappedPatterns(endpoint));
        }
        if (!endpoints.isEmpty()) selected.addProperty("endpoint", FederationDomainGraphProjection.endpointId(context,
                endpoints.get(Math.floorMod(endpointIndex, endpoints.size()))));
        return root.toString();
    }

    /**
     * What the topology's Endpoint panel shows of one Endpoint: where it is, its modes, face and return binding, its
     * native network, and its identity and claim with their epochs.
     */
    private void endpointFacts(com.google.gson.JsonObject choice, EndpointTargetBinding endpoint) {
        var runtime = endpoint.runtime();
        choice.addProperty("position", runtime.position().toShortString());
        choice.addProperty("x", runtime.position().getX());
        choice.addProperty("y", runtime.position().getY());
        choice.addProperty("z", runtime.position().getZ());
        choice.addProperty("dimension", level.dimension().location().toString());
        choice.addProperty("nodeReady", endpoint.subnetNode().isActive() && endpoint.subnetNode().hasGridBooted());
        choice.addProperty("configuredMode", runtime.configuredMode().name());
        choice.addProperty("runtimeMode", runtime.mode().map(mode ->
                mode instanceof space.controlnet.ae2federation.processing.endpoint.EndpointModeGeneration.Local
                        ? "LOCAL" : "FEDERATED").orElse("UNBOUND"));
        choice.addProperty("face", runtime.federationFace().getSerializedName());
        choice.addProperty("returnBinding", runtime.itemReturnContext().isPresent() || runtime.fluidReturnContext().isPresent());
        choice.addProperty("claimResult", endpoint.lastClaimResultCode());
        choice.addProperty("endpointIdentity", endpoint.endpointIdentity().id().value().toString());
        choice.addProperty("instanceEpoch", endpoint.endpointIdentity().instanceEpoch().value());
        choice.addProperty("claimEpoch", endpoint.claimState().epoch().value());
        choice.addProperty("generation", runtime.generation());
        FederationDomainRegistryAccess.confirmedNetworkId(endpoint.subnetNode().getGrid())
                .ifPresent(network -> choice.addProperty("nativeNetwork", network.value().toString()));
        endpoint.claimState().owner().ifPresent(owner -> {
            choice.addProperty("owner", owner.provider().id().value().toString());
            choice.addProperty("ownerInstance", owner.provider().instanceEpoch().value());
        });
    }

    /** The pattern slots of the domain's Providers whose wires go to {@code endpoint}, with the Provider's position. */
    private com.google.gson.JsonArray mappedPatterns(EndpointTargetBinding endpoint) {
        var patterns = new com.google.gson.JsonArray();
        for (var entry : currentProviders()) {
            var controller = entry.controller().orElse(null);
            if (controller == null) continue;
            var inventory = entry.provider().patternInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (!controller.endpointsForSlot(slot).contains(endpoint.endpointIdentity())) continue;
                var pattern = new com.google.gson.JsonObject();
                pattern.addProperty("slot", slot);
                var stack = inventory.getStackInSlot(slot);
                pattern.addProperty("label", stack.isEmpty() ? "" : stack.getHoverName().getString());
                if (controller instanceof net.minecraft.world.level.block.entity.BlockEntity entity) {
                    pattern.addProperty("provider", entity.getBlockPos().toShortString());
                }
                patterns.add(pattern);
            }
        }
        return patterns;
    }

    /**
     * One Endpoint the selected Provider may map, as its wires view shows it: its claim state towards that Provider and,
     * when it is loaded, where it and its owner are. {@code members} orders the networks the view names.
     */
    private com.google.gson.JsonObject targetChoice(com.google.gson.JsonObject root, ProviderObservationRegistry.Entry entry,
            space.controlnet.ae2federation.processing.claim.EndpointIdentity endpoint, List<NetworkId> members) {
        var id = endpointChoiceId(endpoint);
        var choice = addChoice(root, "target", id, shortId(endpoint.id().value().toString()));
        var binding = currentEndpoints().stream().filter(candidate -> candidate.endpointIdentity().equals(endpoint))
                .findFirst().orElse(null);
        choice.addProperty("state", mappingTargetState(entry, endpoint, binding));
        choice.addProperty("retained", entry.controller().filter(controller -> controller.retained(endpoint)).isPresent());
        choice.addProperty("ownedHere", binding != null && binding.claimState().owner()
                .filter(owner -> owner.provider().equals(entry.identity())).isPresent());
        if (binding != null) {
            choice.addProperty("position", binding.runtime().position().toShortString());
            choice.addProperty("nodeReady", binding.subnetNode().isActive() && binding.subnetNode().hasGridBooted());
            choice.addProperty("claimEpoch", binding.claimState().epoch().value());
            addLaneFlow(choice, entry, endpoint);
            binding.claimState().owner().ifPresent(owner -> {
                choice.addProperty("owner", owner.provider().id().value().toString());
                choice.addProperty("ownerInstance", owner.provider().instanceEpoch().value());
                // Where the owner is, so the player can find it; absent when it is not a loaded block.
                currentProviders().stream().filter(candidate -> candidate.identity().equals(owner.provider()))
                        .flatMap(candidate -> candidate.controller().stream())
                        .filter(net.minecraft.world.level.block.entity.BlockEntity.class::isInstance)
                        .map(controller -> ((net.minecraft.world.level.block.entity.BlockEntity) controller).getBlockPos())
                        .findFirst().ifPresent(position -> choice.addProperty("ownerPosition", position.toShortString()));
            });
            // The same order as the "networks" array, so the canvas names the Endpoint's network as the overview does.
            choice.addProperty("networkIndex", FederationDomainRegistryAccess.confirmedNetworkId(binding.subnetNode().getGrid())
                    .map(members::indexOf).orElse(-1));
        }
        return choice;
    }

    /**
     * The Provider screen's state: {@code ready} while its Federation face's domain is current and mapping is allowed,
     * {@code noface} when the face joined no single domain at opening, and {@code stale_context} once that domain changed.
     */
    public String providerStatusCode() {
        if (context == null) return "noface";
        return mappingAllowed && currentFederationDomain().isPresent() && isStillValid(player) ? "ready" : "stale_context";
    }

    /**
     * Whether the mapping this session edits is the one of the Provider it was opened on: the Provider screen edits
     * nothing else, even if that Provider has left the domain and another one would be selected in its place.
     */
    public boolean editsOpenedProvider() {
        var provider = providerEntity().orElse(null);
        return provider != null && selectedProvider().flatMap(ProviderObservationRegistry.Entry::controller)
                .filter(controller -> controller == provider).isPresent();
    }

    /** The Provider this session was opened on, while it is still loaded there. */
    public Optional<space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity> providerEntity() {
        return entrance instanceof DevicePolicyEntrance device && device.provider() && device.present(level)
                && device.entity() instanceof space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity provider
                ? Optional.of(provider) : Optional.empty();
    }

    /**
     * What the Provider's own screen shows: its nine pattern slots, read from the Provider itself so they show even when
     * its Federation face joins no domain; then, when it does, the Endpoints of that domain as its wires view draws them,
     * each one's return buffer, and the owner's patterns of an Endpoint another Provider holds, for reading only.
     */
    public String providerChoices() {
        refreshPreparedRelease();
        var root = new com.google.gson.JsonObject();
        var selected = new com.google.gson.JsonObject();
        root.add("selected", selected);
        for (var group : new String[] {"slot", "target", "lanes", "networks", "processingProviders"}) {
            root.add(group, new com.google.gson.JsonArray());
        }
        var provider = providerEntity().orElse(null);
        var domain = currentFederationDomain().orElse(null);
        root.addProperty("face", domain != null);
        // Opened on a domain that has changed since: not "no domain", but a screen to reopen.
        root.addProperty("stale", domain == null && context != null);
        root.addProperty("scope", domain != null ? "domain" : deviceDomainAvailability == null ? "unavailable"
                : deviceDomainAvailability.key());
        if (provider == null) return root.toString();
        root.addProperty("position", provider.getBlockPos().toShortString());
        var inventory = provider.mappedProvider().patternInventory();
        int used = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            var choice = addChoice(root, "slot", Integer.toString(slot), "");
            patternSlot(choice, provider.mappedProvider(), Optional.of(provider), slot);
            if (!inventory.getStackInSlot(slot).isEmpty()) used++;
        }
        selected.addProperty("slot", Integer.toString(Math.floorMod(mappingSlotIndex, inventory.size())));
        // Wires the Provider keeps while its face is off the domain, so the screen can say they are waiting there.
        int kept = 0;
        for (int slot = 0; slot < inventory.size(); slot++) kept += provider.endpointsForSlot(slot).size();
        root.addProperty("mappings", kept);
        var members = domain == null ? List.<NetworkId>of() : List.copyOf(domain.memberships().keySet());
        for (var member : members) {
            var network = new com.google.gson.JsonObject();
            network.addProperty("id", member.value().toString());
            space.controlnet.ae2federation.persistence.NetworkNames.get(level).name(member)
                    .ifPresent(name -> network.addProperty("name", name));
            root.getAsJsonArray("networks").add(network);
        }
        var card = new com.google.gson.JsonObject();
        card.addProperty("id", provider.providerIdentity().toString());
        card.addProperty("selected", true);
        card.addProperty("graph", true);
        card.addProperty("position", provider.getBlockPos().toShortString());
        var node = provider.getMainNode().getNode();
        card.addProperty("networkIndex", node == null ? -1 : FederationDomainRegistryAccess.confirmedNetworkId(node.getGrid())
                .map(members::indexOf).orElse(-1));
        card.addProperty("slotsUsed", used);
        card.addProperty("slotsTotal", inventory.size());
        card.add("slots", new com.google.gson.JsonArray());
        root.getAsJsonArray("processingProviders").add(card);
        var entry = selectedProvider().filter(candidate -> candidate.controller().orElse(null) == provider).orElse(null);
        var targets = new java.util.HashMap<String, com.google.gson.JsonObject>();
        var endpoints = domain == null || entry == null || !mappingAllowed
                ? List.<space.controlnet.ae2federation.processing.claim.EndpointIdentity>of() : mappingEndpoints();
        for (var endpoint : endpoints) {
            var choice = targetChoice(root, entry, endpoint, members);
            if (choice.get("state").getAsString().equals("occupied")) addOwnerPatterns(choice, endpoint);
            targets.put(choice.get("id").getAsString(), choice);
        }
        addReturnBuffers(root, provider, targets);
        if (endpoints.isEmpty()) return root.toString();
        selected.addProperty("target", endpointChoiceId(selectedMappingEndpoint(endpoints)));
        if (pendingRelease != null) {
            var release = new com.google.gson.JsonObject();
            release.addProperty("endpoint", pendingRelease.endpoint().id().value().toString());
            release.addProperty("epoch", pendingRelease.epoch().value());
            release.addProperty("lane", pendingRelease.lane());
            release.addProperty("position", currentEndpoints().stream()
                    .filter(binding -> binding.endpointIdentity().equals(pendingRelease.endpoint()))
                    .map(binding -> binding.runtime().position().toShortString()).findFirst().orElse(""));
            root.add("release", release);
        }
        return root.toString();
    }

    /**
     * Each bound Lane's return buffer: results that came back through its Endpoint and wait to enter the ME network,
     * and whether it still has a send in progress. Either one keeps the Endpoint from being released, so the Endpoint's
     * own choice in {@code targets} carries them too.
     */
    private void addReturnBuffers(com.google.gson.JsonObject root,
            space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity provider,
            java.util.Map<String, com.google.gson.JsonObject> targets) {
        var ops = level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var endpoints = currentEndpoints();
        for (int index = 0; index < provider.laneCount(); index++) {
            var endpoint = provider.laneEndpoint(index).orElse(null);
            if (endpoint == null) continue;
            var lane = provider.lane(index);
            var returns = new java.util.ArrayList<appeng.api.stacks.GenericStack>();
            var buffer = lane.getReturnInv();
            for (int slot = 0; slot < buffer.size(); slot++) {
                var stack = buffer.getStack(slot);
                if (stack != null) returns.add(stack);
            }
            var json = new com.google.gson.JsonObject();
            json.addProperty("lane", index);
            json.addProperty("endpoint", endpointChoiceId(endpoint));
            endpoints.stream().filter(binding -> binding.endpointIdentity().equals(endpoint)).findFirst()
                    .ifPresent(binding -> json.addProperty("position", binding.runtime().position().toShortString()));
            json.add("returns", appeng.api.stacks.GenericStack.CODEC.listOf().encodeStart(ops, returns).getOrThrow());
            json.addProperty("pendingSend", lane.hasPendingSend());
            root.getAsJsonArray("lanes").add(json);
            var target = targets.get(endpointChoiceId(endpoint));
            if (target != null) {
                target.addProperty("returnKinds", returns.size());
                target.addProperty("pendingSend", lane.hasPendingSend());
            }
        }
    }

    /** The patterns another Provider has wired to an Endpoint it holds, so this screen can show them read-only. */
    private void addOwnerPatterns(com.google.gson.JsonObject choice,
            space.controlnet.ae2federation.processing.claim.EndpointIdentity endpoint) {
        var owner = currentEndpoints().stream().filter(binding -> binding.endpointIdentity().equals(endpoint)).findFirst()
                .flatMap(binding -> binding.claimState().owner())
                .flatMap(identity -> currentProviders().stream().filter(entry -> entry.identity().equals(identity.provider()))
                        .findFirst())
                .orElse(null);
        if (owner == null || owner.controller().isEmpty()) return;
        var patterns = new com.google.gson.JsonArray();
        var inventory = owner.provider().patternInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!owner.controller().orElseThrow().endpointsForSlot(slot).contains(endpoint)) continue;
            var pattern = new com.google.gson.JsonObject();
            pattern.addProperty("id", Integer.toString(slot));
            patternSlot(pattern, owner, slot);
            patterns.add(pattern);
        }
        choice.add("ownerPatterns", patterns);
    }

    private Optional<ProviderObservationRegistry.Entry> navigationOwner(EndpointTargetBinding endpoint) {
        var owner = endpoint.claimState().owner();
        return owner.flatMap(identity -> currentProviders().stream()
                .filter(entry -> entry.controller().isPresent() && entry.identity().equals(identity.provider())).findFirst());
    }

    private boolean navigateEndpoint(String id, String receipt) {
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
        var providers = currentProviders();
        int index = providers.indexOf(owner);
        if (index < 0) return false;
        int slot = java.util.stream.IntStream.range(0, owner.provider().patternInventory().size())
                .filter(candidate -> owner.controller().orElseThrow().endpointsForSlot(candidate).contains(endpoint.endpointIdentity()))
                .findFirst().orElse(0);
        mappingProviderIndex = index;
        mappingProviderIdentity = null;
        mappingSlotIndex = slot;
        mappingEndpointSelection = endpoint.endpointIdentity();
        mappingLaneIndex = 0;
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
        // Every Endpoint starts in Local mode and a claim switches it to Federated, so only one that really serves an
        // adjacent native Provider is "local"; an unbound one is free to map.
        if (binding.runtime().configuredMode() != space.controlnet.ae2federation.ae2.processing.endpoint.EndpointMode.FEDERATED
                && binding.runtime().mode().filter(space.controlnet.ae2federation.processing.endpoint.EndpointModeGeneration.Local.class::isInstance).isPresent())
            return "local";
        return "unclaimed";
    }

    /** One pattern slot of a Provider: its pattern's inputs and outputs and the Endpoints its wires go to. */
    private void patternSlot(com.google.gson.JsonObject choice, ProviderObservationRegistry.Entry entry, int slot) {
        patternSlot(choice, entry.provider(), entry.controller(), slot);
    }

    private void patternSlot(com.google.gson.JsonObject choice,
            space.controlnet.ae2federation.processing.provider.MappedPatternProvider provider,
            Optional<? extends space.controlnet.ae2federation.processing.provider.ProviderMappingController> mapping, int slot) {
        var stack = provider.patternInventory().getStackInSlot(slot);
        choice.addProperty("label", stack.isEmpty() ? "" : stack.getHoverName().getString());
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
        choice.addProperty("mapped", provider.lanesForSlot(slot).size());
        var slotEndpoints = new com.google.gson.JsonArray();
        mapping.ifPresent(controller -> controller.endpointsForSlot(slot)
                .forEach(endpoint -> slotEndpoints.add(endpointChoiceId(endpoint))));
        choice.add("endpoints", slotEndpoints);
    }

    /**
     * Every Provider of the domain for the stacked processing canvas: slot use for its header and, for Providers other
     * than the selected one, their patterns and wires so the canvas can show them read-only.
     */
    private com.google.gson.JsonArray processingProvidersJson() {
        var array = new com.google.gson.JsonArray();
        var providers = currentProviders();
        var selected = selectedProvider().orElse(null);
        for (int index = 0; index < providers.size(); index++) {
            var entry = providers.get(index);
            if (index >= MAX_PROCESSING_PROVIDERS && entry != selected) continue;
            var json = new com.google.gson.JsonObject();
            json.addProperty("id", FederationDomainGraphProjection.providerId(context, entry));
            json.addProperty("selected", entry.equals(selected));
            json.addProperty("graph", entry.controller().isPresent());
            // The same order as the "networks" array, so the canvas colours wires like the overview colours cards.
            json.addProperty("networkIndex", FederationDomainRegistryAccess.confirmedNetworkId(entry.provider().getGrid())
                    .map(network -> selection == null ? -1 : selection.members().indexOf(network)).orElse(-1));
            if (entry.controller().orElse(null) instanceof net.minecraft.world.level.block.entity.BlockEntity entity) {
                json.addProperty("position", entity.getBlockPos().toShortString());
            }
            var inventory = entry.provider().patternInventory();
            int used = 0;
            var slots = new com.google.gson.JsonArray();
            for (int slot = 0; slot < inventory.size(); slot++) {
                var stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                used++;
                if (entry == selected) continue;
                var slotJson = new com.google.gson.JsonObject();
                slotJson.addProperty("id", Integer.toString(slot));
                patternSlot(slotJson, entry, slot);
                slots.add(slotJson);
            }
            json.addProperty("slotsUsed", used);
            json.addProperty("slotsTotal", inventory.size());
            json.add("slots", slots);
            array.add(json);
        }
        return array;
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
        if (!service.revision(key).equals(expectedRevision)) {
            expectedRevision = service.revision(key);
            reject(PolicyEditorSessionState.Status.STALE_REVISION);
            return;
        }
        var enabled = service.configured(key).isEmpty() || !mode(service, key).enabled();
        if (!applyLinked(service, key, enabled ? RuleMode.ENABLED : RuleMode.DISABLED)) {
            reject(PolicyEditorSessionState.Status.STALE_REVISION);
        }
    }

    /**
     * Applies one explicit pair-editor switch. The rule's own revision is compared with the one the client saw, so a
     * concurrent edit is reported without disabling the rest of this session.
     */
    public boolean setPolicy(String encoded) {
        var target = PolicySwitchTarget.parse(encoded).orElse(null);
        if (target == null || selection == null || !state.editingAllowed()) return false;
        if (!isStillValid(player) || context == null || !contextCurrent()) {
            reject(PolicyEditorSessionState.Status.STALE_CONTEXT);
            return true;
        }
        var key = target.key();
        var members = selection.members();
        if (!members.contains(key.consumerNetworkId()) || !members.contains(key.providerNetworkId())) return false;
        selection = new PolicyEditorSelection(members, members.indexOf(key.consumerNetworkId()),
                members.indexOf(key.providerNetworkId()), key.capability().ordinal());
        acknowledgmentId = "";
        var service = PolicyService.get(level);
        var revision = service.revision(key);
        expectedRevision = revision;
        if (!revision.equals(target.observedRevision())) {
            state.conflicted();
            return true;
        }
        if (!applyLinked(service, key, target.mode())) state.conflicted();
        return true;
    }

    /**
     * Sets {@code key} to {@code mode} with the rules linked to it, in one edit: a crafting rule brings its storage
     * rule, and energy is one pool per pair. False when the edit was refused because a rule changed meanwhile.
     */
    private boolean applyLinked(PolicyService service, PolicyKey key, RuleMode mode) {
        var edits = RuleLinks.of(key, mode, other -> mode(service, other)).stream()
                .filter(change -> mode(service, change.key()) != change.mode())
                .map(change -> new PolicyEdit(change.key(), service.revision(change.key()),
                        service.configured(change.key()).map(record -> record.rule())
                                .orElseGet(() -> defaults(change.key().capability())).withMode(change.mode())))
                .toList();
        linkedChanges = List.of();
        if (edits.isEmpty()) {
            state.selectionChanged();
            return true;
        }
        var result = service.editAll(edits);
        expectedRevision = service.revision(key);
        if (!(result instanceof PolicyMutationResult.Accepted accepted)) return false;
        state.accepted();
        acknowledgmentId = "policy-" + accepted.revision().value();
        linkedChanges = edits.stream().filter(edit -> !edit.key().equals(key))
                .map(edit -> new RuleLinks.Change(edit.key(), RuleMode.of(edit.rule()))).toList();
        return true;
    }

    private static RuleMode mode(PolicyService service, PolicyKey key) {
        return service.configured(key).map(record -> RuleMode.of(record.rule())).orElse(RuleMode.DISABLED);
    }

    /**
     * Identity, location and AE2 service figures for each member network and each related domain's network shown
     * read-only. Figures such as stored energy change every tick, so the text is rebuilt at most once a second
     * instead of on every binding poll.
     */
    public String networkOverviewText() {
        if (selection == null || context == null) return "";
        var now = level.getGameTime();
        var described = new java.util.ArrayList<>(selection.members());
        relatedNetworks.stream().sorted(java.util.Comparator.comparing(network -> network.value().toString()))
                .forEach(described::add);
        if (overviewText.isEmpty() || now - overviewTick >= OVERVIEW_INTERVAL_TICKS || now < overviewTick
                || !described.equals(overviewMembers)) {
            overviewMembers = List.copyOf(described);
            overviewTick = now;
            overviewText = NetworkOverview.describe(level, overviewMembers).toString();
        }
        return overviewText;
    }

    private static com.google.gson.JsonObject termsJson(space.controlnet.ae2federation.policy.PolicyRule rule) {
        var terms = RuleTerms.of(rule);
        var json = new com.google.gson.JsonObject();
        var operations = new com.google.gson.JsonArray();
        terms.operations().forEach(operations::add);
        json.add("operations", operations);
        json.addProperty("filter", terms.filter());
        json.addProperty("filterEntries", terms.filterEntries());
        json.addProperty("reexport", terms.reexport());
        return json;
    }

    /**
     * Networks and rules of every other domain connected to this one through shared networks, however far (a domain
     * that shares a network with a connected domain is connected too). The workspace shows them read-only: editing
     * stays with the Router or Bridge of the domain that owns the pair. At most {@link #MAX_RELATED_NETWORKS} networks
     * are sent, nearest domains first; {@code relatedTotal} says how many there are.
     */
    private void addRelated(com.google.gson.JsonObject root) {
        var registry = FederationDomainRegistryAccess.get(level);
        var members = new java.util.HashSet<>(selection.members());
        var networksOut = new com.google.gson.JsonArray();
        var rulesOut = new com.google.gson.JsonArray();
        root.add("relatedNetworks", networksOut);
        root.add("relatedRules", rulesOut);
        java.util.Comparator<space.controlnet.ae2federation.identity.NetworkId> byId =
                java.util.Comparator.comparing(network -> network.value().toString());
        var reach = ConnectedDomains.reach(selection.members(), context.federationDomainId(),
                network -> registry.federationdomainsFor(network).stream().sorted().toList(),
                domainId -> registry.federationDomain(domainId).map(domain -> domain.memberships().keySet().stream()
                        .sorted(byId).toList()).orElse(List.of()));
        root.addProperty("relatedTotal", reach.networks().size());
        var domains = new java.util.LinkedHashSet<>(reach.domains());
        var seen = new java.util.LinkedHashSet<space.controlnet.ae2federation.identity.NetworkId>();
        var ruleKeys = new java.util.HashSet<space.controlnet.ae2federation.policy.PolicyKey>();
        var names = space.controlnet.ae2federation.persistence.NetworkNames.get(level);
        var records = space.controlnet.ae2federation.persistence.PolicySavedData.get(level).snapshot().entries().values();
        for (var domainId : domains) {
            var domain = registry.federationDomain(domainId).orElse(null);
            if (domain == null) continue;
            var domainMembers = domain.memberships().keySet();
            domainMembers.stream().sorted(byId)
                    .filter(network -> !members.contains(network) && seen.size() < MAX_RELATED_NETWORKS)
                    .filter(seen::add).forEach(network -> {
                        var row = new com.google.gson.JsonObject();
                        row.addProperty("id", network.value().toString());
                        row.addProperty("domain", domainId.value());
                        names.name(network).ifPresent(name -> row.addProperty("name", name));
                        networksOut.add(row);
                    });
            records.stream()
                    .filter(space.controlnet.ae2federation.policy.PolicyRecord.Configured.class::isInstance)
                    .map(space.controlnet.ae2federation.policy.PolicyRecord.Configured.class::cast)
                    .filter(record -> domainMembers.contains(record.key().consumerNetworkId())
                            && domainMembers.contains(record.key().providerNetworkId())
                            && !record.key().consumerNetworkId().equals(record.key().providerNetworkId())
                            && !(members.contains(record.key().consumerNetworkId())
                                    && members.contains(record.key().providerNetworkId()))
                            && (members.contains(record.key().consumerNetworkId()) || seen.contains(record.key().consumerNetworkId()))
                            && (members.contains(record.key().providerNetworkId()) || seen.contains(record.key().providerNetworkId()))
                            && ruleKeys.add(record.key()))
                    .sorted(java.util.Comparator.comparing(record -> record.key().toString()))
                    .forEach(record -> {
                        var row = new com.google.gson.JsonObject();
                        row.addProperty("consumer", record.key().consumerNetworkId().value().toString());
                        row.addProperty("provider", record.key().providerNetworkId().value().toString());
                        row.addProperty("capability", record.key().capability().name());
                        row.addProperty("enabled", record.rule().enabled());
                        row.addProperty("reexport", RuleMode.of(record.rule()) == RuleMode.REEXPORT);
                        row.addProperty("revision", record.revision().value());
                        row.add("terms", termsJson(record.rule()));
                        row.addProperty("domain", domainId.value());
                        rulesOut.add(row);
                    });
        }
        // The related domains each shown network is in: the canvas links only networks that share a domain.
        for (var rows : List.of(root.getAsJsonArray("networks"), networksOut)) {
            rows.forEach(value -> {
                var row = value.getAsJsonObject();
                var in = new com.google.gson.JsonArray();
                registry.federationdomainsFor(space.controlnet.ae2federation.identity.NetworkId.parse(row.get("id").getAsString()))
                        .stream().filter(domains::contains).sorted().forEach(domainId -> in.add(domainId.value()));
                row.add("domains", in);
            });
        }
        relatedNetworks = java.util.Set.copyOf(seen);
    }

    /**
     * Accepted deliveries of each enabled rule among the shown networks over the last five seconds. Only real,
     * accepted transfers are counted; a rule with no entry moved nothing in that window. Rebuilt twice a second.
     */
    public String pairFlowText() {
        if (selection == null || context == null) return "";
        var now = level.getGameTime();
        if (!flowText.isEmpty() && now - flowTick < FLOW_INTERVAL_TICKS && now >= flowTick) return flowText;
        flowTick = now;
        var shown = new java.util.HashSet<>(selection.members());
        shown.addAll(relatedNetworks);
        var observability = space.controlnet.ae2federation.observability.LevelObservabilityService.get(level);
        var out = new com.google.gson.JsonArray();
        space.controlnet.ae2federation.persistence.PolicySavedData.get(level).snapshot().entries().values().stream()
                .filter(space.controlnet.ae2federation.policy.PolicyRecord.Configured.class::isInstance)
                .map(space.controlnet.ae2federation.policy.PolicyRecord.Configured.class::cast)
                .filter(record -> record.rule().enabled() && shown.contains(record.key().consumerNetworkId())
                        && shown.contains(record.key().providerNetworkId()))
                .sorted(java.util.Comparator.comparing(record -> record.key().toString()))
                .forEach(record -> {
                    var summary = observability.pairFlow(record.key());
                    if (!summary.active()) return;
                    var row = new com.google.gson.JsonObject();
                    row.addProperty("consumer", record.key().consumerNetworkId().value().toString());
                    row.addProperty("provider", record.key().providerNetworkId().value().toString());
                    row.addProperty("capability", record.key().capability().name());
                    row.addProperty("events", summary.events());
                    row.addProperty("amount", summary.amount());
                    row.addProperty("age", summary.ticksSinceLast());
                    out.add(row);
                });
        // What each Endpoint's owner sent it and got back, for the link from the owner's network to its node.
        for (var endpoint : currentEndpoints()) {
            var owner = endpoint.claimState().owner().orElse(null);
            if (owner == null) continue;
            var entry = currentProviders().stream().filter(candidate -> candidate.identity().equals(owner.provider()))
                    .findFirst().orElse(null);
            if (entry == null) continue;
            var totals = laneTotals(entry, endpoint.endpointIdentity());
            if (totals[0] == 0 && totals[2] == 0) continue;
            var row = new com.google.gson.JsonObject();
            row.addProperty("endpoint", FederationDomainGraphProjection.endpointId(context, endpoint));
            row.addProperty("events", totals[0]);
            row.addProperty("amount", totals[1]);
            row.addProperty("returnedEvents", totals[2]);
            row.addProperty("returned", totals[3]);
            out.add(row);
        }
        flowText = out.toString();
        return flowText;
    }

    /** Renames a member network whose identity is settled; an empty name clears it. */
    public boolean renameNetwork(String encoded) {
        var target = NetworkRenameTarget.parse(encoded).orElse(null);
        if (target == null || selection == null || !state.editingAllowed()
                || !selection.members().contains(target.network())) return false;
        var grids = NetworkOverview.gridsByNetwork(level).getOrDefault(target.network(), List.of());
        var settlements = grids.stream().map(grid -> grid.getService(
                space.controlnet.ae2federation.identity.NetworkIdentityService.class).settlement()).toList();
        if (!NetworkIdentityState.of(target.network(), settlements).renamable()) return false;
        space.controlnet.ae2federation.persistence.NetworkNames.get(level).rename(target.network(), target.name());
        return true;
    }

    /** Session, distance and domain authority without the selected rule's revision; switches carry their own. */
    public boolean matchesContext(ServerPlayer candidate, FederationDomainReference requestedContext) {
        return isStillValid(candidate) && context != null && context.equals(requestedContext)
                && contextCurrent() && state.editingAllowed() && selection != null;
    }

    public void nextMappingProvider() {
        if (!authorizeMappingAction()) {
            mappingAcknowledgment = "rejected-session";
            return;
        }
        var providers = currentProviders();
        mappingProviderIndex = nextIndex(mappingProviderIndex, providers.size());
        mappingProviderIdentity = null;
        mappingSlotIndex = 0;
        mappingLaneIndex = 0;
        mappingAcknowledgment = "ready";
    }

    public void nextMappingSlot() {
        if (!authorizeMappingAction()) {
            mappingAcknowledgment = "rejected-session";
            return;
        }
        selectedProvider().ifPresentOrElse(entry -> {
            mappingSlotIndex = nextIndex(mappingSlotIndex, entry.provider().patternInventory().size());
            mappingAcknowledgment = "ready";
        }, () -> mappingAcknowledgment = "rejected-no-provider");
    }

    public void nextMappingLane() {
        if (!authorizeMappingAction()) {
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
        if (!authorizeMappingAction()) {
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
        if (targetState == ProviderTargetState.CLAIM_MISMATCH) {
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

    /**
     * Sets one wire between a pattern slot of the selected Provider and one of its Endpoint choices to an explicit
     * state. The slot and Endpoint become the selection; an unchanged wire is acknowledged without a toggle, so a
     * repeated request cannot reverse it. Ownership is checked by the Provider exactly as for the list view.
     */
    public boolean setMapping(String encoded) {
        if (!authorizeMappingAction()) return false;
        var target = MappingWireTarget.parse(encoded).orElse(null);
        var entry = selectedProvider().orElse(null);
        if (target == null || entry == null || entry.controller().isEmpty()) return false;
        var slot = target.slotIndex();
        if (slot >= entry.provider().patternInventory().size()) return false;
        var endpoints = mappingEndpoints();
        var endpoint = endpoints.stream().filter(candidate -> endpointChoiceId(candidate).equals(target.endpoint()))
                .findFirst().orElse(null);
        if (endpoint == null) return false;
        mappingSlotIndex = slot;
        mappingLaneIndex = endpoints.indexOf(endpoint);
        mappingEndpointSelection = endpoint;
        if (entry.controller().orElseThrow().endpointsForSlot(slot).contains(endpoint) == target.mapped()) {
            mappingAcknowledgment = "ready";
        } else {
            toggleMapping();
        }
        return true;
    }

    /** Prepares a target-specific confirmation or executes the matching, still-current prepared release. */
    public void releaseEndpoint() {
        if (!authorizeMappingAction()) {
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
        if (!authorizeMappingAction()) {
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
        if (!authorizeMappingAction()) {
            return;
        }
        endpointIndex = nextIndex(endpointIndex, currentEndpoints().size());
    }

    public String graphSnapshotText() {
        return context == null ? space.controlnet.ae2federation.client.domain.FederationDomainGraphSnapshot.empty().encode()
                : FederationDomainGraphProjection.snapshot(level, context, selectedProvider()).encode();
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

    public Component entranceText() {
        return entrance.label(level);
    }

    public Component membersText() {
        if (context == null && deviceDomainAvailability != null) return Component.translatable("ae2federation.ui.workspace.local_scope");
        if (selection == null) {
            return Component.translatable("ae2federation.ui.domain.members.pending");
        }
        var links = space.controlnet.ae2federation.persistence.PolicySavedData.get(level).snapshot().entries().values().stream()
                .filter(space.controlnet.ae2federation.policy.PolicyRecord.Configured.class::isInstance)
                .map(record -> record.key())
                .filter(key -> selection.members().contains(key.consumerNetworkId())
                        && selection.members().contains(key.providerNetworkId())
                        && !key.consumerNetworkId().equals(key.providerNetworkId()))
                .map(key -> java.util.Set.of(key.consumerNetworkId(), key.providerNetworkId()))
                .distinct().count();
        return Component.translatable("ae2federation.ui.domain.members", selection.members().size()).append(" · ")
                .append(Component.translatable(links == 1 ? "ae2federation.ui.domain.links.one" : "ae2federation.ui.domain.links", links));
    }

    /** The two server counters a stale edit is judged against: the rule store's and the domain topology's. */
    public Component revisionsText() {
        var policy = space.controlnet.ae2federation.persistence.PolicySavedData.get(level).highWatermark().value();
        var topology = FederationDomainRegistryAccess.get(level).topologyRevision();
        return Component.translatable("ae2federation.ui.domain.revisions", policy, topology);
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
        return runtimeObservation(key, rule).toComponent();
    }

    /** The last observed runtime result of one rule, as translation keys rather than rendered text. */
    record RuntimeObservation(String code, String operation, String backend, String storage) {
        Component toComponent() {
            String prefix = "ae2federation.ui.domain.runtime.";
            if (code.equals("operation_missing")) return Component.translatable(prefix + code,
                    Component.translatable(prefix + "operation." + operation));
            var text = Component.translatable(prefix + code);
            if (!backend.isEmpty()) text.append("\n").append(Component.translatable(prefix + "backend_reason",
                    Component.translatable(prefix + "backend." + backend)));
            if (!storage.isEmpty()) text.append("\n").append(Component.translatable(prefix + "storage_reason",
                    Component.translatable(prefix + "provenance." + storage)));
            return text;
        }

        com.google.gson.JsonObject toJson() {
            var json = new com.google.gson.JsonObject();
            json.addProperty("code", code);
            if (!operation.isEmpty()) json.addProperty("operation", operation);
            if (!backend.isEmpty()) json.addProperty("backend", backend);
            if (!storage.isEmpty()) json.addProperty("storage", storage);
            return json;
        }
    }

    private RuntimeObservation runtimeObservation(PolicyKey key, PolicyRule rule) {
        if (rule == null) return new RuntimeObservation("unconfigured", "", "", "");
        if (!rule.enabled()) return new RuntimeObservation("off", "", "", "");
        var required = switch (key.capability()) {
            case CRAFTING -> List.of(PolicyOperation.REQUEST);
            case ME_POWER -> List.of(PolicyOperation.SUPPLY);
            case STORAGE -> List.<PolicyOperation>of();
        };
        for (var operation : required) {
            if (!rule.operations().contains(operation)) {
                return new RuntimeObservation("operation_missing", operation.name().toLowerCase(java.util.Locale.ROOT), "", "");
            }
        }
        boolean published = switch (key.capability()) {
            case STORAGE -> space.controlnet.ae2federation.storage.mount.StorageMountService.hasPublishedBinding(level, key);
            case CRAFTING -> space.controlnet.ae2federation.crafting.projection.CraftingProjectionService.status(level, key).map(space.controlnet.ae2federation.crafting.projection.CraftingProjectionService.Status::active).orElse(false);
            case ME_POWER -> space.controlnet.ae2federation.energy.EnergySharingService.shares(level, key);
        };
        if (published) return new RuntimeObservation("published", "", "", "");
        var backendReason = switch (key.capability()) {
            case CRAFTING -> space.controlnet.ae2federation.crafting.projection.CraftingProjectionService.status(level, key).flatMap(space.controlnet.ae2federation.crafting.projection.CraftingProjectionService.Status::reason);
            case ME_POWER -> space.controlnet.ae2federation.energy.EnergySharingService.lastDiagnostic(level, key)
                    .map(space.controlnet.ae2federation.policy.BindingDiagnostic::reason);
            default -> Optional.<space.controlnet.ae2federation.policy.BindingDiagnostic.Reason>empty();
        };
        var backend = backendReason.map(reason -> reason.name().toLowerCase(java.util.Locale.ROOT)).orElse("");
        var storage = "";
        if (key.capability() == PolicyCapability.STORAGE) {
            var diagnostic = space.controlnet.ae2federation.storage.mount.StorageMountService.lastDiagnosticIfPresent(level, key);
            if (diagnostic != null) storage = diagnostic.name().toLowerCase(java.util.Locale.ROOT);
        }
        return new RuntimeObservation("unobserved", "", backend, storage);
    }

    public Component statusText() {
        if (context == null && deviceDomainAvailability != null) {
            return Component.translatable("ae2federation.ui.workspace.device_scope." + deviceDomainAvailability.key());
        }
        return switch (state.status()) {
            case READY -> Component.translatable("ae2federation.ui.domain.status.ready");
            case PENDING -> Component.translatable("ae2federation.ui.domain.status.pending");
            case DISABLED -> Component.translatable("ae2federation.ui.domain.status.disabled", entrance.diagnostic());
            case ACCEPTED -> acceptedText();
            case STALE_CONTEXT -> Component.translatable("ae2federation.ui.domain.status.stale_context");
            case STALE_REVISION -> Component.translatable("ae2federation.ui.domain.status.stale_revision",
                    expectedRevision.value());
            case CONFLICT -> Component.translatable("ae2federation.ui.domain.status.conflict", expectedRevision.value());
        };
    }

    /**
     * "Server confirmed: Crafting rule on · revision 12 · also Storage rule on", from the rule the server just accepted
     * and the rules linked to it.
     */
    private Component acceptedText() {
        var key = selection.key();
        var text = Component.translatable("ae2federation.ui.domain.status.accepted", capabilityName(key),
                modeName(mode(PolicyService.get(level), key)), expectedRevision.value());
        for (var linked : linkedChanges) {
            text.append(Component.translatable("ae2federation.ui.domain.status.accepted.also",
                    capabilityName(linked.key()), modeName(linked.mode())));
        }
        return text;
    }

    private static Component capabilityName(PolicyKey key) {
        return Component.translatable("ae2federation.ui.workspace.capability."
                + key.capability().name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component modeName(RuleMode mode) {
        return Component.translatable("ae2federation.ui.domain.status.accepted." + switch (mode) {
            case DISABLED -> "off";
            case ENABLED -> "on";
            case REEXPORT -> "reexport";
        });
    }

    public String statusCode() {
        return state.status().name().toLowerCase(java.util.Locale.ROOT);
    }

    private List<ProviderObservationRegistry.Entry> currentProviders() {
        return currentFederationDomain().map(federationDomain -> FederationDomainGraphProjection.providerEntries(level, federationDomain)).orElse(List.of());
    }

    private Optional<ProviderObservationRegistry.Entry> selectedProvider() {
        var providers = currentProviders();
        if (providers.isEmpty()) return Optional.empty();
        if (mappingProviderIdentity != null) {
            for (int index = 0; index < providers.size(); index++) {
                if (providers.get(index).identity().equals(mappingProviderIdentity)) {
                    mappingProviderIndex = index;
                    return Optional.of(providers.get(index));
                }
            }
        }
        var entry = providers.get(Math.floorMod(mappingProviderIndex, providers.size()));
        mappingProviderIdentity = entry.identity();
        return Optional.of(entry);
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

    /** Deliveries and returns over this Provider's lanes to one Endpoint, in the flow window. */
    private void addLaneFlow(com.google.gson.JsonObject choice, ProviderObservationRegistry.Entry entry,
            space.controlnet.ae2federation.processing.claim.EndpointIdentity endpoint) {
        var totals = laneTotals(entry, endpoint);
        if (totals[1] > 0) choice.addProperty("laneSent", totals[1]);
        if (totals[3] > 0) choice.addProperty("laneReturned", totals[3]);
    }

    /**
     * What the Provider's lanes to {@code endpoint} moved over the flow window: sent events and amount, then returned
     * events and amount.
     */
    private long[] laneTotals(ProviderObservationRegistry.Entry entry,
            space.controlnet.ae2federation.processing.claim.EndpointIdentity endpoint) {
        var totals = new long[4];
        var controller = entry.controller().orElse(null);
        if (controller == null) return totals;
        var observability = space.controlnet.ae2federation.observability.LevelObservabilityService.get(level);
        var provider = entry.identity().toString();
        for (int lane = 0; lane < entry.provider().lanes().size(); lane++) {
            if (!controller.laneEndpoint(lane).filter(endpoint::equals).isPresent()) continue;
            for (int returned = 0; returned <= 1; returned++) {
                var summary = observability.laneFlow(new space.controlnet.ae2federation.observability.LevelObservabilityService
                        .LaneKey(provider, lane, returned == 1));
                totals[returned * 2] += summary.events();
                totals[returned * 2 + 1] += summary.amount();
            }
        }
        return totals;
    }

    /** What links this domain's networks: its Router group or its Bridge, with the device positions. */
    private com.google.gson.JsonObject viaJson(FederationDomainSnapshot domain) {
        var json = new com.google.gson.JsonObject();
        var kind = RelatedDomainLabel.of(domain.federationDomainId().value()).kind();
        json.addProperty("kind", kind);
        // A Router group's nodes also hold its Federation cables and device ports; it is named by its Routers.
        var shown = kind.equals("router") ? domain.nodes().stream().filter(node -> !cableOrPort(node)).toList()
                : List.copyOf(domain.nodes());
        json.addProperty("count", shown.size());
        var nodes = new com.google.gson.JsonArray();
        shown.stream().limit(MAX_VIA_NODES).forEach(node -> {
            var position = net.minecraft.core.BlockPos.of(node.blockPosition());
            var row = new com.google.gson.JsonObject();
            row.addProperty("dimension", node.dimension());
            row.addProperty("position", position.toShortString());
            nodes.add(row);
        });
        json.add("nodes", nodes);
        return json;
    }

    /** Whether a loaded domain node is a Federation cable or a device port rather than a Router; unloaded ones count. */
    private boolean cableOrPort(space.controlnet.ae2federation.domain.FederationDomainNodeId node) {
        var dimension = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(node.dimension()));
        var world = level.getServer().getLevel(dimension);
        var position = net.minecraft.core.BlockPos.of(node.blockPosition());
        return world != null && world.isLoaded(position)
                && !(world.getBlockEntity(position) instanceof space.controlnet.ae2federation.router.RouterBlockEntity);
    }

    /**
     * The opened domain is still the one this session edits: its generation is unchanged, which holds while its member
     * networks stay the same, and an entrance that was a node of it (a Router, Endpoint or Provider) still is. A relay
     * Router cut out of a domain whose members stay connected elsewhere must not keep editing that domain.
     */
    private boolean contextCurrent() {
        var registry = FederationDomainRegistryAccess.get(level);
        if (context == null || !registry.isCurrent(context)) {
            return false;
        }
        return contextNode == null || registry.federationDomain(context.federationDomainId())
                .map(snapshot -> snapshot.nodes().contains(contextNode)).orElse(false);
    }

    private Optional<FederationDomainSnapshot> currentFederationDomain() {
        if (context == null || !contextCurrent()) {
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
        linkedChanges = List.of();
        refreshExpectedRevision();
    }

    /** Session, distance and domain authority for Provider mapping; it does not depend on a selected rule. */
    private boolean authorizeMappingAction() {
        if (!mappingAllowed) {
            return false;
        }
        if (!isStillValid(player) || context == null || !contextCurrent()) {
            mappingAllowed = false;
            reject(PolicyEditorSessionState.Status.STALE_CONTEXT);
            return false;
        }
        return true;
    }

    /** Whether a mapping request still matches this session's live domain; no rule revision is involved. */
    public boolean matchesMappingContext(ServerPlayer candidate, FederationDomainReference requestedContext) {
        return mappingAllowed && isStillValid(candidate) && context != null && context.equals(requestedContext)
                && contextCurrent();
    }

    private boolean authorizeAction() {
        if (!state.editingAllowed() || selection == null) {
            return false;
        }
        if (!isStillValid(player) || context == null || !contextCurrent()) {
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
        linkedChanges = List.of();
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
            case ME_POWER -> PolicyRule.enabled(java.util.Set.of(space.controlnet.ae2federation.policy.PolicyOperation.SUPPLY));
        };
    }

}
