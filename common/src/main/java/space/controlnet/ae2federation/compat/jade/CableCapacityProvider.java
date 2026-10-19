package space.controlnet.ae2federation.compat.jade;

import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;
import space.controlnet.ae2federation.bundle.BundleContacts;
import space.controlnet.ae2federation.bundle.BundledCables;
import space.controlnet.ae2federation.domain.CableCapacity;
import space.controlnet.ae2federation.domain.CableCapacityCounter;
import space.controlnet.ae2federation.domain.CableCapacityLimit;
import space.controlnet.ae2federation.router.FederationCableBlockEntity;

/**
 * A Federation Cable's capacity count against the server's limit, and whether that is its subdomain's or its domain's.
 * A bundled cable also names its cross-section and capacity, and its contacts with devices: the whole ones counted, the
 * partial ones in the error colour.
 */
enum CableCapacityProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("ae2federation", "cable_capacity");
    private static final String UNITS = "CapacityUnits";
    private static final String DOMAIN = "CapacityDomain";
    /** The enforced capacity of this cable; absent when capacity is not enforced or the cable has no limit. */
    private static final String LIMIT = "CapacityLimit";
    private static final String BUNDLE = "Bundle";
    /** Partial contacts shown at most; more add nothing a player can act on. */
    private static final int MAX_PARTIAL = 3;

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) return;
        var position = accessor.getPosition();
        int factor = FederationCableBlockEntity.capacityFactor(level, position);
        CableCapacity.demand(level, position).ifPresent(demand -> {
            data.putInt(UNITS, demand.units());
            data.putBoolean(DOMAIN, demand.scope() == CableCapacityCounter.Scope.DOMAIN);
            CableCapacityLimit.current(factor).ifPresent(limit -> data.putInt(LIMIT, limit));
        });
        BundledCables.get(level).at(position).ifPresent(membership -> {
            var bundle = new CompoundTag();
            var shape = membership.bundle().shape();
            bundle.putInt("narrow", shape.narrow());
            bundle.putInt("wide", shape.wide());
            bundle.putBoolean("unlimited", shape.multiplier().isEmpty());
            CableCapacityLimit.current(factor).ifPresent(limit -> bundle.putInt("capacity", limit));
            var summary = BundleContacts.summary(level, membership.bundle());
            bundle.putInt("ends", summary.ends());
            bundle.putInt("sides", summary.sides());
            var partial = new ListTag();
            summary.partial().stream().limit(MAX_PARTIAL).forEach(contact -> {
                var entry = new CompoundTag();
                entry.putLong("device", contact.firstDevice().asLong());
                entry.putInt("covered", contact.covered());
                entry.putInt("cells", contact.cells().size());
                partial.add(entry);
            });
            bundle.put("partial", partial);
            data.put(BUNDLE, bundle);
        });
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (data.contains(BUNDLE)) {
            var bundle = data.getCompound(BUNDLE);
            tooltip.replace(JadeIds.CORE_OBJECT_NAME, FederationJadeText.bundleTitle());
            tooltip.add(FederationJadeText.bundleShape(bundle.getInt("narrow"), bundle.getInt("wide"),
                    bundle.contains("capacity") ? OptionalInt.of(bundle.getInt("capacity")) : OptionalInt.empty(),
                    bundle.getBoolean("unlimited")));
        }
        if (data.contains(UNITS)) {
            tooltip.add(FederationJadeText.capacity(data.getInt(UNITS), data.contains(LIMIT)
                    ? OptionalInt.of(data.getInt(LIMIT)) : OptionalInt.empty(), data.getBoolean(DOMAIN)));
        }
        if (data.contains(BUNDLE)) {
            var bundle = data.getCompound(BUNDLE);
            tooltip.add(FederationJadeText.bundleConnected(bundle.getInt("ends"), bundle.getInt("sides")));
            for (var element : bundle.getList("partial", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) element;
                var device = net.minecraft.core.BlockPos.of(entry.getLong("device"));
                tooltip.add(FederationJadeText.bundlePartial(device.getX(), device.getY(), device.getZ(),
                        entry.getInt("covered"), entry.getInt("cells")));
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
