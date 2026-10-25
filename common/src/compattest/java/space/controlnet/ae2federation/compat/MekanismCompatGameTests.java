package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.domain.port.RouterPortKind;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.router.RouterBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/** Federation processing through Mekanism's own machines, powered as players power them. */
@PrefixGameTestTemplate(false)
public final class MekanismCompatGameTests {
    private MekanismCompatGameTests() {
    }

    /**
     * The Federation Pattern Provider sends cobblestone through an Endpoint into a Crusher that a Basic Energy Cube
     * powers, and a hopper under the Crusher pushes the gravel back into the Endpoint.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointCrusher(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new Crusher());
        helper.succeedWhen(scene::tick);
    }

    /**
     * The guide's Crusher example as its player builds and tries it: the Endpoint powers the Crusher's subnet, and a
     * job waits while the Crusher's top is switched off, then finishes once it takes items again. The Crusher takes
     * 200 ticks a batch, so 300 ticks without gravel show that it never got the cobblestone.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1400)
    public static void endpointCrusherTopOff(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new Crusher()).poweredThroughEndpoint().interruptedBy(300,
                (test, crusher) -> MekanismSetup.configure(test, crusher, "ITEM", "NONE", Direction.UP, false),
                (test, crusher) -> MekanismSetup.configure(test, crusher, "ITEM", "INPUT", Direction.UP, false));
        helper.succeedWhen(scene::tick);
    }

    /**
     * The guide's power plant: a plant network with a Mekanism Induction Matrix and an AE2 Energy Acceptor powers a
     * workshop and a district through ME power, with no energy cell on any network. The district orders sticks from
     * the workshop's pattern provider and assembler. The matrix is charged through its port as the test begins, a
     * test-only stand-in for a charged matrix, and must have given up energy by the end. The exercise switches off ME
     * power for the workshop: it goes dark and sticks leave the district's craftables.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "scale_36_empty", timeoutTicks = 1200)
    public static void inductionMatrixPowerPlant(GameTestHelper helper) {
        var scene = new RouterCraftingScene(helper, new BlockPos(18, 1, 18));
        // The Energy Acceptor sits on a cable at the plant's start; the matrix's port is just beyond it.
        var acceptor = scene.start(Direction.NORTH).above();
        var port = acceptor.north();
        long[] charged = {-1};
        scene.member("plant", Direction.NORTH, (test, start, outward) -> placePowerPlant(test, start), acceptor,
                        entity -> charged[0] >= 0 || chargedMatrix(helper, port, charged))
                .provider("workshop", Direction.EAST, RouterCraftingScene::placeAssemblyProvider,
                        scene.start(Direction.EAST), RouterCraftingScene::patternProvider,
                        RouterCraftingScene.sticksPattern(helper))
                .consumer("district", Direction.SOUTH, RouterCraftingScene::placeCpu, RouterCraftingScene::formed,
                        RouterCraftingScene.STICKS, 8, RouterCraftingScene.PLANKS, 4, "workshop")
                .poweredBy("plant")
                .withoutEnergyCell()
                .thenSwitchingOffPower("workshop", RouterCraftingScene.STICKS, "district")
                .checkingAtEnd(() -> helper.assertTrue(matrixEnergy(helper, port) < charged[0],
                        "The networks must have run on the matrix's energy: it still holds " + matrixEnergy(helper, port)
                                + " of " + charged[0] + " FE"));
        helper.succeedWhen(scene::tick);
    }

    /** FE the test charges the matrix with: plenty for the three networks, and small enough to read back as an int. */
    private static final int MATRIX_CHARGE = 1_000_000;

