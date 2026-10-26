package space.controlnet.ae2federation.power;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import space.controlnet.ae2federation.domain.port.FederationPortCapability;

public final class PowerRegistration {
    private static final String MOD_ID = "ae2federation";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<BeamConverterBlock> BEAM_CONVERTER = BLOCKS.registerBlock("beam_converter",
            properties -> new BeamConverterBlock(false, properties),
            BlockBehaviour.Properties.of().strength(4.0F).sound(SoundType.METAL));
    public static final DeferredBlock<BeamConverterBlock> CREATIVE_BEAM_CONVERTER = BLOCKS.registerBlock(
            "creative_beam_converter", properties -> new BeamConverterBlock(true, properties),
            BlockBehaviour.Properties.of().strength(4.0F).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> BEAM_CONVERTER_ITEM = ITEMS.registerSimpleBlockItem(BEAM_CONVERTER,
            new Item.Properties());
    public static final DeferredItem<BlockItem> CREATIVE_BEAM_CONVERTER_ITEM = ITEMS.registerSimpleBlockItem(
            CREATIVE_BEAM_CONVERTER, new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeamConverterBlockEntity>>
            BEAM_CONVERTER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("beam_converter",
                    () -> BlockEntityType.Builder.of(BeamConverterBlockEntity::new, BEAM_CONVERTER.get(),
                            CREATIVE_BEAM_CONVERTER.get()).build(null));

    private PowerRegistration() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        modBus.addListener(PowerRegistration::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        var type = BEAM_CONVERTER_BLOCK_ENTITY.get();
        event.registerBlockEntity(FederationPortCapability.BLOCK, type, BeamConverterBlockEntity::federationDomainPort);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, BeamConverterBlockEntity::energy);
    }
}
