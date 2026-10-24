package space.controlnet.ae2federation.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainRecords;

/**
 * The overworld's {@link FederationDomainRecords}, so a Federation domain keeps its id across restarts. Each dimension
 * name is written once, in a table the anchor nodes refer to by index.
 */
public final class FederationDomainSavedData extends SavedData {
    private static final String DATA_NAME = "ae2federation_domain_ids";

    private final FederationDomainRecords records;

    public FederationDomainSavedData(FederationDomainRecords records) {
        this.records = records;
        records.onChange(this::setDirty);
    }

    public static FederationDomainSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(
                () -> new FederationDomainSavedData(new FederationDomainRecords()), FederationDomainSavedData::load),
                DATA_NAME);
    }

    public FederationDomainRecords records() {
        return records;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var dimensions = new ListTag();
        var index = new HashMap<String, Integer>();
        var domains = new ListTag();
        records.all().forEach((sequence, anchors) -> {
            var domain = new CompoundTag();
            domain.putLong("sequence", sequence);
            domain.put("devices", nodes(anchors.devices(), dimensions, index));
            domain.put("cables", nodes(anchors.cables(), dimensions, index));
            domains.add(domain);
        });
        tag.putLong("sequence", records.sequence());
        tag.put("dimensions", dimensions);
        tag.put("domains", domains);
        return tag;
    }

    public static FederationDomainSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var records = new FederationDomainRecords();
        var dimensions = new ArrayList<String>();
        tag.getList("dimensions", Tag.TAG_STRING).forEach(dimension -> dimensions.add(dimension.getAsString()));
        for (var entry : tag.getList("domains", Tag.TAG_COMPOUND)) {
            var domain = (CompoundTag) entry;
            records.put(domain.getLong("sequence"), nodes(domain.getList("devices", Tag.TAG_COMPOUND), dimensions),
                    nodes(domain.getList("cables", Tag.TAG_COMPOUND), dimensions));
        }
        records.advanceSequence(tag.getLong("sequence"));
        return new FederationDomainSavedData(records);
    }

    private static ListTag nodes(Set<FederationDomainNodeId> nodes, ListTag dimensions, java.util.Map<String, Integer> index) {
        var list = new ListTag();
        for (var node : nodes) {
            var saved = new CompoundTag();
            saved.putInt("d", index.computeIfAbsent(node.dimension(), dimension -> {
                dimensions.add(StringTag.valueOf(dimension));
                return dimensions.size() - 1;
            }));
            saved.putLong("p", node.blockPosition());
            if (!node.part().isEmpty()) {
                saved.putString("s", node.part());
            }
            list.add(saved);
        }
        return list;
    }

    private static Set<FederationDomainNodeId> nodes(ListTag list, List<String> dimensions) {
        var nodes = new TreeSet<FederationDomainNodeId>();
        for (var entry : list) {
            var saved = (CompoundTag) entry;
            nodes.add(new FederationDomainNodeId(dimensions.get(saved.getInt("d")), saved.getLong("p"),
                    saved.getString("s")));
        }
        return nodes;
    }
}
