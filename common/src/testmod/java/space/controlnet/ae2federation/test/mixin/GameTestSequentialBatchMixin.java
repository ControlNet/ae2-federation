package space.controlnet.ae2federation.test.mixin;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestBatchFactory;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.FederationTestMod;

/**
 * TEST-ONLY, development batch selection: vanilla groups tests by batch name and runs a batch's tests at the same time.
 * These tests share level-wide registries (domains, policies, Endpoint bindings), so each selected test gets its own
 * batch and they run one after another in the requested order. Before each batch the test id property names only that
 * test, so evidence and receipts read the same id as in a single-test run; after each batch its test area is cleared.
 */
@Mixin(GameTestBatchFactory.class)
abstract class GameTestSequentialBatchMixin {
    @Inject(method = "fromTestFunction", at = @At("HEAD"), cancellable = true)
    private static void ae2federation$oneTestPerBatch(Collection<TestFunction> functions, ServerLevel level,
            CallbackInfoReturnable<Collection<GameTestBatch>> result) {
        if (!"batch".equals(System.getProperty("ae2federation.testSelection"))) {
            return;
        }
        var order = FederationTestMod.batchIds(System.getProperty("ae2federation.testId", ""));
        List<GameTestBatch> batches = functions.stream()
                .sorted(Comparator.comparingInt(function -> order.indexOf(shortName(function))))
                .map(function -> {
                    var batch = GameTestBatchFactory.toGameTestBatch(
                            List.of(GameTestBatchFactory.toGameTestInfo(function, 0, level)),
                            function.batchName() + "/" + shortName(function), 0);
                    return new GameTestBatch(batch.name(), batch.gameTestInfos(),
                            batchLevel -> {
                                removeMockPlayers(batchLevel);
                                // Evidence writers name the running test by this property, as they do when it runs alone.
                                System.setProperty("ae2federation.testId", shortName(function));
                                batch.beforeBatchFunction().accept(batchLevel);
                            }, batchLevel -> {
                                batch.afterBatchFunction().accept(batchLevel);
                                clearTestAreas(batch, batchLevel);
                            });
                })
                .toList();
        result.setReturnValue(batches);
    }

    /**
     * Mock players from an earlier test stay on the player list; their embedded connections negotiated no mod payloads,
     * so a later test's AE2 packets to them would crash the server. Each test starts with no players, as it would alone.
     */
    private static void removeMockPlayers(ServerLevel level) {
        var players = level.getServer().getPlayerList();
        List.copyOf(players.getPlayers()).forEach(players::remove);
    }

    /**
     * A finished test's blocks would otherwise keep ticking: its AE2 grids, Providers and Endpoints would count in later
     * tests' level-wide observations. Clearing the area removes them the way breaking the blocks would.
     */
    private static void clearTestAreas(GameTestBatch batch, ServerLevel level) {
        for (var info : batch.gameTestInfos()) {
            var structure = info.getStructureBlockEntity();
            if (structure != null) {
                StructureUtils.clearSpaceForStructure(StructureUtils.getStructureBoundingBox(structure), level);
            }
        }
    }

    /** The method name as the batch list names it: test names carry a class prefix unless the class opts out. */
    private static String shortName(TestFunction function) {
        var name = function.testName().toLowerCase(java.util.Locale.ROOT);
        var dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }
}
