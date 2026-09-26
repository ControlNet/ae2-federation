package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.storage.resources.NativeResourceAmounts;
import space.controlnet.ae2federation.test.energy.NativeEnergyFixtures;
import space.controlnet.ae2federation.test.resources.ResourceEvidence;
import space.controlnet.ae2federation.test.resources.ResourceStorageFixture;

@PrefixGameTestTemplate(false)
public final class ResourceQualificationGameTests {
    private static final IActionSource SOURCE = IActionSource.empty();

    private ResourceQualificationGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void resourcesItemFluidComponents(GameTestHelper helper) {
        var fixture = new ResourceStorageFixture(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native item and fluid storage");
            var firstStack = namedDiamond("component-a");
            var secondStack = namedDiamond("component-b");
            var first = AEItemKey.of(firstStack);
            var second = AEItemKey.of(secondStack);
            var water = AEFluidKey.of(Fluids.WATER);
            helper.assertTrue(first != null && second != null && water != null, "Native keys must resolve");
            helper.assertTrue(!first.equals(second), "Distinct item components must not alias");
            var registries = helper.getLevel().registryAccess();
            var firstRoundTrip = AEKey.fromTagGeneric(registries, first.toTagGeneric(registries));
            var secondRoundTrip = AEKey.fromTagGeneric(registries, second.toTagGeneric(registries));
            var waterRoundTrip = AEKey.fromTagGeneric(registries, water.toTagGeneric(registries));
            helper.assertValueEqual(firstRoundTrip, first, "First component key must serialize exactly");
            helper.assertValueEqual(secondRoundTrip, second, "Second component key must serialize exactly");
            helper.assertValueEqual(waterRoundTrip, water, "Fluid key must serialize exactly");
            helper.assertTrue(!firstRoundTrip.equals(secondRoundTrip), "Serialization must preserve component identity");
            helper.assertTrue(first.getType().filter().matches(first) && first.getType().filter().matches(second),
                    "Native item type filter must recognize component-bearing item keys");
            helper.assertTrue(water.getType().filter().matches(water) && !water.getType().filter().matches(first),
                    "Native fluid filter must reject item keys");
            appeng.api.storage.AEKeyFilter exactFirst = first::equals;
            helper.assertTrue(exactFirst.matches(first) && !exactFirst.matches(second),
                    "Exact native key filter must preserve component identity");

            var items = fixture.itemStorage();
            var fluids = fixture.fluidStorage();
            helper.assertValueEqual(items.insert(first, 7, Actionable.SIMULATE, SOURCE), 7L,
                    "Native item simulation must report capacity");
            helper.assertValueEqual(items.getAvailableStacks().get(first), 0L,
                    "Native item simulation must not mutate");
            helper.assertValueEqual(items.insert(first, 7, Actionable.MODULATE, SOURCE), 7L,
                    "Native first component insert must succeed");
            helper.assertValueEqual(items.insert(second, 11, Actionable.MODULATE, SOURCE), 11L,
                    "Native second component insert must succeed independently");
            helper.assertValueEqual(items.getAvailableStacks().get(first), 7L,
                    "First component quantity must remain independent");
            helper.assertValueEqual(items.getAvailableStacks().get(second), 11L,
                    "Second component quantity must remain independent");
            helper.assertValueEqual(fluids.insert(water, 5_000L, Actionable.MODULATE, SOURCE), 5_000L,
                    "Native fluid insert must preserve native long units");
            helper.assertValueEqual(fluids.extract(water, 1_250L, Actionable.SIMULATE, SOURCE), 1_250L,
                    "Native fluid extraction simulation must report native units");
            helper.assertValueEqual(fluids.getAvailableStacks().get(water), 5_000L,
                    "Native fluid simulation must not mutate");
            helper.assertValueEqual(fluids.extract(water, 1_250L, Actionable.MODULATE, SOURCE), 1_250L,
                    "Native fluid extraction must preserve native units");
            ResourceEvidence.write("resourcesitemfluidcomponents", 20, Map.ofEntries(
                    Map.entry("componentKeysDistinct", "true"), Map.entry("componentRoundTrip", "true"),
                    Map.entry("fluidRoundTrip", "true"), Map.entry("exactFilterDistinct", "true"),
                    Map.entry("itemTypeFilter", "true"), Map.entry("fluidTypeFilter", "true"),
                    Map.entry("firstQuantity", "7"), Map.entry("secondQuantity", "11"),
                    Map.entry("fluidInserted", "5000"), Map.entry("fluidSimulatedExtract", "1250"),
                    Map.entry("fluidRemaining", "3750"), Map.entry("nativeAmountType", "long"),
                    Map.entry("fluidUnit", "millibucket")));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void resourcesOptionalAbsent(GameTestHelper helper) {
        helper.succeedWhen(() -> {
            var classResource = ResourceQualificationGameTests.class.getClassLoader().getResource(
                    "com/glodblock/github/appflux/common/me/key/FluxKey.class");
            helper.assertTrue(classResource == null, "Default runtime must not contain Applied Flux classes");
            helper.assertTrue(missingAppliedFluxKeyType(), "Default AE2 key registry must not contain Applied Flux");
            helper.assertTrue(AEKeyTypes.getAll().stream().noneMatch(candidate ->
                    candidate.getId().getNamespace().equals("appflux")), "No Applied Flux key type may leak into default runtime");
            helper.assertTrue(missingAppliedFluxClass(), "Applied Flux class loading must report optional absence");
            ResourceEvidence.write("resourcesoptionalabsent", 4, Map.of(
                    "addonClassPresent", "false", "addonKeyTypePresent", "false",
                    "compatibility", "missing", "substituteKeyCreated", "false",
                    "defaultRuntimeIsolated", "true"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void resourcesRejectOverflow(GameTestHelper helper) {
        var fixture = new ResourceStorageFixture(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native storage");
            var key = AEItemKey.of(Items.IRON_INGOT);
            var storage = fixture.itemStorage();
            helper.assertTrue(key != null, "Native item key must resolve");
            helper.assertTrue(rejects(() -> NativeResourceAmounts.checkedAdd(Long.MAX_VALUE, 1)),
                    "Long overflow must be rejected");
            helper.assertTrue(rejects(() -> NativeResourceAmounts.checkedAdd(0, -1)),
                    "Negative quantity must be rejected");
            var simulated = storage.insert(key, Long.MAX_VALUE, Actionable.SIMULATE, SOURCE);
            helper.assertTrue(simulated >= 0, "Native capacity response must not wrap negative");
            helper.assertValueEqual(storage.getAvailableStacks().get(key), 0L,
                    "Overflow-boundary simulation must not mutate storage");
            helper.assertValueEqual(NativeResourceAmounts.checkedAdd(Long.MAX_VALUE - 1, 1), Long.MAX_VALUE,
                    "Largest valid long quantity must be retained exactly");
            ResourceEvidence.write("resourcesrejectoverflow", 7, Map.of(
                    "overflowRejected", "true", "negativeRejected", "true",
                    "negativeSubmittedToNative", "false", "simulationNonNegative", "true",
                    "simulationMutation", "false", "largestValid", Long.toString(Long.MAX_VALUE),
                    "quantityType", "long"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void resourcesRejectFePowerCoupling(GameTestHelper helper) {
        var resources = new ResourceStorageFixture(helper);
        var energy = new NativeEnergyFixtures(helper, 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(resources.ready() && energy.ready(), "Waiting for native storage and AE power services");
            energy.charge(0, 1_000);
            var powerBefore = energy.stored(0);
            var key = AEItemKey.of(Items.REDSTONE);
            helper.assertTrue(key != null, "Native item key must resolve");
            var inserted = resources.itemStorage().insert(key, 64, Actionable.MODULATE, SOURCE);
            var powerAfter = energy.stored(0);
            helper.assertValueEqual(inserted, 64L, "Native resource operation must complete independently");
            helper.assertValueEqual(powerAfter, powerBefore, "Stored resources must not charge or drain AE power");
            helper.assertTrue(missingAppliedFluxKeyType(),
                    "AE power must not synthesize an absent FE key");
            helper.assertTrue(resources.itemStorage().getAvailableStacks().keySet().stream().noneMatch(candidate ->
                    candidate.getType().getId().getNamespace().equals("appflux")),
                    "AE power must not appear as stored FE without a native key operation");
            ResourceEvidence.write("resourcesrejectfepowercoupling", 6, Map.of(
                    "resourceInserted", "64", "aePowerBefore", number(powerBefore),
                    "aePowerAfter", number(powerAfter), "feKeyPresent", "false",
                    "syntheticFeKey", "false", "unitConversion", "none",
                    "coupled", "false"));
            energy.close();
        });
    }

    private static ItemStack namedDiamond(String name) {
        var stack = new ItemStack(Items.DIAMOND);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static boolean missingAppliedFluxClass() {
        try {
            Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey", false,
                    ResourceQualificationGameTests.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException expected) {
            return true;
        }
    }

    private static boolean missingAppliedFluxKeyType() {
        try {
            AEKeyTypes.get(ResourceLocation.fromNamespaceAndPath("appflux", "flux"));
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean rejects(Runnable operation) {
        try {
            operation.run();
            return false;
        } catch (IllegalArgumentException | ArithmeticException expected) {
            return true;
        }
    }

    private static String number(double value) {
        return Long.toString(Math.round(value));
    }
}
