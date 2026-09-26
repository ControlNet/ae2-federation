package space.controlnet.ae2federation.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyFilterMode;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRecord;
import space.controlnet.ae2federation.policy.PolicyResource;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyStoreSnapshot;

final class PolicyStateCodec {
    private static final int SCHEMA_VERSION = 1;

    private PolicyStateCodec() {
    }

    static CompoundTag save(PolicyStoreSnapshot snapshot, CompoundTag tag) {
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putLong("highWatermark", snapshot.highWatermark().value());
        var encoded = new ListTag();
        snapshot.entries().values().stream().sorted(Comparator.comparing(PolicyStateCodec::sortKey))
                .map(PolicyStateCodec::saveRecord).forEach(encoded::add);
        tag.put("entries", encoded);
        return tag;
    }

    static PolicyStoreSnapshot load(CompoundTag tag) {
        if (tag.getInt("schema") != SCHEMA_VERSION || tag.getLong("highWatermark") < 0) {
            throw new IllegalArgumentException("Unsupported or malformed policy persistence schema");
        }
        var highWatermark = new PolicyRevision(tag.getLong("highWatermark"));
        var entries = new HashMap<PolicyKey, PolicyRecord>();
        for (var raw : tag.getList("entries", Tag.TAG_COMPOUND)) {
            var record = loadRecord((CompoundTag) raw, highWatermark);
            if (entries.put(record.key(), record) != null) {
                throw new IllegalArgumentException("Duplicate persisted policy key");
            }
        }
        return new PolicyStoreSnapshot(highWatermark, entries);
    }

    private static CompoundTag saveRecord(PolicyRecord record) {
        var tag = new CompoundTag();
        tag.putUUID("consumer", record.key().consumerNetworkId().value());
        tag.putUUID("provider", record.key().providerNetworkId().value());
        tag.putString("capability", record.key().capability().name());
        tag.putLong("revision", record.revision().value());
        tag.putBoolean("deleted", record instanceof PolicyRecord.Deleted);
        if (record instanceof PolicyRecord.Configured configured) {
            saveRule(tag, configured.rule());
        }
        return tag;
    }

    private static void saveRule(CompoundTag tag, PolicyRule rule) {
        tag.putBoolean("enabled", rule.enabled());
        tag.putBoolean("allowReexport", rule.allowReexport());
        tag.putString("filterMode", rule.filter().mode().name());
        var operations = new ListTag();
        rule.operations().stream().map(Enum::name).sorted().map(StringTag::valueOf).forEach(operations::add);
        tag.put("operations", operations);
        var resources = new ListTag();
        rule.filter().entries().stream().sorted(Comparator.comparing(PolicyStateCodec::sortResource))
                .map(PolicyStateCodec::saveResource).forEach(resources::add);
        tag.put("resources", resources);
    }

    private static PolicyRecord loadRecord(CompoundTag tag, PolicyRevision highWatermark) {
        if (!tag.hasUUID("consumer") || !tag.hasUUID("provider") || tag.getLong("revision") < 1) {
            throw new IllegalArgumentException("Malformed persisted policy identity or revision");
        }
        var key = new PolicyKey(new NetworkId(tag.getUUID("consumer")), new NetworkId(tag.getUUID("provider")),
                PolicyCapability.valueOf(tag.getString("capability")));
        var revision = new PolicyRevision(tag.getLong("revision"));
        if (revision.compareTo(highWatermark) > 0) {
            throw new IllegalArgumentException("Persisted policy revision exceeds its high watermark");
        }
        return tag.getBoolean("deleted") ? new PolicyRecord.Deleted(key, revision)
                : new PolicyRecord.Configured(key, revision, loadRule(tag));
    }

    private static PolicyRule loadRule(CompoundTag tag) {
        var operations = new HashSet<PolicyOperation>();
        for (var raw : tag.getList("operations", Tag.TAG_STRING)) {
            operations.add(PolicyOperation.valueOf(raw.getAsString()));
        }
        var resources = new HashSet<PolicyResource>();
        for (var raw : tag.getList("resources", Tag.TAG_COMPOUND)) {
            var resource = (CompoundTag) raw;
            resources.add(new PolicyResource(ResourceLocation.parse(resource.getString("type")),
                    ResourceLocation.parse(resource.getString("id"))));
        }
        return new PolicyRule(tag.getBoolean("enabled"), operations,
                new PolicyFilter(PolicyFilterMode.valueOf(tag.getString("filterMode")), resources),
                tag.getBoolean("allowReexport"));
    }

    private static CompoundTag saveResource(PolicyResource resource) {
        var tag = new CompoundTag();
        tag.putString("type", resource.resourceType().toString());
        tag.putString("id", resource.resourceId().toString());
        return tag;
    }

    private static String sortKey(PolicyRecord record) {
        return record.key().consumerNetworkId().value() + ":" + record.key().providerNetworkId().value()
                + ":" + record.key().capability().name();
    }

    private static String sortResource(PolicyResource resource) {
        return resource.resourceType() + ":" + resource.resourceId();
    }
}
