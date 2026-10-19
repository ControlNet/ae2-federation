package space.controlnet.ae2federation.quantum;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import space.controlnet.ae2federation.domain.port.FederationPortCapability;

public final class QuantumRegistration {
    private static final String MOD_ID = "ae2federation";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE, MOD_ID);

    public static final DeferredBlock<QuantumCubeBlock> QUANTUM_CUBE = BLOCKS.registerBlock("quantum_cube",
            QuantumCubeBlock::new, BlockBehaviour.Properties.of().strength(4.0F).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> QUANTUM_CUBE_ITEM = ITEMS.registerSimpleBlockItem(QUANTUM_CUBE,
            new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumCubeBlockEntity>>
            QUANTUM_CUBE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("quantum_cube",
                    () -> BlockEntityType.Builder.of(QuantumCubeBlockEntity::new, QUANTUM_CUBE.get()).build(null));
    /** The cube an AE2 Memory Card copied: its dimension and lowest block. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> CARD_CUBE =
            COMPONENTS.registerComponentType("quantum_cube_card", builder -> builder.persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC));

    private QuantumRegistration() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(QuantumRegistration::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(FederationPortCapability.BLOCK, QUANTUM_CUBE_BLOCK_ENTITY.get(),
                QuantumCubeBlockEntity::federationDomainPort);
    }
}
