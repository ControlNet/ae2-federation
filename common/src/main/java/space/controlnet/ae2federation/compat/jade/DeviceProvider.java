package space.controlnet.ae2federation.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import space.controlnet.ae2federation.router.DeviceStructure;
import space.controlnet.ae2federation.router.DeviceStructureUpdater;
import space.controlnet.ae2federation.router.DeviceSummary;

/**
 * A Router, Switch or Beam Converter: its body's size or why it does not form, and the networks a Switch body attaches.
 */
enum DeviceProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("ae2federation", "device");
    private static final String BODY = "DeviceBody";
    private static final String SIZE = "DeviceSize";
    private static final String NETWORKS = "DeviceNetworks";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) return;
        var summary = DeviceSummary.of(level, accessor.getPosition());
        switch (summary.body()) {
            case DeviceStructure.Valid valid -> {
                data.putString(BODY, "valid");
                var size = valid.max().subtract(valid.min());
                data.putIntArray(SIZE, new int[] {size.getX() + 1, size.getY() + 1, size.getZ() + 1});
            }
            case DeviceStructure.Invalid invalid -> data.putString(BODY,
                    DeviceStructureUpdater.reasonKey(level.getBlockState(accessor.getPosition()).getBlock(),
                            invalid.reason()));
            case DeviceStructure.Unknown unknown -> data.putString(BODY, "unloaded");
        }
        summary.networks().ifPresent(count -> data.putInt(NETWORKS, count));
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (data.contains(NETWORKS)) tooltip.add(FederationJadeText.networks(data.getInt(NETWORKS)));
        if (!data.contains(BODY)) return;
        var size = data.getIntArray(SIZE);
        FederationJadeText.body(data.getString(BODY), size.length == 3 ? size[0] : 1, size.length == 3 ? size[1] : 1,
                size.length == 3 ? size[2] : 1).ifPresent(tooltip::add);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
