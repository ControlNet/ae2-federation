package space.controlnet.ae2federation.test.mixed;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import space.controlnet.ae2federation.test.FederationTestMod;

public final class MixedMachineRegistration {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FederationTestMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, FederationTestMod.MOD_ID);

    public static final DeferredBlock<MixedMachineBlock> BLOCK = BLOCKS.registerBlock("mixed_processing_machine",
            MixedMachineBlock::new, BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.METAL));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MixedMachineBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("mixed_processing_machine", () -> BlockEntityType.Builder.of(
                    MixedMachineBlockEntity::new, BLOCK.get()).build(null));

    private MixedMachineRegistration() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        modBus.addListener(MixedMachineRegistration::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLOCK_ENTITY.get(),
                (machine, side) -> machine.inputHandler());
    }
}
