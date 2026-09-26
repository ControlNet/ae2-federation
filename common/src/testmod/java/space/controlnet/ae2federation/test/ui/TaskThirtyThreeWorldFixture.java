package space.controlnet.ae2federation.test.ui;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.phys.Vec3;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.ClaimRequest;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.claim.NativeTargetDomainRegistry;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
import space.controlnet.ae2federation.processing.provider.MappedPatternProviderHost;
import space.controlnet.ae2federation.processing.provider.ProviderFace;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderOrientation;
import space.controlnet.ae2federation.processing.provider.ProviderRuntime;
import space.controlnet.ae2federation.processing.provider.ProviderTargetRequest;

final class TaskThirtyThreeWorldFixture {
    private static final String STATE = "task33.world";

    private TaskThirtyThreeWorldFixture() {
    }

    static ScenarioBuilder arrange(ScenarioBuilder scenario) {
        return TaskFifteenWorldFixture.arrange(scenario)
                .server("place production Provider and Endpoint diagnostics", TaskThirtyThreeWorldFixture::place)
                .serverTicks(4)
                .waitUntilServer("Provider and Endpoint join the scoped Federation Domain", TaskThirtyThreeWorldFixture::ready)
                .teardownServer("remove Task 33 processing fixtures", TaskThirtyThreeWorldFixture::cleanup);
    }

    static void openRouter(ServerContext context) {
        TaskFifteenWorldFixture.openRouter(context);
    }

    static void openBridge(ServerContext context) {
        TaskFifteenWorldFixture.openBridge(context);
    }

    static boolean endpointHasAmbiguousDomains(ServerContext context) {
        var position = state(context).endpointPosition();
        var network = FederationDomainRegistryAccess.confirmedNetworkId(endpoint(context).getMainNode().getGrid()).orElseThrow();
        var nodeId = FederationDomainRegistryAccess.nodeId(context.level(), position);
        var candidates = FederationDomainRegistryAccess.get(context.level()).snapshot().federationDomains().values().stream()
                .filter(domain -> domain.memberships().size() >= 2 && domain.memberships().containsKey(network)).toList();
        require(candidates.size() >= 2 && candidates.stream().noneMatch(domain -> domain.nodes().contains(nodeId)),
                "Direct Endpoint must match multiple real domains without a physical domain attachment");
        var session = space.controlnet.ae2federation.client.policy.FederationDomainPolicySession.forDevice(context.player(), position);
        var choices = com.google.gson.JsonParser.parseString(session.workspaceChoices()).getAsJsonObject();
        return session.context().isEmpty() && !session.canSubmit() && !session.selectTarget("capability:CRAFTING")
                && choices.get("scope").getAsString().equals("ambiguous")
                && choices.get("localEndpointPosition").getAsString().equals(position.toShortString());
    }

    static void openEndpoint(ServerContext context) {
        space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu.openDevice(context.player(), state(context).endpointPosition());
    }

    static void openProviderMapping(ServerContext context) {
        var session = space.controlnet.ae2federation.client.policy.FederationDomainPolicySession.forDevice(context.player(), state(context).hostPosition());
        require(session.context().isPresent(), "Device entrance requires a unique domain; network="
                + FederationDomainRegistryAccess.confirmedNetworkId(provider(context).getMainNode().getGrid())
                + "; domains=" + FederationDomainRegistryAccess.get(context.level()).snapshot().federationDomains());
        space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu.openDevice(context.player(), state(context).hostPosition());
    }

    static boolean mappingAccepted(ServerContext context) {
        var provider = provider(context);
        return provider != null && provider.mappedProvider().lanesForSlot(0).equals(Set.of(0));
    }

