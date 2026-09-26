package space.controlnet.ae2federation.test.energy;

import appeng.block.networking.EnergyCellBlock;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import space.controlnet.ae2federation.test.FederationTestMod;

public final class LargeEnergyCellRegistration {
    public static final double CAPACITY = 2_000_000_000.0;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FederationTestMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, FederationTestMod.MOD_ID);

    public static final DeferredBlock<EnergyCellBlock> BLOCK = BLOCKS.register("large_energy_cell",
            () -> new EnergyCellBlock(CAPACITY, CAPACITY, 2_000_000_000));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyCellBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("large_energy_cell", () -> {
                var typeHolder = new AtomicReference<BlockEntityType<EnergyCellBlockEntity>>();
                var type = BlockEntityType.Builder.of((position, state) ->
                        new EnergyCellBlockEntity(typeHolder.get(), position, state), BLOCK.get()).build(null);
                typeHolder.setPlain(type);
                BLOCK.get().setBlockEntity(EnergyCellBlockEntity.class, type, null, null);
                return type;
            });

    private LargeEnergyCellRegistration() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
    }
}
