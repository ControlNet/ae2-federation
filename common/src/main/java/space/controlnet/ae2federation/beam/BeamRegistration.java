package space.controlnet.ae2federation.beam;

import net.minecraft.core.registries.BuiltInRegistries;
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

public final class BeamRegistration {
    private static final String MOD_ID = "ae2federation";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<BeamFormerBlock> BEAM_FORMER = BLOCKS.registerBlock("beam_former",
            BeamFormerBlock::new, BlockBehaviour.Properties.of().strength(4.0F).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> BEAM_FORMER_ITEM = ITEMS.registerSimpleBlockItem(BEAM_FORMER,
            new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeamFormerBlockEntity>>
            BEAM_FORMER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("beam_former",
                    () -> BlockEntityType.Builder.of(BeamFormerBlockEntity::new, BEAM_FORMER.get()).build(null));

    private BeamRegistration() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        modBus.addListener(BeamRegistration::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(FederationPortCapability.BLOCK, BEAM_FORMER_BLOCK_ENTITY.get(),
                BeamFormerBlockEntity::federationDomainPort);
    }
}