    static void installRichPatterns(ServerContext context) {
        var namedDiamond = new net.minecraft.world.item.ItemStack(Items.DIAMOND);
        namedDiamond.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Calibrated Diamond"));
        var water = appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        var inventory = provider(context).getTerminalPatternInventory();
        inventory.insertItem(2, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
                List.of(new GenericStack(AEItemKey.of(namedDiamond), 4_000_000_000L), new GenericStack(water, 1500))), false);
        inventory.insertItem(3, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                List.of(new GenericStack(water, 2500))), false);
    }

    static void changeStoragePolicyExternally(ServerContext context) {
        var source = FederationDomainRegistryAccess.confirmedNetworkId(provider(context).getMainNode().getGrid()).orElseThrow();
        var target = FederationDomainRegistryAccess.confirmedNetworkId(endpoint(context).getMainNode().getGrid()).orElseThrow();
        var service = space.controlnet.ae2federation.policy.PolicyService.get(context.level());
        for (var key : List.of(
                new space.controlnet.ae2federation.policy.PolicyKey(source, target, space.controlnet.ae2federation.policy.PolicyCapability.STORAGE),
                new space.controlnet.ae2federation.policy.PolicyKey(target, source, space.controlnet.ae2federation.policy.PolicyCapability.STORAGE))) {
            service.edit(new space.controlnet.ae2federation.policy.PolicyEdit(key, service.revision(key),
                    space.controlnet.ae2federation.policy.PolicyRule.storageDefaults().withEnabled(false)));
        }
    }

    static void installBrowserRule(ServerContext context) {
        var consumer = FederationDomainRegistryAccess.confirmedNetworkId(provider(context).getMainNode().getGrid()).orElseThrow();
        var provider = FederationDomainRegistryAccess.confirmedNetworkId(endpoint(context).getMainNode().getGrid()).orElseThrow();
        var key = new space.controlnet.ae2federation.policy.PolicyKey(consumer, provider,
                space.controlnet.ae2federation.policy.PolicyCapability.CRAFTING);
        var service = space.controlnet.ae2federation.policy.PolicyService.get(context.level());
        service.edit(new space.controlnet.ae2federation.policy.PolicyEdit(key, service.revision(key),
                space.controlnet.ae2federation.policy.PolicyRule.enabled(Set.of(
                        space.controlnet.ae2federation.policy.PolicyOperation.REQUEST)).withEnabled(false)));
        context.put("rule.consumer", consumer.value().toString());
        context.put("rule.provider", provider.value().toString());
    }

    static void removeBrowserRuleOperation(ServerContext context) {
        var consumer = FederationDomainRegistryAccess.confirmedNetworkId(provider(context).getMainNode().getGrid()).orElseThrow();
        var provider = FederationDomainRegistryAccess.confirmedNetworkId(endpoint(context).getMainNode().getGrid()).orElseThrow();
        var key = new space.controlnet.ae2federation.policy.PolicyKey(consumer, provider,
                space.controlnet.ae2federation.policy.PolicyCapability.CRAFTING);
        var policies = space.controlnet.ae2federation.policy.PolicyService.get(context.level());
        policies.edit(new space.controlnet.ae2federation.policy.PolicyEdit(key, policies.revision(key),
                space.controlnet.ae2federation.policy.PolicyRule.enabled(Set.of())));
    }

    static void observeUnavailableCraftingBackend(ServerContext context) {
        var consumerGrid = provider(context).getMainNode().getGrid();
        var providerGrid = endpoint(context).getMainNode().getGrid();
        var consumer = FederationDomainRegistryAccess.confirmedNetworkId(consumerGrid).orElseThrow();
        var provider = FederationDomainRegistryAccess.confirmedNetworkId(providerGrid).orElseThrow();
        var key = new space.controlnet.ae2federation.policy.PolicyKey(consumer, provider,
                space.controlnet.ae2federation.policy.PolicyCapability.CRAFTING);
        var policies = space.controlnet.ae2federation.policy.PolicyService.get(context.level());
        policies.edit(new space.controlnet.ae2federation.policy.PolicyEdit(key, policies.revision(key),
                space.controlnet.ae2federation.policy.PolicyRule.enabled(Set.of(
                        space.controlnet.ae2federation.policy.PolicyOperation.REQUEST))));
        var bindings = space.controlnet.ae2federation.crafting.binding.CraftingBindingService.get(context.level());
        bindings.observeConnectedGrids(consumerGrid, providerGrid);
        var diagnostic = space.controlnet.ae2federation.crafting.binding.CraftingBindingService.lastDiagnostic(context.level(), key).orElseThrow();
        require(diagnostic.reason() == space.controlnet.ae2federation.policy.BindingDiagnostic.Reason.CRAFTING_PROVIDER_MISSING,
                "Actual native backend must report its missing crafting provider");
        for (int i = 0; i < 100; i++) {
            require(space.controlnet.ae2federation.crafting.binding.CraftingBindingService.lastDiagnostic(context.level(), key)
                    .orElseThrow() == diagnostic, "Diagnostic reads must preserve the recorded observation");
        }
    }

    static void placeNativeCraftingProvider(ServerContext context) {
        var position = state(context).endpointPosition().east(4);
        require(context.level().isEmptyBlock(position), "Native crafting fixture position must be empty");
        context.put("crafting.nativeProviderPosition", position);
        context.level().setBlockAndUpdate(position, appeng.core.definitions.AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        var provider = (appeng.blockentity.crafting.PatternProviderBlockEntity) context.level().getBlockEntity(position);
        var network = FederationDomainRegistryAccess.confirmedNetworkId(endpoint(context).getMainNode().getGrid()).orElseThrow();
        provider.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", network));
    }

    static boolean nativeProviderHasNoCpu(ServerContext context) {
        var position = context.<BlockPos>get("crafting.nativeProviderPosition");
        var nativeProvider = (appeng.blockentity.crafting.PatternProviderBlockEntity) context.level().getBlockEntity(position);
        if (nativeProvider == null || nativeProvider.getMainNode().getNode() == null) return false;
        var node = nativeProvider.getMainNode().getNode();
        var targetGrid = endpoint(context).getMainNode().getGrid();
        if (node.getGrid() != targetGrid) {
            GridHelper.createConnection(node, endpoint(context).getMainNode().getNode());
            return false;
        }
        if (!node.isActive() || !node.hasGridBooted()) return false;
        var consumerGrid = provider(context).getMainNode().getGrid();
        var key = new space.controlnet.ae2federation.policy.PolicyKey(
                FederationDomainRegistryAccess.confirmedNetworkId(consumerGrid).orElseThrow(),
                FederationDomainRegistryAccess.confirmedNetworkId(targetGrid).orElseThrow(),
                space.controlnet.ae2federation.policy.PolicyCapability.CRAFTING);
        var bindings = space.controlnet.ae2federation.crafting.binding.CraftingBindingService.get(context.level());
        bindings.observeConnectedGrids(consumerGrid, targetGrid);
        return space.controlnet.ae2federation.crafting.binding.CraftingBindingService.lastDiagnostic(context.level(), key)
                .filter(diagnostic -> diagnostic.reason() == space.controlnet.ae2federation.policy.BindingDiagnostic.Reason.CRAFTING_CPU_MISSING)
                .isPresent();
    }

    static void setReverseCraftingRule(ServerContext context, boolean enabled) {
        var consumer = FederationDomainRegistryAccess.confirmedNetworkId(provider(context).getMainNode().getGrid()).orElseThrow();
        var target = FederationDomainRegistryAccess.confirmedNetworkId(endpoint(context).getMainNode().getGrid()).orElseThrow();
        var key = new space.controlnet.ae2federation.policy.PolicyKey(target, consumer,
                space.controlnet.ae2federation.policy.PolicyCapability.CRAFTING);
        var policies = space.controlnet.ae2federation.policy.PolicyService.get(context.level());
        var result = policies.edit(new space.controlnet.ae2federation.policy.PolicyEdit(key, policies.revision(key),
                space.controlnet.ae2federation.policy.PolicyRule.enabled(Set.of(
                        space.controlnet.ae2federation.policy.PolicyOperation.REQUEST)).withEnabled(enabled)));
        require(result instanceof space.controlnet.ae2federation.policy.PolicyMutationResult.Accepted,
                "Reverse crafting policy edit must be accepted");
    }

    static void disableObservedCraftingRule(ServerContext context) {
        var consumer = FederationDomainRegistryAccess.confirmedNetworkId(provider(context).getMainNode().getGrid()).orElseThrow();
        var target = FederationDomainRegistryAccess.confirmedNetworkId(endpoint(context).getMainNode().getGrid()).orElseThrow();
        var key = new space.controlnet.ae2federation.policy.PolicyKey(consumer, target,
                space.controlnet.ae2federation.policy.PolicyCapability.CRAFTING);
        var before = space.controlnet.ae2federation.crafting.binding.CraftingBindingService.lastDiagnostic(context.level(), key).orElseThrow();
        var policies = space.controlnet.ae2federation.policy.PolicyService.get(context.level());
        var result = policies.edit(new space.controlnet.ae2federation.policy.PolicyEdit(key, policies.revision(key),
                policies.configured(key).orElseThrow().rule().withEnabled(false)));
        require(result instanceof space.controlnet.ae2federation.policy.PolicyMutationResult.Accepted,
                "Observed crafting rule must accept a new disabled revision");
        require(!before.matches(policies.revision(key), FederationDomainRegistryAccess.get(context.level()).snapshot().topologyRevision()),
                "Previous backend observation must not match the actual new policy revision");
        require(space.controlnet.ae2federation.crafting.binding.CraftingBindingService.lastDiagnostic(context.level(), key).isEmpty(),
                "Disabled rule must not retain its prior backend failure");
        context.put("runtime.disabledRevision", policies.revision(key).value());
        context.put("runtime.disabledKey", key);
        context.put("runtime.expectedRevision", space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu.currentRequest(
                context.player(), space.controlnet.ae2federation.client.menu.FederationDomainPolicyAction.TOGGLE_POLICY)
                .orElseThrow().expectedRevision());
    }

    static boolean displayedRevisionDoesNotGrantAuthority(ServerContext context) {
        var request = space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu.currentRequest(context.player(),
                space.controlnet.ae2federation.client.menu.FederationDomainPolicyAction.TOGGLE_POLICY).orElseThrow();
        require(request.expectedRevision().equals(context.get("runtime.expectedRevision")),
                "Rendering the current revision must not silently refresh submission authority");
        var result = space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu.dispatch(context.player(), request);
        var key = context.<space.controlnet.ae2federation.policy.PolicyKey>get("runtime.disabledKey");
        var configured = space.controlnet.ae2federation.policy.PolicyService.get(context.level()).configured(key).orElseThrow();
        return result == space.controlnet.ae2federation.client.menu.FederationDomainPolicyActionResult.STALE_REVISION
                && !configured.rule().enabled() && configured.revision().value() == context.<Long>get("runtime.disabledRevision");
    }

    static void removeEndpointEnergySource(ServerContext context) {
        var consumer = FederationDomainRegistryAccess.confirmedNetworkId(provider(context).getMainNode().getGrid()).orElseThrow();
        var target = FederationDomainRegistryAccess.confirmedNetworkId(endpoint(context).getMainNode().getGrid()).orElseThrow();
        var key = new space.controlnet.ae2federation.policy.PolicyKey(consumer, target,
                space.controlnet.ae2federation.policy.PolicyCapability.ME_POWER);
        context.put("energy.diagnosticKey", key);
        var policies = space.controlnet.ae2federation.policy.PolicyService.get(context.level());
        var result = policies.edit(new space.controlnet.ae2federation.policy.PolicyEdit(key, policies.revision(key),
                space.controlnet.ae2federation.policy.PolicyRule.enabled(Set.of(
                        space.controlnet.ae2federation.policy.PolicyOperation.SUPPLY))));
        require(result instanceof space.controlnet.ae2federation.policy.PolicyMutationResult.Accepted,
                "Energy diagnostic rule must be accepted");
        var powerPosition = state(context).endpointPosition().west();
        require(context.level().getBlockState(powerPosition).is(appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block()),
                "Only the fixture's own creative energy cell may be removed");
        context.level().setBlockAndUpdate(powerPosition, Blocks.AIR.defaultBlockState());
    }

    static boolean energySourceUnavailable(ServerContext context) {
        var consumerGrid = provider(context).getMainNode().getGrid();
        var targetGrid = endpoint(context).getMainNode().getGrid();
        var key = context.<space.controlnet.ae2federation.policy.PolicyKey>get("energy.diagnosticKey");
        if (FederationDomainRegistryAccess.confirmedNetworkId(consumerGrid).filter(key.consumerNetworkId()::equals).isEmpty()
                || FederationDomainRegistryAccess.confirmedNetworkId(targetGrid).filter(key.providerNetworkId()::equals).isEmpty()) return false;
        space.controlnet.ae2federation.energy.EnergyBindingService.get(context.level()).observeConnectedGrids(consumerGrid, targetGrid);
        return space.controlnet.ae2federation.energy.EnergyBindingService.lastDiagnostic(context.level(), key)
                .filter(diagnostic -> diagnostic.reason() == space.controlnet.ae2federation.policy.BindingDiagnostic.Reason.ENERGY_SOURCE_MISSING)
                .isPresent();
    }

    static void verifyPolicySnapshotReads(ServerContext context) {
        var session = space.controlnet.ae2federation.client.policy.FederationDomainPolicySession.forDevice(
                context.player(), state(context).hostPosition());
        var storage = space.controlnet.ae2federation.storage.mount.StorageMountService.get(context.level());
        var before = storage.dependencyRefreshCount();
        var validations = storage.sourceValidationCount();
        for (int index = 0; index < 100; index++) session.ruleText();
        require(storage.dependencyRefreshCount() == before && storage.sourceValidationCount() == validations,
                "Reading policy details must not reconcile storage or validate operation authority");
    }

    static boolean dispatchRealWork(ServerContext context) {
        var provider = provider(context);
        var target = endpoint(context);
        if (Boolean.TRUE.equals(context.get("task33.workSent"))) {
            return target.getMainNode().getGrid().getStorageService().getInventory().extract(AEItemKey.of(Items.IRON_INGOT),
                    1, appeng.api.config.Actionable.SIMULATE, new appeng.me.helpers.BaseActionSource()) == 1;
        }
        var sourceId = FederationDomainRegistryAccess.confirmedNetworkId(provider.getMainNode().getGrid()).orElseThrow();
        var targetId = FederationDomainRegistryAccess.confirmedNetworkId(target.getMainNode().getGrid()).orElseThrow();
        var service = space.controlnet.ae2federation.policy.PolicyService.get(context.level());
        var key = new space.controlnet.ae2federation.policy.PolicyKey(sourceId, targetId,
                space.controlnet.ae2federation.policy.PolicyCapability.PROCESSING);
        if (service.configured(key).isEmpty()) {
            service.edit(new space.controlnet.ae2federation.policy.PolicyEdit(key, service.revision(key),
                    space.controlnet.ae2federation.policy.PolicyRule.enabled(Set.of(
                            space.controlnet.ae2federation.policy.PolicyOperation.EXECUTE,
                            space.controlnet.ae2federation.policy.PolicyOperation.SUPPLY))));
        }
        var pattern = PatternDetailsHelper.decodePattern(provider.getTerminalPatternInventory().getStackInSlot(1), context.level());
        var counter = new appeng.api.stacks.KeyCounter();
        counter.add(AEItemKey.of(Items.IRON_INGOT), 1);
        var lane = provider.laneFor(target.endpointIdentity()).orElseThrow();
        if (!provider.lane(lane).pushPattern(pattern, new appeng.api.stacks.KeyCounter[] {counter})) {
            var diagnostic = "sourceActive=" + provider.getMainNode().getNode().isActive()
                    + "; targetActive=" + target.getMainNode().getNode().isActive()
                    + "; patterns=" + provider.lane(lane).getAvailablePatterns().size()
                    + "; resolution=" + provider.runtime().orElseThrow().lastResolution();
            if (!diagnostic.equals(context.get("task33.dispatchDiagnostic"))) {
                context.put("task33.dispatchDiagnostic", diagnostic);
                com.mojang.logging.LogUtils.getLogger().info("GUI retention fixture awaiting native dispatch: {}", diagnostic);
            }
            return false;
        }
        context.put("task33.workSent", true);
        return target.getMainNode().getGrid().getStorageService().getInventory().extract(AEItemKey.of(Items.IRON_INGOT),
                1, appeng.api.config.Actionable.SIMULATE, new appeng.me.helpers.BaseActionSource()) == 1;
    }

    static boolean slotOneUnmapped(ServerContext context) {
        return provider(context).endpointsForSlot(1).isEmpty();
    }

    static boolean endpointRetained(ServerContext context) {
        return provider(context).retained(endpoint(context).endpointIdentity())
                && endpoint(context).claimState() instanceof space.controlnet.ae2federation.processing.claim.ClaimState.Owned;
    }

    static boolean endpointReleased(ServerContext context) {
        return !provider(context).retained(endpoint(context).endpointIdentity())
                && endpoint(context).claimState() instanceof space.controlnet.ae2federation.processing.claim.ClaimState.Unclaimed;
    }

    static void toggleExternalMapping(ServerContext context) {
        var provider = provider(context);
        var result = provider.toggleEndpoint(provider.mappedProvider().mappingHandle(0), endpoint(context).binding());
        if (!result.startsWith("accepted-")) {
            throw new IllegalStateException("External mapping edit failed: " + result);
        }
    }

    static String mappingLanes(ServerContext context) {
        return provider(context).mappedProvider().lanesForSlot(0).toString();
    }

    static String endpointNativeNetwork(ServerContext context) {
        return FederationDomainRegistryAccess.confirmedNetworkId(endpoint(context).getMainNode().getGrid())
                .orElseThrow().value().toString();
    }

    static long providerInstanceEpoch(ServerContext context) {
        return provider(context).providerIdentity().instanceEpoch().value();
    }

    static void positionRouterOverviewCamera(ServerContext context) {
        var router = TaskFifteenWorldFixture.routerPosition(context);
        positionCamera(context, router.south(4).west(2).above(2), router);
    }

    static void positionBridgeCamera(ServerContext context) {
        var bridge = TaskFifteenWorldFixture.bridgePosition(context);
        positionCamera(context, bridge.north(3).above(), bridge);
    }

    static void positionProviderCamera(ServerContext context) {
        var fixture = state(context);
        positionCamera(context, fixture.hostPosition().east(3).above(2), fixture.hostPosition());
    }

    static void positionEndpointCamera(ServerContext context) {
        var fixture = state(context);
        positionCamera(context, fixture.endpointPosition().east(2).south(3).above(2), fixture.endpointPosition());
    }

    static String providerId(ServerContext context) {
        return provider(context).providerIdentity().id().value().toString();
    }

    static String endpointId(ServerContext context) {
        var endpoint = endpoint(context);
        return endpoint == null ? "missing" : endpoint.endpointIdentity().id().value().toString();
    }

    static String claimConflict(ServerContext context) {
        var endpoint = endpoint(context);
        if (endpoint == null) {
            return "endpoint-missing";
        }
        return endpoint.claim(new ClaimRequest(endpoint.endpointIdentity(), endpoint.claimState().epoch(),
                new EndpointOwnerIdentity(ProviderIdentity.create()))).getClass().getSimpleName();
    }

    static void transferIdleClaimToCompetitor(ServerContext context) {
        var provider = provider(context);
        var endpoint = endpoint(context);
        var binding = endpoint.binding();
        for (int slot = 0; slot < provider.mappedProvider().patternInventory().size(); slot++) {
            if (provider.endpointsForSlot(slot).contains(endpoint.endpointIdentity())) {
                var result = provider.toggleEndpoint(provider.mappedProvider().mappingHandle(slot), binding);
                require(result.startsWith("accepted-"), "Idle test mapping must be removed before transfer: " + result);
            }
        }
        require(endpoint.claimState().owner().isEmpty(), "Idle endpoint must release its claim before transfer");
        var competitor = ProviderIdentity.create();
        var result = endpoint.claim(new ClaimRequest(endpoint.endpointIdentity(), endpoint.claimState().epoch(),
                new EndpointOwnerIdentity(competitor)));
        require(result instanceof space.controlnet.ae2federation.processing.claim.ClaimResult.Acquired,
                "Competing claim must be acquired through real authority");
        context.put("target.competitor", competitor.id().value().toString());
    }

    static void recordEndpointNavigation(ServerContext context) {
        var consumer = FederationDomainRegistryAccess.confirmedNetworkId(provider(context).getMainNode().getGrid()).orElseThrow();
        var source = FederationDomainRegistryAccess.confirmedNetworkId(endpoint(context).getMainNode().getGrid()).orElseThrow();
        context.put("navigation.consumer", consumer.value().toString());
        context.put("navigation.provider", source.value().toString());
        context.put("navigation.target", endpoint(context).endpointIdentity().id().value() + ":"
                + endpoint(context).endpointIdentity().instanceEpoch().value());
        var key = new space.controlnet.ae2federation.policy.PolicyKey(consumer, source,
                space.controlnet.ae2federation.policy.PolicyCapability.PROCESSING);
        context.put("navigation.policyRevision", space.controlnet.ae2federation.policy.PolicyService.get(context.level()).revision(key).value());
    }

    static boolean endpointNavigationReadOnly(ServerContext context) {
        var consumer = FederationDomainRegistryAccess.confirmedNetworkId(provider(context).getMainNode().getGrid()).orElseThrow();
        var source = FederationDomainRegistryAccess.confirmedNetworkId(endpoint(context).getMainNode().getGrid()).orElseThrow();
        var key = new space.controlnet.ae2federation.policy.PolicyKey(consumer, source,
                space.controlnet.ae2federation.policy.PolicyCapability.PROCESSING);
        return space.controlnet.ae2federation.policy.PolicyService.get(context.level()).revision(key).value()
                == context.<Long>get("navigation.policyRevision")
                && provider(context).endpointsForSlot(1).contains(endpoint(context).endpointIdentity())
                && endpoint(context).claimState().epoch().value() == 1;
    }

    static void placeSecondObservedEndpoint(ServerContext context) {
        var position = state(context).endpointPosition().east(2);
        require(context.level().isEmptyBlock(position), "Second Endpoint fixture position must be empty");
        var network = FederationDomainRegistryAccess.confirmedNetworkId(endpoint(context).getMainNode().getGrid()).orElseThrow();
        context.level().setBlockAndUpdate(position, ProcessingRegistration.ENDPOINT.get().defaultBlockState());
        context.put("endpoint.secondPosition", position);
        var endpoint = (EndpointBlockEntity) context.level().getBlockEntity(position);
        endpoint.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", network));
    }

    static boolean secondEndpointObserved(ServerContext context) {
        var position = context.<BlockPos>get("endpoint.secondPosition");
        var second = (EndpointBlockEntity) context.level().getBlockEntity(position);
        if (second == null || second.getMainNode().getNode() == null || second.binding() == null) return false;
        var original = endpoint(context).getMainNode().getNode();
        if (second.getMainNode().getGrid() != original.getGrid()) {
            GridHelper.createConnection(second.getMainNode().getNode(), original);
            return false;
        }
        if (!second.getMainNode().getNode().isActive() || !second.getMainNode().getNode().hasGridBooted()) return false;
        var session = space.controlnet.ae2federation.client.policy.FederationDomainPolicySession.forRouter(
                context.player(), TaskFifteenWorldFixture.routerPosition(context));
        var choices = com.google.gson.JsonParser.parseString(session.workspaceChoices()).getAsJsonObject().getAsJsonArray("endpoint");
        if (choices.size() != 2) return false;
        context.put("endpoint.secondId", second.endpointIdentity().id().value().toString());
        require(second.claimState().owner().isEmpty(), "Comparison Endpoint must be genuinely unclaimed");
        return true;
    }

    static void placeIsolatedEndpoint(ServerContext context) {
        var position = state(context).endpointPosition().east(4).south(3);
        require(context.level().isEmptyBlock(position) && context.level().isEmptyBlock(position.west()),
                "Isolated Endpoint fixture positions must be empty");
        context.put("endpoint.isolatedPosition", position);
        context.level().setBlockAndUpdate(position, ProcessingRegistration.ENDPOINT.get().defaultBlockState());
        context.level().setBlockAndUpdate(position.west(),
                appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        positionCamera(context, position.south(3), position);
    }

    static void openNewEndpointBeforeIdentitySettles(ServerContext context) {
        placeIsolatedEndpoint(context);
        var session = space.controlnet.ae2federation.client.policy.FederationDomainPolicySession.forDevice(
                context.player(), context.get("endpoint.isolatedPosition"));
        var choices = com.google.gson.JsonParser.parseString(session.workspaceChoices()).getAsJsonObject();
        require(choices.get("scope").getAsString().equals("unconfirmed") && !session.canSubmit(),
                "Newly placed production Endpoint must have unconfirmed identity and no edit authority before its first tick");
        openIsolatedEndpoint(context);
    }

    static boolean isolatedEndpointReady(ServerContext context) {
        var position = context.<BlockPos>get("endpoint.isolatedPosition");
        var endpoint = (EndpointBlockEntity) context.level().getBlockEntity(position);
        if (endpoint == null || endpoint.binding() == null || endpoint.getMainNode().getNode() == null) return false;
        var network = FederationDomainRegistryAccess.confirmedNetworkId(endpoint.getMainNode().getGrid());
        if (network.isEmpty() || !endpoint.getMainNode().getNode().isActive()) return false;
        var session = space.controlnet.ae2federation.client.policy.FederationDomainPolicySession.forDevice(context.player(), position);
        var choices = com.google.gson.JsonParser.parseString(session.workspaceChoices()).getAsJsonObject();
        if (!choices.get("scope").getAsString().equals("no_domain")) return false;
        require(session.context().isEmpty(), "Isolated Endpoint must have no editable domain");
        context.put("endpoint.isolatedId", endpoint.endpointIdentity().id().value().toString());
        context.put("endpoint.isolatedNetwork", network.orElseThrow().value().toString());
        return true;
    }

    static void openIsolatedEndpoint(ServerContext context) {
        space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu.openDevice(
                context.player(), context.get("endpoint.isolatedPosition"));
    }

    static void recordBridgeFocus(ServerContext context) {
        var bridge = TaskFifteenWorldFixture.multipartBridge(context).rightClickContext();
        var session = space.controlnet.ae2federation.client.policy.FederationDomainPolicySession.forBridge(context.player(), bridge);
        var scope = session.context().orElseThrow();
        var network = FederationDomainRegistryAccess.confirmedNetworkId(bridge.mainGrid()).orElseThrow();
        context.put("bridge.focus", space.controlnet.ae2federation.observability.id.MemberId
                .forNetwork(scope.federationDomainId(), network).value());
    }

    static void disconnectBridgeOuterSide(ServerContext context) {
        var bridge = TaskFifteenWorldFixture.multipartBridge(context);
        context.level().setBlockAndUpdate(TaskFifteenWorldFixture.bridgePosition(context).relative(bridge.getSide()),
                Blocks.AIR.defaultBlockState());
    }

    static boolean bridgeOuterSideMissing(ServerContext context) {
        return TaskFifteenWorldFixture.multipartBridge(context).operationalReason()
                == space.controlnet.ae2federation.bridge.BridgeOperationalReason.MISSING_OUTER_ATTACHMENT;
    }

    static String multipartAttachment(ServerContext context) {
        var bridge = TaskFifteenWorldFixture.multipartBridge(context);
        return bridge.getSide().getSerializedName() + ":bridge;cable-extension:"
                + (int) bridge.getCableConnectionLength(AECableType.GLASS);
    }

    private static void place(ServerContext context) {
        var hostPosition = TaskFifteenWorldFixture.routerPosition(context).south(2);
        var endpointPosition = hostPosition.south(3);
        var existing = GridHelper.getExposedNode(context.level(),
                TaskFifteenWorldFixture.mainNetworkCablePosition(context), Direction.UP);
        require(existing != null, "Task 33 Provider requires the settled main network cable");
        var networkId = FederationDomainRegistryAccess.confirmedNetworkId(existing.getGrid()).orElseThrow(
                () -> new IllegalStateException("Task 33 Provider requires confirmed main network identity"));
        var targetExisting = GridHelper.getExposedNode(context.level(), TaskFifteenWorldFixture.routerPosition(context).north(), Direction.UP);
        require(targetExisting != null, "Endpoint requires the settled outer network");
        var targetNetworkId = FederationDomainRegistryAccess.confirmedNetworkId(targetExisting.getGrid()).orElseThrow();
        // The production ME Federation Pattern Provider block; its Federation face points away from the Endpoint.
        context.level().setBlockAndUpdate(hostPosition, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP));
        context.level().setBlockAndUpdate(endpointPosition, ProcessingRegistration.ENDPOINT.get().defaultBlockState());
        // Connect the Provider's upward Federation port to the Router's physical domain.
        for (int north = 0; north <= 2; north++) {
            context.level().setBlockAndUpdate(hostPosition.above().north(north),
                    space.controlnet.ae2federation.router.RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
        }
        var endpoint = context.level().getBlockEntity(endpointPosition) instanceof EndpointBlockEntity value
                ? value
                : null;
        require(endpoint != null, "Task 33 Endpoint must be placed before identity seeding");
        endpoint.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", targetNetworkId));
        context.level().setBlockAndUpdate(hostPosition.west(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        context.level().setBlockAndUpdate(endpointPosition.west(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        context.level().setBlockAndUpdate(endpointPosition.south(), appeng.core.definitions.AEBlocks.ME_CHEST.block().defaultBlockState());
        ((appeng.blockentity.storage.MEChestBlockEntity) context.level().getBlockEntity(endpointPosition.south()))
                .setCell(appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
        var provider = (FederationPatternProviderBlockEntity) context.level().getBlockEntity(hostPosition);
        provider.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
        context.put(STATE, new State(hostPosition, endpointPosition, existing, targetExisting));
    }

    private static boolean ready(ServerContext context) {
        var state = state(context);
        var endpoint = endpoint(context);
        var provider = provider(context);
        if (endpoint == null || provider == null || endpoint.getMainNode().getNode() == null
                || provider.getMainNode().getNode() == null || provider.runtime().isEmpty()) {
            return false;
        }
        if (provider.getMainNode().getGrid() != state.existing.getGrid()) {
            GridHelper.createConnection(provider.getMainNode().getNode(), state.existing);
            return false;
        }
        if (endpoint.getMainNode().getGrid() != state.targetExisting.getGrid()) {
            GridHelper.createConnection(endpoint.getMainNode().getNode(), state.targetExisting);
            return false;
        }
        var binding = endpoint.binding();
        if (binding == null) {
            return false;
        }
        if (provider.getTerminalPatternInventory().getStackInSlot(0).isEmpty()) {
            // Inserted through the Provider's native Pattern inventory, as AE2's menu or Pattern Access Terminal do.
            provider.getTerminalPatternInventory().insertItem(0, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 4_000_000_000L)),
                    List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1))), false);
            provider.getTerminalPatternInventory().insertItem(1, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                    List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1))), false);
        }
        var providerNetwork = FederationDomainRegistryAccess.confirmedNetworkId(provider.getMainNode().getGrid())
                .orElse(null);
        var endpointNetwork = FederationDomainRegistryAccess.confirmedNetworkId(binding.subnetNode().getGrid())
                .orElse(null);
        var routerNode = FederationDomainRegistryAccess.nodeId(context.level(),
                TaskFifteenWorldFixture.routerPosition(context));
        var federationDomains = FederationDomainRegistryAccess.get(context.level()).snapshot().federationDomains()
                .values();
        if (providerNetwork == null || endpointNetwork == null || providerNetwork.equals(endpointNetwork) || !federationDomains.stream()
                .anyMatch(domain -> domain.nodes().contains(routerNode)
                        && domain.memberships().containsKey(providerNetwork) && domain.memberships().containsKey(endpointNetwork))) {
            return false;
        }
        if (!(endpoint.claimState() instanceof space.controlnet.ae2federation.processing.claim.ClaimState.Owned)) {
            // Pattern slot 1 maps the Endpoint through the production controller, which claims it (epoch 1).
            var status = provider.toggleEndpoint(provider.mappedProvider().mappingHandle(1), binding);
            require(status.startsWith("accepted-"), "Task 33 Endpoint mapping must be accepted: " + status);
        }
        return true;
    }

    private static EndpointBlockEntity endpoint(ServerContext context) {
        var state = state(context);
        return context.level().getBlockEntity(state.endpointPosition()) instanceof EndpointBlockEntity endpoint
                ? endpoint : null;
    }

    private static FederationPatternProviderBlockEntity provider(ServerContext context) {
        var state = state(context);
        return context.level().getBlockEntity(state.hostPosition()) instanceof FederationPatternProviderBlockEntity provider
                ? provider : null;
    }

    private static State state(ServerContext context) {
        var state = context.<State>get(STATE);
        if (state == null) {
            throw new IllegalStateException("Task 33 world was not arranged");
        }
        return state;
    }

    private static void cleanup(ServerContext context) {
        var state = context.<State>get(STATE);
        if (state == null) {
            return;
        }
        var nativeProvider = context.<BlockPos>get("crafting.nativeProviderPosition");
        if (nativeProvider != null) context.level().setBlockAndUpdate(nativeProvider, Blocks.AIR.defaultBlockState());
        var isolatedEndpoint = context.<BlockPos>get("endpoint.isolatedPosition");
        if (isolatedEndpoint != null) {
            context.level().setBlockAndUpdate(isolatedEndpoint, Blocks.AIR.defaultBlockState());
            context.level().setBlockAndUpdate(isolatedEndpoint.west(), Blocks.AIR.defaultBlockState());
        }
        var secondEndpoint = context.<BlockPos>get("endpoint.secondPosition");
        if (secondEndpoint != null) context.level().setBlockAndUpdate(secondEndpoint, Blocks.AIR.defaultBlockState());
        for (int north = 0; north <= 2; north++) {
            context.level().setBlockAndUpdate(state.hostPosition().above().north(north), Blocks.AIR.defaultBlockState());
        }
        context.level().setBlockAndUpdate(state.hostPosition().west(), Blocks.AIR.defaultBlockState());
        context.level().setBlockAndUpdate(state.endpointPosition().west(), Blocks.AIR.defaultBlockState());
        context.level().setBlockAndUpdate(state.endpointPosition().south(), Blocks.AIR.defaultBlockState());
        context.level().setBlockAndUpdate(state.endpointPosition(), Blocks.AIR.defaultBlockState());
        context.level().setBlockAndUpdate(state.hostPosition(), Blocks.AIR.defaultBlockState());
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new IllegalStateException(message);
        }
    }

    private static void positionCamera(ServerContext context, BlockPos camera, BlockPos target) {
        var player = context.player();
        player.connection.teleport(camera.getX() + 0.5, camera.getY() + 0.5, camera.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
    }

    private record State(BlockPos hostPosition, BlockPos endpointPosition,
            appeng.api.networking.IGridNode existing, appeng.api.networking.IGridNode targetExisting) {
    }
}
