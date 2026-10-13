package space.controlnet.ae2federation.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;

/** A Federation Pattern Provider's patterns and mappings, as the header of its own screen counts them. */
enum PatternProviderProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("ae2federation", "pattern_provider");
    private static final String SUMMARY = "ProviderPatterns";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof FederationPatternProviderBlockEntity provider)) return;
        var summary = provider.patternSummary();
        data.putIntArray(SUMMARY, new int[] {summary.used(), summary.total(), summary.mappings()});
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var summary = accessor.getServerData().getIntArray(SUMMARY);
        if (summary.length == 3) tooltip.add(FederationJadeText.provider(summary[0], summary[1], summary[2]));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
