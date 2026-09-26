package space.controlnet.ae2federation.test.mixed;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public final class MixedFactoryBenchmarkState {
    private final GameTestHelper helper;
    private final MixedFactoryProfile profile;
    private final List<MixedFactoryIteration> iterations = new ArrayList<>();
    private MixedFactoryScene scene;
    private long startedNanos;

    public MixedFactoryBenchmarkState(GameTestHelper helper, MixedFactoryProfile profile) {
        this.helper = helper;
        this.profile = profile;
        MixedFactoryRuntimeReceipt.begin(profile);
    }

    public boolean tick() {
        if (iterations.size() == profile.totalIterations()) return true;
        if (scene == null) {
            var index = iterations.size();
            scene = new MixedFactoryScene(helper, profile, index, index < profile.warmupIterations());
            startedNanos = System.nanoTime();
        }
        if (!scene.ready() || !scene.tick()) return false;
        var observation = scene.observation();
        validate(observation);
        var elapsedNanos = Math.max(1, System.nanoTime() - startedNanos);
        MixedFactoryRuntimeReceipt.endScene(elapsedNanos);
        iterations.add(new MixedFactoryIteration(iterations.size(), observation.warmup(),
                elapsedNanos, observation, scene.initialSource(), scene.finalSource(),
                scene.callbackResults(), scene.finalStocked(), scene.finalExported()));
        scene.close();
        scene = null;
        resetNativeScene();
        if (iterations.size() == profile.totalIterations()) {
            MixedFactoryEvidence.writeBenchmark(profile, List.copyOf(iterations));
            return true;
        }
        return false;
    }

    private void resetNativeScene() {
        for (var x = 1; x <= 8; x++) {
            for (var y = 1; y <= 7; y++) {
                for (var z = 1; z <= 8; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        }
    }

    public String progress() {
        return "iteration=" + iterations.size() + "/" + profile.totalIterations() + ",scene="
                + (scene == null ? "reset" : scene.progress());
    }

    private void validate(MixedFactoryObservation.Snapshot observation) {
        var logicalOrders = profile.blockedLanes() + 1;
        if (observation.planning() != Math.multiplyExact(logicalOrders, 2)
                || observation.submitted() != logicalOrders
                || observation.craftingIds().size() != 2 || observation.waitingJobs().size() != profile.blockedLanes()
                || observation.executing() < profile.recipeChainLength() + profile.blockedLanes()
                || observation.handlers().size() < profile.recipeChainLength() + profile.blockedLanes()
                || observation.stocking().size() != profile.stockingCycles()
                || observation.peakInFlight() > profile.cpuLimit()) {
            throw new IllegalStateException("Mixed native iteration did not satisfy configured workload axes: planning="
                    + observation.planning() + ",submitted=" + observation.submitted() + ",jobs="
                    + observation.craftingIds().size() + ",waiting=" + observation.waitingJobs().size()
                    + ",executing=" + observation.executing() + ",handlers=" + observation.handlers().size()
                    + ",stocking=" + observation.stocking().size() + ",peak=" + observation.peakInFlight());
        }
    }
}
