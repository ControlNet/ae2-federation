package space.controlnet.ae2federation.test.processing;

import appeng.api.networking.IGrid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;

final class GeneratedFactoryTopology {
    private static final int GRID_COUNT = 16;
    private static final int PATTERN_COUNT = 256;
    private static final int LANE_COUNT = 15;

    private final long seed;
    private final int targetCount;
    private final List<BlockPos> targetPositions;
    private final List<Set<Integer>> patternLaneAssignments;
    private final List<PatternRun> patternSchedule;
    private final List<EndpointCohort> endpointCohorts;
    private final String generationDigest;
    private final String scheduleDigest;

    GeneratedFactoryTopology(long seed, int targetCount) {
        if (targetCount != 0 && targetCount != LANE_COUNT) {
            throw new IllegalArgumentException("Generated target Grid count must be zero or 15");
        }
        this.seed = seed;
        this.targetCount = targetCount;
        var random = new Random(seed);
        targetPositions = positions(random, targetCount);
        var patternOrder = shuffledIndexes(random, PATTERN_COUNT);
        var laneOrder = shuffledIndexes(random, LANE_COUNT);
        patternLaneAssignments = assignments(patternOrder, laneOrder);
        patternSchedule = schedule(patternOrder, laneOrder);
        endpointCohorts = cohorts(laneOrder);
        generationDigest = sha256(serializedTopology());
        scheduleDigest = sha256(patternSchedule + "|" + endpointCohorts);
    }

    List<BlockPos> targetPositions() {
        return targetPositions;
    }

    List<Set<Integer>> patternLaneAssignments() {
        return patternLaneAssignments;
    }

    List<PatternRun> patternSchedule() {
        return patternSchedule;
    }

    List<EndpointCohort> endpointCohorts() {
        return endpointCohorts;
    }

    boolean ready(IGrid source, List<IGrid> targets) {
        return runtimeGridCount(source, targets) == 1 + targetPositions.size();
    }

    int runtimeGridCount(IGrid source, List<IGrid> targets) {
        var grids = Collections.newSetFromMap(new IdentityHashMap<IGrid, Boolean>());
        grids.add(source);
        grids.addAll(targets);
        return grids.size();
    }

    String generationDigest() {
        return generationDigest;
    }

    String scheduleDigest() {
        return scheduleDigest;
    }

    boolean deterministicReloadMatches() {
        var regenerated = new GeneratedFactoryTopology(seed, targetCount);
        return generationDigest.equals(regenerated.generationDigest)
                && scheduleDigest.equals(regenerated.scheduleDigest);
    }

    private String serializedTopology() {
        var assignments = patternLaneAssignments.stream()
                .map(lanes -> lanes.stream().sorted().map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(",")))
                .collect(java.util.stream.Collectors.joining(";"));
        return targetPositions + "|" + assignments;
    }

    private static List<BlockPos> positions(Random random, int count) {
        var candidates = new ArrayList<BlockPos>();
        for (int x : new int[] { 1, 5, 9, 13 }) {
            for (int z : new int[] { 1, 5, 9, 13 }) {
                candidates.add(new BlockPos(x, 6, z));
            }
        }
        Collections.shuffle(candidates, random);
        return List.copyOf(candidates.subList(0, count));
    }

    private static List<Integer> shuffledIndexes(Random random, int count) {
        var indexes = new ArrayList<Integer>(count);
        for (int index = 0; index < count; index++) {
            indexes.add(index);
        }
        Collections.shuffle(indexes, random);
        return List.copyOf(indexes);
    }

    private static List<Set<Integer>> assignments(List<Integer> patterns, List<Integer> lanes) {
        var result = new ArrayList<Set<Integer>>(Collections.nCopies(PATTERN_COUNT, Set.of()));
        for (int order = 0; order < patterns.size(); order++) {
            var slot = patterns.get(order);
            result.set(slot, order == PATTERN_COUNT - 1 ? Set.copyOf(lanes)
                    : Set.of(lanes.get(order % LANE_COUNT)));
        }
        return List.copyOf(result);
    }

    private static List<PatternRun> schedule(List<Integer> patterns, List<Integer> lanes) {
        var result = new ArrayList<PatternRun>(PATTERN_COUNT);
        for (int order = 0; order < patterns.size(); order++) {
            result.add(new PatternRun(order, patterns.get(order), lanes.get(order % LANE_COUNT)));
        }
        return List.copyOf(result);
    }

    private static List<EndpointCohort> cohorts(List<Integer> endpointOrder) {
        var result = new ArrayList<EndpointCohort>(5);
        for (int cohort = 0; cohort < 5; cohort++) {
            result.add(new EndpointCohort(cohort, EndpointCohortScenario.values()[cohort],
                    List.copyOf(endpointOrder.subList(cohort * 3, cohort * 3 + 3))));
        }
        return List.copyOf(result);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record PatternRun(int scheduleIndex, int patternSlot, int laneIndex) {
    }

    record EndpointCohort(int cohortId, EndpointCohortScenario scenario, List<Integer> laneIndexes) {
        EndpointCohort {
            java.util.Objects.requireNonNull(scenario);
            laneIndexes = List.copyOf(laneIndexes);
            if (laneIndexes.size() != 3 || laneIndexes.stream().distinct().count() != 3) {
                throw new IllegalArgumentException("Endpoint cohort must contain three distinct Lanes");
            }
        }
    }
}
