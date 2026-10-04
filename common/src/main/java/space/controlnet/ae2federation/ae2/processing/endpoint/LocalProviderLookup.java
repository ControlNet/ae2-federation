package space.controlnet.ae2federation.ae2.processing.endpoint;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.block.crafting.PushDirection;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.helpers.IGridConnectedBlockEntity;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;

/**
 * Finds the pattern provider of another network that pushes into an Endpoint's Federation face: a block, or a cable part
 * on the face towards the Endpoint. A provider is whatever offers AE2's crafting-provider service on its grid node, as
 * AE2's own providers and every addon provider do, so the cable-part provider and addon providers with their own logic
 * qualify as AE2's block does. A Federation Pattern Provider never does: its front selects Federated mode.
 */
public final class LocalProviderLookup {
    private static final Map<Class<?>, Optional<Method>> TARGET_METHODS = new ConcurrentHashMap<>();

    private LocalProviderLookup() {
    }

    /** The provider at {@code providerPosition} that pushes towards {@code targetSide}, its face touching the Endpoint. */
    public static Optional<NativeLocalProvider> find(ServerLevel level, BlockPos providerPosition, Direction targetSide) {
        if (!level.isLoaded(providerPosition)) {
            return Optional.empty();
        }
        var entity = level.getBlockEntity(providerPosition);
        if (entity == null || entity instanceof FederationPatternProviderBlockEntity) {
            return Optional.empty();
        }
        Object host = entity instanceof IPartHost parts ? parts.getPart(targetSide) : entity;
        IGridNode node = host instanceof IPart part ? part.getGridNode()
                : host instanceof IGridConnectedBlockEntity connected ? connected.getMainNode().getNode() : null;
        var provider = node == null ? null : node.getService(ICraftingProvider.class);
        if (provider == null || !pushesTowards(level, providerPosition, host, targetSide)) {
            return Optional.empty();
        }
        // The return inventory the provider offers to blocks around it, which is how a machine returns output to it.
        // Some providers wrap it, as AE2 Lightning Tech's Overloaded provider charges power for outside returns.
        GenericInternalInventory returns = level.getCapability(AECapabilities.GENERIC_INTERNAL_INV, providerPosition,
                targetSide);
        if (returns == null && host instanceof PatternProviderLogicHost logicHost) {
            returns = logicHost.getLogic().getReturnInv();
        }
        return returns == null ? Optional.empty()
                : Optional.of(new NativeLocalProvider(provider, node, providerPosition, targetSide, returns));
    }

    /**
     * Whether a grid block or part on that face has no grid node yet, as on the tick it was placed: whether it is a
     * provider is known only once AE2 creates its node.
     */
    public static boolean awaitingNode(ServerLevel level, BlockPos providerPosition, Direction targetSide) {
        if (!level.isLoaded(providerPosition)) {
            return false;
        }
        var entity = level.getBlockEntity(providerPosition);
        if (entity == null || entity instanceof FederationPatternProviderBlockEntity) {
            return false;
        }
        Object host = entity instanceof IPartHost parts ? parts.getPart(targetSide) : entity;
        return host instanceof IPart part ? part.getGridNode() == null
                : host instanceof IGridConnectedBlockEntity connected && connected.getMainNode().getNode() == null;
    }

    /**
     * AE2's hosts name their push sides; Advanced AE's own host has the same method; a provider without one names its
     * push direction in its block state, as AE2's block does.
     */
    private static boolean pushesTowards(ServerLevel level, BlockPos position, Object host, Direction side) {
        if (host instanceof PatternProviderLogicHost logicHost) {
            return logicHost.getTargets().contains(side);
        }
        var targets = TARGET_METHODS.computeIfAbsent(host.getClass(), LocalProviderLookup::targetMethod);
        if (targets.isPresent()) {
            try {
                return targets.get().invoke(host) instanceof Collection<?> sides && sides.contains(side);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return false;
            }
        }
        var state = level.getBlockState(position);
        for (var property : state.getProperties()) {
            if (property instanceof EnumProperty<?> && property.getValueClass() == PushDirection.class) {
                var direction = ((PushDirection) state.getValue(property)).getDirection();
                return direction == null || direction == side;
            }
        }
        return true;
    }

    private static Optional<Method> targetMethod(Class<?> type) {
        try {
            var method = type.getMethod("getTargets");
            return Collection.class.isAssignableFrom(method.getReturnType()) ? Optional.of(method) : Optional.empty();
        } catch (NoSuchMethodException exception) {
            return Optional.empty();
        }
    }
}
