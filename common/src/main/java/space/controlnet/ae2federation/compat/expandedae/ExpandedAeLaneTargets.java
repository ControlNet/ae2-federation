package space.controlnet.ae2federation.compat.expandedae;

import appeng.api.networking.security.IActionSource;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.util.ConfigManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.ae2.processing.AddonLaneTargets;

/**
 * ExpandedAE replaces the targets of every AE2 provider logic with its own {@code PatternProviderTargetCache}, which
 * carries its blocking modes. Absent without it.
 */
public final class ExpandedAeLaneTargets implements AddonLaneTargets {
    private static final @Nullable Constructor<?> TARGETS = targets();
    private static final @Nullable Method FIND = TARGETS == null ? null : method(TARGETS.getDeclaringClass(), "find");

    @Override
    public boolean available() {
        return FIND != null;
    }

    @Override
    public Object create(ServerLevel level, BlockPos position, Direction side, IActionSource source,
            ConfigManager config) throws ReflectiveOperationException {
        return TARGETS.newInstance(level, position, side, source, config);
    }

    @Override
    public @Nullable PatternProviderTarget find(Object targets) throws ReflectiveOperationException {
        return (PatternProviderTarget) FIND.invoke(targets);
    }

    private static @Nullable Constructor<?> targets() {
        try {
            return Class.forName("lu.kolja.expandedae.helper.pattern.PatternProviderTargetCache").getConstructor(
                    ServerLevel.class, BlockPos.class, Direction.class, IActionSource.class, ConfigManager.class);
        } catch (ReflectiveOperationException | LinkageError absent) {
            return null;
        }
    }

    private static @Nullable Method method(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }
}
