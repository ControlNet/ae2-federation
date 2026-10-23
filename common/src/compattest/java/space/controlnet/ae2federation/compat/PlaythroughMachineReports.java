package space.controlnet.ae2federation.compat;

import com.google.gson.GsonBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Machine reports preserve all Phase 1 evidence; separate path runs can be joined by task identity. */
final class PlaythroughMachineReports {
    /**
     * Records every machine step of the proof with its planned runs per layout. A step is observed in a layout that
     * passed with runs above zero, since the job completes only after every output of those runs arrived. Only
     * observed steps are listed as real machine recipes; a planned step with zero runs is not machine coverage.
     */
    static void steps(Map<String, Object> row, PlaythroughRecipes.Proof proof, net.minecraft.server.level.ServerLevel level,
                      PlaythroughFactory.Result baseline, PlaythroughFactory.Result federated) {
        var result = new java.util.TreeMap<String, Object>();
        var real = new java.util.TreeSet<String>();
        if (proof != null) proof.machines().forEach((id, recipe) -> {
            var inputs = new java.util.TreeMap<String, Long>();
            var outputs = new java.util.TreeMap<String, Long>();
            var catalysts = new java.util.TreeMap<Integer, String>();
            recipe.inputs().forEach(stack -> inputs.merge(PlaythroughAccounting.name(stack.what(), level), stack.amount(), Math::addExact));
            recipe.outputs().forEach(stack -> outputs.merge(PlaythroughAccounting.name(stack.what(), level), stack.amount(), Math::addExact));
            recipe.catalysts().forEach((slot, stack) -> catalysts.put(slot, stack.toString()));
            var runs = new java.util.LinkedHashMap<String, Long>();
            var observed = new java.util.ArrayList<String>();
            for (var layout : List.of(Map.entry("baseline", baseline), Map.entry("federated", federated))) {
                Long count = layout.getValue().cleanup().get("machineRuns") instanceof Map<?, ?> planned
                        ? (Long) planned.get(id) : null;
                runs.put(layout.getKey(), count);
                if (layout.getValue().pass() && count != null && count > 0) observed.add(layout.getKey());
            }
            if (!observed.isEmpty()) real.add(id);
            result.put(id, Map.of("adapter", recipe.adapter(), "inputs", inputs, "outputs", outputs, "fixedCatalysts", catalysts,
                    "runs", runs, "observedIn", observed));
        });
        row.put("machineSteps", result);
        row.put("realMachineSteps", real);
    }

    static void write(Path directory, Map<String, Object> report) throws java.io.IOException {
        if (PlaythroughMachineAdapters.ENABLED.isEmpty()) return;
        var text = new StringBuilder("# Machine playthrough\n\nPath: ").append(PlaythroughMachines.PATH)
                .append(". Baseline: plain AE2 processing. Checkpoint: ").append(report.get("checkpointId"))
                .append(". Completed: ").append(report.get("completed"))
                .append(". Harness error: ").append(report.get("harnessError"))
                .append(".\n\nEnabled adapters: ").append(report.get("machineBackedRecipeClasses"))
                .append(".\n\n| Item | Task | Result | Real machine recipes | Planned, not observed | Remaining stand-ins |\n|---|---|---|---|---|---|\n");
        for (var value : (List<?>) report.get("targets")) {
            var row = (Map<?, ?>) value;
            text.append('|').append(row.get("item")).append('|').append(row.get("taskId")).append('|')
                    .append(row.get("classification")).append('|').append(escape(row.get("realMachineSteps")))
                    .append('|').append(escape(unobserved(row))).append('|').append(escape(row.get("standIns"))).append("|\n");
        }
        text.append("\nUnreadable machine recipes (remain stand-ins): ").append(escape(report.get("machineRejections")))
                .append("\n\nFull inventories, signatures, extraction counts, machine buffers and transit checks are in JSON.\n");
        PlaythroughCheckpoint.write(directory.resolve("playthrough-machines.md"), text.toString());
        PlaythroughCheckpoint.write(directory.resolve("playthrough-machines.json"),
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report));
    }
    private static Object unobserved(Map<?, ?> row) {
        if (!(row.get("machineSteps") instanceof Map<?, ?> steps)) return List.of();
        var real = row.get("realMachineSteps") instanceof java.util.Collection<?> observed ? observed : List.of();
        return steps.keySet().stream().filter(id -> !real.contains(id)).toList();
    }
    private static String escape(Object value) { return String.valueOf(value).replace("|", "\\|").replace("\n", " "); }
}