    /**
     * An ME cable at {@code start}, an Energy Acceptor on it, and beyond that a 3x4x3 Induction Matrix whose port, in
     * the middle of its south face, touches the acceptor: induction casing round a basic cell under a basic provider.
     */
    private static void placePowerPlant(GameTestHelper helper, BlockPos start) {
        helper.assertTrue(appeng.api.parts.PartHelper.setPart(helper.getLevel(), helper.absolutePos(start), null, null,
                appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT)) != null,
                "A cable must carry the Energy Acceptor");
        helper.setBlock(start.above(), appeng.core.definitions.AEBlocks.ENERGY_ACCEPTOR.block());
        var casing = AddonCraftingScene.block("mekanism:induction_casing");
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    var position = start.offset(x, y, -z);
                    boolean inside = x == 0 && z == 2 && (y == 1 || y == 2);
                    if (!inside) helper.setBlock(position, casing);
                }
            }
        }
        helper.setBlock(start.offset(0, 1, -2), AddonCraftingScene.block("mekanism:basic_induction_cell"));
        helper.setBlock(start.offset(0, 2, -2), AddonCraftingScene.block("mekanism:basic_induction_provider"));
        helper.setBlock(start.offset(0, 1, -1), AddonCraftingScene.block("mekanism:induction_port"));
    }

    /**
     * Once the matrix at {@code port} has formed, charges it with {@link #MATRIX_CHARGE} FE through the port, over as
     * many ticks as the port needs, and turns the port to output, into the Energy Acceptor; notes the charge in {@code
     * charged}.
     */
    private static boolean chargedMatrix(GameTestHelper helper, BlockPos port, long[] charged) {
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(port));
        try {
            var multiblock = entity.getClass().getMethod("getMultiblock").invoke(entity);
            helper.assertTrue((boolean) multiblock.getClass().getMethod("isFormed").invoke(multiblock),
                    "Waiting for the Induction Matrix to form");
            var energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(port),
                    Direction.SOUTH);
            helper.assertTrue(energy != null, "The induction port must take FE");
            // The port takes only so much a tick, set by the provider's tier.
            energy.receiveEnergy(MATRIX_CHARGE - energy.getEnergyStored(), false);
            helper.assertTrue(energy.getEnergyStored() >= MATRIX_CHARGE, "Charging the matrix: "
                    + energy.getEnergyStored() + " FE");
            entity.getClass().getMethod("setActive", boolean.class).invoke(entity, true);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The induction port cannot be read", exception);
        }
        charged[0] = matrixEnergy(helper, port);
        return true;
    }

    /** FE the matrix holds, read through its port. */
    private static long matrixEnergy(GameTestHelper helper, BlockPos port) {
        var energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(port),
                Direction.SOUTH);
        helper.assertTrue(energy != null, "The induction port must report its FE");
        return energy.getEnergyStored();
    }

    /**
     * A player moves blocks around a Switch with Mekanism's Cardboard Box: the ME chest on its west face is boxed, so
     * the face lets go of it and drops its node, then unboxed, so the face attaches it again; then the Switch itself is
     * boxed and unboxed and attaches the chest once more.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void cardboardBoxBesideSwitch(GameTestHelper helper) {
        var center = new BlockPos(6, 6, 6);
        var chest = center.west();
        var fixtures = new RouterFixtures(helper);
        fixtures.placeSwitch(center);
        fixtures.placeNativeDevice(center, Direction.WEST);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                helper.assertValueEqual(westKind(fixtures, center), RouterPortKind.NATIVE_ME,
                        "The Switch must attach the ME chest");
                box(helper, player, chest);
                phase[0] = 1;
                helper.fail("Waiting for the Switch to let go of the boxed chest");
            }
            if (phase[0] == 1) {
                helper.assertValueEqual(westKind(fixtures, center), RouterPortKind.DISCONNECTED,
                        "A boxed chest must leave the Switch's face");
                helper.assertTrue(fixtures.router(center).boundaryNode(Direction.WEST) == null,
                        "The face's node must go with it");
                unbox(helper, player, chest, AEBlocks.ME_CHEST.block());
                phase[0] = 2;
                helper.fail("Waiting for the unboxed chest to attach");
            }
            if (phase[0] == 2) {
                helper.assertValueEqual(westKind(fixtures, center), RouterPortKind.NATIVE_ME,
                        "An unboxed chest must attach again");
                box(helper, player, center);
                phase[0] = 3;
                helper.fail("The Switch stays boxed for a tick");
            }
            if (phase[0] == 3) {
                unbox(helper, player, center, RouterRegistration.SWITCH.get());
                phase[0] = 4;
                helper.fail("Waiting for the unboxed Switch to attach the chest");
            }
            helper.assertValueEqual(westKind(fixtures, center), RouterPortKind.NATIVE_ME,
                    "An unboxed Switch must attach the chest beside it");
            fixtures.close();
        });
    }

    /**
     * A player boxes the middle Federation Cable between two Switches with Mekanism's Cardboard Box, which splits their
     * networks' domain, and unboxes it, which joins them again.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void cardboardBoxFederationCable(GameTestHelper helper) {
        var west = new BlockPos(2, 2, 2);
        var east = new BlockPos(6, 2, 2);
        var middle = west.east(2);
        var fixtures = new RouterFixtures(helper);
        fixtures.placeSwitch(west);
        fixtures.placeSwitch(east);
        fixtures.placeNativeDevice(west, Direction.WEST);
        fixtures.placeNativeDevice(east, Direction.EAST);
        for (int step = 1; step <= 3; step++) fixtures.placeFederationCable(west.east(step));
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var phase = new int[1];
        helper.succeedWhen(() -> {
            var registry = FederationDomainRegistryAccess.get(helper.getLevel());
            var left = network(helper, fixtures.router(west), Direction.WEST);
            var right = network(helper, fixtures.router(east), Direction.EAST);
            if (phase[0] == 0) {
                helper.assertTrue(registry.shareFederationDomain(left, right), "The cable must join the two networks");
                box(helper, player, middle);
                phase[0] = 1;
                helper.fail("Waiting for the domain to split");
            }
            if (phase[0] == 1) {
                helper.assertTrue(!registry.shareFederationDomain(left, right), "A boxed cable must split the domain");
                unbox(helper, player, middle, RouterRegistration.FEDERATION_CABLE.get());
                phase[0] = 2;
                helper.fail("Waiting for the unboxed cable to link");
            }
            helper.assertTrue(registry.shareFederationDomain(left, right), "An unboxed cable must join them again");
            fixtures.close();
        });
    }

    private static RouterPortKind westKind(RouterFixtures fixtures, BlockPos router) {
        return fixtures.router(router).binding(Direction.WEST).kind();
    }

    /** Uses a Cardboard Box on {@code position}, as a player picks a block up with it. */
    private static void box(GameTestHelper helper, ServerPlayer player, BlockPos position) {
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AddonCraftingScene.item("mekanism:cardboard_box")));
        var result = use(helper, player, position);
        helper.assertTrue(result.consumesAction(), "The Cardboard Box must take " + helper.getBlockState(position)
                + ": " + result);
        helper.assertTrue(helper.getBlockState(position).is(AddonCraftingScene.block("mekanism:cardboard_box")),
                "A Cardboard Box must stand at " + position + ", not " + helper.getBlockState(position));
    }

    /** Sneak-uses the box at {@code position} with an empty hand, as a player unpacks it, and expects {@code block}. */
    private static void unbox(GameTestHelper helper, ServerPlayer player, BlockPos position, Block block) {
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var result = use(helper, player, position);
        helper.assertTrue(result.consumesAction(), "The Cardboard Box must unpack: " + result);
        helper.assertTrue(helper.getBlockState(position).is(block),
                "The box must leave " + block + " at " + position + ", not " + helper.getBlockState(position));
    }

    private static InteractionResult use(GameTestHelper helper, ServerPlayer player, BlockPos position) {
        var absolute = helper.absolutePos(position);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.UP, 0.5), Direction.UP, absolute,
                false);
        return player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                hit);
    }

    /** The network attached to {@code router}'s {@code face}, once its identity has settled. */
    private static NetworkId network(GameTestHelper helper, RouterBlockEntity router, Direction face) {
        helper.assertTrue(router.binding(face) instanceof RouterPortBinding.Native,
                "The Switch must attach the network on its " + face + " face");
        var attachment = ((RouterPortBinding.Native) router.binding(face)).attachment();
        return FederationDomainRegistryAccess.confirmedNetworkId(attachment.grid())
                .orElseThrow(() -> new GameTestAssertException("Waiting for the network's identity to settle"));
    }

    /**
     * The guide's ore line: one Provider maps iron ore to an Enrichment Chamber's Endpoint and iron dust to an Energized
     * Smelter's, so ordering iron ingots runs both, the dust returning to the Provider's network between them. Each
     * machine takes 200 ticks an item. The exercise takes the Smelter's hopper away for 900 ticks, longer than enriching
     * both ores and smelting one dust: no ingot may return until the hopper is back.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "scale_36_empty", timeoutTicks = 3200)
    public static void endpointOreLine(GameTestHelper helper) {
        var dust = enriched(helper, new ItemStack(Items.IRON_ORE));
        var scene = new EndpointChainScene(helper, 2,
                new PoweredMachine("mekanism:enrichment_chamber", AEItemKey.of(Items.IRON_ORE), AEItemKey.of(dust),
                        dust.getCount()),
                new PoweredMachine("mekanism:energized_smelter", AEItemKey.of(dust), AEItemKey.of(Items.IRON_INGOT), 1))
                .cuttingOff(1, 900);
        helper.succeedWhen(scene::tick);
    }

    /**
     * What the Enrichment Chamber makes from {@code input}, read from the loaded recipes as a player reads it off the
     * machine: a modpack may change it, as All the Mods unifies Mekanism's iron dust into its own.
     */
    private static ItemStack enriched(GameTestHelper helper, ItemStack input) {
        @SuppressWarnings("unchecked")
        var type = (RecipeType<Recipe<SingleRecipeInput>>) BuiltInRegistries.RECIPE_TYPE.get(
                ResourceLocation.parse("mekanism:enriching"));
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().getRecipeFor(type, new SingleRecipeInput(input), level).orElseThrow(
                () -> new IllegalStateException("No enriching recipe for " + input));
        return recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess());
    }

    /**
     * A fluid comes back through the Endpoint: the Federation Pattern Provider sends apples into a Nutritional Liquifier
     * standing on the Endpoint, which ejects the nutritional paste it makes, a fluid, down into the Endpoint.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLiquifierFluid(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new Liquifier());
        helper.succeedWhen(scene::tick);
    }

    /** The same fluid return in Local mode, into the return inventory of another network's AE2 Pattern Provider. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalLiquifierFluid(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new Liquifier()).throughLocalProvider("ae2:pattern_provider");
        helper.succeedWhen(scene::tick);
    }

    /**
     * The consumer requests gravel; the provider network's AE2 Pattern Provider pushes cobblestone into a Crusher,
     * which ejects the gravel back into the provider, as a player sets its side to input and output.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void crusherProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", List.of("ae2:1k_crafting_storage"),
                new ProviderCrusher());
        helper.succeedWhen(scene::tick);
    }

    /** A Crusher east of the provider, taking and returning items on its west side, powered from its east. */
    private static final class ProviderCrusher implements AddonCraftingScene.Machine {
        @Override
        public String blockId() {
            return "mekanism:crusher";
        }

        @Override
        public AEItemKey output() {
            return AEItemKey.of(Items.GRAVEL);
        }

        @Override
        public void placeAround(GameTestHelper helper, BlockPos position) {
            var cube = position.east();
            helper.setBlock(cube, AddonCraftingScene.block("mekanism:creative_energy_cube"));
            MekanismSetup.fill(helper, cube);
            MekanismSetup.configure(helper, cube, "ENERGY", "OUTPUT", Direction.WEST, true);
            MekanismSetup.configure(helper, position, "ITEM", "INPUT_OUTPUT", Direction.WEST, true);
            MekanismSetup.configure(helper, position, "ENERGY", "INPUT", Direction.EAST, false);
        }

        /** Nothing to move: the Crusher ejects into the provider by itself. */
        @Override
        public void collect(GameTestHelper helper, BlockPos position, MEStorage network) {
        }
    }

    /**
     * A Mekanism machine powered by a charged Basic Energy Cube on its west, taking items on top and giving its product
     * at the bottom, as a player configures them.
     */
    private record PoweredMachine(String blockId, AEKey input, AEKey output, long outputPerInput)
            implements EndpointMachineScene.Machine {
        @Override
        public void prepare(GameTestHelper helper, BlockPos position) {
            var cube = position.west();
            helper.setBlock(cube, AddonCraftingScene.block("mekanism:basic_energy_cube"));
            MekanismSetup.fill(helper, cube);
            MekanismSetup.configure(helper, cube, "ENERGY", "OUTPUT", Direction.EAST, true);
            MekanismSetup.configure(helper, position, "ITEM", "INPUT", Direction.UP, false);
            MekanismSetup.configure(helper, position, "ITEM", "OUTPUT", Direction.DOWN, false);
            MekanismSetup.configure(helper, position, "ENERGY", "INPUT", Direction.WEST, false);
        }

        @Override
        public String state(GameTestHelper helper, BlockPos position) {
            return MekanismSetup.describe(helper, position);
        }
    }

    private static final class Crusher implements EndpointMachineScene.Machine {
        @Override
        public String blockId() {
            return "mekanism:crusher";
        }

        @Override
        public AEKey input() {
            return AEItemKey.of(Items.COBBLESTONE);
        }

        @Override
        public AEKey output() {
            return AEItemKey.of(Items.GRAVEL);
        }

        /**
         * A charged Basic Energy Cube beside the Crusher, ejecting energy into it; the Crusher takes items on top,
         * gives its product at the bottom and takes energy from the cube's side, as a player configures them.
         */
        @Override
        public void prepare(GameTestHelper helper, BlockPos position) {
            var cube = position.west();
            helper.setBlock(cube, AddonCraftingScene.block("mekanism:basic_energy_cube"));
            MekanismSetup.fill(helper, cube);
            MekanismSetup.configure(helper, cube, "ENERGY", "OUTPUT", Direction.EAST, true);
            MekanismSetup.configure(helper, position, "ITEM", "INPUT", Direction.UP, false);
            MekanismSetup.configure(helper, position, "ITEM", "OUTPUT", Direction.DOWN, false);
            MekanismSetup.configure(helper, position, "ENERGY", "INPUT", Direction.WEST, false);
        }

        @Override
        public String state(GameTestHelper helper, BlockPos position) {
            var level = helper.getLevel();
            var absolute = helper.absolutePos(position);
            var text = new StringBuilder();
            for (var side : net.minecraft.core.Direction.values()) {
                var items = level.getCapability(Capabilities.ItemHandler.BLOCK, absolute, side);
                var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, absolute, side);
                text.append(side).append(":items=").append(items == null ? "-" : describe(items))
                        .append(",energy=").append(energy == null ? "-" : energy.getEnergyStored()).append(' ');
            }
            var cube = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(position.west()), null);
            var entity = level.getBlockEntity(absolute);
            var cubeEntity = level.getBlockEntity(helper.absolutePos(position.west()));
            text.append("config=").append(MekanismSetup.describe(helper, position));
            return text + "cube=" + (cube == null ? "-" : cube.getEnergyStored()) + " entity="
                    + (entity == null ? null : entity.getClass().getName()) + " state=" + level.getBlockState(absolute)
                    + " cubeEntity=" + (cubeEntity == null ? null : cubeEntity.getClass().getName())
                    + " cubeState=" + level.getBlockState(helper.absolutePos(position.west()));
        }

        private static String describe(net.neoforged.neoforge.items.IItemHandler handler) {
            var text = new StringBuilder("[");
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                text.append(handler.getStackInSlot(slot)).append(slot + 1 < handler.getSlots() ? "," : "");
            }
            return text.append(']').toString();
        }
    }

    /** The provider network shares iron in a Basic Bin through a Storage Bus. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void storageBusBin(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "ae2:storage_bus", "mekanism:basic_bin",
                AEItemKey.of(Items.IRON_INGOT), 9, 4, bus -> {
                });
        helper.succeedWhen(scene::tick);
    }

    /** The same bin under a rule without Insert: the consumer takes iron but cannot store it back. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void storageBusBinExtractOnly(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "ae2:storage_bus", "mekanism:basic_bin",
                AEItemKey.of(Items.IRON_INGOT), 9, 4, bus -> {
                }).extractOnly();
        helper.succeedWhen(scene::tick);
    }

    /** The provider network shares water in a Basic Fluid Tank through a Storage Bus. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void storageBusMekanismFluidTank(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "ae2:storage_bus", "mekanism:basic_fluid_tank",
                AEFluidKey.of(Fluids.WATER), 4 * AEFluidKey.AMOUNT_BUCKET, AEFluidKey.AMOUNT_BUCKET, bus -> {
                });
        helper.succeedWhen(scene::tick);
    }

    private static final class Liquifier implements EndpointMachineScene.Machine {
        @Override
        public String blockId() {
            return "mekanism:nutritional_liquifier";
        }

        @Override
        public AEKey input() {
            return AEItemKey.of(Items.APPLE);
        }

        @Override
        public AEKey output() {
            var paste = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(
                    net.minecraft.resources.ResourceLocation.parse("mekanism:nutritional_paste"));
            return AEFluidKey.of(paste);
        }

        @Override
        public String outputCell() {
            return "ae2:fluid_storage_cell_1k";
        }

        /** An apple feeds 4, and the Liquifier makes 50 mB of paste per point. */
        @Override
        public long outputPerInput() {
            return 200;
        }

        @Override
        public boolean ejectsIntoEndpoint() {
            return true;
        }

        @Override
        public Direction inputFace() {
            return Direction.EAST;
        }

        /** Apples in on the Storage Bus side, paste ejected down into the Endpoint, energy from the cube behind. */
        @Override
        public void prepare(GameTestHelper helper, BlockPos position) {
            var cube = position.west();
            helper.setBlock(cube, AddonCraftingScene.block("mekanism:creative_energy_cube"));
            MekanismSetup.fill(helper, cube);
            MekanismSetup.configure(helper, cube, "ENERGY", "OUTPUT", Direction.EAST, true);
            MekanismSetup.configure(helper, position, "ITEM", "INPUT", Direction.EAST, false);
            MekanismSetup.configure(helper, position, "FLUID", "OUTPUT", Direction.DOWN, true);
            MekanismSetup.configure(helper, position, "ENERGY", "INPUT", Direction.WEST, false);
        }

        @Override
        public String state(GameTestHelper helper, BlockPos position) {
            var fluids = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                    helper.absolutePos(position.below()), Direction.UP);
            return MekanismSetup.describe(helper, position) + " endpointFluidHandler=" + fluids;
        }
    }

}
