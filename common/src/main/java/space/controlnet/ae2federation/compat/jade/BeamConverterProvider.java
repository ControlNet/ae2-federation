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
 * A Beam Converter's body buffer and, while its domain needs power, the FE the body took in on the last tick and whether
 * it matches the domain's largest cable cross-section.
 */
enum BeamConverterProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("ae2federation", "beam_converter");
    private static final String STORED = "ConverterStored";
    private static final String CAPACITY = "ConverterCapacity";
    private static final String INPUT = "ConverterInput";
    private static final String MATCHES = "ConverterMatches";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)
                || !(accessor.getBlockEntity() instanceof BeamConverterBlockEntity converter)) {
            return;
        }
        var position = accessor.getPosition();
        var body = converter.body();
        data.putInt(STORED, (int) body.stream().mapToDouble(BeamConverterBlockEntity::stored).sum());
        data.putInt(CAPACITY, (int) (body.size() * BeamConverterBlockEntity.CAPACITY));
        var dimension = level.dimension().location().toString();
        var positions = body.stream().map(BeamConverterBlockEntity::getBlockPos).collect(java.util.stream.Collectors.toSet());
        FederationDomainRegistryAccess.get(level).federationDomainOf(FederationDomainRegistryAccess.nodeId(level, position))
                .flatMap(domain -> OperatingPowerService.reading(level.getServer(), domain.federationDomainId()))
                .ifPresent(reading -> {
                    var entries = reading.converters().stream()
                            .filter(entry -> positions.contains(entry.position()) && entry.dimension().equals(dimension))
                            .toList();
                    if (entries.isEmpty()) return;
                    data.putInt(INPUT, (int) entries.stream().mapToDouble(OperatingPowerService.ConverterReading::input).sum());
                    data.putBoolean(MATCHES, entries.get(0).matches());
                });
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (!data.contains(STORED)) return;
        tooltip.add(FederationJadeText.converter(data.getInt(STORED), data.contains(CAPACITY) ? data.getInt(CAPACITY)
                        : (int) BeamConverterBlockEntity.CAPACITY,
                data.contains(INPUT) ? OptionalInt.of(data.getInt(INPUT)) : OptionalInt.empty(),
                data.contains(MATCHES) ? Optional.of(data.getBoolean(MATCHES)) : Optional.empty()));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
