package space.controlnet.ae2federation.compat;

import com.google.gson.GsonBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Machine reports preserve all Phase 1 evidence; separate path runs can be joined by task identity. */
final class PlaythroughMachineReports {
    static Map<String, Object> steps(PlaythroughRecipes.Proof proof, net.minecraft.server.level.ServerLevel level) {
        var result = new java.util.TreeMap<String, Object>();
        if (proof == null) return result;
        proof.machines().forEach((id, recipe) -> {
            var inputs = new java.util.TreeMap<String, Long>();
            var outputs = new java.util.TreeMap<String, Long>();
            var catalysts = new java.util.TreeMap<Integer, String>();
            recipe.inputs().forEach(stack -> inputs.merge(PlaythroughAccounting.name(stack.what(), level), stack.amount(), Math::addExact));
            recipe.outputs().forEach(stack -> outputs.merge(PlaythroughAccounting.name(stack.what(), level), stack.amount(), Math::addExact));
            recipe.catalysts().forEach((slot, stack) -> catalysts.put(slot, stack.toString()));
            result.put(id, Map.of("adapter", recipe.adapter(), "inputs", inputs, "outputs", outputs, "fixedCatalysts", catalysts));
        });
        return result;
    }

    static void write(Path directory, Map<String, Object> report) throws java.io.IOException {
        if (PlaythroughMachineAdapters.ENABLED.isEmpty()) return;
        var text = new StringBuilder("# Machine playthrough\n\nPath: ").append(PlaythroughMachines.PATH)
                .append(". Baseline: plain AE2 processing. Checkpoint: ").append(report.get("checkpointId"))
                .append(". Completed: ").append(report.get("completed"))
                .append(". Harness error: ").append(report.get("harnessError"))
                .append(".\n\nEnabled adapters: ").append(report.get("machineBackedRecipeClasses"))
                .append(".\n\n| Item | Task | Result | Real machine recipes | Remaining stand-ins |\n|---|---|---|---|---|\n");
        for (var value : (List<?>) report.get("targets")) {
            var row = (Map<?, ?>) value;
            text.append('|').append(row.get("item")).append('|').append(row.get("taskId")).append('|')
                    .append(row.get("classification")).append('|').append(escape(row.get("machineSteps")))
                    .append('|').append(escape(row.get("standIns"))).append("|\n");
        }
        text.append("\nUnreadable machine recipes (remain stand-ins): ").append(escape(report.get("machineRejections")))
                .append("\n\nFull inventories, signatures, extraction counts, machine buffers and transit checks are in JSON.\n");
        PlaythroughCheckpoint.write(directory.resolve("playthrough-machines.md"), text.toString());
        PlaythroughCheckpoint.write(directory.resolve("playthrough-machines.json"),
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report));
    }
    private static String escape(Object value) { return String.valueOf(value).replace("|", "\\|").replace("\n", " "); }
}
