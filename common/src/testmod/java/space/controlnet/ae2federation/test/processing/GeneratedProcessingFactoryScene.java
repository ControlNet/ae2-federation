package space.controlnet.ae2federation.test.processing;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

public final class GeneratedProcessingFactoryScene implements AutoCloseable {
    private enum Phase { SETUP, PLANNING, REJECTION_WINDOW, RELOAD_GAP, EXECUTION, COMPLETE }
    private final ProcessingBenchmarkProfile profile;
    private final ProcessingBenchmarkObservation.Scene kind;
    private final GeneratedFactoryTopology topology;
    private final GeneratedSceneFixture fixture;
    private final List<PatternParticipationReceipt> receipts = new ArrayList<>(256);
    private final List<EndpointCohortObservation> cohorts = new ArrayList<>(5);
    private ProcessingCraftingCoordinator crafting;
    private ControlledProcessingMachine machine;
    private Phase phase = Phase.SETUP;
    private ProcessingBenchmarkResult result;
    private GeneratedFactoryTopology.PatternRun currentRun;
    private ProcessingBenchmarkObservation.Snapshot jobStart;
    private ProcessingBenchmarkMeasurement measurement;
    private long currentRevision;
    private long startedNanos;
    private long targetBeforeRejection;
    private long rejectedBeforeWindow;
    private long rejectedTargetMutation;
    private long jobStartConsumedInput;
    private long jobStartPrimaryOutput;
    private long jobStartByproductOutput;
    private int measuredJobs;
    private int totalSubmitted;
    private boolean warmup;
    private String readiness = "not-started";
    public GeneratedProcessingFactoryScene(GameTestHelper helper, ProcessingBenchmarkProfile profile,
            ProcessingBenchmarkObservation.Scene kind) {
        this.profile = profile;
        this.kind = kind;
        topology = new GeneratedFactoryTopology(profile.seed(),
                kind == ProcessingBenchmarkObservation.Scene.NATIVE ? 0 : profile.logicalLanes());
        fixture = new GeneratedSceneFixture(helper, profile, kind, topology);
        this.helper = helper;
    }
    private final GameTestHelper helper;
    public boolean ready() {
        if (phase != Phase.SETUP) {
            return true;
        }
        if (crafting != null && !crafting.ready(fixture.sourceNode())) {
            readiness = "crafting-" + crafting.readiness();
            return false;
        }
        if (!fixture.ready()) {
            readiness = fixture.status();
            return false;
        }
        var networkId = space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.confirmedNetworkId(fixture.sourceGrid());
        if (networkId.isEmpty()) {
            readiness = "source-identity-pending";
            return false;
        }
        if (crafting == null) {
            crafting = new ProcessingCraftingCoordinator(helper, kind, networkId.orElseThrow());
            readiness = "crafting-grid-created";
            return false;
        }
        if (!topology.ready(fixture.sourceGrid(), fixture.targetGrids())) {
            readiness = "generated-topology-pending";
            return false;
        }
        readiness = "ready";
        return true;
    }
    public void start() {
        if (phase != Phase.SETUP) {
            throw new IllegalStateException("Generated Processing scene already started");
        }
        ProcessingBenchmarkObservation.reset(kind);
        ProcessingBenchmarkObservation.registerLanes(kind, fixture.lanes());
        crafting.insertStartingInput(profile.startingResourceUnits());
        machine = new ControlledProcessingMachine(kind, fixture.nativeFixture(), fixture.federationFixture(), crafting,
                profile);
        startedNanos = System.nanoTime();
        startJob(scheduleOffset(), false);
    }
    public boolean tick() {
        ProcessingBenchmarkObservation.select(kind);
        switch (phase) {
            case SETUP -> throw new IllegalStateException("Generated Processing scene was not started");
            case PLANNING -> tickPlanning();
            case REJECTION_WINDOW -> tickRejectionWindow();
            case RELOAD_GAP -> tickReloadGap();
            case EXECUTION -> tickExecution();
            case COMPLETE -> { return true; }
        }
        return phase == Phase.COMPLETE;
    }
    private void tickPlanning() {
        if (!crafting.planReady()) {
            return;
        }
        if (!crafting.planIsComplete()) {
            throw new IllegalStateException("Generated Processing plan is incomplete");
        }
        var rejection = !warmup && requiresRejectionWindow();
        if (rejection) {
            fixture.fillTarget();
            targetBeforeRejection = fixture.targetInput();
            rejectedBeforeWindow = ProcessingBenchmarkObservation.snapshot(kind).rejectedPushes();
        }
        if (!crafting.submit()) {
            return;
        }
        totalSubmitted++;
        phase = rejection ? Phase.REJECTION_WINDOW : Phase.EXECUTION;
    }
    private void tickRejectionWindow() {
        crafting.observeCpuTick();
        var snapshot = ProcessingBenchmarkObservation.snapshot(kind);
        if (snapshot.rejectedPushes() <= rejectedBeforeWindow) {
            return;
        }
        rejectedTargetMutation = Math.max(rejectedTargetMutation, fixture.targetInput() - targetBeforeRejection);
        if (measuredJobs == 0) {
            crafting.beginRequesterReload();
            phase = Phase.RELOAD_GAP;
        } else {
            fixture.clearTarget();
            phase = Phase.EXECUTION;
        }
    }
    private void tickReloadGap() {
        crafting.finishRequesterReload();
        fixture.clearTarget();
        phase = Phase.EXECUTION;
    }
    private void tickExecution() {
        crafting.observeCpuTick();
        machine.tick();
        if (!crafting.complete((long) totalSubmitted * profile.primaryOutputUnits())) {
            return;
        }
        if (!warmup && measuredJobs + 1 == profile.measurementSamples()
                && byproductAmount() < (long) totalSubmitted * profile.byproductOutputUnits()) {
            return;
        }
        recordParticipation();
        if (warmup) {
            finishResult();
            return;
        }
        measuredJobs++;
        finishCohortIfComplete();
        if (measuredJobs < profile.measurementSamples()) {
            startJob(scheduleOffset() + measuredJobs, false);
        } else if (hasParticipationWarmup()) {
            measurement = measurement();
            startJob(255, true);
        } else {
            measurement = measurement();
            finishResult();
        }
    }
    private void startJob(int scheduleIndex, boolean participationWarmup) {
        warmup = participationWarmup;
        currentRun = topology.patternSchedule().get(scheduleIndex);
        currentRevision = fixture.selectPattern(currentRun);
        machine.selectPattern(currentRun.patternSlot());
        machine.selectCohortScenario(currentScenario());
        startCohortIfNeeded();
        jobStart = ProcessingBenchmarkObservation.snapshot(kind);
        jobStartConsumedInput = machine.consumedInput();
        jobStartPrimaryOutput = machine.producedPrimary();
        jobStartByproductOutput = machine.producedByproduct();
        crafting.begin(ProcessingBenchmarkPatternMarker.key(currentRun.patternSlot()), profile.primaryOutputUnits());
        phase = Phase.PLANNING;
    }
    private void recordParticipation() {
        var end = ProcessingBenchmarkObservation.snapshot(kind);
        receipts.add(new PatternParticipationReceipt(currentRun.scheduleIndex(), currentRun.patternSlot(),
                 currentRevision, currentRun.laneIndex(), end.plannerCalls() - jobStart.plannerCalls(),
                 end.submittedJobs() - jobStart.submittedJobs(), end.cpuBusyTicks() - jobStart.cpuBusyTicks(),
                 machine.consumedInput() - jobStartConsumedInput,
                 end.machineCompletions() - jobStart.machineCompletions(), fixture.returnOwner(currentRun.laneIndex()),
                 machine.producedPrimary() - jobStartPrimaryOutput,
                 machine.producedByproduct() - jobStartByproductOutput, warmup));
    }
    private void startCohortIfNeeded() {
        if (isCohortScene() && measuredJobs % 3 == 0 && !warmup) {
            var cohort = topology.endpointCohorts().get(measuredJobs / 3);
            ProcessingBenchmarkObservation.startCohort(cohort.cohortId(), cohort.scenario(), cohort.laneIndexes());
        }
    }
    private void finishCohortIfComplete() {
        ProcessingEndpointCohortRecorder.finishIfComplete(isCohortScene(), measuredJobs, topology, fixture, cohorts);
    }

