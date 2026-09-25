package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.processing.ProviderLifecycleFixtureSupport.*;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.core.definitions.AEItems;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;
import space.controlnet.ae2federation.test.processing.ProviderLifecycleEvidence;

@PrefixGameTestTemplate(false)
public final class ProviderLifecycleGameTests {
    private ProviderLifecycleGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void providerPatternMapping(GameTestHelper helper) {
        runFixture(helper, NativeProviderLaneFixtures.subsetAssignments(), fixture -> {
            fixture.installPatterns(3);
            helper.assertValueEqual(patternSizes(fixture), List.of(1, 2, 2), "Mapped Pattern entries must be exact");
            var laneOnePattern = pattern(fixture, 1, 1);
            var laneTwoPattern = pattern(fixture, 2, 0);
            var equalProviders = fixture.publishedProviders(laneOnePattern);
            helper.assertValueEqual(equalProviders.size(), 2, "Equal Pattern must retain two provider media");
            helper.assertTrue(equalProviders.contains(fixture.lane(1)) && equalProviders.contains(fixture.lane(2)),
                    "Equal Pattern media must be the assigned native Lanes");
            helper.assertTrue(laneOnePattern.equals(laneTwoPattern) && laneOnePattern != laneTwoPattern,
                    "Each Lane must retain its own equal decoded Pattern handle");
            var removed = pattern(fixture, 0, 0);
            clearPattern(fixture, 0);
            helper.assertValueEqual(patternSizes(fixture), List.of(0, 1, 2), "Removal must refresh assigned Lanes");
            helper.assertValueEqual(fixture.publishedProviders(removed).size(), 0,
                    "Removed Pattern must leave native publication");
            setPattern(fixture, 0, Items.COBBLESTONE, Items.EMERALD);
            helper.assertValueEqual(patternSizes(fixture), List.of(1, 2, 2), "Replacement must republish assigned Lanes");
            var before = nativeProviderRefreshInvocations(fixture);
            helper.assertTrue(replaceMapping(fixture, mappingHandle(fixture, 2), Set.of(0)),
                    "Current mapping handle must update");
            helper.assertValueEqual(refreshDelta(before, nativeProviderRefreshInvocations(fixture)), List.of(1L, 0L, 1L),
                    "Only old and new mapped Lanes may refresh");
            helper.assertValueEqual(fixture.composition().lanesForSlot(2), Set.of(0),
                    "Updated mapping must be deterministic");
            ProviderLifecycleEvidence.write("providerpatternmapping", 10, Map.of(
                    "patternSlots", "3", "laneCount", "3", "providerEntries", "5",
                    "equalProviderMedia", "2", "removedProviderMedia", "0",
                    "mappingRefreshDelta", "1,0,1", "replacementPublished", "true"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void providerRefreshPriority(GameTestHelper helper) {
        runFixture(helper, twoLaneAssignments(), fixture -> {
            var beforeInsert = nativeProviderRefreshInvocations(fixture);
            setPattern(fixture, 0, Items.COBBLESTONE, Items.DIAMOND);
            helper.assertValueEqual(refreshDelta(beforeInsert, nativeProviderRefreshInvocations(fixture)),
                    List.of(1L, 1L, 0L), "Insertion must refresh only assigned native publications");
            fixture.lane(0).getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
            putTargetItem(fixture, Items.COBBLESTONE);
            helper.assertTrue(!fixture.push(0, 0), "Blocking must use the mapped Pattern input set");
            var oldPattern = pattern(fixture, 0, 0);
            setPattern(fixture, 0, Items.DIRT, Items.EMERALD);
            helper.assertTrue(!pushDetails(fixture, 0, oldPattern), "Replacement must reject stale Pattern details");
            helper.assertTrue(blockingInputs(fixture, 0).contains(appeng.api.stacks.AEItemKey.of(Items.DIRT))
                    && !blockingInputs(fixture, 0).contains(appeng.api.stacks.AEItemKey.of(Items.COBBLESTONE)),
                    "Replacement must rebuild native Blocking inputs");
            clearTarget(fixture);
            helper.assertTrue(fixture.push(0, 0), "Replacement must refresh native Blocking inputs");
            var beforePriority = nativeProviderRefreshInvocations(fixture);
            fixture.composition().setPriority(37);
            helper.assertTrue(fixture.composition().lanes().stream().allMatch(lane -> lane.getPatternPriority() == 37),
                    "Shared native priority must reach every Lane");
            helper.assertValueEqual(refreshDelta(beforePriority, nativeProviderRefreshInvocations(fixture)),
                    List.of(1L, 1L, 1L), "Priority must refresh every global native provider");
            var containers = activePatternContainers(fixture);
            helper.assertValueEqual(containers.size(), 1, "The Grid must expose one physical Pattern container");
            helper.assertTrue(containers.getFirst() == fixture
                    && fixture.getTerminalPatternInventory() == fixture.composition().patternInventory(),
                    "Pattern Access Terminal must bind the physical inventory owner");
            fixture.composition().setPatternAccessTerminalVisible(false);
            helper.assertTrue(!fixture.isVisibleInTerminal(), "Native terminal visibility must hide the physical owner");
            fixture.composition().setPatternAccessTerminalVisible(true);
            helper.assertTrue(fixture.isVisibleInTerminal(), "Native terminal visibility must restore the physical owner");
            fixture.composition().setLockCraftingMode(LockCraftingMode.LOCK_WHILE_HIGH);
            setRedstoneSignal(fixture, true);
            helper.assertTrue(fixture.composition().lanes().stream().allMatch(
                    lane -> lane.getCraftingLockedReason() == LockCraftingMode.LOCK_WHILE_HIGH),
                    "Redstone high state must reach every native Lane");
            setRedstoneSignal(fixture, false);
            helper.assertTrue(fixture.composition().lanes().stream().allMatch(
                    lane -> lane.getCraftingLockedReason() == LockCraftingMode.NONE),
                    "Redstone state clearing must reach every native Lane");
            ProviderLifecycleEvidence.write("providerrefreshpriority", 13, Map.of(
                    "insertRefreshDelta", "1,1,0", "priorityRefreshDelta", "1,1,1", "nativePriority", "37",
                    "blockingRefreshed", "true", "terminalContainers", "1", "redstoneLanes", "3"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void providerSingleInventory(GameTestHelper helper) {
        runFixture(helper, NativeProviderLaneFixtures.sharedPatternAssignments(), fixture -> {
            setPattern(fixture, 0, Items.COBBLESTONE, Items.DIAMOND);
            helper.assertValueEqual(fixture.composition().patternInventory().size(), 3,
                    "Physical owner must expose all Pattern slots");
            helper.assertTrue(fixture.composition().lanes().stream().allMatch(lane -> lane.getPatternInv().size() == 0),
                    "Native Lanes must own no Pattern slots");
            helper.assertTrue(fixture.ownerSaveCalls() > 0, "Physical inventory mutation must save through its owner");
            helper.assertValueEqual(fixture.laneSaveCalls(), 0, "Pattern mutation must not save through Lane inventories");
            var containers = activePatternContainers(fixture);
            helper.assertValueEqual(containers.size(), 1, "Only one Pattern container may be terminal-visible");
            helper.assertTrue(containers.getFirst().getTerminalPatternInventory() == fixture.composition().patternInventory(),
                    "Terminal extraction must use the physical inventory");
            var drops = new java.util.ArrayList<ItemStack>();
            fixture.composition().addDrops(drops);
            var patternDrops = drops.stream().filter(AEItems.PROCESSING_PATTERN::is).mapToInt(ItemStack::getCount).sum();
            helper.assertValueEqual(patternDrops, 1, "Physical encoded Pattern must drop exactly once");
            var extracted = fixture.composition().patternInventory().extractItem(0, 1, false);
            helper.assertValueEqual(extracted.getCount(), 1, "Physical inventory must own extraction");
            helper.assertTrue(fixture.composition().lanes().stream().allMatch(lane -> lane.getAvailablePatterns().isEmpty()),
                    "Physical extraction must clear every assigned publication");
            ProviderLifecycleEvidence.write("providersingleinventory", 9, Map.of(
                    "patternSlots", "3", "lanePatternSlots", "0", "terminalContainers", "1",
                    "patternDrops", "1", "ownerSaved", "true", "laneInventorySaves", "0"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void providerRejectStalePattern(GameTestHelper helper) {
        runFixture(helper, oneLanePerSlot(), fixture -> {
            setPattern(fixture, 0, Items.COBBLESTONE, Items.DIAMOND);
            var stalePattern = pattern(fixture, 0, 0);
            setPattern(fixture, 0, Items.COBBLESTONE, Items.DIAMOND);
            var replacement = pattern(fixture, 0, 0);
            helper.assertTrue(stalePattern.equals(replacement), "Replacement details must be semantically equal");
            helper.assertTrue(stalePattern != replacement, "Replacement must create a new decoded native handle");
            helper.assertTrue(pushDetails(fixture, 0, stalePattern),
                    "An equal Pattern handle executes, as native PatternProviderLogic compares Patterns by equality");
            helper.assertTrue(pushDetails(fixture, 0, replacement), "Current replacement handle must execute natively");
            var staleMapping = mappingHandle(fixture, 0);
            helper.assertTrue(replaceMapping(fixture, staleMapping, Set.of(0, 1)), "Current mapping must apply");
            helper.assertTrue(!replaceMapping(fixture, staleMapping, Set.of(2)), "Stale mapping handle must be rejected");
            clearPattern(fixture, 0);
            helper.assertTrue(!pushDetails(fixture, 0, replacement), "Removed Pattern handle must be rejected");
            setPattern(fixture, 0, Items.DIRT, Items.EMERALD);
            var beforeLoad = pattern(fixture, 0, 0);
            var tag = new CompoundTag();
            fixture.composition().writeToNBT(tag, helper.getLevel().registryAccess());
            setPattern(fixture, 0, Items.SAND, Items.GOLD_INGOT);
            var overwritten = pattern(fixture, 0, 0);
            fixture.composition().readFromNBT(tag, helper.getLevel().registryAccess());
            helper.assertTrue(!pushDetails(fixture, 0, overwritten),
                    "A Pattern absent after rebuild must be rejected");
            helper.assertTrue(pushDetails(fixture, 0, beforeLoad), "The restored persisted Pattern executes natively");
            helper.assertValueEqual(fixture.composition().lanesForSlot(0), Set.of(0, 1),
                    "Reload must restore authoritative mapping ownership");
            helper.assertValueEqual(patternSizes(fixture), List.of(1, 1, 0),
                    "Reload must rebuild only persisted Lane ownership");
            ProviderLifecycleEvidence.write("providerrejectstalepattern", 11, Map.of(
                    "equalHandleAccepted", "true", "removedStaleRejected", "true", "reloadStaleRejected", "true",
                    "staleMappingRejected", "true", "restoredMapping", "0,1", "restoredLaneEntries", "1,1,0"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void providerNativeTickerCount(GameTestHelper helper) {
        runFixture(helper, NativeProviderLaneFixtures.sharedPatternAssignments(), fixture -> {
            setPattern(fixture, 0, Items.COBBLESTONE, Items.DIAMOND);
            var pattern = pattern(fixture, 0, 0);
            helper.assertValueEqual(fixture.composition().nativeTickerDelegateCount(), 3,
                    "Composite ticker must retain one native delegate per Lane");
            helper.assertTrue(fixture.composition().hasSinglePhysicalTickerService(),
                    "Physical node must expose the one composite ticker service");
            helper.assertTrue(!fixture.composition().hasPhysicalCraftingProviderService(),
                    "Physical node must not install a duplicate crafting provider service");
            helper.assertTrue(fixture.nativeTickerInvocations().stream().allMatch(count -> count > 0),
                    "Grid ticking must invoke all native Lane delegates");
            helper.assertValueEqual(fixture.publishedProviders(pattern).size(), 3,
                    "Three Lanes must remain three native provider media");
            helper.assertTrue(tickerStatus(fixture).awake(), "Native alert must wake the physical ticker");
            helper.assertTrue(sleepNativeTicker(fixture), "Native tick manager must sleep the physical ticker");
            helper.assertTrue(tickerStatus(fixture).sleeping(), "Physical ticker must report native sleeping state");
            helper.assertTrue(fixture.wakeNativeTicker() && tickerStatus(fixture).awake(),
                    "Native wake must restore the physical ticker");
            fixture.composition().close();
            helper.assertValueEqual(fixture.publishedProviders(pattern).size(), 0,
                    "Closing lifecycle must remove every Lane provider");
            ProviderLifecycleEvidence.write("providernativetickercount", 10, Map.of(
                    "physicalTickerServices", "1", "physicalProviderServices", "0", "tickerDelegates", "3",
                    "delegatesInvoked", "3", "providerMedia", "3", "providersAfterClose", "0",
                    "nativeSleepWake", "true"));
        });
    }

    private static void runFixture(GameTestHelper helper, List<IntPredicate> assignments,
            java.util.function.Consumer<NativeProviderLaneFixtures> assertions) {
        var fixture = new NativeProviderLaneFixtures(helper, assignments);
        var registered = new boolean[] { false };
        helper.succeedWhen(() -> {
            if (!fixture.connectEnergy() || !fixture.composition().isActive()) {
                helper.assertTrue(false, "Waiting for active mapped native Provider node");
            }
            if (!registered[0]) {
                fixture.register();
                registered[0] = true;
            }
            fixture.wakeNativeTicker();
            if (fixture.nativeTickerInvocations().stream().anyMatch(count -> count == 0)) {
                helper.assertTrue(false, "Waiting for all native Provider ticker delegates");
            }
            assertions.accept(fixture);
            fixture.close();
        });
    }

    private static List<Integer> patternSizes(NativeProviderLaneFixtures fixture) {
        return fixture.composition().lanes().stream().map(lane -> lane.getAvailablePatterns().size()).toList();
    }

    private static List<Long> refreshDelta(List<Long> before, List<Long> after) {
        return java.util.stream.IntStream.range(0, before.size()).mapToObj(index -> after.get(index) - before.get(index)).toList();
    }

    private static List<IntPredicate> twoLaneAssignments() {
        return List.of(slot -> slot == 0, slot -> slot == 0, slot -> slot == 1);
    }

    private static List<IntPredicate> oneLanePerSlot() {
        return List.of(slot -> slot == 0, slot -> slot == 1, slot -> slot == 2);
    }
}
