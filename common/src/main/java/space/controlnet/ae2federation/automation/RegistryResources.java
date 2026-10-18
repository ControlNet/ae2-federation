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

    @Override
    public List<Candidate> values(String type, String prefix) {
        var typed = prefix.toLowerCase(Locale.ROOT);
        var found = new ArrayList<Candidate>();
        if (type.equals("Item") || type.equals("Resource")) {
            BuiltInRegistries.ITEM.entrySet().forEach(entry -> {
                var id = entry.getKey().location().toString();
                if (entry.getValue() != Items.AIR && id.contains(typed)) {
                    found.add(new Candidate(id, Candidate.Kind.VALUE, entry.getValue().getDescription().getString()));
                }
            });
        }
        if (type.equals("Fluid") || type.equals("Resource")) {
            BuiltInRegistries.FLUID.entrySet().forEach(entry -> {
                var fluid = entry.getValue();
                var id = entry.getKey().location().toString();
                if (fluid != Fluids.EMPTY && fluid.defaultFluidState().isSource() && id.contains(typed)) {
                    found.add(new Candidate(id, Candidate.Kind.VALUE,
                            fluid.getFluidType().getDescription().getString()));
                }
            });
        }
        // Path matches first ("iron" finds minecraft:iron_ingot before minecraft:raw_iron), then by ID.
        found.sort(Comparator.comparing((Candidate candidate) -> !path(candidate.label()).startsWith(typed))
                .thenComparing(Candidate::label));
        return found.size() > MAX_VALUES ? List.copyOf(found.subList(0, MAX_VALUES)) : found;
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }
}
