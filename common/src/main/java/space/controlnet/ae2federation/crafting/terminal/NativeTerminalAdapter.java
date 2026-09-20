package space.controlnet.ae2federation.crafting.terminal;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyFilterMode;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyResource;
import space.controlnet.ae2federation.policy.PolicyService;

public final class NativeTerminalAdapter {
    private NativeTerminalAdapter() {
    }

    public static Optional<NativeTerminalSession> discover(ServerLevel level, IGrid consumerGrid,
            NetworkId providerNetworkId, IActionSource actionSource) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Native terminal discovery is server-thread owned");
        }
        var consumerNetworkId = FabricRegistryAccess.confirmedNetworkId(consumerGrid);
        if (consumerNetworkId.isEmpty()) {
            return Optional.empty();
        }
        var key = new PolicyKey(consumerNetworkId.orElseThrow(), providerNetworkId, PolicyCapability.CRAFTING);
        var binding = CraftingBindingService.get(level).capability(key);
        if (binding.isEmpty()) {
            return Optional.empty();
        }
        var configured = PolicyService.get(level).configured(key);
        if (configured.isEmpty() || !configured.orElseThrow().rule().enabled()
                || !configured.orElseThrow().rule().operations().contains(PolicyOperation.REQUEST)) {
            return Optional.empty();
        }
        var snapshot = binding.orElseThrow().submissionSnapshot();
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        var filter = configured.orElseThrow().rule().filter();
        Set<AEKey> craftables = snapshot.orElseThrow().service().getCraftables(keyToCheck -> permits(filter, keyToCheck));
        var authority = CraftingBindingService.get(level);
        return Optional.of(new NativeTerminalSession(level, authority, snapshot.orElseThrow(), actionSource, craftables));
    }

    private static boolean permits(PolicyFilter filter, AEKey key) {
        var resource = new PolicyResource(key.getType().getId(), key.getId());
        return switch (filter.mode()) {
            case ALL -> true;
            case ALLOW_LIST -> filter.entries().contains(resource);
            case DENY_LIST -> !filter.entries().contains(resource);
        };
    }
}
