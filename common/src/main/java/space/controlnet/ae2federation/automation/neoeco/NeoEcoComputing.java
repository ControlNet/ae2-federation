package space.controlnet.ae2federation.automation.neoeco;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import space.controlnet.ae2federation.domain.port.FederationPortCapability;

/**
 * The Federation computation system on Neo ECO AE Extension: one controller per Neo ECO tier, built into Neo ECO's own
 * computation structure. Every class in this package refers to Neo ECO, so only {@link NeoEcoSupport} may reach it,
 * and only once Neo ECO is loaded.
 */
public final class NeoEcoComputing {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("ae2federation");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("ae2federation");
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "ae2federation");
    private static final Map<ECOTier, Rarity> RARITY = Map.of(
            ECOTier.L4, Rarity.UNCOMMON, ECOTier.L6, Rarity.RARE, ECOTier.L9, Rarity.EPIC);
    public static final Map<ECOTier, DeferredBlock<EcoComputationSystemBlock>> CONTROLLERS = new EnumMap<>(ECOTier.class);
    public static final Map<ECOTier, DeferredHolder<BlockEntityType<?>, BlockEntityType<EcoComputationSystemBlockEntity>>>
            CONTROLLER_TYPES = new EnumMap<>(ECOTier.class);

    static {
        for (var tier : ECOTier.values()) {
            var name = "eco_computation_system_" + tier.name().toLowerCase(java.util.Locale.ROOT);
            var block = BLOCKS.registerBlock(name, EcoComputationSystemBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
            ITEMS.registerSimpleBlockItem(block, new Item.Properties().rarity(RARITY.get(tier)));
            CONTROLLERS.put(tier, block);
            CONTROLLER_TYPES.put(tier, BLOCK_ENTITY_TYPES.register(name, () -> {
                var holder = new BlockEntityType<?>[1];
                @SuppressWarnings("unchecked")
                var type = (BlockEntityType<EcoComputationSystemBlockEntity>) BlockEntityType.Builder.of(
                        (position, state) -> new EcoComputationSystemBlockEntity(holder[0], position, state, tier),
                        block.get()).build(null);
                holder[0] = type;
                // The block is typed on Neo ECO's controller block entity, which ours extends, as Neo ECO's own
                // registration casts its types.
                @SuppressWarnings("unchecked")
                var entityClass = (Class<ECOComputationSystemBlockEntity>) (Class<?>) EcoComputationSystemBlockEntity.class;
                @SuppressWarnings("unchecked")
                var entityType = (BlockEntityType<ECOComputationSystemBlockEntity>) (BlockEntityType<?>) type;
                block.get().setBlockEntity(entityClass, entityType, null, (level, position, state, entity) ->
                        EcoComputationSystemBlockEntity.serverTick(level, position, state,
                                (EcoComputationSystemBlockEntity) entity));
                NEBlockEntity.registerCalculatorFactory(type, EcoComputationClusterCalculator::new);
                return type;
            }));
        }
    }

    private NeoEcoComputing() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        modBus.addListener(NeoEcoComputing::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (var type : CONTROLLER_TYPES.values()) {
            event.registerBlockEntity(FederationPortCapability.BLOCK, type.get(),
                    EcoComputationSystemBlockEntity::federationPort);
        }
    }
}
