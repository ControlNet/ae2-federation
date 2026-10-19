package space.controlnet.ae2federation.compat.jade;

import java.util.Locale;
import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import space.controlnet.ae2federation.beam.BeamFormerBlockEntity;
import space.controlnet.ae2federation.beam.BeamLinkService;
import space.controlnet.ae2federation.domain.CableCapacity;
import space.controlnet.ae2federation.domain.CableCapacityCounter;
import space.controlnet.ae2federation.domain.CableCapacityLimit;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.router.RouterBlock;

/** A formed Beam Former's beam, and its capacity count against its square's limit as a cable of that square shows. */
enum BeamFormerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("ae2federation", "beam_former");
    private static final String BEAM = "Beam";
    private static final String UNITS = "BeamUnits";
    private static final String DOMAIN = "BeamDomain";
    private static final String LIMIT = "BeamLimit";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)
                || !(accessor.getBlockEntity() instanceof BeamFormerBlockEntity former)
                || !RouterBlock.formed(former.getBlockState())) {
            return;
        }
        var status = BeamLinkService.get(level).status(former.bodyMin());
        var beam = new CompoundTag();
        beam.putString("kind", status.kind().name().toLowerCase(Locale.ROOT));
        beam.putInt("value", status.value());
        if (status.blocker() != null) beam.putLong("blocker", status.blocker().asLong());
        data.put(BEAM, beam);
        var node = FederationDomainRegistryAccess.nodeId(level, accessor.getPosition());
        CableCapacity.demand(level, node).ifPresent(demand -> {
            data.putInt(UNITS, demand.units());
            data.putBoolean(DOMAIN, demand.scope() == CableCapacityCounter.Scope.DOMAIN);
            CableCapacityLimit.current(former.capacityFactor()).ifPresent(limit -> data.putInt(LIMIT, limit));
        });
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (!data.contains(BEAM)) return;
        var beam = data.getCompound(BEAM);
        var blocker = net.minecraft.core.BlockPos.of(beam.getLong("blocker"));
        tooltip.add(FederationJadeText.beam(beam.getString("kind"), beam.getInt("value"), blocker.getX(), blocker.getY(),
                blocker.getZ()));
        if (data.contains(UNITS)) {
            tooltip.add(FederationJadeText.capacity(data.getInt(UNITS),
                    data.contains(LIMIT) ? OptionalInt.of(data.getInt(LIMIT)) : OptionalInt.empty(),
                    data.getBoolean(DOMAIN)));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
