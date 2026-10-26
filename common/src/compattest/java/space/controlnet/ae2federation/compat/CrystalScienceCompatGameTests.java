package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with AE2 Crystal Science's own blocks. */
@PrefixGameTestTemplate(false)
public final class CrystalScienceCompatGameTests {
    private static final String METEORITE = "ae2cs:meteorite_pattern_provider";

    private CrystalScienceCompatGameTests() {
    }

    /**
     * The provider network's pattern sits in a Meteorite Pattern Provider, which crafts it itself with no Molecular
     * Assembler and stores what it made on its own network, from where the return router takes it.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void meteoriteCrafting(GameTestHelper helper) {
        helper.succeedWhen(meteorite(helper)::tick);
    }

    /**
     * The provider network has no storage, so the Meteorite Pattern Provider keeps what it made in its own return slots
     * until it can give it away: it must still reach the consumer's waiting CPU.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void meteoriteCraftingWithoutStorage(GameTestHelper helper) {
        helper.succeedWhen(meteorite(helper).providerWithoutStorage()::tick);
    }

    /**
     * Four Meteorite Overclock Cards let the provider craft up to 520 times a tick: the consumer's CPU pushes 64
     * crafts, and every stick comes back.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void meteoriteOverclocked(GameTestHelper helper) {
        var scene = meteorite(helper).requesting(64).preparingProvider(entity -> helper.assertTrue(
                ((IUpgradeableObject) entity).getUpgrades()
                        .addItems(new ItemStack(AddonCraftingScene.item("ae2cs:overload_card"), 4)).isEmpty(),
                "The Meteorite Pattern Provider must take four Overclock Cards"));
        helper.succeedWhen(scene::tick);
    }

    private static AddonCraftingScene meteorite(GameTestHelper helper) {
        var block = AddonCraftingScene.block(METEORITE);
        return AddonCraftingScene.structure(helper, METEORITE, List.of("ae2:1k_crafting_storage"),
                place -> place.setBlock(AddonCraftingScene.PROVIDER, block), AddonCraftingScene.PROVIDER,
                entity -> entity != null && entity.getBlockState().is(block));
    }

    /**
     * The provider network's pattern is a Resonating Pattern, AE2 Crystal Science's own kind, which names a target for
     * each input: its cobblestone goes to a chest two blocks away and its sand to the chest beside the provider. The
     * consumer plans with that pattern and its push through the projection must route each input to its own target.
     * Every input has a target: AE2 Crystal Science 1.2.1 refuses a push that leaves some inputs to the provider's
     * sides, because it then tries each routed input again at zero and takes the zero it gets as "does not fit".
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void resonatingRouting(GameTestHelper helper) {
        helper.succeedWhen(resonating(helper, false)::tick);
    }

    /** As {@link #resonatingRouting}, with both networks joined into one Grid: the push as the addon makes it. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void resonatingRoutingJoined(GameTestHelper helper) {
        helper.succeedWhen(resonating(helper, true)::tick);
    }

    private static AddonCraftingScene resonating(GameTestHelper helper, boolean joined) {
        var scene = new AddonCraftingScene(helper, "ae2cs:resonating_pattern_provider",
                List.of("ae2:1k_crafting_storage"), new RoutedMachine())
                .installingPatternsWith((entity, pattern) -> ((PatternContainer) entity).getTerminalPatternInventory()
                        .addItems(resonating(helper, pattern)).isEmpty())
                .checkingPlan((plan, local) -> helper.assertTrue(plan.patternTimes().keySet().stream()
                                .anyMatch(details -> details.getClass().getName().equals(RESONATING_DETAILS)),
                        "The consumer must plan with the Resonating Pattern: " + plan.patternTimes()));
        if (joined) scene.joinedBeforeOrder();
        return scene;
    }

    private static final String RESONATING_DETAILS =
            "io.github.lounode.ae2cs.common.me.crafting.ResonatingPatternDetails";

    /** {@code processing} as a Resonating Pattern sending each input to its chest in {@link RoutedMachine#targets}. */
    private static ItemStack resonating(GameTestHelper helper, ItemStack processing) {
        try {
            var details = Class.forName(RESONATING_DETAILS);
            var pattern = (ItemStack) details.getMethod("encode", ItemStack.class).invoke(null, processing);
            helper.assertFalse(pattern.isEmpty(), "AE2 Crystal Science must make the pattern a Resonating Pattern");
            @SuppressWarnings("unchecked")
            var component = (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE
                    .get(ResourceLocation.parse("ae2cs:encoded_resonating_pattern"));
            var encoded = pattern.get(component);
            @SuppressWarnings("unchecked")
            var inputs = (List<GenericStack>) encoded.getClass().getMethod("sparseInputs").invoke(encoded);
            var targetType = Class.forName(details.getPackageName() + ".EncodedResonatingPattern$Target");
            var withTarget = encoded.getClass().getMethod("withTarget", int.class, targetType);
            for (int index = 0; index < inputs.size(); index++) {
                if (inputs.get(index) == null) continue;
                var chest = RoutedMachine.targets().get(inputs.get(index).what());
                helper.assertTrue(chest != null, "No chest for " + inputs.get(index));
                var target = targetType.getConstructor(GlobalPos.class, Direction.class).newInstance(
                        GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(chest)), Direction.WEST);
                encoded = withTarget.invoke(encoded, index, target);
            }
            pattern.set(component, encoded);
            return pattern;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("AE2 Crystal Science's Resonating Pattern is not available", exception);
        }
    }

