package space.controlnet.ae2federation.compat.jade;

import java.util.Locale;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
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
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.quantum.QuantumCubeBlockEntity;
import space.controlnet.ae2federation.quantum.QuantumLinkService;
import space.controlnet.ae2federation.quantum.QuantumPairIndex;
import space.controlnet.ae2federation.router.RouterBlock;

/** A formed Quantum Cube's link, and its capacity count against its square's limit as a cable of that square shows. */
enum QuantumCubeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("ae2federation", "quantum_cube");
    private static final String LINK = "QuantumLink";
    private static final String UNITS = "QuantumUnits";
    private static final String DOMAIN = "QuantumDomain";
    private static final String LIMIT = "QuantumLimit";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)
                || !(accessor.getBlockEntity() instanceof QuantumCubeBlockEntity cube)
                || !RouterBlock.formed(cube.getBlockState())) {
            return;
        }
        var status = QuantumLinkService.get(level.getServer())
                .status(FederationDomainRegistryAccess.dimension(level), cube.bodyMin());
        if (status == null) return;
        var link = new CompoundTag();
        link.putString("kind", status.kind().name().toLowerCase(Locale.ROOT));
        if (status.partner() != null) {
            link.putString("dimension", status.partner().dimension());
            link.putLong("at", status.partner().min().asLong());
        } else if (status.kind() == QuantumPairIndex.Status.Kind.PARTNER_NOT_LOADED && cube.boundTo() != null) {
            link.putString("dimension", cube.boundTo().dimension().location().toString());
            link.putLong("at", cube.boundTo().pos().asLong());
        }
        data.put(LINK, link);
        var node = FederationDomainRegistryAccess.nodeId(level, accessor.getPosition());
        CableCapacity.demand(level, node).ifPresent(demand -> {
            data.putInt(UNITS, demand.units());
            data.putBoolean(DOMAIN, demand.scope() == CableCapacityCounter.Scope.DOMAIN);
            CableCapacityLimit.current(cube.capacityFactor()).ifPresent(limit -> data.putInt(LIMIT, limit));
        });
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (!data.contains(LINK)) return;
        var link = data.getCompound(LINK);
        var at = link.contains("at") ? BlockPos.of(link.getLong("at")) : null;
        tooltip.add(FederationJadeText.quantumLink(link.getString("kind"), link.getString("dimension"), at));
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