    private void finishResult() {
        fixture.restoreSeededMappings();
        var patterns = ProcessingBenchmarkTopologyView.patterns(fixture);
        result = new ProcessingBenchmarkResult(topology.runtimeGridCount(fixture.sourceGrid(), fixture.targetGrids()),
                patterns.size(), patterns.size(), fixture.lanes().size(),
                ProcessingBenchmarkTopologyView.providerEntries(fixture, patterns),
                ProcessingBenchmarkTopologyView.providerEntries(fixture, patterns), fixture.lanes().size(),
                crafting.cpuCount(), profile.startingResourceUnits(), measurement.acceptedInput(),
                rejectedTargetMutation, measurement.deliveredPrimary(), measurement.deliveredByproduct(),
                measurement.finalInput(), topology.generationDigest(), topology.scheduleDigest(),
                topology.deterministicReloadMatches(), crafting.originalLinkId(), crafting.reloadedLinkId(),
                measurement.observation(), receipts, cohorts, new long[] { measurement.elapsedNanos() });
        phase = Phase.COMPLETE;
    }
    private ProcessingBenchmarkMeasurement measurement() {
        return ProcessingBenchmarkMeasurement.capture(machine, crafting, kind, startedNanos);
    }

    private int scheduleOffset() { return profile.batchUnits() == profile.largeBatchUnits() ? 0 : 15; }
    private boolean hasParticipationWarmup() { return profile.batchUnits() == profile.smallBatchUnits(); }
    private boolean isCohortScene() {
        return kind == ProcessingBenchmarkObservation.Scene.FEDERATION
                && profile.batchUnits() == profile.largeBatchUnits();
    }
    private EndpointCohortScenario currentScenario() {
        return isCohortScene() ? topology.endpointCohorts().get(measuredJobs / 3).scenario() : null;
    }
    private boolean requiresRejectionWindow() {
        var scenario = currentScenario();
        return measuredJobs == 0 || scenario == EndpointCohortScenario.REJECTING;
    }
    private long byproductAmount() { return ProcessingBenchmarkMeasurement.byproductAmount(crafting); }

    public String readiness() { return readiness; }
    public ProcessingBenchmarkResult result() { return java.util.Objects.requireNonNull(result); }
    public ProcessingBenchmarkResult resultOrNull() { return result; }
    public boolean started() { return phase != Phase.SETUP; }
    public ProcessingBenchmarkObservation.Scene kind() { return kind; }
    public String progress() { return phase + ",jobs=" + measuredJobs + ",submitted=" + totalSubmitted; }

    @Override
    public void close() {
        if (crafting != null) crafting.close();
        fixture.close();
    }

}