    private static final AEItemKey COBBLESTONE = AEItemKey.of(Items.COBBLESTONE);
    private static final AEItemKey SAND = AEItemKey.of(Items.SAND);

    /**
     * The chest beside the provider takes the sand and a chest two blocks east of the provider the cobblestone; the
     * test makes glass from each pair, and fails if an input reached the other chest.
     */
    private record RoutedMachine() implements AddonCraftingScene.Machine {
        private static final BlockPos COBBLESTONE_CHEST = AddonCraftingScene.PROVIDER.east(2);

        static Map<AEKey, BlockPos> targets() {
            return Map.of(COBBLESTONE, COBBLESTONE_CHEST, SAND, AddonCraftingScene.PROVIDER.east());
        }

        @Override
        public String blockId() {
            return "minecraft:chest";
        }

        @Override
        public Map<AEItemKey, Long> inputs() {
            var inputs = new LinkedHashMap<AEItemKey, Long>();
            inputs.put(COBBLESTONE, 1L);
            inputs.put(SAND, 1L);
            return inputs;
        }

        @Override
        public AEKey output() {
            return AEItemKey.of(Items.GLASS);
        }

        @Override
        public void placeAround(GameTestHelper helper, BlockPos position) {
            helper.setBlock(COBBLESTONE_CHEST, Blocks.CHEST);
        }

        @Override
        public void collect(GameTestHelper helper, BlockPos position, MEStorage network) {
            var local = helper.<ChestBlockEntity>getBlockEntity(position);
            var remote = helper.<ChestBlockEntity>getBlockEntity(COBBLESTONE_CHEST);
            helper.assertFalse(count(local, Items.COBBLESTONE) > 0, "Cobblestone must go to its target, not beside");
            helper.assertFalse(count(remote, Items.SAND) > 0, "Sand must go beside the provider, not to the target");
            long pairs = Math.min(count(local, Items.SAND), count(remote, Items.COBBLESTONE));
            if (pairs == 0) return;
            remove(local, Items.SAND, pairs);
            remove(remote, Items.COBBLESTONE, pairs);
            network.insert(output(), pairs, Actionable.MODULATE, IActionSource.empty());
        }

        private static void remove(ChestBlockEntity chest, net.minecraft.world.item.Item item, long amount) {
            for (int slot = 0; slot < chest.getContainerSize() && amount > 0; slot++) {
                if (!chest.getItem(slot).is(item)) continue;
                int taken = (int) Math.min(amount, chest.getItem(slot).getCount());
                chest.removeItem(slot, taken);
                amount -= taken;
            }
        }

