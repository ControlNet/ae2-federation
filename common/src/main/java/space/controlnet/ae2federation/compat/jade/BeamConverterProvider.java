package space.controlnet.ae2federation.compat.jade;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.power.BeamConverterBlockEntity;
import space.controlnet.ae2federation.power.OperatingPowerService;

/**
 * A Beam Converter block's buffer and, while its domain needs power, the FE it took in on the last tick and whether its
 * body matches the domain's largest cable cross-section.
 */
enum BeamConverterProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("ae2federation", "beam_converter");
    private static final String STORED = "ConverterStored";
    private static final String INPUT = "ConverterInput";
    private static final String MATCHES = "ConverterMatches";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)
                || !(accessor.getBlockEntity() instanceof BeamConverterBlockEntity converter)) {
            return;
        }
        var position = accessor.getPosition();
        data.putInt(STORED, (int) converter.stored());
        var dimension = level.dimension().location().toString();
        FederationDomainRegistryAccess.get(level).federationDomainOf(FederationDomainRegistryAccess.nodeId(level, position))
                .flatMap(domain -> OperatingPowerService.reading(level.getServer(), domain.federationDomainId()))
                .flatMap(reading -> reading.converters().stream()
                        .filter(entry -> entry.position().equals(position) && entry.dimension().equals(dimension))
                        .findFirst())
                .ifPresent(entry -> {
                    data.putInt(INPUT, (int) entry.input());
                    data.putBoolean(MATCHES, entry.matches());
                });
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (!data.contains(STORED)) return;
        tooltip.add(FederationJadeText.converter(data.getInt(STORED), (int) BeamConverterBlockEntity.CAPACITY,
                data.contains(INPUT) ? OptionalInt.of(data.getInt(INPUT)) : OptionalInt.empty(),
                data.contains(MATCHES) ? Optional.of(data.getBoolean(MATCHES)) : Optional.empty()));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
