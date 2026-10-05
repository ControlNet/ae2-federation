package space.controlnet.ae2federation.processing.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class ProviderPlacementRegistryTest {
    private static final ProviderId PROVIDER = new ProviderId(new UUID(1, 2));
    private static final GlobalPos HERE = at("overworld", 0, 64, 0);
    private static final GlobalPos THERE = at("overworld", 16, 64, 0);

    private static GlobalPos at(String dimension, int x, int y, int z) {
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace(dimension)),
                new BlockPos(x, y, z));
    }

    @Test
    void aProviderLoadingWhereItLivesIsNoCopy() {
        var registry = new ProviderPlacementRegistry();
        assertTrue(registry.place(PROVIDER, HERE));
        assertTrue(registry.place(PROVIDER, HERE), "a chunk load places it where it already lives");
    }

    @Test
    void theSameIdentityElsewhereIsACopy() {
        var registry = new ProviderPlacementRegistry();
        registry.place(PROVIDER, HERE);
        assertFalse(registry.place(PROVIDER, THERE));
        assertFalse(registry.place(PROVIDER, at("the_nether", 0, 64, 0)), "the dimension is part of the position");
    }

    /** A block mover removes the Provider before it places it again, so the moved block keeps its identity. */
    @Test
    void aRemovedProviderMayBePlacedAgainElsewhere() {
        var registry = new ProviderPlacementRegistry();
        registry.place(PROVIDER, HERE);
        registry.remove(PROVIDER, THERE);
        assertFalse(registry.place(PROVIDER, THERE), "a copy's removal does not free the original's identity");
        registry.remove(PROVIDER, HERE);
        assertTrue(registry.place(PROVIDER, THERE));
    }

    @Test
    void placementsSurviveASave() {
        var registry = new ProviderPlacementRegistry();
        registry.place(PROVIDER, HERE);
        var loaded = ProviderPlacementRegistry.load(registry.save(new CompoundTag(), null), null);
        assertFalse(loaded.place(PROVIDER, THERE));
        assertTrue(loaded.place(PROVIDER, HERE));
    }
}