        private static long count(ChestBlockEntity chest, net.minecraft.world.item.Item item) {
            long count = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (chest.getItem(slot).is(item)) count += chest.getItem(slot).getCount();
            }
            return count;
        }
    }

    /**
     * The provider network's Mirror Pattern Provider holds no pattern: it is bound, as its Mirror Binder binds it, to
     * an AE2 Pattern Provider on no network at all, and offers that provider's pattern as its own. The consumer orders
     * through the Mirror's projection, and the Mirror pushes into the machine beside it.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void mirrorCrafting(GameTestHelper helper) {
        var source = AddonCraftingScene.PROVIDER.north(2);
        var scene = new AddonCraftingScene(helper, "ae2cs:mirror_pattern_provider", "minecraft:chest",
                List.of("ae2:1k_crafting_storage"), true)
                .installingPatternsWith((entity, pattern) -> {
                    helper.setBlock(source, AEBlocks.PATTERN_PROVIDER.block());
                    var sourceHost = helper.<PatternProviderBlockEntity>getBlockEntity(source);
                    if (!sourceHost.getLogic().getPatternInv().addItems(pattern).isEmpty()) return false;
                    sourceHost.getLogic().updatePatterns();
                    bind(helper, entity, source);
                    return true;
                });
        helper.succeedWhen(scene::tick);
    }

    /** Binds the Mirror Pattern Provider {@code mirror} to the provider at {@code source}, as a Mirror Binder does. */
    private static void bind(GameTestHelper helper, BlockEntity mirror, BlockPos source) {
        try {
            var logic = ((PatternProviderLogicHost) mirror).getLogic();
            var targetType = Class.forName("io.github.lounode.ae2cs.common.me.logic.MirroredPatternProviderTarget");
            var target = targetType.getConstructor(GlobalPos.class, Direction.class)
                    .newInstance(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(source)), null);
            logic.getClass().getMethod("setMirrorTarget", targetType).invoke(logic, target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("AE2 Crystal Science's Mirror Pattern Provider is not there", exception);
        }
        helper.assertFalse(((PatternProviderLogicHost) mirror).getLogic().getAvailablePatterns().isEmpty(),
                "The Mirror Pattern Provider must offer the bound provider's pattern");
    }

    /**
     * The provider network's AE2 Pattern Provider pushes stone into a Crystal Pulverizer, which runs on that network's
     * AE power (shared from the consumer by the ME power rule) and, with auto-export on as its player sets it, puts the
     * cobblestone back into the provider.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void pulverizerProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", List.of("ae2:1k_crafting_storage"),
                new Pulverizer());
        helper.succeedWhen(scene::tick);
    }

    /** A Crystal Pulverizer east of the provider, exporting what it makes through its west side, into the provider. */
    private record Pulverizer() implements AddonCraftingScene.Machine {
        @Override
        public String blockId() {
            return "ae2cs:crystal_pulverizer";
        }

        @Override
        public AEItemKey input() {
            return AEItemKey.of(Items.STONE);
        }

        @Override
        public AEKey output() {
            return COBBLESTONE;
        }

        @Override
        public void placeAround(GameTestHelper helper, BlockPos position) {
            var pulverizer = helper.getBlockEntity(position);
            try {
                var components = pulverizer.getClass().getMethod("getMachineComponents").invoke(pulverizer);
                var sideConfig = components.getClass().getMethod("getService", Class.class).invoke(components,
                        Class.forName("io.github.lounode.ae2cs.common.machine.component.SideConfigComponent"));
                sideConfig.getClass().getMethod("setAutoExport", boolean.class).invoke(sideConfig, true);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("AE2 Crystal Science's side configuration is not available", exception);
            }
        }

        @Override
        public void collect(GameTestHelper helper, BlockPos position, MEStorage network) {
        }
    }

    /**
     * An ME Ender Emitter links the devices it is bound to into its own network without cables: on the consumer's
     * cable, bound to the provider network's cable as its Ender Linker binds it, it joins both networks into one Grid,
     * as a cable between them would. With the Bridge removed and nothing else between them, the consumer orders from
     * the provider through that Grid.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void enderEmitterJoin(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler",
                List.of("ae2:1k_crafting_storage"))
                .joinedBeforeOrder(join -> {
                    var emitter = AddonCraftingScene.BASE.above();
                    join.setBlock(emitter, AddonCraftingScene.block("ae2cs:ender_emitter"));
                    // As its player does once the emitter stands and runs: binding it the tick it is placed is lost.
                    join.runAfterDelay(20, () -> bindEmitter(join, emitter, AddonCraftingScene.BASE.north()));
                });
        helper.succeedWhen(scene::tick);
    }

    /** Binds the Ender Emitter at {@code emitter} to the block at {@code target}, as its Ender Linker does. */
    private static void bindEmitter(GameTestHelper helper, BlockPos emitter, BlockPos target) {
        var entity = helper.getBlockEntity(emitter);
        try {
            var bound = (boolean) entity.getClass().getMethod("addPosToEmitter", entity.getClass(), BlockPos.class,
                    boolean.class, boolean.class).invoke(null, entity, helper.absolutePos(target), true, false);
            helper.assertTrue(bound, "The Ender Emitter must take " + target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("AE2 Crystal Science's Ender Emitter is not available", exception);
        }
    }

    /**
     * AE2 Crystal Science's providers have their own logic and still run the Endpoint in Local mode, as blocks and as
     * cable parts: a processing pattern goes through them as through AE2's.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalMeteoritePatternProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace()).throughLocalProvider(METEORITE);
        helper.succeedWhen(scene::tick);
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalMeteoritePatternProviderPart(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProviderPart("ae2cs:meteorite_pattern_provider_part");
        helper.succeedWhen(scene::tick);
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalSimplePatternProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProvider("ae2cs:simple_pattern_provider");
        helper.succeedWhen(scene::tick);
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalResonatingPatternProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProvider("ae2cs:resonating_pattern_provider");
        helper.succeedWhen(scene::tick);
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalResonatingPatternProviderPart(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProviderPart("ae2cs:resonating_pattern_provider_part");
        helper.succeedWhen(scene::tick);
    }
}
