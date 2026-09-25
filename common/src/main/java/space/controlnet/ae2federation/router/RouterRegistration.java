package space.controlnet.ae2federation.router;

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
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.domain.port.FederationPortCapability;

public final class RouterRegistration {
    private static final String MOD_ID = "ae2federation";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<RouterBlock> ROUTER = BLOCKS.registerBlock("router", RouterBlock::new,
            BlockBehaviour.Properties.of().strength(4.0F).sound(SoundType.METAL));
    public static final DeferredBlock<FederationCableBlock> FEDERATION_CABLE = BLOCKS.registerBlock(
            "cable", FederationCableBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.GLASS));
    public static final DeferredItem<BlockItem> ROUTER_ITEM = ITEMS.registerSimpleBlockItem(ROUTER, new Item.Properties());
    public static final DeferredItem<BlockItem> FEDERATION_CABLE_ITEM = ITEMS.registerSimpleBlockItem(
            FEDERATION_CABLE, new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RouterBlockEntity>> ROUTER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("router", () -> BlockEntityType.Builder.of(RouterBlockEntity::new, ROUTER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FederationCableBlockEntity>>
            FEDERATION_CABLE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("cable",
                    () -> BlockEntityType.Builder.of(FederationCableBlockEntity::new, FEDERATION_CABLE.get()).build(null));

    private RouterRegistration() {
    }

    /**
     * Registry ids used before the Router/Cable rename. Registry aliases let chunk palettes, block entity ids and item
     * stacks saved under the old ids load as the new entries; new data is written with the new ids.
     */
    static final java.util.Map<String, String> LEGACY_IDS = java.util.Map.of("hub", "router",
            "federation_cable", "cable");

    public static void register(IEventBus modBus) {
        LEGACY_IDS.forEach((legacy, current) -> {
            var from = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, legacy);
            var to = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, current);
            BLOCKS.addAlias(from, to);
            ITEMS.addAlias(from, to);
            BLOCK_ENTITY_TYPES.addAlias(from, to);
        });
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        modBus.addListener(RouterRegistration::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, ROUTER_BLOCK_ENTITY.get(),
                (router, context) -> router);
        event.registerBlockEntity(FederationPortCapability.BLOCK, ROUTER_BLOCK_ENTITY.get(), RouterBlockEntity::federationDomainPort);
        event.registerBlock(FederationPortCapability.BLOCK,
                (level, position, state, blockEntity, face) -> face == null
                        ? null
                        : new FederationPort(position, face),
                FEDERATION_CABLE.get());
    }
}
