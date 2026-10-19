package space.controlnet.ae2federation.automation;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import space.controlnet.ae2federation.function.Candidate;

/**
 * Resources as AE2 sees them: an ID is a resource of every AE2 key type that reads it back as itself, so addon types
 * (Mekanism chemicals and the like) count without a list of them. Items and fluids are {@code Item} and {@code Fluid};
 * an addon's type is named after its key type's path, as {@code Chemical}. Completion offers items and fluids only.
 */
public final class RegistryResources implements GameCatalog.Resources {
    /** Enough to fill a completion popup; more would only be scrolled past. */
    private static final int MAX_VALUES = 50;

    private final HolderLookup.Provider registries;

    public RegistryResources(HolderLookup.Provider registries) {
        this.registries = registries;
    }

    @Override
    public Set<String> kinds(String id) {
        var location = ResourceLocation.tryParse(id);
        if (location == null) {
            return Set.of();
        }
        var tag = new CompoundTag();
        tag.putString("id", id);
        var kinds = new LinkedHashSet<String>();
        for (var type : AEKeyTypes.getAll()) {
            var key = type.loadKeyFromTag(registries, tag);
            // A codec may fall back to a default (air, empty) for an unknown ID: only the same ID counts.
            if (key != null && location.equals(key.getId())) {
                kinds.add(kind(type));
            }
        }
        return kinds;
    }

    /**
     * The AE2 key a resource string names. A string that is several kinds at once (checked programs annotate those)
     * is taken as an item, then a fluid, then the first addon type.
     */
    public java.util.Optional<appeng.api.stacks.AEKey> key(String id) {
        var location = ResourceLocation.tryParse(id);
        if (location == null) {
            return java.util.Optional.empty();
        }
        var tag = new CompoundTag();
        tag.putString("id", id);
        var types = new ArrayList<AEKeyType>(List.of(AEKeyType.items(), AEKeyType.fluids()));
        AEKeyTypes.getAll().stream().filter(type -> !types.contains(type))
                .sorted(Comparator.comparing(type -> type.getId().toString())).forEach(types::add);
        for (var type : types) {
            var key = type.loadKeyFromTag(registries, tag);
            if (key != null && location.equals(key.getId())) {
                return java.util.Optional.of(key);
            }
        }
        return java.util.Optional.empty();
    }

    static String kind(AEKeyType type) {
        if (type == AEKeyType.items()) {
            return "Item";
        }
        if (type == AEKeyType.fluids()) {
            return "Fluid";
        }
        var path = type.getId().getPath();
        return path.isEmpty() ? path : path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1);
    }

    /** Items and fluids whose ID or shown name {@code prefix} finds, ranked by {@link NameMatch#rank}. */
    @Override
    public List<Candidate> values(String type, String prefix) {
        var found = new ArrayList<Candidate>();
        if (type.equals("Item") || type.equals("Resource")) {
            BuiltInRegistries.ITEM.entrySet().forEach(entry -> {
                if (entry.getValue() != Items.AIR) {
                    found.add(new Candidate(entry.getKey().location().toString(), Candidate.Kind.VALUE,
                            entry.getValue().getDescription().getString()));
                }
            });
        }
        if (type.equals("Fluid") || type.equals("Resource")) {
            BuiltInRegistries.FLUID.entrySet().forEach(entry -> {
                var fluid = entry.getValue();
                if (fluid != Fluids.EMPTY && fluid.defaultFluidState().isSource()) {
                    found.add(new Candidate(entry.getKey().location().toString(), Candidate.Kind.VALUE,
                            fluid.getFluidType().getDescription().getString()));
                }
            });
        }
        var ranked = NameMatch.rank(found, prefix);
        return ranked.size() > MAX_VALUES ? List.copyOf(ranked.subList(0, MAX_VALUES)) : ranked;
    }
}
