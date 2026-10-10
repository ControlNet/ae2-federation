package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
import space.controlnet.ae2federation.test.processing.ProductionProviderScene;
import space.controlnet.ae2federation.test.storage.RouterStorageMountFixture;

/** Applied Flux's FE storage shared with a network that runs Mekanism machines on it. */
@PrefixGameTestTemplate(false)
public final class AppliedFluxMekanismCompatGameTests {
    /** West of the consumer's ME Chest: the Flux Accessor, and the Crusher it powers. */
    private static final BlockPos ACCESSOR = new BlockPos(2, 4, 5);
    private static final BlockPos CRUSHER = new BlockPos(1, 4, 5);
    private static final long STORED = 1_000_000;
    private static final int COBBLESTONE = 2;
    /** Cobblestone added after the Storage rule is off: more than the Crusher's own energy buffer can crush. */
    private static final int MORE_COBBLESTONE = 8;
    /** How long the Crusher is watched once the rule is off, enough for its buffer to run out several times. */
    private static final int WATCHED_TICKS = 500;

    private AppliedFluxMekanismCompatGameTests() {
    }

    /**
     * The guide's power bank: the provider network keeps FE in a 1k FE Storage Cell, and the workshop network, with no
     * storage or energy cell of its own, uses its storage and its ME power. The workshop's Flux Accessor sends the bank's
     * FE into a Mekanism Crusher, which crushes cobblestone into gravel on it. The guide's exercise: with the Storage
     * rule switched off, the bank's FE leaves the workshop, the accessor draws no more, and the Crusher stops once the
     * energy it holds runs out.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 2000)
    public static void fluxAccessorRunsCrusher(GameTestHelper helper) {
        var fixtures = new RouterStorageMountFixture(helper, false);
        fixtures.consumerChest().setCell(ItemStack.EMPTY);
        fixtures.providerChest().setCell(new ItemStack(AddonCraftingScene.item("appflux:fe_1k_cell")));
        helper.setBlock(ACCESSOR, AddonCraftingScene.block("appflux:flux_accessor"));
        helper.setBlock(CRUSHER, AddonCraftingScene.block("mekanism:crusher"));
        MekanismSetup.configure(helper, CRUSHER, "ITEM", "INPUT", Direction.UP, false);
        MekanismSetup.configure(helper, CRUSHER, "ITEM", "OUTPUT", Direction.DOWN, false);
        MekanismSetup.configure(helper, CRUSHER, "ENERGY", "INPUT", Direction.EAST, false);
        var fe = AppliedFluxCompatGameTests.fluxKey();
        var source = IActionSource.empty();
        var level = helper.getLevel();
        int[] step = {0};
        long[] afterOff = {0, 0};
        helper.succeedWhen(() -> {
            if (step[0] == 0 && fixtures.networksSettled()) {
                fixtures.connectRouters();
                step[0] = 1;
            }
            helper.assertTrue(step[0] >= 1 && fixtures.connected(), "The two Routers must join one Federation Domain");
            var provider = fixtures.providerGrid().getStorageService().getInventory();
            if (step[0] == 1) {
                helper.assertValueEqual(provider.insert(fe, STORED, Actionable.MODULATE, source), STORED,
                        "The power bank's FE cell must store " + STORED + " FE");
                var policies = PolicyService.get(level);
                policies.edit(new PolicyEdit(fixtures.key(), PolicyRevision.NONE, PolicyRule.storageDefaults()));
                policies.edit(new PolicyEdit(fixtures.energyKey(), PolicyRevision.NONE,
                        PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY))));
                var input = level.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(CRUSHER),
                        Direction.UP);
                helper.assertTrue(input != null, "The Crusher must take items on top");
                helper.assertTrue(input.insertItem(0, new ItemStack(Items.COBBLESTONE, COBBLESTONE), false).isEmpty(),
                        "The Crusher must take the cobblestone");
                step[0] = 2;
            }
            var gravel = gravel(helper);
            var left = provider.getAvailableStacks().get(fe);
            if (step[0] == 2) {
                helper.assertValueEqual(gravel, COBBLESTONE, "Waiting for the Crusher to crush the cobblestone on "
                        + "the power bank's FE; the bank holds " + left + " FE, the workshop is powered: "
                        + fixtures.consumerGrid().getEnergyService().isNetworkPowered() + ", "
                        + MekanismSetup.describe(helper, CRUSHER));
                helper.assertTrue(left < STORED, "The FE the Crusher ran on must come from the power bank: " + left);
                var policies = PolicyService.get(level);
                var key = fixtures.key();
                policies.edit(new PolicyEdit(key, policies.revision(key), PolicyRule.storageDefaults().withEnabled(false)));
                var input = level.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(CRUSHER),
                        Direction.UP);
                helper.assertTrue(input.insertItem(0, new ItemStack(Items.COBBLESTONE, MORE_COBBLESTONE), false)
                        .isEmpty(), "The Crusher must take more cobblestone");
                step[0] = 3;
            }
            if (step[0] == 3) {
                helper.assertValueEqual(fixtures.consumerGrid().getStorageService().getInventory()
                        .getAvailableStacks().get(fe), 0L, "Waiting for the bank's FE to leave the workshop");
                afterOff[0] = left;
                afterOff[1] = helper.getTick();
                step[0] = 4;
            }
            helper.assertTrue(helper.getTick() - afterOff[1] >= WATCHED_TICKS, "Watching the Crusher without the rule");
            helper.assertValueEqual(left, afterOff[0], "With the Storage rule off the accessor must draw no FE");
            helper.assertTrue(gravel < COBBLESTONE + MORE_COBBLESTONE, "The Crusher must stop once its own energy "
                    + "runs out, but crushed " + gravel);
            fixtures.close();
        });
    }

    /**
     * The Induction Card works in the Federation Pattern Provider as in AE2's own: the Provider sends its network's FE
     * into the machine touching it, here a Crusher beside it (not on its Federation face), and only with the card.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void inductionCardPowersAdjacentMachine(GameTestHelper helper) {
        var energy = new BlockPos(1, 1, 3);
        var chest = new BlockPos(2, 1, 3);
        var providerAt = new BlockPos(3, 1, 3);
        var crusher = new BlockPos(3, 1, 2);
        helper.setBlock(energy, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(chest, AEBlocks.ME_CHEST.block());
        helper.<MEChestBlockEntity>getBlockEntity(chest).setCell(new ItemStack(AddonCraftingScene.item("appflux:fe_1k_cell")));
        helper.setBlock(providerAt, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(crusher, AddonCraftingScene.block("mekanism:crusher"));
        MekanismSetup.configure(helper, crusher, "ENERGY", "INPUT", Direction.SOUTH, false);
        var fe = AppliedFluxCompatGameTests.fluxKey();
        var card = AddonCraftingScene.item("appflux:induction_card");
        int[] step = {0};
        long[] since = {0};
        helper.succeedWhen(() -> {
            var provider = helper.<FederationPatternProviderBlockEntity>getBlockEntity(providerAt);
            var grid = provider.getMainNode().getGrid();
            helper.assertTrue(grid != null && grid.getEnergyService().isNetworkPowered(),
                    "Waiting for the Provider's network to be powered");
            var storage = grid.getStorageService().getInventory();
            if (step[0] == 0) {
                helper.assertValueEqual(storage.insert(fe, STORED, Actionable.MODULATE, IActionSource.empty()), STORED,
                        "The FE cell must store " + STORED + " FE");
                since[0] = helper.getTick();
                step[0] = 1;
            }
            var input = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(crusher),
                    Direction.SOUTH);
            helper.assertTrue(input != null, "The Crusher must take FE on its south face");
            if (step[0] == 1) {
                helper.assertTrue(helper.getTick() - since[0] >= 100, "Watching the Crusher before the card goes in");
                helper.assertValueEqual(input.getEnergyStored(), 0, "Without the card the Provider must send no FE");
                helper.assertValueEqual(storage.getAvailableStacks().get(fe), STORED, "Without the card no FE leaves");
                helper.assertTrue(provider.upgrades().toItemHandler().insertItem(0, new ItemStack(card), false).isEmpty(),
                        "The Provider must take the Induction Card");
                step[0] = 2;
            }
            helper.assertTrue(input.getEnergyStored() > 0, "Waiting for the card to power the Crusher; the network "
                    + "holds " + storage.getAvailableStacks().get(fe) + " FE, " + MekanismSetup.describe(helper, crusher));
            helper.assertTrue(storage.getAvailableStacks().get(fe) < STORED, "The Crusher's FE must come from the network");
        });
    }

    /** The guide's Induction Card example with a Mekanism Crusher ({@link InductionCardExample}). */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1600)
    public static void inductionCardPowersEndpointMachine(GameTestHelper helper) {
        InductionCardExample.run(helper, new FluxPoweredCrusher());
    }

    /**
     * The Provider's FE reaches an Endpoint across Federation Cables and a Router, into a Basic Energy Cube touching
     * that Endpoint, and only while the Provider may push to it: once the Endpoint leaves the domain no FE goes.
     * The Federation Cable in front of the Provider answers with the relay only on the Provider's side.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1200)
    public static void providerEnergyFollowsTheLink(GameTestHelper helper) {
        var scene = new ProductionProviderScene(helper);
        // West of Endpoint A, which looks north at the Router.
        var cube = new BlockPos(6, 1, 4);
        helper.setBlock(cube, AddonCraftingScene.block("mekanism:basic_energy_cube"));
        MekanismSetup.configure(helper, cube, "ENERGY", "INPUT", Direction.EAST, false);
        helper.<MEChestBlockEntity>getBlockEntity(ProductionProviderScene.SOURCE_CHEST)
                .setCell(new ItemStack(AddonCraftingScene.item("appflux:fe_1k_cell")));
        var fe = AppliedFluxCompatGameTests.fluxKey();
        var card = AddonCraftingScene.item("appflux:induction_card");
        var level = helper.getLevel();
        int[] step = {0};
        long[] watched = {0, 0, 0};
        helper.succeedWhen(() -> {
            var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(cube), Direction.EAST);
            helper.assertTrue(energy != null, "The cube must take FE from the Endpoint's side");
            var relay = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    helper.absolutePos(ProductionProviderScene.CABLE_NEAR), Direction.WEST);
            if (step[0] == 0) {
                var readiness = scene.topologyReadiness();
                helper.assertTrue("ready".equals(readiness), "Waiting for production topology: " + readiness);
                helper.assertTrue(scene.setAccess(ProductionProviderScene.Target.A, true), "Endpoint A must connect");
                scene.installPattern(0);
                var status = scene.map(0, ProductionProviderScene.Target.A);
                helper.assertTrue(status.startsWith("accepted-"), "Mapping A: " + status);
                step[0] = 1;
                helper.fail("Mapped the pattern to Endpoint A");
            }
            var storage = scene.sourceGrid().getStorageService().getInventory();
            if (step[0] == 1) {
                helper.assertTrue(relay != null, "The Federation Cable must relay FE from the Provider's side");
                helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK,
                        helper.absolutePos(ProductionProviderScene.CABLE_NEAR), Direction.EAST) == null,
                        "The Federation Cable must not relay FE from any other side");
                helper.assertTrue(relay.receiveEnergy(1_000, true) > 0, "Waiting for the relay to reach the cube");
                helper.assertValueEqual(storage.insert(fe, STORED, Actionable.MODULATE, IActionSource.empty()), STORED,
                        "The FE cell must store " + STORED + " FE");
                helper.assertTrue(scene.provider().upgrades().toItemHandler().insertItem(0, new ItemStack(card), false)
                        .isEmpty(), "The Provider must take the Induction Card");
                step[0] = 2;
            }
            if (step[0] == 2) {
                helper.assertTrue(energy.getEnergyStored() > 0 && storage.getAvailableStacks().get(fe) < STORED,
                        "Waiting for the card's FE to reach the cube: cube " + energy.getEnergyStored() + ", network "
                        + storage.getAvailableStacks().get(fe));
                helper.assertTrue(scene.setAccess(ProductionProviderScene.Target.A, false), "Endpoint A must disconnect");
                watched[0] = helper.getTick();
                step[0] = 3;
            }
            if (step[0] == 3) {
                helper.assertTrue(helper.getTick() - watched[0] >= 20, "Letting the disconnect settle");
                helper.assertValueEqual(relay.receiveEnergy(1_000, true), 0,
                        "A disconnected Endpoint must get no FE through the relay");
                watched[0] = helper.getTick();
                watched[1] = energy.getEnergyStored();
                watched[2] = storage.getAvailableStacks().get(fe);
                step[0] = 4;
            }
            helper.assertTrue(helper.getTick() - watched[0] >= 100, "Watching the cube after the disconnect");
            helper.assertValueEqual((long) energy.getEnergyStored(), watched[1],
                    "The cube must get no FE once its Endpoint left the domain");
            helper.assertValueEqual(storage.getAvailableStacks().get(fe), watched[2],
                    "No FE may leave the network once the Endpoint left the domain");
        });
    }

    /**
     * A Crusher on the Endpoint with no energy source but the Endpoint below it: cobblestone in from the subnet's
     * Storage Bus on its east face, gravel ejected down into the Endpoint, energy taken from below.
     */
    private static final class FluxPoweredCrusher implements EndpointMachineScene.Machine {
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

        /** The Provider network's FE: an FE cell in the scene's second ME Chest. */
        @Override
        public String outputCell() {
            return "appflux:fe_1k_cell";
        }

        @Override
        public boolean ejectsIntoEndpoint() {
            return true;
        }

        @Override
        public Direction inputFace() {
            return Direction.EAST;
        }

        @Override
        public void prepare(GameTestHelper helper, BlockPos position) {
            MekanismSetup.configure(helper, position, "ITEM", "INPUT", Direction.EAST, false);
            MekanismSetup.configure(helper, position, "ITEM", "OUTPUT", Direction.DOWN, true);
            MekanismSetup.configure(helper, position, "ENERGY", "INPUT", Direction.DOWN, false);
        }

        @Override
        public String state(GameTestHelper helper, BlockPos position) {
            var energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(position),
                    Direction.DOWN);
            return "energy=" + (energy == null ? "-" : energy.getEnergyStored()) + " "
                    + MekanismSetup.describe(helper, position);
        }
    }

    private static int gravel(GameTestHelper helper) {
        var output = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(CRUSHER),
                Direction.DOWN);
        var gravel = 0;
        for (int slot = 0; output != null && slot < output.getSlots(); slot++) {
            if (output.getStackInSlot(slot).is(Items.GRAVEL)) gravel += output.getStackInSlot(slot).getCount();
        }
        return gravel;
    }
}
