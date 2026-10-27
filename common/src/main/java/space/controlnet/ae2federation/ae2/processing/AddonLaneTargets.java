package space.controlnet.ae2federation.ae2.processing;

import appeng.api.networking.security.IActionSource;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.util.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * An add-on that replaces the targets of every AE2 provider logic with its own, such as for its blocking modes, so a
 * Lane builds its Endpoint target the same way and blocks as a native provider does with that add-on.
 */
public interface AddonLaneTargets {
    /** Whether the add-on is present with the API this adapter knows. */
    boolean available();

    /** The add-on's targets of the block at {@code position}, reached from {@code side}. */
    Object create(ServerLevel level, BlockPos position, Direction side, IActionSource source, ConfigManager config)
            throws ReflectiveOperationException;

    /** The target {@code targets}, made by {@link #create}, push into now. */
    @Nullable PatternProviderTarget find(Object targets) throws ReflectiveOperationException;
}
