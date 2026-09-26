package space.controlnet.ae2federation.test;

import appeng.api.config.LockCraftingMode;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;

@PrefixGameTestTemplate(false)
public final class ProviderLaneGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderLaneGameTests.class);
    private static final String STRUCTURE = "ae2federation_test:harness_native_smoke";

    private ProviderLaneGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void laneNativeThreeWay(GameTestHelper helper) {
        runFixture(helper, NativeProviderLaneFixtures.sharedPatternAssignments(), fixture -> {
            fixture.installPatterns(1);
            helper.assertValueEqual(fixture.composition().lanes().size(), 3, "Three native lane contexts must exist");
            helper.assertTrue(fixture.composition().lanes().stream()
                    .allMatch(lane -> lane.getAvailablePatterns().size() == 1), "One Pattern must publish in all lanes");
            var providers = fixture.publishedProviders(0, 0);
            helper.assertValueEqual(providers.size(), 3, "AE2 must publish three native provider mediums for one Pattern");
            helper.assertTrue(providers.containsAll(fixture.composition().lanes()),
                    "Published provider mediums must be the three captured native lanes");
            helper.assertTrue(fixture.nativeTickerInvocations().stream().allMatch(count -> count > 0),
                    "Native grid ticking must invoke every captured lane ticker");
            fixture.lockUntilResult(0);
            helper.assertTrue(fixture.push(0, 0), "Lane A must accept the first native push");
            helper.assertValueEqual(fixture.lane(0).getCraftingLockedReason(), LockCraftingMode.LOCK_UNTIL_RESULT,
                    "Lane A must retain native lock state");
            helper.assertTrue(!fixture.push(0, 0), "Locked lane A must reject a second native push");
            helper.assertTrue(fixture.push(1, 0), "Lane B must progress while A is locked");
            helper.assertTrue(fixture.push(2, 0), "Lane C must progress while A is locked");
            helper.assertValueEqual(fixture.targetItemCount(), 3, "All successful native pushes must reach the target");
            fixture.composition().close();
            helper.assertValueEqual(fixture.publishedProviders(0, 0).size(), 0,
                    "Closing the composition must remove every global provider medium");
            writeEvidence("lanenativethreeway", 12, Map.of("laneCount", "3", "publishedProviderContexts", "3",
                    "publishedProvidersMatchLanes", "true", "nativeTickerDelegatesInvoked", "3",
                    "publishedProvidersAfterClose", "0",
                    "laneALocked", "true", "laneBProgressed", "true", "laneCProgressed", "true",
                    "nativePushCount", "3"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void lanePatternSubsets(GameTestHelper helper) {
        runFixture(helper, NativeProviderLaneFixtures.subsetAssignments(), fixture -> {
            fixture.installPatterns(3);
            var sizes = fixture.composition().lanes().stream().map(lane -> lane.getAvailablePatterns().size()).toList();
            helper.assertValueEqual(sizes, List.of(1, 2, 2), "Lane Pattern views must follow assigned slot subsets");
            helper.assertTrue(fixture.push(0, 0), "Lane A subset must remain executable");
            helper.assertTrue(fixture.push(1, 1), "Lane B second subset Pattern must remain executable");
            helper.assertTrue(fixture.push(2, 1), "Lane C third Pattern must remain executable");
            helper.assertValueEqual(fixture.targetItemCount(), 3, "Subset pushes must use the native target path");
            writeEvidence("lanepatternsubsets", 5, Map.of("laneAPatterns", "1", "laneBPatterns", "2",
                    "laneCPatterns", "2", "nativePushCount", "3"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void laneSingleDropOwner(GameTestHelper helper) {
        runFixture(helper, NativeProviderLaneFixtures.sharedPatternAssignments(), fixture -> {
            fixture.installPatterns(1);
            var drops = new java.util.ArrayList<ItemStack>();
            fixture.composition().addDrops(drops);
            var patterns = drops.stream().filter(AEItems.PROCESSING_PATTERN::is).mapToInt(ItemStack::getCount).sum();
            helper.assertValueEqual(patterns, 1, "Encoded Pattern must have one drop owner");
            helper.assertValueEqual(fixture.composition().patternInventory().size(), 3,
                    "Only the physical inventory owns extractable Pattern slots");
            helper.assertTrue(fixture.composition().lanes().stream().allMatch(lane -> lane.getPatternInv().size() == 0),
                    "Native lanes must not own duplicate Pattern inventories");
            writeEvidence("lanesingledropowner", 3, Map.of("singleDropOwner", "true", "patternDrops", "1",
                    "lanePatternInventorySlots", "0"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void laneRejectLocalFallback(GameTestHelper helper) {
        runFixture(helper, NativeProviderLaneFixtures.sharedPatternAssignments(), fixture -> {
            fixture.installPatterns(1);
            fixture.installBypassCraftingMachine();
            helper.assertTrue(fixture.hasBypassCraftingMachine(),
                    "A real adjacent AE2 crafting machine must exist outside the configured target side");
            fixture.removeConfiguredTarget();
            helper.assertTrue(!fixture.push(0, 0), "Native push must fail when the configured target is absent");
            helper.assertValueEqual(fixture.targetItemCount(), 0, "No local fallback may consume Pattern inputs");
            helper.assertTrue(fixture.bypassCraftingMachineIsEmpty(),
                    "The non-target AE2 crafting machine must not receive Pattern inputs");
            writeEvidence("lanerejectlocalfallback", 4, Map.of("localFallbackAccepted", "false",
                    "adjacentMachineCandidate", "true", "adjacentMachineBypass", "false"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void laneNativeReload(GameTestHelper helper) {
        runFixture(helper, NativeProviderLaneFixtures.sharedPatternAssignments(), fixture -> {
            fixture.installPatterns(1);
            fixture.lockUntilResult(0);
            helper.assertTrue(fixture.push(0, 0), "Pre-save native push must establish locked state");
            var tag = new CompoundTag();
            fixture.composition().writeToNBT(tag, helper.getLevel().registryAccess());
            fixture.lane(0).resetCraftingLock();
            fixture.composition().patternInventory().clear();
            fixture.composition().readFromNBT(tag, helper.getLevel().registryAccess());
            helper.assertValueEqual(fixture.lane(0).getCraftingLockedReason(), LockCraftingMode.LOCK_UNTIL_RESULT,
                    "Native lane lock must round-trip through NBT");
            helper.assertValueEqual(fixture.lane(0).getAvailablePatterns().size(), 1,
                    "Mapped Pattern view must rebuild after native state load");
            helper.assertValueEqual(fixture.composition().patternInventory().getStackInSlot(0).getCount(), 1,
                    "Physical Pattern inventory must round-trip once");
            writeEvidence("lanenativereload", 4, Map.of("nativeStateRestored", "true", "laneALocked", "true",
                    "singlePatternRestored", "true"));
        });
    }

    private static void runFixture(GameTestHelper helper, List<java.util.function.IntPredicate> assignments,
            java.util.function.Consumer<NativeProviderLaneFixtures> assertions) {
        var fixture = new NativeProviderLaneFixtures(helper, assignments);
        var registered = new boolean[] { false };
        helper.succeedWhen(() -> {
            if (!fixture.connectEnergy()) {
                helper.assertTrue(false, "Waiting for native Provider Lane energy connection");
            }
            if (!fixture.composition().isActive()) {
                helper.assertTrue(false, "Waiting for active native Provider Lane node");
            }
            if (!registered[0]) {
                fixture.register();
                registered[0] = true;
            }
            fixture.wakeNativeTicker();
            if (fixture.nativeTickerInvocations().stream().anyMatch(count -> count == 0)) {
                helper.assertTrue(false, "Waiting for native grid ticking to invoke every Provider Lane delegate");
            }
            assertions.accept(fixture);
            fixture.close();
        });
    }

    private static void writeEvidence(String testId, int assertions, Map<String, String> facts) {
        facts.forEach((name, value) -> LOGGER.info("AE2F_LANE_TRACE testId={} fact={} value={}", testId, name, value));
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) {
            return;
        }
        var path = Path.of(configured).toAbsolutePath().normalize();
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        var properties = new Properties();
        properties.setProperty("schemaVersion", "1");
        properties.setProperty("status", "passed");
        properties.setProperty("kind", "verify");
        properties.setProperty("testId", testId);
        properties.setProperty("structure", STRUCTURE);
        properties.setProperty("assertions", Integer.toString(assertions));
        properties.setProperty("operations", "1");
        properties.setProperty("inserted", "0");
        properties.setProperty("extracted", "0");
        properties.setProperty("elapsedNanos", "0");
        facts.forEach(properties::setProperty);
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation native Provider Lane evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Provider Lane evidence to " + path, exception);
        }
    }
}
