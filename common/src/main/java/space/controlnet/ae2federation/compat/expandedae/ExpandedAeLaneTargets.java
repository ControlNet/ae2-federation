package space.controlnet.ae2federation.compat.expandedae;

import appeng.api.networking.security.IActionSource;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.util.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.ae2.processing.AddonLaneTargets;

/**
 * ExpandedAE replaces the targets of every AE2 provider logic with its own {@code PatternProviderTargetCache}, which
 * carries its blocking modes. From 2.1.2 on that cache takes the provider's config manager and gives AE2's target type;
 * an older ExpandedAE, or none, leaves Lanes to AE2's own targets. This class only checks for ExpandedAE;
 * {@link ExpandedAeLaneTargetsDirect}, which calls it, is loaded only when ExpandedAE is there.
 */
public final class ExpandedAeLaneTargets implements AddonLaneTargets {
    private static final String MOD_ID = "expandedae";
    private static final DefaultArtifactVersion FIRST_SUPPORTED = new DefaultArtifactVersion("2.1.2");

    @Override
    public boolean available() {
        var mods = ModList.get();
        return mods != null && mods.getModContainerById(MOD_ID)
                .map(mod -> mod.getModInfo().getVersion().compareTo(FIRST_SUPPORTED) >= 0).orElse(false);
    }

    @Override
    public Object create(ServerLevel level, BlockPos position, Direction side, IActionSource source,
            ConfigManager config) {
        return ExpandedAeLaneTargetsDirect.create(level, position, side, source, config);
    }

    @Override
    public @Nullable PatternProviderTarget find(Object targets) {
        return ExpandedAeLaneTargetsDirect.find(targets);
    }
}
