package space.controlnet.ae2federation.compat.expandedae;

import appeng.api.networking.security.IActionSource;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.util.ConfigManager;
import lu.kolja.expandedae.helper.pattern.PatternProviderTargetCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** {@link ExpandedAeLaneTargets} through ExpandedAE's own type; loaded only when ExpandedAE is installed. */
final class ExpandedAeLaneTargetsDirect {
    private ExpandedAeLaneTargetsDirect() {
    }

    static Object create(ServerLevel level, BlockPos position, Direction side, IActionSource source,
            ConfigManager config) {
        return new PatternProviderTargetCache(level, position, side, source, config);
    }

    static @Nullable PatternProviderTarget find(Object targets) {
        return ((PatternProviderTargetCache) targets).find();
    }
}
