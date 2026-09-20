package space.controlnet.ae2federation.test.crafting;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.crafting.binding.CraftingCapabilityBinding;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.policy.PolicyKey;

public final class NativeCraftingAuthorityReceipt {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeCraftingAuthorityReceipt.class);

    private NativeCraftingAuthorityReceipt() {
    }

    public static void captureCurrent(AuthorityLabel label, ServerLevel level, CraftingCapabilityBinding binding) {
        requireSelected(label);
        var grid = binding.sourceGrid().orElseThrow();
        var observation = observe(grid);
        var source = binding.providerSources().getFirst();
        if (binding.providerSources().size() != 1 || source.node() != observation.node()
                || source.provider() != observation.provider()
                || !source.registrationNodeId().equals(observation.nodeId())
                || binding.nativeService().orElseThrow() != observation.service()
                || !sameIdentity(binding.nativeCpus(), observation.cpus())) {
            throw new IllegalStateException("Crafting binding does not expose the directly observed native authority");
        }
        var registry = FabricRegistryAccess.get(level);
        LOGGER.info("AE2F_CRAFT_AUTHORITY testId={} selected={} phase={} networkId={} grid={} service={} "
                        + "nodeId={} node={} provider={} cpus={} pattern={} binding={} generation={} fabrics={} "
                        + "topology={} commonFabrics={} withdrawals={} access=current capability=true",
                label.testId(), selectedTest(), label.phase(), observation.networkId(), identity(grid),
                identity(observation.service()), observation.nodeId(), identity(observation.node()),
                identity(observation.provider()), observation.cpuIdentities(), identity(observation.pattern()),
                identity(binding), binding.revision().providerGeneration().value(), fabricReferences(binding),
                registry.snapshot().topologyRevision(), commonFabricCount(registry, binding.relationship().key()),
                CraftingBindingService.get(level).withdrawalCount());
    }

    public static void captureUnavailable(AuthorityLabel label, ServerLevel level, IGrid grid) {
        requireSelected(label);
        var observation = observe(grid);
        if (!observation.cpus().isEmpty()) {
            throw new IllegalStateException("Unavailable authority receipt requires zero native CPUs");
        }
        LOGGER.info("AE2F_CRAFT_AUTHORITY testId={} selected={} phase={} networkId={} grid={} service={} "
                        + "nodeId={} node={} provider={} cpus={} pattern={} binding=none generation=none fabrics=none "
                        + "topology={} commonFabrics=0 withdrawals={} access=unavailable capability=false",
                label.testId(), selectedTest(), label.phase(), observation.networkId(), identity(grid),
                identity(observation.service()), observation.nodeId(), identity(observation.node()),
                identity(observation.provider()), observation.cpuIdentities(), identity(observation.pattern()),
                FabricRegistryAccess.get(level).snapshot().topologyRevision(),
                CraftingBindingService.get(level).withdrawalCount());
    }

    public static void captureWithdrawal(AuthorityLabel label, WithdrawalObservation withdrawal) {
        requireSelected(label);
        var binding = withdrawal.binding();
        var service = withdrawal.service();
        var registry = FabricRegistryAccess.get(withdrawal.level());
        var denied = !binding.isCurrent() && binding.nativeService().isEmpty() && binding.sourceGrid().isEmpty()
                && binding.nativeProviders().isEmpty() && binding.providerSources().isEmpty()
                && binding.nativeCpus().isEmpty() && service.capability(withdrawal.key()).isEmpty();
        LOGGER.info("AE2F_CRAFT_AUTHORITY testId={} selected={} phase={} networkId={} grid={} service={} "
                        + "nodeId=withdrawn node=withdrawn provider=withdrawn cpus=none pattern=withdrawn binding={} "
                        + "generation={} fabrics={} topology={} commonFabrics={} withdrawals={} access={} capability=false",
                label.testId(), selectedTest(), label.phase(), withdrawal.key().providerNetworkId(),
                identity(withdrawal.sourceGrid()), identity(withdrawal.sourceGrid().getCraftingService()), identity(binding),
                binding.revision().providerGeneration().value(), fabricReferences(binding),
                registry.snapshot().topologyRevision(), commonFabricCount(registry, withdrawal.key()),
                service.withdrawalCount(), denied ? "withdrawn" : "exposed");
    }

    private static NativeObservation observe(IGrid grid) {
        var providers = new java.util.ArrayList<appeng.api.networking.IGridNode>();
        for (var node : grid.getNodes()) {
            if (node.getGrid() == grid && node.isActive() && node.hasGridBooted()
                    && node.getService(ICraftingProvider.class) != null) {
                providers.add(node);
            }
        }
        if (providers.size() != 1) {
            throw new IllegalStateException("Authority receipt requires exactly one active native crafting provider");
        }
        var node = providers.getFirst();
        var provider = node.getService(ICraftingProvider.class);
        var patterns = provider.getAvailablePatterns();
        if (patterns.size() != 1) {
            throw new IllegalStateException("Authority receipt requires exactly one native pattern object");
        }
        var identityService = grid.getService(NetworkIdentityService.class);
        var networkId = FabricRegistryAccess.confirmedNetworkId(grid).orElseThrow();
        var service = grid.getCraftingService();
        return new NativeObservation(networkId.toString(), service, node,
                identityService.lineage(node).nodeId(), provider, java.util.List.copyOf(service.getCpus()),
                patterns.getFirst());
    }

    private static boolean sameIdentity(Iterable<?> first, Iterable<?> second) {
        var firstIds = identities(first);
        return firstIds.equals(identities(second));
    }

    private static String identities(Iterable<?> values) {
        var identities = new java.util.ArrayList<String>();
        values.forEach(value -> identities.add(identity(value)));
        identities.sort(Comparator.naturalOrder());
        return identities.isEmpty() ? "none" : String.join(",", identities);
    }

    private static String fabricReferences(CraftingCapabilityBinding binding) {
        return binding.revision().fabrics().stream()
                .map(reference -> reference.fabricId().value() + "@" + reference.generation())
                .sorted().collect(java.util.stream.Collectors.joining(","));
    }

    private static long commonFabricCount(space.controlnet.ae2federation.fabric.FabricRegistry registry, PolicyKey key) {
        var provider = registry.fabricsFor(key.providerNetworkId());
        return registry.fabricsFor(key.consumerNetworkId()).stream().filter(provider::contains).count();
    }

    private static void requireSelected(AuthorityLabel label) {
        if (!selectedTest().equals(label.testId())) {
            throw new IllegalArgumentException("Authority receipt test ID does not match the selected child");
        }
    }

    private static String selectedTest() {
        return System.getProperty("ae2federation.testId", "");
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    public record AuthorityLabel(String testId, String phase) {
    }

    public record WithdrawalObservation(ServerLevel level, PolicyKey key, IGrid sourceGrid,
            CraftingCapabilityBinding binding, CraftingBindingService service) {
    }

    private record NativeObservation(String networkId, Object service, Object node, Object nodeId, Object provider,
            java.util.List<?> cpus, Object pattern) {
        private String cpuIdentities() {
            return identities(cpus);
        }
    }
}
