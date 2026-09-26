package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.ae2.storage.NativeStorageProvenance;
import space.controlnet.ae2federation.test.storage.StorageProofFixtures;

@PrefixGameTestTemplate(false)
public final class StorageNativeCharacterizationGameTests {
    private StorageNativeCharacterizationGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void storageNativeOperationsPriorityCharacterization(GameTestHelper helper) {
        var fixture = new StorageProofFixtures(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for powered native storage Grid");
            var provenance = new NativeStorageProvenance();
            var source = provenance.qualify(fixture.nativeNode()).getFirst().storage();
            var low = provenance.createProvider();
            var high = provenance.createProvider();
            low.mountRoute("characterization-low", source, 10);
            high.mountRoute("characterization-high", source, 40);

            var selected = provenance.sources(List.of(low, high)).getFirst();
            var key = AEItemKey.of(Items.IRON_INGOT);
            var actionSource = IActionSource.empty();
            helper.assertValueEqual(selected.priority(), 40, "Highest native route priority must win");
            helper.assertValueEqual(source.insert(key, 8, Actionable.SIMULATE, actionSource), 8L,
                    "Native insertion simulation must report accepted amount");
            helper.assertValueEqual(source.getAvailableStacks().get(key), 0L,
                    "Native insertion simulation must not mutate storage");
            helper.assertValueEqual(source.insert(key, 8, Actionable.MODULATE, actionSource), 8L,
                    "Native insertion must return the accepted amount");
            helper.assertValueEqual(source.extract(key, 3, Actionable.SIMULATE, actionSource), 3L,
                    "Native extraction simulation must report available amount");
            helper.assertValueEqual(source.getAvailableStacks().get(key), 8L,
                    "Native extraction simulation must not mutate storage");
            helper.assertValueEqual(source.extract(key, 3, Actionable.MODULATE, actionSource), 3L,
                    "Native extraction must return the extracted amount");
            helper.assertValueEqual(source.getAvailableStacks().get(key), 5L,
                    "Native source quantity must reflect only modulated operations");
        });
    }
}
