package space.controlnet.ae2federation.test.processing;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

final class GeneratedSceneFixture implements AutoCloseable {
    private final GameTestHelper helper;
    private final ProcessingBenchmarkProfile profile;
    private final ProcessingBenchmarkObservation.Scene kind;
    private final GeneratedFactoryTopology topology;
    private final NativeProviderLaneFixtures nativeFixture;
    private final GeneratedFederationTargets federationFixture;
    private boolean registered;
    private String status = "created";

    GeneratedSceneFixture(GameTestHelper helper, ProcessingBenchmarkProfile profile,
            ProcessingBenchmarkObservation.Scene kind, GeneratedFactoryTopology topology) {
        this.helper = helper;
        this.profile = profile;
        this.kind = kind;
        this.topology = topology;
        var assignments = ProcessingBenchmarkPatternCatalog.assignments(topology);
        nativeFixture = kind == ProcessingBenchmarkObservation.Scene.NATIVE
                ? new NativeProviderLaneFixtures(helper, assignments, true, profile.physicalPatterns()) : null;
        federationFixture = kind == ProcessingBenchmarkObservation.Scene.FEDERATION
                ? new GeneratedFederationTargets(helper, assignments, profile.physicalPatterns(),
                        topology.targetPositions()) : null;
    }

    boolean ready() {
        if (kind == ProcessingBenchmarkObservation.Scene.FEDERATION) {
            var ready = federationFixture.initialize();
            status = ready ? "ready" : "federation-" + federationFixture.status();
            if (ready && !registered) {
                ProcessingBenchmarkPatternCatalog.install(federationFixture, profile);
                registered = true;
            }
            return ready;
        }
        if (!nativeFixture.connectEnergy()) {
            status = "native-energy-pending";
            return false;
        }
        if (!registered) {
            nativeFixture.register();
            ProcessingBenchmarkPatternCatalog.install(nativeFixture, profile);
            registered = true;
        }
        status = "ready";
        return true;
    }

    String status() {
        return status;
    }

    NativeProviderLaneFixtures nativeFixture() {
        return nativeFixture;
    }

    GeneratedFederationTargets federationFixture() {
        return federationFixture;
    }

    List<PatternProviderLogic> lanes() {
        return java.util.stream.IntStream.range(0, profile.logicalLanes())
                .mapToObj(index -> kind == ProcessingBenchmarkObservation.Scene.NATIVE
                        ? (PatternProviderLogic) nativeFixture.lane(index) : federationFixture.providerLogic(index))
                .toList();
    }

    IGridNode sourceNode() {
        return kind == ProcessingBenchmarkObservation.Scene.NATIVE
                ? nativeFixture.managedNode().getNode() : federationFixture.sourceNode();
    }

    IGrid sourceGrid() {
        return kind == ProcessingBenchmarkObservation.Scene.NATIVE
                ? nativeFixture.managedNode().getGrid() : federationFixture.sourceGrid();
    }

    List<IGrid> targetGrids() {
        return kind == ProcessingBenchmarkObservation.Scene.NATIVE ? List.of() : federationFixture.targetGrids();
    }

    long targetInput() {
        return kind == ProcessingBenchmarkObservation.Scene.NATIVE
                ? nativeFixture.targetItemCount(Items.COBBLESTONE)
                : federationFixture.targetAmount(AEItemKey.of(Items.COBBLESTONE));
    }

    void fillTarget() {
        if (kind == ProcessingBenchmarkObservation.Scene.FEDERATION) {
            federationFixture.fillTargets(AEItemKey.of(Items.COBBLESTONE));
            return;
        }
        var chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(
                helper.absolutePos(NativeProviderLaneFixtures.TARGET_POS));
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            chest.setItem(slot, new ItemStack(Items.SAND, 64));
        }
    }

    void clearTarget() {
        if (kind == ProcessingBenchmarkObservation.Scene.FEDERATION) {
            federationFixture.clearTargets(AEItemKey.of(Items.COBBLESTONE));
        } else {
            ((ChestBlockEntity) helper.getLevel().getBlockEntity(
                    helper.absolutePos(NativeProviderLaneFixtures.TARGET_POS))).clearContent();
        }
    }

    long selectPattern(GeneratedFactoryTopology.PatternRun run) {
        for (int slot = 0; slot < profile.logicalPatterns(); slot++) {
            mapPattern(slot, slot == run.patternSlot() ? Set.of(run.laneIndex()) : Set.of());
        }
        return mappingRevision(run.patternSlot());
    }

    void restoreSeededMappings() {
        for (int slot = 0; slot < profile.logicalPatterns(); slot++) {
            mapPattern(slot, topology.patternLaneAssignments().get(slot));
        }
    }

    String returnOwner(int laneIndex) {
        if (kind == ProcessingBenchmarkObservation.Scene.NATIVE) {
            return "native:" + laneIndex;
        }
        var owner = federationFixture.endpointBinding(laneIndex).runtime().itemReturnContext().orElseThrow().owner();
        if (owner.logic() != federationFixture.providerLogic(laneIndex)
                || owner.lane().isEmpty()) {
            throw new IllegalStateException("Federation return owner does not match the selected Lane");
        }
        return owner.lane().orElseThrow().toString();
    }

    String endpointIdentity(int laneIndex) {
        return kind == ProcessingBenchmarkObservation.Scene.NATIVE
                ? "native:" + laneIndex : federationFixture.endpointIdentity(laneIndex);
    }

    private void mapPattern(int slot, Set<Integer> lanes) {
        if (kind == ProcessingBenchmarkObservation.Scene.NATIVE) {
            var provider = nativeFixture.composition();
            if (!provider.replaceMapping(provider.mappingHandle(slot), lanes)) {
                throw new IllegalStateException("Native Pattern Lane mapping changed concurrently");
            }
        } else {
            federationFixture.mapPattern(slot, lanes);
        }
    }

    private long mappingRevision(int slot) {
        return kind == ProcessingBenchmarkObservation.Scene.NATIVE
                ? nativeFixture.composition().mappingHandle(slot).generation()
                : federationFixture.mappingRevision(slot);
    }

    @Override
    public void close() {
        if (nativeFixture != null) {
            nativeFixture.close();
        }
        if (federationFixture != null) {
            federationFixture.close();
        }
    }
}
