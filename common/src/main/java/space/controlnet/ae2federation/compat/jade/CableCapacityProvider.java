package space.controlnet.ae2federation.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import space.controlnet.ae2federation.domain.CableCapacity;
import space.controlnet.ae2federation.domain.CableCapacityCounter;
import space.controlnet.ae2federation.domain.CableCapacityLimit;

/**
 * One line on a Federation Cable: the capacity it counts against the server's limit, and whether that is its
 * subdomain's or its domain's.
 */
enum CableCapacityProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("ae2federation", "cable_capacity");
    private static final String UNITS = "CapacityUnits";
    private static final String DOMAIN = "CapacityDomain";
    /** The enforced capacity of one cable; absent when capacity is not enforced. */
    private static final String LIMIT = "CapacityLimit";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) return;
        CableCapacity.demand(level, accessor.getPosition()).ifPresent(demand -> {
            data.putInt(UNITS, demand.units());
            data.putBoolean(DOMAIN, demand.scope() == CableCapacityCounter.Scope.DOMAIN);
            CableCapacityLimit.current().ifPresent(limit -> data.putInt(LIMIT, limit));
        });
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (!data.contains(UNITS)) return;
        tooltip.add(FederationJadeText.capacity(data.getInt(UNITS), data.contains(LIMIT)
                ? java.util.OptionalInt.of(data.getInt(LIMIT)) : java.util.OptionalInt.empty(), data.getBoolean(DOMAIN)));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
