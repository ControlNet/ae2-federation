package space.controlnet.ae2federation.hub;

import appeng.api.AECapabilities;
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
import space.controlnet.ae2federation.fabric.port.FederationPort;
import space.controlnet.ae2federation.fabric.port.FederationPortCapability;

public final class HubRegistration {
    private static final String MOD_ID = "ae2federation";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<HubBlock> HUB = BLOCKS.registerBlock("hub", HubBlock::new,
            BlockBehaviour.Properties.of().strength(4.0F).sound(SoundType.METAL));
    public static final DeferredBlock<FederationCableBlock> FEDERATION_CABLE = BLOCKS.registerBlock(
            "federation_cable", FederationCableBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.GLASS));
    public static final DeferredItem<BlockItem> HUB_ITEM = ITEMS.registerSimpleBlockItem(HUB, new Item.Properties());
    public static final DeferredItem<BlockItem> FEDERATION_CABLE_ITEM = ITEMS.registerSimpleBlockItem(
            FEDERATION_CABLE, new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HubBlockEntity>> HUB_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("hub", () -> BlockEntityType.Builder.of(HubBlockEntity::new, HUB.get()).build(null));

    private HubRegistration() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        modBus.addListener(HubRegistration::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, HUB_BLOCK_ENTITY.get(),
                (hub, context) -> hub);
        event.registerBlockEntity(FederationPortCapability.BLOCK, HUB_BLOCK_ENTITY.get(), HubBlockEntity::fabricPort);
        event.registerBlock(FederationPortCapability.BLOCK,
                (level, position, state, blockEntity, face) -> face == null
                        ? null
                        : new FederationPort(position, face),
                FEDERATION_CABLE.get());
    }
}
