package space.controlnet.ae2federation.compat;

import appeng.api.inventories.InternalInventory;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with AE2 Pattern Disk's own blocks. */
@PrefixGameTestTemplate(false)
public final class PatternDiskCompatGameTests {
    private PatternDiskCompatGameTests() {
    }

    /** A Pattern Disk Provider serves the remote craft from a pattern written onto a 1k Pattern Disk. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void patternDiskProviderCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2_pattern_disk:pattern_disk_provider", "ae2:molecular_assembler",
                List.of("ae2:1k_crafting_storage")).installingPatternsWith((entity, pattern) -> insertOnDisk(helper,
                entity, pattern));
        helper.succeedWhen(scene::tick);
    }

    /** The same provider runs the remote request's processing pattern, from a disk, in a machine. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void patternDiskProviderProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2_pattern_disk:pattern_disk_provider", "minecraft:chest",
                List.of("ae2:1k_crafting_storage"), true).installingPatternsWith((entity, pattern) -> insertOnDisk(
                helper, entity, pattern));
        helper.succeedWhen(scene::tick);
    }

    /**
     * Writes the pattern onto a new disk with Pattern Disk's public API and puts the disk into the provider, as its
     * Encoding Terminal and a player do. Called by reflection: the test mod does not build against the addon.
     */
    private static boolean insertOnDisk(GameTestHelper helper, BlockEntity provider, ItemStack pattern) {
        try {
            var api = Class.forName("io.github.lounode.ae2pattern.api.PatternDiskApi");
            var sinkType = Class.forName("io.github.lounode.ae2pattern.api.BlankPatternSink");
            // Blank patterns the encoding would take or give back: the test has as many as it needs.
            var sink = Proxy.newProxyInstance(sinkType.getClassLoader(), new Class<?>[] { sinkType },
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "test blank pattern sink";
                        default -> true;
                    });
            var disk = new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("ae2_pattern_disk:pattern_disk_1k")));
            var leftover = (ItemStack) api.getMethod("insert", ItemStack.class, ItemStack.class, Level.class, sinkType)
                    .invoke(null, disk, pattern, helper.getLevel(), sink);
            helper.assertTrue(leftover.isEmpty(), "The 1k Pattern Disk must take the pattern");
            var disks = (InternalInventory) provider.getClass().getMethod("getDiskInventory").invoke(provider);
            return disks.addItems(disk).isEmpty();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("AE2 Pattern Disk's API changed", exception);
        }
    }

    /** AE2 Pattern Disk's provider runs the Endpoint in Local mode, with its pattern written to a disk. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalPatternDiskProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProvider("ae2_pattern_disk:pattern_disk_provider")
                .installingLocalPatternWith((provider, pattern) -> insertOnDisk(helper, (BlockEntity) provider, pattern));
        helper.succeedWhen(scene::tick);
    }

}
